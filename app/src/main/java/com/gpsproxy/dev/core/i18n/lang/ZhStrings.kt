package com.gpsproxy.dev.core.i18n.lang

import com.gpsproxy.dev.core.i18n.AppLanguage
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.text.ChannelTexts
import com.gpsproxy.dev.core.i18n.text.CommonTexts
import com.gpsproxy.dev.core.i18n.text.ContainerTexts
import com.gpsproxy.dev.core.i18n.text.ErrorTexts
import com.gpsproxy.dev.core.i18n.text.HomeTexts
import com.gpsproxy.dev.core.i18n.text.LogTexts
import com.gpsproxy.dev.core.i18n.text.MessageTexts
import com.gpsproxy.dev.core.i18n.text.NotificationTexts
import com.gpsproxy.dev.core.i18n.text.OnboardingTexts
import com.gpsproxy.dev.core.i18n.text.PlaceTexts
import com.gpsproxy.dev.core.i18n.text.RouteTexts
import com.gpsproxy.dev.core.i18n.text.SelfCheckTexts
import com.gpsproxy.dev.core.i18n.text.SettingsTexts

object ZhStrings : Strings {
    override val language = AppLanguage.ZH_HANS
    override val common: CommonTexts = ZhCommon
    override val home: HomeTexts = ZhHome
    override val settings: SettingsTexts = ZhSettings
    override val route: RouteTexts = ZhRoute
    override val error: ErrorTexts = ZhError
    override val selfCheck: SelfCheckTexts = ZhSelfCheck
    override val channel: ChannelTexts = ZhChannel
    override val message: MessageTexts = ZhMessage
    override val place: PlaceTexts = ZhPlace
    override val container: ContainerTexts = ZhContainer
    override val onboarding: OnboardingTexts = ZhOnboarding
    override val notification: NotificationTexts = ZhNotification
    override val log: LogTexts = ZhLog
}

private object ZhCommon : CommonTexts {
    override val errorDetailSeparator = "："
    override val cancel = "取消"
    override val confirm = "确定"
    override val back = "返回"
    override val retry = "重试"
    override val close = "关闭"
}

private object ZhSettings : SettingsTexts {
    override val pageTitle = "设置"
    override val generalSection = "通用"
    override val coordSystemSection = "坐标系"
    override val locationSection = "定位"
    override val payloadSection = "定位载荷"
    override val sessionSection = "会话"
    override val injectionSection = "注入"
    override val startupLogSection = "启动与日志"
    override val aboutSection = "关于"

    override val languageTitle = "界面语言"
    override val languageSubtitle = "切换后立即生效；历史日志与已生成的名称保持写入时语言"
    override fun languageName(language: AppLanguage): String = when (language) {
        AppLanguage.SYSTEM -> "跟随系统"
        AppLanguage.ZH_HANS -> "简体中文"
        AppLanguage.EN -> "English"
    }

    override val coordSystemTitle = "注入坐标系"
    override val coordSystemSubtitle = "国内应用统一使用 GCJ-02；海外应用可改为 WGS-84"
    override fun coordSystemDescription(system: String): String = when (system) {
        "GCJ02" -> "国测局加密坐标，国内应用通用"
        "WGS84" -> "GPS 原始坐标，海外应用通用"
        "BD09" -> "百度二次偏移坐标"
        else -> system
    }

    override val accuracyTitle = "模拟精度"
    override val accuracySubtitle = "写入 Location.accuracy，过小易被判定异常"
    override val driftTitle = "随机漂移"
    override val driftSubtitle = "在静止点为坐标叠加 0.5–2 m 抖动，仅用于自然度"
    override val blurTitle = "位置模糊"
    override val blurSubtitle = "在半径内随机平移真实坐标，应用无法还原选点"
    override val blurRadiusTitle = "模糊半径"
    override val blurRadiusSubtitle = "半径越大越难定位；半径过大会跨越街区"
    override val intervalTitle = "静止时更新频率"
    override val intervalSubtitle = "影响耗电与轨迹自然度"

    override val reportSpeedTitle = "上报速度"
    override val reportSpeedSubtitle = "静止时写入 Location.speed；只影响载荷，不产生位移"
    override val reportBearingTitle = "上报航向"
    override val reportBearingSubtitle = "静止时写入 Location.bearing；0 表示沿用上次航向"
    override val altitudeTitle = "海拔"
    override val altitudeSubtitle = "写入 Location.altitude 的基准值，仍叠加 ±1 m 抖动"
    override val walkSpeedTitle = "步行速度"
    override val walkSpeedSubtitle = "悬浮面板摇杆按到底时的最大速度"

    override val autoStopTitle = "模拟时长上限"
    override val autoStopSubtitle = "按墙钟计时，暂停不暂停计时；改动只影响下一次启动"
    override val unlimited = "不限"
    override fun minutes(value: Int): String = "$value 分钟"

    override val channelTitle = "主注入通道"
    override val channelSubtitle = "容器代理免 root、无 isMock；系统 Mock 需开发者选项授权"
    override fun channelName(channel: String): String = when (channel) {
        "CONTAINER" -> "容器代理"
        "OFFICIAL" -> "系统 Mock"
        else -> channel
    }

    override fun channelDescription(channel: String): String = when (channel) {
        "CONTAINER" -> "免 root：沙箱内代理定位，不写系统 Mock，无 isMock 标记"
        "OFFICIAL" -> "写入系统测试提供者，需开发者选项授权，坐标带 isMock 标记"
        else -> ""
    }

    override val containerRuntimeTitle = "容器运行时"
    override val containerRuntimeReady = "已就绪：坐标经 Binder 下发到沙箱，容器内应用读取到模拟坐标"
    override val containerRuntimeUnavailable = "不可用：请先在容器检测页重试，或改用系统 Mock 通道"
    override val ready = "就绪"
    override val unavailable = "不可用"
    override val containerSpaceTitle = "容器空间"
    override val containerSpaceSubtitle = "导入目标应用，在隔离沙箱中运行并模拟定位"
    override val overlayTitle = "系统悬浮球"
    override val overlaySubtitle = "切到其他应用后显示，可在目标应用上暂停/停止"

    override val lastSelfCheckTitle = "上次引擎自检"
    override val lastSelfCheckNever = "尚未完成过一次完整自检"
    override fun lastSelfCheckAt(time: String): String = "最近一次：$time"
    override val never = "从未"
    override val checked = "已检测"
    override val notificationDetailTitle = "前台通知内容"
    override val notificationDetailSubtitle = "Android 要求前台服务显示通知，这里控制信息密度而不是开关"
    override fun notificationDetailName(detail: String): String = when (detail) {
        "COMPACT" -> "精简"
        "DETAILED" -> "详细"
        else -> detail
    }

    override fun notificationDetailDescription(detail: String): String = when (detail) {
        "COMPACT" -> "只显示坐标与精度，通知更安静"
        "DETAILED" -> "追加通道、坐标系与模糊半径"
        else -> ""
    }

