package com.gpsproxy.dev.domain.simulation

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.geo.GeoPoint
import com.gpsproxy.dev.core.geo.GeoUtils
import com.gpsproxy.dev.core.geo.RoutePath
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.core.log.LogSink
import com.gpsproxy.dev.core.random.DefaultRandomSource
import com.gpsproxy.dev.core.result.DomainError
import com.gpsproxy.dev.core.result.Outcome
import com.gpsproxy.dev.core.result.failure
import com.gpsproxy.dev.core.result.success
import com.gpsproxy.dev.core.time.Clock
import com.gpsproxy.dev.domain.settings.AppSettings
import com.gpsproxy.dev.domain.settings.DetailLevel
import com.gpsproxy.dev.domain.session.SessionRecorder
import com.gpsproxy.dev.domain.settings.SettingsGateway
import com.gpsproxy.dev.domain.simulation.blur.BlurPolicy
import com.gpsproxy.dev.domain.simulation.channel.ChannelMonitor
import com.gpsproxy.dev.domain.simulation.channel.InjectionStrategy
import com.gpsproxy.dev.domain.simulation.channel.InjectionStrategyFactory
import com.gpsproxy.dev.domain.simulation.model.ChannelState
import com.gpsproxy.dev.domain.simulation.model.MotionVector
import com.gpsproxy.dev.domain.simulation.model.RouteConfig
import com.gpsproxy.dev.domain.simulation.model.RouteKind
import com.gpsproxy.dev.domain.simulation.model.RouteProgress
import com.gpsproxy.dev.domain.simulation.model.RunState
import com.gpsproxy.dev.domain.simulation.model.SimMode
import com.gpsproxy.dev.domain.simulation.model.SimulationState
import com.gpsproxy.dev.domain.simulation.port.ContainerGateway
import com.gpsproxy.dev.domain.simulation.port.LocationSampleFactory
import com.gpsproxy.dev.domain.simulation.port.PermissionFacts
import com.gpsproxy.dev.domain.simulation.port.SystemMockGateway
import com.gpsproxy.dev.domain.simulation.pump.PublishOutcome
import com.gpsproxy.dev.domain.simulation.pump.RouteFrameSource
import com.gpsproxy.dev.domain.simulation.pump.SimulationPump
import com.gpsproxy.dev.domain.simulation.pump.SinglePointFrameSource
import com.gpsproxy.dev.domain.simulation.route.RoamConfig
import com.gpsproxy.dev.domain.simulation.route.RoamPlanner
import com.gpsproxy.dev.domain.simulation.route.RoutePlan
import com.gpsproxy.dev.domain.simulation.route.RouteProgressor
import com.gpsproxy.dev.domain.simulation.route.RouteRequest
import com.gpsproxy.dev.domain.simulation.route.RouteRuntime
import com.gpsproxy.dev.domain.simulation.route.RouteSource
import com.gpsproxy.dev.domain.simulation.route.RouteSourceKind
import com.gpsproxy.dev.domain.simulation.route.RouteUsageRecorder
import com.gpsproxy.dev.domain.simulation.state.RouteStartPayload
import com.gpsproxy.dev.domain.simulation.state.SimulationCommand
import com.gpsproxy.dev.domain.simulation.state.SimulationStateMachine
import com.gpsproxy.dev.domain.simulation.state.SimulationTransition
import com.gpsproxy.dev.domain.simulation.state.SingleStartPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 定位模拟的应用服务（Facade）。
 *
 * 对外只暴露与旧 `SimulationController` 等价的一组用例方法，内部把职责
 * 分给四个协作者：
 * - [SimulationStateMachine]：会话生命周期是否合法；
 * - [InjectionStrategy]：写哪条通道、如何准备与收尾；
 * - [SimulationPump] + FrameSource：多久推进一次、每帧推什么；
 * - [ChannelMonitor]：通道状态与命中计数的唯一持有者。
 *
 * 因此本类只剩"编排 + 错误收敛 + 状态投影"，不再包含轨迹数学、模糊推导、
 * 基站哈希或通道分支。改任何一个领域规则都不需要碰这里。
 *
 * 线程模型：所有状态迁移都在调用线程上同步完成（UI 事件或帧协程），
 * [scope] 只用于设置采集与帧循环；`MutableStateFlow` 保证跨线程可见性。
 */
