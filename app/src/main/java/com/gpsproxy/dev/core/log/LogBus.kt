package com.gpsproxy.dev.core.log

import android.util.Log
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.StringsHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class LogSeverity { INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Long,
    val severity: LogSeverity,
    val message: String,
) {
    fun timeText(): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

    private companion object {
        /**
         * `DateTimeFormatter` 是线程安全的，`SimpleDateFormat` 不是。
         * 旧实现用共享的 `SimpleDateFormat` 在注入线程与 UI 线程并发格式化，
         * 属于偶发时间串扰的隐患。
         */
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

/**
 * 日志接收端口的抽象。
 *
 * 领域层只依赖这个接口写日志；生产环境注入 [LogBus]（同时转发到 Logcat），
 * 测试可以注入内存实现或内存 sink，不需要 Android 运行时。
 */
interface LogSink {

    /** UI 可见的最近日志（数量有上限，避免重组开销随历史无限增长）。 */
    val entries: StateFlow<List<LogEntry>>

    fun log(severity: LogSeverity, message: String)

    /** 在写入时刻按当前语言解析日志模板。 */
    fun log(severity: LogSeverity, text: Strings.() -> String) =
        log(severity, StringsHolder.text(text))

    /** 全量快照，用于诊断包导出。 */
    fun snapshot(): List<LogEntry>

    fun clear()

    fun info(message: String) = log(LogSeverity.INFO, message)

    fun info(text: Strings.() -> String) = log(LogSeverity.INFO, text)

    fun warn(message: String) = log(LogSeverity.WARN, message)

    fun warn(text: Strings.() -> String) = log(LogSeverity.WARN, text)

    fun error(message: String) = log(LogSeverity.ERROR, message)

    fun error(text: Strings.() -> String) = log(LogSeverity.ERROR, text)
}

/**
 * 线程安全的内存环形日志。
 *
 * 设计要点：
 * - 用真正有界的环形缓冲（容量 [capacity]），而不是"无界 list + 每次拷贝"；
 * - 只有 [renderLimit] 条会推给 UI，且只在**新增一条**时复制该小窗口，
 *   避免旧实现每条日志都 `toList().takeLast(200)` 带来的 O(n) 分配；
 * - 时间戳来源可注入，测试里可以固定时间。
 *
 * @param writer 可选的外部落点（生产环境是 Logcat，测试里通常为 null）
 */
class InMemoryLogSink(
    private val capacity: Int = 2000,
    private val renderLimit: Int = 200,
    private val timestamp: () -> Long = System::currentTimeMillis,
    private val writer: ((LogEntry) -> Unit)? = null,
) : LogSink {

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(capacity)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    override val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    override fun log(severity: LogSeverity, message: String) {
        val entry = LogEntry(timestamp = timestamp(), severity = severity, message = message)
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > capacity) {
                buffer.removeFirst()
            }
            _entries.value = buffer.takeLast(renderLimit)
        }
        writer?.invoke(entry)
    }

    override fun snapshot(): List<LogEntry> = synchronized(lock) { buffer.toList() }

    override fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }
}

/**
 * 进程级默认日志出口：内存环形缓冲 + Logcat 双写。
 *
 * 保留单例是为了让平台层（Service/Receiver）无需依赖注入即可记录启动过程；
 * 领域层不会直接引用它，而是通过 [LogSink] 端口注入，因此测试不受影响。
 */
object LogBus : LogSink {

    private const val TAG = "GpsProxy"

    private val sink = InMemoryLogSink(
        writer = { entry ->
            when (entry.severity) {
                LogSeverity.INFO -> Log.i(TAG, entry.message)
                LogSeverity.WARN -> Log.w(TAG, entry.message)
                LogSeverity.ERROR -> Log.e(TAG, entry.message)
            }
        },
    )

    override val entries: StateFlow<List<LogEntry>> get() = sink.entries

    override fun log(severity: LogSeverity, message: String) = sink.log(severity, message)

    override fun snapshot(): List<LogEntry> = sink.snapshot()

    override fun clear() = sink.clear()
}