    override val bootRestoreTitle = "开机恢复"
    override val bootRestoreSubtitle = "重启后只显示一条恢复通知，不会自动启动前台服务（Android 12+ 限制）"
    override val logLevelTitle = "日志级别"
    override val logLevelSubtitle = "详细模式会记录更多通道与坐标转换信息"
    override fun detailLevelName(level: String): String = when (level) {
        "NORMAL" -> "一般"
        "VERBOSE" -> "详细"
        else -> level
    }

    override val clearLogsTitle = "清空实时日志"
    override val clearLogsSubtitle = "仅清除本机内存缓冲，不涉及任何上传"

    override val capabilityTitle = "能力边界"
    override val capabilityText =
        "容器代理通道：免 root，只在隔离沙箱内 Hook 定位 API，不写系统 Mock、坐标不带 isMock，" +
            "也不修改主机上的应用；作用范围仅限容器内已导入的应用。" +
            "系统 Mock 通道作用于宿主全部应用，但坐标带 isMock 标记且需开发者选项授权。" +
            "目标应用仍可能通过多源一致性校验识别异常，这不在规避范围内。"
    override val openDiagnostics = "打开运行诊断"

    override val versionTitle = "版本"
    override fun versionSubtitle(version: String, buildType: String): String =
        "GpsProxy $version · $buildType 构建"
    override val poiSourceTitle = "POI 数据源"
    override val poiSourceLive = "已配置高德 Web 服务 Key，可搜索真实 POI"
    override val poiSourceBuiltin = "内置示例数据；在 local.properties 配置高德 Web 服务 Key 可启用真实搜索"
    override val amap = "高德地图"
    override val builtin = "内置示例"
    override val supportTitle = "支持范围"
    override val supportSubtitle = "Android 8.1（API 27）及以上 · arm64 设备"
    override val supportValue = "API 27+"
    override val privacy = "定位数据仅在本机生成与保存；除可选的 POI 请求外不联网。"

    override val accuracyDialogTitle = "模拟精度"
    override val intervalDialogTitle = "静止时更新频率"
    override val blurRadiusDialogTitle = "模糊半径"
    override val logLevelDialogTitle = "日志级别"
    override val reportSpeedDialogTitle = "上报速度"
    override val reportBearingDialogTitle = "上报航向"
    override val altitudeDialogTitle = "海拔"
    override val walkSpeedDialogTitle = "步行速度"
    override val autoStopDialogTitle = "模拟时长上限"
    override fun intervalOption(millis: Int): String = when (millis) {
        500 -> "0.5 秒"
        1000 -> "1 秒"
        2000 -> "2 秒"
        else -> "${millis / 1000} 秒"
    }

    override fun accuracyOption(meters: Int): String = "±$meters m"
    override fun kmh(value: Int): String = "$value km/h"
    override fun degrees(value: Int): String = "$value°"
    override fun meters(value: Int): String = "$value m"
}

private object ZhHome : HomeTexts {
    override val overlayTitle = "快捷操控"
    override val overlayBadge = "悬浮球"
    override val favoritePoint = "收藏点"
    override val repick = "重新选点"
    override val resumeInjection = "恢复注入"
    override val pauseInjection = "暂停注入"
    override val stopSimulation = "停止模拟"
    override val walkSpeed = "步行速度"
    override val walkHintHold = "按住行走，松开停止"
    override val walkHintDrag = "摇杆拖动微调位置，松开后保持"
    override val overlayContentDescription = "快捷操控"
    override val startSimulation = "开始模拟"

    override val statusNotStarted = "未开始模拟"
    override val statusPaused = "模拟已暂停"
    override val statusRouteCompleted = "巡航已完成"
    override val statusChannelMiss = "通道未命中"
    override val statusRouteActive = "巡航生效中"
    override val statusWalking = "行走中"
    override val statusActive = "模拟生效中"

    override val mapWatermarkHome = "离线示意底图 · 长按地图选点"
    override val mapWatermarkDefault = "离线示意底图 · GCJ-02"
    override val mapZoomIn = "放大"
    override val mapZoomOut = "缩小"
    override val mapRecenter = "回到标记"
    override val searchHint = "搜索地点，或长按地图选点"
    override val permissions = "权限"
    override val quickFavorites = "收藏与历史"
    override val quickRoute = "路线模拟"
    override val quickApps = "目标应用"
    override val quickSettings = "设置"
    override val externalMap = "外部地图"
    override val copyCoords = "复制坐标"
    override val favorite = "收藏"
    override fun blurChip(radiusMeters: Int): String = "模糊 ±${radiusMeters}m"
    override fun blurDetail(radiusMeters: Int, pickedCoords: String): String =
        "模糊 ±$radiusMeters m · 选点 $pickedCoords"
    override fun autoStopAt(time: String): String = "自动停止 $time"
    override val metricAccuracy = "精度"
    override val metricSpeed = "速度"
    override val metricAltitude = "海拔"
    override val metricChannels = "通道"
    override val viewRoute = "查看巡航"
    override val pickOnMap = "地图选点"

    override val blockContainerInitTitle = "容器运行时不可用"
    override val blockContainerInitMessage =
        "虚拟化引擎未随应用启动，或容器服务进程无法连接。\n\n" +
            "可以进入容器空间重新检测，或暂时改用系统 Mock 通道" +
            "（作用于全部应用，但坐标带 isMock 标记）。"
    override val blockRetryTitle = "容器服务暂时不可达"
    override val blockRetryMessage =
        "没能读取容器内的应用清单：容器进程可能正在启动，或被系统冻结。\n\n" +
            "已导入的应用不会丢失，稍后点「重试」即可；" +
            "持续失败时进入容器空间查看自检结果。"
    override val blockContainerAppsTitle = "还没有容器应用"
    override val blockContainerAppsMessage =
        "容器代理是免 root 通道，但只对容器内已导入的应用生效：\n\n" +
            "1. 从已安装应用或 APK 导入目标应用\n" +
            "2. 回到首页点击「开始模拟」\n\n" +
            "容器内应用读取到的坐标不带 isMock 标记。"
    override val blockPermissionsTitle = "还需要系统授权"
    override val blockPermissionsMessage =
        "1. 打开开发者选项\n" +
            "2. 选择「模拟位置信息应用」\n" +
            "3. 选择 GpsProxy\n\n" +
            "同时需要授予定位权限（Android 14+ 启动前台定位服务时强校验）。"
    override val blockNoneTitle = "暂时无法开始"
    override val blockNoneMessage = "请稍后重试。"
    override val blockConfirmImportApps = "去导入应用"
    override val blockConfirmContainerInit = "查看容器空间"
    override val blockConfirmDeveloperOptions = "去开发者选项"
    override val blockConfirmPermissions = "去权限总览"
    override val blockConfirmOk = "知道了"
    override val blockDismissContainer = "容器空间"
    override val blockDismissLater = "稍后"
}
private object ZhRoute : RouteTexts {
    override fun travelModeLabel(mode: String): String = when (mode) {
        "DRIVING" -> "驾车"
        "CYCLING" -> "骑行"
        "WALKING" -> "步行"
        else -> mode
    }