class SimulationEngine(
    private val settingsGateway: SettingsGateway,
    private val permissions: PermissionFacts,
    private val mockGateway: SystemMockGateway,
    private val containerGateway: ContainerGateway,
    private val monitor: ChannelMonitor,
    private val strategyFactory: InjectionStrategyFactory,
    private val routeSource: RouteSource,
    private val samples: LocationSampleFactory,
    private val clock: Clock,
    private val log: LogSink,
    private val scope: CoroutineScope,
    private val sessionRecorder: SessionRecorder = SessionRecorder.Noop,
    private val roamPlanner: RoamPlanner = RoamPlanner(DefaultRandomSource()),
    private val routeUsageRecorder: RouteUsageRecorder = RouteUsageRecorder.Noop,
    /** 定时停止到期后的平台收尾（前台服务/悬浮球/提示），由组合根注入。 */
    private val onAutoStopFinished: () -> Unit = {},
    /** 运行期生成的地址文案按写入时语言固定。 */
    private val strings: () -> Strings = { StringsHolder.current },
) : com.gpsproxy.dev.domain.simulation.port.SimulationControl,
    com.gpsproxy.dev.domain.simulation.port.SimulationStarter {

    private val _state = MutableStateFlow(SimulationState())
    override val state: StateFlow<SimulationState> = _state.asStateFlow()

    val channels: StateFlow<List<ChannelState>> = monitor.states

    /** 设置快照：帧循环每帧读一次，避免在热路径上 suspend 读 DataStore。 */
    @Volatile
    private var currentSettings = AppSettings()

    private var blur: BlurPolicy = BlurPolicy.of(0)

    /**
     * 本次会话实际使用的通道。
     *
     * 与"设置里当前选的通道"分开保存：会话中途改设置不应切换通道，
     * 否则会出现前半程免 root、后半程 isMock 的割裂状态。
     */
    private var activeStrategy: InjectionStrategy? = null

    private var routeRuntime: RouteRuntime? = null
    private var lastBearing = 0f
    private var consecutiveFailures = 0

    /** 摇杆行走中的方向/速度；null 表示未行走。 */
    private var manualMotion: MotionVector? = null

    /** 模糊策略的缓存不是线程安全的：UI 线程与行走帧协程都会调用，统一加锁。 */
    private val blurLock = Any()

    private var autoStopJob: Job? = null
    private var autoStopDeadline = 0L

    private val stateMachine = SimulationStateMachine(strings)
    private val progressor = RouteProgressor()
    private val pump = SimulationPump(scope, clock, log)

    private var settingsJob: Job? = null

    /**
     * 会话录制事件串行化。
     *
     * 启动与停止可能在同一毫秒内连续发生（用户点了开始又立刻停止）。
     * 如果两个事件各自 `launch` 写盘，执行顺序无法保证，可能留下"已停止却仍标记运行中"
     * 的错误状态，导致下次开机多弹一次提醒。用单消费者通道保证 FIFO。
     */
    private val sessionEvents = Channel<SessionEvent>(Channel.UNLIMITED)

    /** 最近路线记录事件，同样串行化，避免连点开始造成乱序。 */
    private val routeEvents = Channel<RouteConfig>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (event in sessionEvents) {
                when (event) {
                    is SessionEvent.Started -> sessionRecorder.onStarted(event.state)
                    SessionEvent.Stopped -> sessionRecorder.onStopped()
                }
            }
        }
        scope.launch {
            for (config in routeEvents) {
                runCatching { routeUsageRecorder.onRouteStarted(config) }
                    .onFailure { log.warn { log.routeHistoryFailed(it.javaClass.simpleName) } }
            }
        }
        settingsJob = scope.launch {
            settingsGateway.settings.collect { settings ->
                val previousRadius = blur.radiusMeters
                currentSettings = settings
                val radius = if (settings.blurEnabled) settings.blurRadiusMeters.coerceIn(50, 5000) else 0
                if (radius != previousRadius) {
                    blur = BlurPolicy.of(radius)
                    // 设置流可能在选点之后才首次到达（控制器构造早于 UI 恢复上次坐标），
                    // 这里补一次重算，保证恢复出来的注入点也带上模糊偏移。
                    applyBlurToCurrentState()
                }
            }
        }
    }

    /** 首次就绪时写入默认坐标（通常来自上次会话或内置示例）。只在尚未选点时生效。 */
    override fun initialize(point: GeoPoint?, address: String) {
        if (_state.value.point != null || point == null) return
        _state.update {
            it.copy(
                point = point,
                injectedPoint = injected(point),
                blurRadiusMeters = blur.radiusMeters,
                address = address,
                accuracy = currentSettings.accuracyMeters,
            )
        }
    }

    /**
     * 用户选定新目标点。
     *
     * 轨迹巡航中改坐标会先停掉巡航（两者互斥），单点运行中则立即推送新坐标，
     * 不需要用户重新点开始。
     */
    override fun setPoint(point: GeoPoint, address: String) {
        if (_state.value.mode == SimMode.ROUTE) stop()
        _state.update {
            it.copy(
                point = point,
                injectedPoint = injected(point),
                blurRadiusMeters = blur.radiusMeters,
                address = address,
                errorCode = null,
            )
        }
        scope.launch { settingsGateway.rememberPoint(point.lat, point.lng, address) }
        log.info { log.targetSet(point.formatted(), address) }
        val current = _state.value
        if (current.mode == SimMode.SINGLE && current.runState == RunState.RUNNING) {
            val motion = manualMotion
            publish(
                current.injectedPoint ?: point,
                motion?.speedMps ?: 0f,
                motion?.normalizedBearing ?: current.bearing,
            )
        }
    }

    /**
     * 开始单点模拟。
     *
     * 前置条件由通道策略决定：
     * - 容器代理（默认，免 root）：要求容器运行时可用且有已导入应用；
     * - 系统 Mock：要求已被设为模拟位置应用。
     *
     * 首帧写入失败即视为启动失败，而不是让用户等三次重试。
     */
    override fun startSingle(): Outcome<Unit> {
        val target = _state.value.point
            ?: return abortStart(ErrorCode.COORD_0101, "尚未选择模拟位置")

        val strategy = strategyFactory.forChannel(currentSettings.injectionChannel)
        val readiness = strategy.prepare()
        if (!readiness.ready) {
            return abortStart(readiness.error ?: strategy.failureError, readiness.reason.ifBlank { strategy.failureReason })
        }

        val payload = SingleStartPayload(
            target = target,
            injected = injected(target),
            blurRadiusMeters = blur.radiusMeters,
            accuracyMeters = currentSettings.accuracyMeters,
            installedProviders = mockGateway.installedProviderNames,
            startedAt = clock.wallMillis(),
            channel = currentSettings.injectionChannel,
        )
        if (apply(SimulationCommand.StartSingle(payload)) == null) {
            return failure(ErrorCode.INJ_0202, "单点会话状态迁移被拒绝")
        }

        activeStrategy = strategy
        consecutiveFailures = 0
        manualMotion = null
        val firstSpeed = currentSettings.reportSpeedKmh / 3.6f
        val firstBearing = if (currentSettings.reportBearingDeg == 0) {
            lastBearing
        } else {
            currentSettings.reportBearingDeg.toFloat()
        }
        if (publish(payload.injected, firstSpeed, firstBearing) != PublishOutcome.PUBLISHED) {
            return abortStart(strategy.failureError, strategy.failureReason)
        }

        startSinglePump()
        recordSessionStarted()
        armAutoStop()
        val channelLabel = strings().settings.channelName(currentSettings.injectionChannel.name)
        log.info {
            log.singleStarted(
                channelLabel,
                if (blur.radiusMeters > 0) log.blurSuffix(blur.radiusMeters) else "",
            )
        }
        return success()
    }

    /**
     * 开始轨迹巡航。
     *
     * 按 [RouteKind] 选择轨迹来源：
     * - [RouteKind.POINT_TO_POINT]：高德/内置曲线，支持途经点；
     * - [RouteKind.ROAM]：随机闭合环线，强制循环；
     * - [RouteKind.IMPORTED]：直接使用导入的 GPX 折线。
     *
     * 三种来源最终都落到同一个 [RouteRuntime]，因此进度、暂停、倍速、
     * 跳转、反向、模糊与路线收藏无需分支。
     */
    override suspend fun startRoute(config: RouteConfig): Outcome<Unit> {
        val effective = sanitizeRouteConfig(config)
        val plan = when (effective.kind) {
            RouteKind.IMPORTED -> {
                if (effective.trackPoints.size < 2) {
                    return abortStart(ErrorCode.ROUTE_0401, "导入轨迹点数不足")
                }
                RoutePlan(effective.trackPoints, RouteSourceKind.IMPORTED)
            }

            RouteKind.ROAM -> {
                if (effective.roamRadiusMeters < RoamPlanner.MIN_RADIUS_METERS) {
                    return abortStart(ErrorCode.ROUTE_0201, "漫游半径过小")
                }
                roamPlanner.plan(
                    RoamConfig(
                        anchor = effective.start,
                        radiusMeters = effective.roamRadiusMeters,
                        speedKmh = effective.speedKmh,
                    ),
                )
            }

            RouteKind.POINT_TO_POINT -> runCatching {
                routeSource.plan(
                    RouteRequest(
                        start = effective.start,
                        end = effective.end,
                        waypoints = effective.waypoints,
                        mode = effective.mode,
                    ),
                )
            }.getOrNull() ?: return abortStart(ErrorCode.ROUTE_0302, "轨迹插值点为空")
        }
        if (plan.points.size < 2) {
            return abortStart(ErrorCode.ROUTE_0302, "轨迹插值点为空")
        }

        val basePath = RoutePath(plan.points)
        if (basePath.totalDistance < MIN_ROUTE_DISTANCE_METERS) {
            return abortStart(ErrorCode.ROUTE_0201, "轨迹总里程过短")
        }

        val strategy = strategyFactory.forChannel(currentSettings.injectionChannel)
        val readiness = strategy.prepare()
        if (!readiness.ready) {
            return abortStart(readiness.error ?: strategy.failureError, readiness.reason.ifBlank { strategy.failureReason })
        }

        val runtime = progressor.createRuntime(effective, basePath, blur)
        val start = runtime.path.positionAt(0.0)
            ?: return abortStart(ErrorCode.ROUTE_0302, "轨迹插值点为空")
        lastBearing = runtime.lastBearing
        val fullDistance = runtime.fullDistanceMeters
        val payload = RouteStartPayload(
            config = effective,
            target = plan.points.first(),
            injected = start.point,
            blurRadiusMeters = blur.radiusMeters,
            accuracyMeters = currentSettings.accuracyMeters,
            bearing = lastBearing,
            progress = RouteProgress(
                fraction = 0f,
                distanceDoneM = 0.0,
                distanceTotalM = fullDistance,
                remainingSeconds = (fullDistance / effective.speedMps).toInt(),
                pointIndex = 0,
                totalPoints = plan.points.size,
            ),
            installedProviders = mockGateway.installedProviderNames,
            startedAt = clock.wallMillis(),
            routeSource = plan.source,
            pathPoints = plan.points,
            degradeReason = plan.degradeReason,
            channel = currentSettings.injectionChannel,
        )
        if (apply(SimulationCommand.StartRoute(payload)) == null) {
            return failure(ErrorCode.INJ_0202, "轨迹会话状态迁移被拒绝")
        }

        routeRuntime = runtime
        activeStrategy = strategy
        consecutiveFailures = 0
        manualMotion = null
        if (publish(start.point, effective.speedMps.toFloat(), lastBearing) != PublishOutcome.PUBLISHED) {
            return abortStart(strategy.failureError, strategy.failureReason)
        }

        startRoutePump(runtime)
        recordSessionStarted()
        recordRouteStarted(effective)
        armAutoStop()
        val routeTexts = strings().route
        val kindName = routeTexts.routeKindLabel(effective.kind.name)
        val modeName = routeTexts.travelModeLabel(effective.mode.name)
        val sourceName = routeTexts.routeSourceLabel(plan.source.name)
        plan.degradeReason?.let { log.warn("route degraded to ${plan.source}: $it") }
        log.info {
            log.routeStarted(
                kindName,
                modeName,
                effective.speedKmh,
                "%.0f".format(basePath.totalDistance),
                plan.points.size,
                sourceName,
                if (blur.radiusMeters > 0) log.blurSuffix(blur.radiusMeters) else "",
            )
        }
        return success()
    }

    /** 暂停推送但保留当前坐标：应用仍会读到最后一次写入的值，不会回落到真实定位。 */
    fun pause() {
        if (apply(SimulationCommand.Pause) == null) return
        pump.stop()
        manualMotion = null
        log.info { log.paused }
    }

    /** 恢复推送。重启帧循环会重置计时，避免把暂停时长算进轨迹位移。 */
    fun resume() {
        if (apply(SimulationCommand.Resume) == null) return
        val runtime = routeRuntime
        if (_state.value.mode == SimMode.ROUTE && runtime != null) {
            startRoutePump(runtime)
        } else {
            startSinglePump()
        }
        log.info { log.resumed }
    }

    /**
     * 调整轨迹倍速（0.25–8）。
     *
     * 只改变运行时倍率，不重建轨迹、不改已走距离；暂停态下保持 speedKmh=0，
     * 但进度卡片上的剩余时间会立即按新速度重算。
     */
    fun setRouteSpeedFactor(factor: Float) {
        val runtime = routeRuntime ?: return
        val current = _state.value
        if (current.mode != SimMode.ROUTE) return
        if (current.runState != RunState.RUNNING && current.runState != RunState.PAUSED) return
        val clamped = factor.coerceIn(MIN_SPEED_FACTOR, MAX_SPEED_FACTOR)
        runtime.speedFactor = clamped
        val paused = current.runState == RunState.PAUSED
        _state.update {
            it.copy(
                speedFactor = clamped,
                speedKmh = if (paused) 0.0 else runtime.effectiveSpeedMps * 3.6,
                progress = it.progress?.copy(remainingSeconds = progressor.remainingSeconds(runtime)),
            )
        }
        log.info { log.speedFactorSet("%.2f".format(clamped)) }
    }

    /**
     * 跳转到轨迹的指定比例位置。
     *
     * 暂停态也会推一帧：用户显式改位置时，目标应用应当看到新坐标，
     * 但会话仍保持暂停，不会继续推进。
     */
    fun seekRouteFraction(fraction: Float) {
        val runtime = routeRuntime ?: return
        val current = _state.value
        if (current.mode != SimMode.ROUTE) return
        if (current.runState != RunState.RUNNING && current.runState != RunState.PAUSED) {
            if (current.runState == RunState.COMPLETED) {
                log.warn { log.seekIgnored }
            }
            return
        }
        val advance = progressor.seek(runtime, fraction) ?: return
        lastBearing = advance.bearing
        val paused = current.runState == RunState.PAUSED
        _state.update {
            it.copy(
                point = advance.targetPoint,
                injectedPoint = advance.injectedPoint,
                bearing = advance.bearing,
                progress = advance.progress,
                speedKmh = if (paused) 0.0 else advance.speedMps * 3.6,
            )
        }
        publish(
            advance.injectedPoint,
            if (paused) 0f else advance.speedMps,
            advance.bearing,
        )
        log.info { log.seeked((advance.progress.fraction * 100).toInt()) }
    }

    /** 反向巡航：下一帧起沿轨迹回退；循环模式会在起点回绕到终点。 */
    fun reverseRoute(): Boolean {
        val runtime = routeRuntime ?: return false
        val current = _state.value
        if (current.mode != SimMode.ROUTE) return false
        if (current.runState != RunState.RUNNING && current.runState != RunState.PAUSED) {
            log.warn { log.reverseIgnored }
            return false
        }
        runtime.direction = -runtime.direction
        log.info { log.direction(runtime.direction > 0) }
        return true
    }

    /** 完成后从头重播。 */
    fun restartRoute(): Boolean {
        val runtime = routeRuntime ?: return false
        if (apply(SimulationCommand.Restart) == null) return false
        progressor.reset(runtime)
        val first = progressor.seek(runtime, 0f) ?: return false
        lastBearing = first.bearing
        _state.update {
            it.copy(
                point = first.targetPoint,
                injectedPoint = first.injectedPoint,
                bearing = first.bearing,
                progress = first.progress,
                speedKmh = runtime.effectiveSpeedMps * 3.6,
                walking = false,
            )
        }
        publish(first.injectedPoint, first.speedMps, first.bearing)
        startRoutePump(runtime)
        armAutoStop()
        log.info { log.restarted }
        return true
    }

    /**
     * 摇杆行走：设置方向与速度，null 表示停止。
     *
     * 只在单点会话运行中生效；轨迹/漫游有自己的推进器，不接受手推。
     */
    fun setManualMotion(vector: MotionVector?) {
        val current = _state.value
        if (current.mode != SimMode.SINGLE || current.runState != RunState.RUNNING) {
            if (vector != null) log.warn { log.walkIgnored }
            return
        }
        val next = vector?.takeIf { it.speedMps > MIN_WALK_SPEED_MPS && it.bearingDeg.isFinite() }
        manualMotion = next
        _state.update {
            it.copy(
                walking = next != null,
                speedKmh = next?.let { v -> v.speedMps * 3.6 } ?: 0.0,
                bearing = next?.normalizedBearing ?: it.bearing,
            )
        }
    }

    /**
     * 停止模拟并恢复真实定位。
     *
     * 无论当前用的是哪条通道，都清理全部三处状态：官方测试提供者、
     * 容器内模拟模式、容器内伪造基站。遗漏任何一处都会让应用继续读到
     * 与环境不匹配的定位数据。
     */
    override fun stop() {
        pump.stop()
        cancelAutoStop()
        manualMotion = null
        runCatching { mockGateway.teardown() }
        runCatching { containerGateway.disableAll() }
        runCatching { containerGateway.clearCell() }
        activeStrategy = null
        routeRuntime = null
        consecutiveFailures = 0
        apply(SimulationCommand.Stop)
        clearRecordedSession()
        monitor.resetToStopped()
        log.info { log.stopped }
    }

    /**
     * 手动微调：以当前真实目标点为基准按米偏移。
     *
     * 基准永远是 [SimulationState.point] 而不是模糊后的注入坐标，
     * 否则用户按「向北」却看到标记往随机方向跳，操作方向会失真。
     */
    override fun nudge(northMeters: Double, eastMeters: Double) {
        val current = _state.value
        if (current.mode == SimMode.ROUTE && current.isActive) {
            log.warn { log.nudgeIgnored }
            return
        }
        val base = current.point ?: return
        val next = GeoUtils.offsetMeters(base, northMeters, eastMeters)
        setPoint(next, strings().route.manualNudge(next.formatted()))
    }

    /**
     * 设置位置模糊半径（米）。0 表示关闭模糊。
     *
     * 运行中改半径需要立即重算：单点模式重设当前注入坐标并推一帧，
     * 轨迹模式重建整条平移后的轨迹，进度保持不变。
     */
    override fun setBlurRadius(meters: Int) {
        val radius = meters.coerceIn(0, 5000)
        blur = BlurPolicy.of(radius)
        val target = _state.value.point
        if (target != null) {
            _state.update {
                it.copy(
                    injectedPoint = injected(target),
                    blurRadiusMeters = radius,
                )
            }
        }
        routeRuntime?.let { progressor.rebindBlur(it, blur) }
        val updated = _state.value
        if (updated.mode == SimMode.SINGLE && updated.runState == RunState.RUNNING) {
            val motion = manualMotion
            publish(
                updated.injectedPoint ?: target ?: return,
                motion?.speedMps ?: 0f,
                motion?.normalizedBearing ?: updated.bearing,
            )
        }
        log.info { if (radius > 0) log.blurEnabled(radius) else log.blurDisabled }
    }

    /**
     * 按当前环境刷新各通道的提示文案。
     *
     * 容器通道是否就绪取决于 :black 进程，系统 Mock 通道取决于开发者选项授权，
     * 两者都可能在本应用启动之后才发生变化，因此这里每次都重新探测。
     */
    fun refreshChannels() {
        monitor.refreshEnvironment(
            mockAllowed = permissions.isMockLocationAllowed(),
            containerAvailable = containerGateway.available,
            active = _state.value.isActive,
        )
    }

    /** 当前注入坐标系，前台通知与诊断报告会展示该值。 */
    fun currentCoordSystem() = currentSettings.coordSystem

    override fun currentSettings(): AppSettings = currentSettings

    /** 进程退出前调用：确保测试提供者被摘掉，不留残影响真实定位。 */
    fun stopForShutdown() {
        runCatching { stop() }
    }

    // ---------- 内部编排 ----------

    /** 应用一次状态迁移；被拒绝时返回 null，由调用方决定是否静默。 */
    private fun apply(command: SimulationCommand): SimulationState? =
        when (val transition = stateMachine.reduce(_state.value, command)) {
            is SimulationTransition.Accepted -> transition.state.also { _state.value = it }
            is SimulationTransition.Rejected -> null
        }

    /**
     * 统一推送入口。
     *
     * 连续失败达到阈值才判定通道失效：容器通道走 Binder，:black 进程重启或
     * 瞬时抖动都会让单次写入失败，只失败一两次就终止会让用户看到"刚开就报错"。
     */
    private fun publish(point: GeoPoint, speedMps: Float, bearing: Float): PublishOutcome {
        val strategy = activeStrategy ?: return PublishOutcome.FAILED
        val settings = currentSettings
        val sample = samples.sample(
            point = point,
            coordSystem = settings.coordSystem,
            accuracyMeters = settings.accuracyMeters,
            speedMps = speedMps,
            bearing = bearing,
            wallMillis = clock.wallMillis(),
            altitudeMeters = settings.altitudeMeters.toDouble(),
        )
        val report = strategy.publish(sample)
        if (!report.success) {
            consecutiveFailures++
            if (consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
                log.warn { log.pushRetry(consecutiveFailures, MAX_CONSECUTIVE_FAILURES) }
                return PublishOutcome.RETRY
            }
            pump.stop()
            cancelAutoStop()
            manualMotion = null
            apply(SimulationCommand.Fail(strategy.failureError, strategy.failureReason))
            log.error { log.injectionFailed(error.title(strategy.failureError)) }
            return PublishOutcome.FAILED
        }
        consecutiveFailures = 0
        monitor.recordHits(report.hits)
        _state.update {
            it.copy(
                accuracy = settings.accuracyMeters,
                altitude = sample.altitude,
                satellites = sample.satellites,
                speedKmh = speedMps * 3.6,
                bearing = bearing,
                injectedCount = it.injectedCount + report.injectedDelta,
            )
        }
        if (settings.detailLevel == DetailLevel.VERBOSE) {
            val channelLabel = _state.value.injectionChannel?.let {
                strings().settings.channelName(it.name)
            } ?: strings().settings.channelName(currentSettings.injectionChannel.name)
            log.info {
                log.injected(
                    point.formatted(),
                    "%.2f".format(speedMps),
                    "%.1f".format(bearing),
                    settings.accuracyMeters,
                    sample.satellites,
                    channelLabel,
                )
            }
        }
        return PublishOutcome.PUBLISHED
    }

    private fun startSinglePump() {
        val source = SinglePointFrameSource(
            state = { _state.value },
            settings = { currentSettings },
            samples = samples,
            publish = { point, speed, bearing -> publish(point, speed, bearing) },
            bearing = { lastBearing },
            failureError = { activeStrategy?.failureError ?: ErrorCode.INJ_0101 },
            motion = { manualMotion },
            onMoved = { next, bearing, speed -> onWalkAdvance(next, bearing, speed) },
        )
        pump.start(source) { error -> handleFrameFailure(error) }
    }

    /**
     * 行走推进的状态投影。
     *
     * 行走中的目标点持续变化，但 `DeterministicBlurPolicy` 的偏移只按半径缓存，
     * 因此"整段行走使用同一个模糊偏移"，不会出现每帧随机抖动。
     */
    private fun onWalkAdvance(next: GeoPoint, bearing: Float, speedMps: Float): GeoPoint? {
        if (!next.isValid() || kotlin.math.abs(next.lat) > MAX_WALK_LAT) {
            manualMotion = null
            _state.update { it.copy(walking = false, speedKmh = 0.0) }
            log.warn { log.walkLatitudeLimit }
            return null
        }
        val nextInjected = injected(next)
        _state.update {
            it.copy(
                point = next,
                injectedPoint = nextInjected,
                bearing = bearing,
                speedKmh = speedMps * 3.6,
            )
        }
        return nextInjected
    }

    private fun startRoutePump(runtime: RouteRuntime) {
        val source = RouteFrameSource(
            runtime = runtime,
            progressor = progressor,
            onAdvance = { advance ->
                lastBearing = advance.bearing
                _state.update {
                    it.copy(
                        point = advance.targetPoint,
                        injectedPoint = advance.injectedPoint,
                        address = routeAddress(runtime.config),
                        speedKmh = if (advance.completed) 0.0 else advance.speedMps * 3.6,
                        bearing = advance.bearing,
                        progress = advance.progress,
                    )
                }
            },
            onCompleted = {
                cancelAutoStop()
                if (apply(SimulationCommand.Complete) != null) {
                    log.info { log.routeCompleted }
                }
            },
            publish = { point, speed, bearing -> publish(point, speed, bearing) },
            failureError = { activeStrategy?.failureError ?: ErrorCode.INJ_0101 },
        )
        pump.start(source) { error -> handleFrameFailure(error) }
    }

    private fun routeAddress(config: RouteConfig): String {
        val route = strings().route
        return when (config.kind) {
            RouteKind.ROAM -> route.routeAddressRoam(config.roamRadiusMeters)
            RouteKind.IMPORTED -> route.routeAddressImported(config.trackPoints.size)
            RouteKind.POINT_TO_POINT -> route.routeAddressPointToPoint(route.travelModeLabel(config.mode.name))
        }
    }

    /** 帧循环自身失败（通道失效、轨迹异常）时的收敛，避免停在"运行中"。 */
    private fun handleFrameFailure(error: ErrorCode) {
        if (_state.value.runState == RunState.ERROR) return
        cancelAutoStop()
        manualMotion = null
        apply(SimulationCommand.Fail(error, "帧循环终止"))
    }

    /**
     * 启动失败收尾：回滚通道、复位状态，并返回带错误码的失败结果。
     *
     * 与 [stop] 的区别是保留"上次错误"用于 UI 引导；通道状态复位为"已停止"，
     * 避免界面还显示某个通道正在下发。
     */
    private fun abortStart(error: ErrorCode, cause: String): Outcome<Unit> {
        pump.stop()
        cancelAutoStop()
        manualMotion = null
        activeStrategy = null
        routeRuntime = null
        consecutiveFailures = 0
        runCatching { mockGateway.teardown() }
        runCatching { containerGateway.disableAll() }
        runCatching { containerGateway.clearCell() }
        apply(SimulationCommand.Abort(error, cause))
        clearRecordedSession()
        monitor.resetToStopped()
        val errorCodeText = error.code
        log.error { log.failure(errorCodeText, cause) }
        return Outcome.Failure(DomainError(code = error, cause = cause))
    }

    /**
     * 记录"当前有活动会话"。
     *
     * 只在**首帧写入成功后**记录：启动失败不应该让用户在下次开机时看到
     * "上次模拟未结束"的提醒。写盘放在后台协程，不阻塞首帧。
     */
    private fun recordSessionStarted() {
        sessionEvents.trySend(SessionEvent.Started(_state.value))
    }

    private fun clearRecordedSession() {
        sessionEvents.trySend(SessionEvent.Stopped)
    }

    /** 轨迹首帧成功后记录最近路线；失败路径不会产生条目。 */
    private fun recordRouteStarted(config: RouteConfig) {
        routeEvents.trySend(config)
    }

    /**
     * 定时停止。
     *
     * 按墙钟计时而不是运行时长：用户的心智模型是"到 15:30 自动停"，
     * 且暂停后恢复不应意外延长模拟。deadline 写入状态供 UI/通知展示。
     */
    private fun armAutoStop() {
        cancelAutoStop()
        val minutes = currentSettings.autoStopMinutes.coerceIn(0, MAX_AUTO_STOP_MINUTES)
        if (minutes <= 0) return
        val deadline = clock.wallMillis() + minutes * 60_000L
        autoStopDeadline = deadline
        _state.update { it.copy(autoStopAtMillis = deadline) }
        autoStopJob = scope.launch {
            clock.delay(minutes * 60_000L)
            // 旧任务可能在用户手动停止后又启动了新会话：deadline 比对避免误停。
            if (autoStopDeadline != deadline) return@launch
            if (!_state.value.isActive) return@launch
            log.info { log.autoStopped(minutes) }
            stop()
            runCatching { onAutoStopFinished() }
        }
    }

    private fun cancelAutoStop() {
        autoStopJob?.cancel()
        autoStopJob = null
        autoStopDeadline = 0L
        if (_state.value.autoStopAtMillis != 0L) {
            _state.update { it.copy(autoStopAtMillis = 0L) }
        }
    }

    /** 模糊策略的缓存跨线程共享，统一加锁读取。 */
    private fun injected(point: GeoPoint): GeoPoint = synchronized(blurLock) { blur.injectedPoint(point) }

    /**
     * 启动前的轨迹参数自净：去重途经点、限制数量、校验导入点、钳制漫游半径。
     *
     * 放在引擎而不是只放在 UI：服务/测试也可能直接调用 startRoute，
     * 领域层必须自己守住不变量。
     */
    private fun sanitizeRouteConfig(config: RouteConfig): RouteConfig {
        val waypoints = ArrayList<GeoPoint>()
        for (point in config.waypoints) {
            if (!point.isValid() || point == config.start || point == config.end) continue
            if (waypoints.any { GeoUtils.distanceMeters(it, point) < MIN_WAYPOINT_GAP_METERS }) continue
            waypoints.add(point)
            if (waypoints.size >= MAX_WAYPOINTS) break
        }
        return when (config.kind) {
            RouteKind.POINT_TO_POINT -> config.copy(waypoints = waypoints)
            RouteKind.ROAM -> config.copy(
                waypoints = emptyList(),
                loop = true,
                roundTrip = false,
                trackPoints = emptyList(),
                roamRadiusMeters = config.roamRadiusMeters.coerceIn(0, RoamPlanner.MAX_RADIUS_METERS),
            )
            RouteKind.IMPORTED -> {
                val points = GeoUtils.downsample(
                    config.trackPoints.filter { it.isValid() }.distinct(),
                    MAX_TRACK_POINTS,
                )
                config.copy(
                    waypoints = emptyList(),
                    trackPoints = points,
                    start = points.firstOrNull() ?: config.start,
                    end = points.lastOrNull() ?: config.end,
                )
            }
        }
    }

    /** 按当前模糊半径重算已选点的注入坐标与轨迹，不改变运行状态。 */
    private fun applyBlurToCurrentState() {
        val target = _state.value.point ?: return
        _state.update {
            it.copy(
                injectedPoint = injected(target),
                blurRadiusMeters = blur.radiusMeters,
            )
        }
        routeRuntime?.let { progressor.rebindBlur(it, blur) }
    }

    private sealed interface SessionEvent {
        data class Started(val state: SimulationState) : SessionEvent
        data object Stopped : SessionEvent
    }

    private companion object {
        /** 连续失败达到该次数才判定注入失败。 */
        const val MAX_CONSECUTIVE_FAILURES = 3

        /** 轨迹总里程下限（米），过短的路线没有播放意义。 */
        const val MIN_ROUTE_DISTANCE_METERS = 10.0

        /** 途经点上限与去重阈值（米）。 */
        const val MAX_WAYPOINTS = 8
        const val MIN_WAYPOINT_GAP_METERS = 0.5

        /** 导入轨迹的领域层兜底上限（用例层已按 20k 截断）。 */
        const val MAX_TRACK_POINTS = 20_000

        /** 倍速范围。 */
        const val MIN_SPEED_FACTOR = 0.25f
        const val MAX_SPEED_FACTOR = 8f

        /** 低于该速度视为"停止行走"，避免摇杆微抖导致状态反复。 */
        const val MIN_WALK_SPEED_MPS = 0.05f

        /** 更高纬度下经度换算会明显失真，直接停止行走。 */
        const val MAX_WALK_LAT = 85.0

        /** 定时停止上限（分钟）。 */
        const val MAX_AUTO_STOP_MINUTES = 240
    }
}