    override fun routeKindLabel(kind: String): String = when (kind) {
        "POINT_TO_POINT" -> "两点路线"
        "ROAM" -> "随机漫游"
        "IMPORTED" -> "导入轨迹"
        else -> kind
    }

    override fun routeSourceLabel(source: String): String = when (source) {
        "NETWORK" -> "路网规划"
        "SYNTHETIC" -> "内置曲线"
        "IMPORTED" -> "导入轨迹"
        "ROAM" -> "随机漫游"
        else -> source
    }

    override fun routeDegraded(reason: String): String = "路网规划不可用，已回退内置曲线（$reason）"

    override fun duration(seconds: Int): String = when {
        seconds < 60 -> "$seconds 秒"
        seconds < 3600 -> "${seconds / 60} 分钟"
        else -> "${seconds / 3600} 小时 ${(seconds % 3600) / 60} 分"
    }

    override fun routeAddressRoam(radiusMeters: Int): String = "随机漫游 · 半径 ${radiusMeters} m"
    override fun routeAddressImported(points: Int): String = "导入轨迹 · $points 点"
    override fun routeAddressPointToPoint(modeLabel: String): String = "轨迹巡航 · $modeLabel"
    override fun manualNudge(coords: String): String = "手动微调 · $coords"

    override fun savedNameRoam(radiusMeters: Int): String = "漫游 · 半径 ${radiusMeters} m"
    override fun savedNameImported(points: Int): String = "导入轨迹 · $points 点"
    override fun savedNameEndpoints(start: String, end: String): String = "$start → $end"
    override fun endpointSummary(start: String, end: String, waypointCount: Int): String =
        "$start → $end" + if (waypointCount == 0) "" else " · $waypointCount 途经点"

    override val watermarkRoam = "漫游范围预览 · 开始后生成随机环线"
    override fun watermarkImported(points: Int): String = "导入轨迹预览 · $points 点"
    override val watermarkRoute = "路线预览 · 开始后按数据源规划"
    override fun startLocation(label: String): String = "我的位置 · $label"
    override val addWaypoint = "添加途经点（可选）"
    override fun waypointCount(count: Int): String = "途经点 · $count 个"
    override val chooseEnd = "选择终点"
    override val routeEnd = "轨迹终点"
    override val cruiseSpeed = "巡航速度"
    override fun roamCenter(label: String): String = "漫游中心 · $label"
    override val roamRadius = "漫游半径"
    override val roamSpeed = "漫游速度"
    override val roamHint = "每次会话生成一条闭合随机环线并循环播放；同一会话每圈路线相同。"
    override val importedTrack = "导入轨迹"
    override fun importedTrackLine(name: String, points: Int): String = "$name · $points 点"
    override fun importedEndpoints(startCoords: String, endCoords: String): String =
        "起点 $startCoords · 终点 $endCoords"
    override val chooseGpx = "选择 GPX 文件"
    override val gpxHint = "支持 GPX 1.0/1.1 的 trkpt / rtept；导入后可直接巡航或先收藏。"
    override val runningBlockedConfig = "已有模拟在运行，请先在首页停止后再开始巡航"
    override val needRoamCenter = "请选择漫游中心后开始"
    override val needGpx = "请先导入 GPX 轨迹"
    override val needEnd = "请选择终点后开始巡航"
    override fun preview(distance: String, duration: String, points: Int): String =
        "参考距离 $distance · 预计 $duration · $points 个预览点"
    override val favoriteRoute = "收藏路线"
    override val startRoute = "开始巡航"
    override val loopRoute = "循环巡航"
    override val loopRouteSubtitle = "到达终点后回到起点"
    override val roundTrip = "往返"
    override val roundTripSubtitle = "原路返回"
    override val tapToEdit = "点击修改"
    override fun waypointsDialogTitle(count: Int, max: Int): String = "途经点 · $count/$max"
    override val moveUp = "上移"
    override val moveDown = "下移"
    override val editWaypoint = "修改"
    override val deleteWaypoint = "删除"
    override val noWaypoints = "还没有途经点"
    override val limitReached = "已达上限"
    override val addWaypointButton = "添加途经点"
    override val done = "完成"

    override val statusError = "注入异常"
    override val statusCompleted = "巡航已完成"
    override val statusPaused = "巡航已暂停"
    override fun statusRunning(speedKmh: Int, factor: String): String =
        "巡航中 · $speedKmh km/h · ${factor}x"
    override val statusIdle = "未开始巡航"
    override fun watermarkRunning(sourceLabel: String): String = "轨迹巡航 · $sourceLabel"
    override val genericTrack = "轨迹"
    override fun stopAt(time: String): String = "停止 $time"
    override val exportGpx = "导出 GPX"
    override val backHome = "返回首页"
    override fun walked(distance: String): String = "已走 $distance"
    override val arrived = "已到达终点"
    override fun remaining(duration: String): String = "剩余 $duration"
    override val dragToSeek = "拖动跳转"
    override val metricPoints = "轨迹点"
    override val metricBearing = "航向"
    override val metricAccuracy = "精度"
    override val speedFactor = "倍速"
    override val reverse = "反向"
    override val resume = "继续"
    override val pause = "暂停"
    override val stop = "停止"
    override val replay = "重播"
    override fun footer(total: String, index: Int, totalPoints: Int): String =
        "全程 $total · 中间点 $index/$totalPoints"
    override fun autoStopSuffix(time: String): String = " · $time 自动停止"

    override val libraryTitle = "路线库"
    override val libraryStart = "路线库起点"
    override val libraryEnd = "路线库终点"
    override val waypointLabel = "途经点"
    override val emptyTitle = "还没有保存的路线"
    override val emptyMessage = "在巡航配置页点「收藏路线」，或直接开始一次巡航——最近路线会自动出现在这里。"
    override fun favoritesSection(count: Int): String = "收藏 · $count"
    override fun recentsSection(count: Int): String = "最近路线 · $count"
    override fun libraryHint(maxRecent: Int, maxFavorites: Int): String =
        "载入只填充草稿，不会自动开始。最近路线最多 $maxRecent 条，收藏最多 $maxFavorites 条；" +
            "收藏不会因为没有再使用而被最近列表挤掉。"
    override val deleteTitle = "删除路线"
    override fun deleteMessage(name: String): String = "确定删除「$name」？该操作不可撤销。"
    override val delete = "删除"
    override val favorite = "收藏"
    override val unfavorite = "取消收藏"
    override val rename = "重命名"
    override fun summaryRoam(center: String, radiusMeters: Int, speedKmh: Int): String =
        "中心 $center · 半径 $radiusMeters m · $speedKmh km/h"
    override fun summaryImported(points: Int, speedKmh: Int): String =
        "$points 个轨迹点 · $speedKmh km/h"
    override fun summaryEndpoints(summary: String, speedKmh: Int): String =
        "$summary · $speedKmh km/h"
    override val summaryLoopSuffix = " · 循环"
    override val summaryRoundTripSuffix = " · 往返"
    override val renameTitle = "重命名路线"
    override val renameMessage = "给这条路线起一个便于识别的名字。"
    override val namePlaceholder = "路线名称"
    override val save = "保存"
}
private object ZhError : ErrorTexts {
    override val suggestionPrefix = "建议："

    override fun title(code: com.gpsproxy.dev.core.error.ErrorCode): String = when (code) {
        com.gpsproxy.dev.core.error.ErrorCode.ENG_0102 -> "设备不满足最低要求"
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0102 -> "未设为模拟位置应用"
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0104 -> "定位权限未授予"
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0101 -> "写入模拟位置失败"
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0202 -> "注入服务启动失败"
        com.gpsproxy.dev.core.error.ErrorCode.COORD_0101 -> "坐标超出合法范围"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0201 -> "未找到可用路线"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0302 -> "轨迹插值点为空"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0401 -> "轨迹文件无法解析"
        com.gpsproxy.dev.core.error.ErrorCode.FILE_0101 -> "轨迹文件读写失败"
        com.gpsproxy.dev.core.error.ErrorCode.DIAG_0101 -> "诊断包导出失败"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0101 -> "容器引擎不可用"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0103 -> "容器内没有目标应用"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0102 -> "应用导入容器失败"
    }

    override fun cause(code: com.gpsproxy.dev.core.error.ErrorCode): String = when (code) {
        com.gpsproxy.dev.core.error.ErrorCode.ENG_0102 ->
            "系统版本低于 Android 8.1，或设备缺少 64 位 ABI。"
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0102 ->
            "系统仅允许被选中的开发工具写入模拟位置。"
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0104 ->
            "前台定位服务需要定位权限，Android 14+ 会强校验。"
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0101 ->
            "addTestProvider / setTestProviderLocation 被系统拒绝。"
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0202 ->
            "系统限制了前台服务启动，常见于后台启动或电池优化。"
        com.gpsproxy.dev.core.error.ErrorCode.COORD_0101 ->
            "纬度必须在 -90~90，经度必须在 -180~180。"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0201 ->
            "起点与终点过近，或路径插值点为空。"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0302 ->
            "路线生成异常，无法得到有效采样点。"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0401 ->
            "GPX 内容损坏、缺少 trkpt/rtept，或有效坐标点少于 2 个。"
        com.gpsproxy.dev.core.error.ErrorCode.FILE_0101 ->
            "文件 URI 授权失效、存储空间不足，或提供方拒绝读写。"
        com.gpsproxy.dev.core.error.ErrorCode.DIAG_0101 ->
            "缓存目录不可写或分享组件不可用。"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0101 ->
            "虚拟化引擎未随应用启动，或容器服务进程无法连接；Android 15+ 为实验性支持。"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0103 ->
            "免 root 通道只在容器内生效，尚未导入任何目标应用时没有可接管对象。"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0102 ->
            "APK 解析失败、ABI 不兼容，或容器存储空间不足。"
    }

    override fun action(code: com.gpsproxy.dev.core.error.ErrorCode): String = when (code) {
        com.gpsproxy.dev.core.error.ErrorCode.ENG_0102 ->
            "更换 Android 8.1+ 的 arm64 设备后重试。本版本不做半可用降级。"
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0102 ->
            "打开开发者选项 → 选择模拟位置信息应用 → 选择 GpsProxy。"
        com.gpsproxy.dev.core.error.ErrorCode.PERM_0104 ->
            "在权限总览中授予「精确位置」权限。"
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0101 ->
            "确认本应用已被选为模拟位置应用，并关闭系统的应用速冻/纯净模式。"
        com.gpsproxy.dev.core.error.ErrorCode.INJ_0202 ->
            "保持应用在前台重新开始模拟，并允许忽略电池优化。"
        com.gpsproxy.dev.core.error.ErrorCode.COORD_0101 ->
            "修正输入，或直接在地图上重新选点。"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0201 ->
            "更换终点，确保起终点距离大于 10 米。"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0302 ->
            "重新选择终点后再开始巡航。"
        com.gpsproxy.dev.core.error.ErrorCode.ROUTE_0401 ->
            "确认文件为 GPX 1.0/1.1 格式且包含轨迹点后重试。"
        com.gpsproxy.dev.core.error.ErrorCode.FILE_0101 ->
            "重新选择文件，或换一个可写的本地目录。"
        com.gpsproxy.dev.core.error.ErrorCode.DIAG_0101 ->
            "清理缓存后重试，或通过系统文件管理器手动导出。"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0101 ->
            "重启应用后在容器初始化页重新检测；仍失败时继续使用官方 Mock 通道。"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0103 ->
            "进入容器应用页，从已安装应用或 APK 导入目标应用后重试。"
        com.gpsproxy.dev.core.error.ErrorCode.BOX_0102 ->
            "换一个 APK 重试，或卸载不用的容器应用释放空间。"
    }
}

private object ZhPlace : PlaceTexts {
    override val searchTitle = "搜索地点"
    override val searchPlaceholder = "搜索地点、地址、公交站"
    override val searchAction = "搜索"
    override fun searchTabLabel(tab: String): String = when (tab) {
        "KEYWORD" -> "关键字"
        "NEARBY" -> "周边"
        "HISTORY" -> "历史"
        else -> tab
    }

    override fun poiCategoryLabel(category: String): String = when (category) {
        "ALL" -> "全部"
        "SCENIC" -> "景点"
        "FOOD" -> "餐饮"
        "TRANSIT" -> "交通"
        "MALL" -> "商场"
        "HOTEL" -> "酒店"
        "HOSPITAL" -> "医院"
        "OTHER" -> "其他"
        else -> category
    }

    override fun poiSourceLabel(source: String): String = when (source) {
        "LOCAL" -> "内置示例数据"
        "AMAP" -> "高德地图"
        else -> source
    }

    override fun dataSource(label: String): String = "数据源：$label"
    override val builtinHint = "（在 local.properties 配置高德 Web Key 可启用真实 POI）"
    override val searchFailed = "搜索失败"
    override val searchError = "网络异常，请稍后重试或使用内置示例数据"
    override val fallbackWarning = "网络异常，已回退内置示例数据"
    override val unnamedPlace = "未命名地点"
    override fun nearAddress(city: String, name: String): String = "$city · ${name}附近"
    override val emptyHistoryTitle = "还没有搜索历史"
    override val emptyHistoryMessage = "搜索过的关键词会保留最近 20 条。"
    override val recentSearches = "最近搜索"
    override val clear = "清空"
    override val noResultsTitle = "没有找到相关地点"
    override val noResultsMessage = "换个关键词试试，或直接在地图上长按选点。"
    override val goPick = "去地图选点"
    override val loading = "加载中…"
    override val loadMore = "加载更多"

    override val favoritesTitle = "收藏与历史"
    override val addNew = "新增"
    override val favoritesTab = "收藏地点"
    override val historyTab = "历史记录"
    override val emptyFavoritesTitle = "还没有收藏地点"
    override val emptyFavoritesMessage = "在地图上选点后点击「收藏」，常用位置会出现在这里。"
    override val mapPick = "地图选点"
    override val noHistoryTitle = "还没有历史记录"
    override val noHistoryMessage = "每次设为模拟位置或开始巡航，都会保留一条最近记录。"
    override fun recentCount(count: Int): String = "最近 $count 条"
    override fun deletedUndo(name: String): String = "已删除「$name」"
    override val undo = "撤销"
    override val renameFavorite = "重命名收藏"
    override val renameFavoriteHint = "最多 20 字"

    override val pickWatermark = "长按地图选点 · 微调步长 1 m"
    override val reverseResolving = "正在解析地址…"
    override fun mockPickAddress(coords: String): String = "模拟选点 · $coords"
    override val chipRouteEnd = "设置路线终点"
    override val chipWaypoint = "设置途经点"
    override val reverseTag = "逆地理"
    override fun blurNotice(radiusMeters: Int): String =
        "位置模糊已开启：实际注入会在 ±$radiusMeters m 内偏移，此选点不会被应用还原"
    override val pasteCoords = "粘贴坐标"
    override val copy = "复制"
    override val latitude = "纬度"
    override val longitude = "经度"
    override val invalidRangeMessage = "纬度范围 -90~90，经度范围 -180~180。修正后再继续。"
    override val setAsWaypoint = "设为途经点"
    override val setAsEnd = "设为终点"
    override val setAsMock = "设为模拟位置"
    override val pickHintPrefix = "长按地图任意位置可重新选点 · 微调按钮步长 1 m · "
    override val waypointOrderHint = "途经点按选择顺序连接"
    override fun coordSystemHint(label: String): String = "坐标系 $label"
    override val unparsableCoords = "无法识别坐标；支持 30.25,120.15、DMS 或 geo: 链接"
    override val clipboardEmpty = "剪贴板为空，请先复制一个坐标"
    override val pickFirst = "请先在地图上选点"
    override val mockStarted = "模拟已开启"
}

private object ZhContainer : ContainerTexts {
    override val targetAppsTitle = "目标应用"
    override val refresh = "刷新"
    override val commonAppsTitle = "常用目标应用"
    override val commonAppsBanner =
        "这里只是保存常用应用，方便一键打开。要对某个应用伪装定位，" +
            "需要把它导入容器（容器代理通道，免 root、无 isMock 标记），" +
            "或使用系统 Mock 通道（作用于全部应用，但带 isMock 标记）。"
    override val filterPlaceholder = "按应用名或包名筛选"
    override val noLaunchableAppsTitle = "未读取到可启动应用"
    override val noLaunchableAppsMessage = "请确认系统未限制应用可见性，然后点击右上角刷新。"
    override val sectionCommon = "常用目标"
    override fun sectionAllApps(count: Int): String = "全部应用 · $count"
    override val systemBadge = "系统"
    override fun openApp(label: String): String = "打开 $label"
    override val removeCommon = "移出常用"
    override val addCommon = "设为常用"
    override val removedCommon = "已移出常用目标"
    override val addedCommon = "已加入常用目标"
    override val cannotLaunchApp = "无法启动该应用"

    override val containerAppsTitle = "容器应用"
    override val engineUnavailableTitle = "容器引擎不可用"
    override val engineUnavailableText =
        "免 root 通道依赖容器引擎，请先完成引擎检测；" +
            "不可用时可改用系统 Mock 通道（作用全局，但需系统授权且坐标带 isMock 标记）。"
    override val goEngineCheck = "去引擎检测"
    override val readsMockTitle = "容器内应用读取模拟坐标"
    override val readsMockText =
        "导入的应用在隔离沙箱中运行，开启模拟后其定位 API 返回模拟坐标，" +
            "且不经过系统测试提供者，坐标不带 isMock 标记。容器内卸载不影响主机上的原应用。"
    override fun sandboxUsage(megabytes: String): String = "沙箱占用 $megabytes MB"
    override val processing = "处理中…"
    override val importInstalled = "导入已装应用"
    override val importApk = "导入 APK 文件"
    override val restoreAll = "一键还原全部容器定位"
    override val loadAppsFailedTitle = "读取容器应用失败"
    override val loadAppsFailedMessage =
        "容器服务暂时不可达，可能正在启动或被系统冻结。稍后点击右上角刷新重试，已导入的应用不会丢失。"
    override val emptyContainerTitle = "容器内还没有应用"
    override val emptyContainerMessage = "点击上方按钮，从已安装应用或 APK 文件导入。"
    override fun containerAppsSection(count: Int): String = "容器内应用 · $count"
    override val cellSynced = "基站已同步"
    override fun locationModeLabel(mode: String): String = when (mode) {
        "CLOSE" -> "关闭"
        "GLOBAL" -> "跟随"
        "OWN" -> "专属"
        else -> mode
    }

    override val ownBadge = "专属"
    override val useCurrentPoint = "用当前选点"
    override val resumeFollow = "恢复跟随"
    override fun launchInContainer(label: String): String = "容器内启动 $label"
    override fun uninstallFromContainer(label: String): String = "从容器卸载 $label"
    override fun importApp(label: String): String = "导入 $label"
    override val unreadableFile = "无法读取所选文件"
    override val readFileFailed = "读取文件失败"
    override val cannotLaunchInContainer = "无法在容器内启动该应用"
    override val uninstalled = "已从容器卸载"
    override val uninstallFailed = "卸载失败"
    override fun modeChanged(packageLabel: String, modeLabel: String): String =
        "$packageLabel 已设为 $modeLabel"
    override val switchFailed = "切换失败：容器服务不可用"
    override val pickFirst = "请先在首页选点"
    override fun ownSet(packageName: String): String = "$packageName 已使用专属坐标"
    override val ownSetFailed = "设置专属坐标失败"
    override fun ownCleared(packageName: String): String = "$packageName 已取消专属坐标"
    override val ownClearFailed = "取消专属坐标失败"
    override val restored = "容器定位状态已还原"
    override fun imported(packageName: String): String = "已导入容器：$packageName"
    override val unknownReason = "未知原因"
    override val engineUnavailable = "容器引擎不可用"
    override val appPackageNotFound = "未找到该应用的安装包"
    override val apkFileMissing = "APK 文件不存在"
    override val importSucceeded = "导入成功"
    override val importFailed = "导入失败"
    override val importFailedConnection = "导入失败：容器服务连接异常"

    override val initTitle = "容器引擎"
    override val initInitializing = "正在初始化虚拟容器"
    override val initReady = "容器引擎已就绪"
    override val initUnavailable = "容器引擎不可用"
    override val initSubtitle = "目标应用导入隔离沙箱运行，定位 API 读取模拟坐标"
    override val preparing = "准备中…"
    override fun checksDone(done: Int, total: Int): String = "$done / $total 项检查完成"
    override val experimentalTitle = "Android 15+ 实验性支持"
    override val experimentalText =
        "当前系统版本的容器支持为实验性质，已在模拟器验证；真机表现可能不同。" +
            "容器不可用时不会自动改写系统定位，需要手动切换到系统 Mock 通道。"
    override val manageApps = "管理容器应用"
    override val recheck = "重新检测"
}

private object ZhOnboarding : OnboardingTexts {
    override val appTitle = "定位模拟器"
    override val tagline = "面向开发者与授权测试的定位验证工具\n虚拟容器代理 · 免 root · 地图选点 · 轨迹模拟"
    override val boundarySection = "使用边界"
    override val boundaryTestOnly = "仅用于自有 App 或已获得授权的测试场景。"
    override val boundaryChannels =
        "容器代理通道免 root、不写系统 Mock，容器内应用读到的坐标不带 isMock 标记；" +
            "系统 Mock 通道仍需开发者选项授权且会被标记为 isMock=true。"
    override val boundaryScope = "作用范围仅限导入容器的应用，不会修改主机上的原应用。"
    override val boundaryLiability = "用于考勤打卡、位置欺诈等场景的后果由使用者自行承担。"
    override val consentLabel = "我已阅读并理解上述使用边界与免责说明，并承诺在合法合规范围内使用。"
    override val agreeContinue = "同意并继续"
    override val declineExit = "不同意并退出"
    override fun versionLine(version: String): String =
        "v$version · 支持 Android 8.1 及以上 arm64 设备"

    override val permissionsTitle = "权限总览"
    override fun permissionsReady(done: Int, total: Int): String = "$done / $total 项已就绪"
    override val hintNeedMockApp = "缺少「模拟位置应用」授权时，无法向系统写入任何坐标。"
    override val hintNeedLocation = "缺少定位权限时，Android 14+ 无法启动前台定位服务。"
    override val hintNeedOverlay = "缺少悬浮窗权限时，切到目标应用后无法呼出悬浮球。"
    override val hintAllReady = "关键权限已就绪，可以开始一次模拟。"
    override val permLocationTitle = "定位（精确位置）"
    override val permLocationSubtitle = "前台定位服务与地图浏览；不读取、不上传真实轨迹"
    override val permNotificationTitle = "通知"
    override val permNotificationSubtitle = "前台服务常驻通知，降低被系统回收的概率"
    override val permOverlayTitle = "悬浮窗"
    override val permOverlaySubtitle = "在其他应用上显示悬浮球与快捷面板"
    override val permMockAppTitle = "模拟位置应用"
    override val permMockAppSubtitle = "开发者选项 → 选择模拟位置信息应用 → GpsProxy"
    override val permBatteryTitle = "忽略电池优化"
    override val permBatterySubtitle = "降低锁屏与长时间巡航被系统回收的概率"
    override val privacyBanner =
        "本应用不申请读取通话记录、短信、通讯录等与定位无关的权限。" +
            "容器代理通道免 root、不写系统 Mock，容器内应用读到的坐标不带 isMock 标记；" +
            "系统 Mock 通道则会被系统标记为 isMock=true。"
    override val refreshStatus = "刷新授权状态"
    override val mockAppDialogTitle = "开启模拟位置应用"
    override val mockAppSteps = listOf(
        "1. 进入系统「开发者选项」",
        "2. 找到「选择模拟位置信息应用」",
        "3. 在列表中选择 GpsProxy",
        "4. 回到本页刷新授权状态",
    )
    override val mockAppFootnote = "部分 ROM 的入口名称略有不同；若找不到该选项，说明系统限制了 Mock 能力。"
    override val goDeveloperOptions = "去开发者选项"
    override val granted = "已授权"
    override val goGrant = "去授权"
    override val locationGranted = "定位权限已授予"
    override val locationDenied = "定位权限未授予"

    override val engineTitle = "注入引擎"
    override val engineInitializing = "正在初始化注入引擎"
    override val engineReady = "注入引擎已就绪"
    override val engineSubtitle = "虚拟容器代理定位：免 root，不重打包、不修改目标应用"
    override val containerNotReadyTitle = "容器运行时未就绪"
    override val containerNotReadyText =
        "免 root 通道依赖 BlackBox 虚拟容器。请在容器检测页重试；" +
            "仍失败时只能改用系统 Mock 通道（需开发者选项授权，坐标带 isMock 标记）。"
    override val checkContainer = "检查容器运行时"
    override val enterHome = "进入首页"
    override val viewUnsupportedReason = "查看不支持原因"
    override val recheck = "重新检测"

    override val deviceCheckTitle = "设备检测"
    override val unsupportedTitle = "当前设备不支持"
    override val unsupportedSubtitle = "注入引擎无法在当前环境安全运行，已阻止继续使用。"
    override val unsupportedFallbackIssue = "设备能力检测未通过"
    override fun errorCodeLine(code: String): String = "错误码：$code"
    override val unsupportedMessage = "检测到设备不满足最低运行要求。这是硬性拦截，不支持跳过。"
    override fun currentEnv(apiLevel: Int, abiLabel: String): String =
        "当前环境：Android $apiLevel · $abiLabel"
    override val viewSupportedDevices = "查看支持机型"
    override val supportScopeTitle = "支持范围"
    override val supportScopeText =
        "Android 8.1 – 15 的 arm64 设备；需要可用的系统定位服务。" +
            "HarmonyOS NEXT 不提供 Android 运行时，无法安装本应用。"
    override val gotIt = "知道了"

    override val issueNo64Bit = "系统仅提供 32 位 ABI，无法运行本工具"
    override val issueHarmony = "HarmonyOS NEXT 不提供 Android 运行时"
    override val unknownAbi = "未知"

    override val splashTagline = "官方 Mock Location · 开发者定位验证"
    override val mapMarkerLabel = "模拟位置"
}

private object ZhSelfCheck : SelfCheckTexts {
    override val engineVersionTitle = "校验系统版本与 ABI"
    override fun engineVersionPending(apiLevel: Int): String = "Android $apiLevel"
    override fun engineSupported(apiLevel: Int, abiLabel: String): String = "Android $apiLevel · $abiLabel"
    override val engineUnsupported = "设备不满足最低要求"
    override val engineContainerTitle = "检查容器虚拟化运行时"
    override val engineContainerPending = "BlackBox 引擎"
    override val engineContainerReady = "容器运行时已就绪 · 免 root"
    override val engineContainerUnavailable = "容器运行时不可用，免 root 通道不可用"
    override val engineProxyTitle = "检查容器定位代理"
    override val engineProxyPending = "ILocationManager 代理"
    override val engineProxyReady = "定位代理可读写"
    override val engineProxyUnavailable = "容器定位代理未就绪"
    override val engineAppsTitle = "检查容器应用"
    override val engineAppsPending = "已导入目标应用"
    override fun appsImported(count: Int): String = "已导入 $count 个应用"
    override val noAppsImported = "尚未导入应用，需先在容器应用页导入"
    override val engineMockTitle = "检查系统 Mock（备用）"
    override val engineMockPending = "开发者选项模拟位置授权"
    override val engineMockReady = "备用通道可用（坐标带 isMock 标记）"
    override val engineMockUnavailable = "未授权，仅容器通道可用"

    override val containerRuntimeTitle = "虚拟化运行时"
    override val containerRuntimePending = "BlackBox 引擎加载"
    override val containerRuntimeReady = "引擎已随应用启动"
    override val containerRuntimeFailed = "引擎加载失败，仅官方 Mock 可用"
    override val abiTitle = "ABI 兼容检查"
    override val abiPending = "容器进程架构"
    override fun abiDetail(abiLabel: String): String = "64 位 ABI · $abiLabel"
    override val abiMissing = "缺少 64 位 ABI"
    override val hookTitle = "Hook 框架"
    override val hookPending = "系统服务代理"
    override val hookReady = "AMS / PMS / Location 代理就绪"
    override val hookFailed = "系统服务代理不可用"
    override val binderTitle = "Binder 通道"
    override val binderPending = "容器服务连接"
    override val binderReady = "容器服务进程连接正常"
    override val binderFailed = "无法连接容器服务进程"
    override val locationProxyTitle = "定位代理"
    override val locationProxyPending = "ILocationManager / 基站代理"
    override val locationProxyReady = "坐标 / 基站回调通道可用"
    override val locationProxyFailed = "定位代理未就绪，容器内应用将读取真实定位"

    override fun exception(error: String): String = "检查异常：$error"
}
private object ZhChannel : ChannelTexts {
    override fun channelName(id: String): String = when (id) {
        "CONTAINER" -> "容器定位代理"
        "GPS" -> "LocationManager · GPS"
        "NETWORK" -> "LocationManager · Network"
        "FUSED" -> "Fused Location"
        "SDK" -> "高德 / 腾讯定位 SDK"
        "SENSOR" -> "Sensor / 计步"
        "CELL" -> "基站指纹"
        "WIFI" -> "WiFi 定位"
        else -> id
    }

    override fun channelDescription(id: String): String = when (id) {
        "CONTAINER" -> "沙箱内 Hook ILocationManager，注入坐标不写系统 Mock、无 isMock 标记"
        "GPS" -> "官方 Mock 通道：写入 GPS 测试提供者（需开发者选项授权）"
        "NETWORK" -> "官方 Mock 通道：写入网络定位测试提供者"
        "FUSED" -> "容器内无 GMS 时由 LocationManager 代理承担；官方通道仅依赖系统转发"
        "SDK" -> "容器内 SDK 走系统定位 API，因此被代理覆盖；宿主侧 SDK 不受影响"
        "SENSOR" -> "本版本不注入传感器与计步数据"
        "CELL" -> "按坐标推导同城基站，随模拟坐标同步下发到容器内 TelephonyManager"
        "WIFI" -> "容器内 WiFi 信息由沙箱代理解析，宿主侧扫描数据不在覆盖范围"
        else -> ""
    }

    override fun note(kind: String, count: Int): String = when (kind) {
        "CONTAINER_READY" -> "已接管 $count 个容器应用 · 免 root"
        "CONTAINER_EMPTY" -> "容器内没有可接管的应用，请先导入目标应用"
        "CONTAINER_UNAVAILABLE" -> "容器运行时不可用，无法注入"
        "CONTAINER_IDLE" -> "免 root 主通道，等待容器就绪"
        "MOUNTED_OFFICIAL" -> "已挂载 · 官方 Mock"
        "NOT_MOUNTED" -> "未挂载"
        "MOUNTED_FUSED" -> "已挂载测试提供者"
        "FUSED_NOT_EXPOSED" -> "系统未暴露 fused 测试提供者"
        "SYNCED" -> "已下发 · 随坐标同步"
        "RUNTIME_READY" -> "运行时就绪 · 免 root"
        "MOCK_STANDBY" -> "备用通道，等待开始"
        "NOT_MOCK_APP" -> "未设为模拟位置应用"
        "FOLLOWS_CONTAINER" -> "随容器通道下发"
        "STOPPED_CONTAINER" -> "已停止，容器应用恢复真实定位"
        "STOPPED" -> "已停止"
        "FUSED_FORWARD_ONLY" -> "仅依赖系统转发"
        "NOT_FORCED" -> "不可直接强制"
        "CONTAINER_ONLY" -> "仅容器内沙箱应用生效"
        "NOT_PROVIDED" -> "不提供"
        "CONTAINER_ACTIVE" -> "容器通道生效"
        "HOST_OUT_OF_SCOPE" -> "宿主侧不在能力范围"
        else -> ""
    }

    override fun hitCount(count: Long): String = "$count 次"
    override val statusMiss = "未命中"
    override val statusUnavailable = "不可用"
    override val statusDisabled = "未启用"

    override val pageTitle = "运行诊断"
    override val export = "导出诊断包"
    override val exportContentDescription = "导出诊断包"
    override val infoBanner = "注入通道逐个探测：命中的通道负责返回模拟坐标；未命中的通道如实展示，不做美化。"
    override val channelsSection = "注入通道"
    override val mockFlagSection = "模拟标识"
    override val mockFlagContainerTitle = "isMock=false（容器代理）"
    override val mockFlagContainerText =
        "坐标由容器内 ILocationManager 代理解析后回填，不经过系统测试提供者，" +
            "因此 Location.isFromMockProvider() 为 false。作用范围仅限容器内已导入的应用。"
    override val mockFlagOfficialTitle = "isMock=true（系统 Mock）"
    override val mockFlagOfficialText =
        "坐标由 Android 官方模拟位置提供者写入，系统会标记为模拟位置。" +
            "本通道作用于宿主全部应用，且无法隐藏该标识。"
    override val blurSection = "位置模糊"
    override fun blurOn(radiusMeters: Int): String = "已开启 · ±$radiusMeters m"
    override val blurOff = "未开启"
    override fun blurActiveText(injectedCoords: String, selectedCoords: String, radiusMeters: Int): String =
        "应用读到的是偏移后的坐标（$injectedCoords），与选点（$selectedCoords）相差不超过 " +
            "±$radiusMeters m（另有 ≤1.25 m 自然漂移）。偏移在会话内固定，不会逐次漂移。"
    override val blurInactiveText = "开启后真实选点会在设定半径内被平移一次，应用无法据此还原用户选点。"
    override val errorsSection = "最近错误"
    override val errorNone = "本次会话没有记录到错误。未命中的通道不代表异常，见上方逐条状态。"
    override fun logsSection(count: Int): String = "实时日志 · 最近 $count 条"
    override val clearLogs = "清空日志"
    override val exportHint = "诊断包包含设备信息、通道状态与脱敏日志，不含目标应用数据。"
    override val exportDialogMessage =
        "将生成一个 ZIP（设备信息、通道状态、脱敏日志），并通过系统分享面板导出。\n\n" +
            "日志不包含目标应用内容、账号或联系人，但会包含当前模拟坐标。确认继续？"
    override val exportConfirm = "导出"
    override val noLogs = "暂无日志"
}
private object ZhMessage : MessageTexts {
    override val simulationStopped = "模拟已停止，已恢复真实定位"
    override val injectionPaused = "已暂停注入，坐标保持不变"
    override val injectionResumed = "已恢复注入"
    override val noMapApp = "未找到可打开地图的应用"
    override val coordsCopied = "已复制坐标"
    override val addedToFavorites = "已加入收藏"
    override fun favoriteName(coords: String): String = "模拟点 · $coords"
    override fun startSuccess(channel: String): String = when (channel) {
        "CONTAINER" -> "模拟已开启，容器内目标应用将读取该坐标（免 root）"
        "OFFICIAL" -> "模拟已开启，系统内应用将读取该坐标"
        else -> "模拟已开启"
    }
    override val logsCleared = "日志已清空"
    override fun diagnosticsExported(fileName: String): String = "诊断包已生成：$fileName"

    override val autoStopped = "已达到设定的模拟时长，已自动停止"
    override val routeStartPointSet = "起点已设为当前模拟位置"
    override val routeIncomplete = "请先补全路线"
    override val routeFavorited = "已收藏当前路线"
    override val routeSaveFailed = "保存路线失败，请稍后重试"
    override fun trackImported(points: Int): String = "已导入 $points 个轨迹点"
    override val routeNeedRoamCenter = "请先设置漫游中心"
    override val routeNeedGpx = "请先导入 GPX 轨迹"
    override val routeNeedEndpoints = "请先设置起点与终点"
    override val routeAlreadyRunning = "已有模拟在运行，请先在首页停止"
    override val routeStarted = "巡航已开始"
    override fun routeLoaded(name: String): String = "已载入「$name」到草稿"
    override val routeUnfavorited = "已取消收藏"
    override val routeFavoriteAdded = "已收藏"
    override val routeUpdateFailed = "更新路线失败"
    override val routeRenamed = "路线已重命名"
    override val routeRenameFailed = "重命名失败"
    override fun routeDeleted(name: String): String = "已删除「$name」"
    override val routeDeleteFailed = "删除失败"
    override fun speedFactorChanged(factor: String): String = "巡航速度 ${factor}x"
    override val routeDirectionSwitched = "已切换巡航方向"
    override val routeReplayed = "已从头重播"
    override val routeCannotReplay = "当前状态无法重播"
    override val noTrackToExport = "当前没有可导出的轨迹"
    override val gpxExported = "已导出 GPX 轨迹"
    override val defaultGpxName = "GpsProxy 轨迹"
}
private object ZhNotification : NotificationTexts {
    override val channelName = "定位注入服务"
    override val channelDescription = "保持模拟定位会话运行"
    override val titlePaused = "模拟已暂停"
    override val titleCompleted = "巡航已完成"
    override val titleError = "注入异常"
    override val titleActive = "模拟生效中"
    override val titleIdle = "定位模拟服务"
    override val noPoint = "未选择坐标"
    override fun blurSuffix(radiusMeters: Int): String = " · 模糊±${radiusMeters}m"
    override fun autoStopSuffix(time: String): String = " · 自动停止 $time"
    override fun compactBody(point: String, accuracyMeters: Int): String = "$point · ±$accuracyMeters m"
    override fun detailedBody(
        point: String,
        accuracyMeters: Int,
        coordSystem: String,
        blurSuffix: String,
        autoStopSuffix: String,
    ): String = "$point · ±$accuracyMeters m · $coordSystem$blurSuffix$autoStopSuffix"
    override val subTextContainer = "容器代理 · isMock=false"
    override val subTextOfficial = "系统 Mock · isMock=true"
    override val resume = "继续"
    override val pause = "暂停"
    override val stop = "停止"

    override val bootChannelName = "会话恢复提醒"
    override val bootChannelDescription = "重启后提醒上次未结束的模拟"
    override val bootTitle = "上次的模拟未结束"
    override fun bootText(point: String): String = "$point · 点击打开 GpsProxy 继续模拟"
    override val lastPoint = "上次选点"
}

private object ZhLog : LogTexts {
    override fun routeHistoryFailed(error: String): String = "路线历史记录失败：$error"
    override fun targetSet(coords: String, address: String): String = "目标已设置：$coords · $address"
    override fun singleStarted(channel: String, blurSuffix: String): String =
        "单点模拟已开始 · 通道=$channel$blurSuffix"
    override fun blurSuffix(meters: Int): String = "（模糊 ±$meters m）"
    override fun routeStarted(
        kind: String,
        mode: String,
        speedKmh: Int,
        distance: String,
        points: Int,
        source: String,
        blurSuffix: String,
    ): String = "巡航已开始：$kind $mode ${speedKmh}km/h，${distance}m，$points 个点 · $source$blurSuffix"
    override val paused = "注入已暂停，坐标保持不变"
    override val resumed = "注入已恢复"
    override fun speedFactorSet(factor: String): String = "巡航速度设为 ${factor}x"
    override val seekIgnored = "跳转被忽略：巡航已完成，请先重播"
    override fun seeked(percent: Int): String = "巡航跳转到 $percent%"
    override val reverseIgnored = "反向被忽略：巡航未在运行或暂停"
    override fun direction(forward: Boolean): String =
        if (forward) "巡航方向=正向" else "巡航方向=反向"
    override val restarted = "巡航已从头重播"
    override val walkIgnored = "行走被忽略：单点会话未在运行"
    override val stopped = "模拟已停止，已恢复真实定位"
    override val nudgeIgnored = "巡航运行中忽略微调"
    override fun blurEnabled(meters: Int): String = "位置模糊已开启 ±$meters m"
    override val blurDisabled = "位置模糊已关闭"
    override fun pushRetry(attempt: Int, max: Int): String = "写入失败（$attempt/$max），正在重试"
    override fun injectionFailed(title: String): String = "注入失败：$title"
    override fun injected(
        coords: String,
        speed: String,
        bearing: String,
        accuracy: Int,
        satellites: Int,
        channel: String,
    ): String = "注入 $coords 速度=$speed 航向=$bearing 精度=$accuracy 卫星=$satellites 通道=$channel"
    override val walkLatitudeLimit = "行走已停止：到达纬度限制"
    override val routeCompleted = "巡航已到达终点"
    override fun failure(code: String, cause: String): String = "$code · $cause"
    override fun autoStopped(minutes: Int): String = "自动停止到期（$minutes 分钟）"
    override val pumpCompleted = "模拟帧循环已完成"
    override fun pumpStopped(code: String): String = "模拟帧循环已停止：$code"
    override val containerUnavailable = "容器通道不可用"
    override val containerNoApps = "容器通道没有目标应用"
    override fun providerIssues(issues: String): String = "提供者异常：$issues"
    override fun officialPushFailed(issues: String): String = "官方 Mock 写入失败：$issues"
    override fun containerMirrorRetry(millis: Long): String = "容器镜像写入失败；${millis}ms 后重试"
    override fun cellFingerprint(lac: Int, cid: Int, dbm: Int): String =
        "基站指纹 lac=$lac cid=$cid dbm=$dbm"
    override val locationManagerUnavailable = "LocationManager 不可用"
    override fun providerDenied(provider: String): String = "$provider：未授权写入模拟位置"
    override val noTestProvider = "没有可用的测试提供者"
}
