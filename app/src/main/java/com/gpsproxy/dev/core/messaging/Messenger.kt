package com.gpsproxy.dev.core.messaging

import com.gpsproxy.dev.core.error.ErrorCode
import com.gpsproxy.dev.core.i18n.Strings
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.core.result.DomainError
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class MessageTone { INFO, SUCCESS, WARN, ERROR }

/**
 * 一条瞬时提示。
 *
 * @param text 展示文案
 * @param tone 视觉语气，决定背景色与图标
 * @param errorCode 若来自领域错误则携带错误码，便于 UI 跳转到诊断页
 */
data class Message(
    val text: String,
    val tone: MessageTone = MessageTone.INFO,
    val errorCode: ErrorCode? = null,
    val id: Long = System.nanoTime(),
)

/**
 * 瞬时消息通道。
 *
 * 旧实现使用全局 `ToastBus` 单例，屏幕与 ViewModel 都能直接调用，
 * 导致"谁在什么条件下弹什么"完全不可追踪，也无法在测试里断言。
 * 现在统一走端口：ViewModel/用例注入 [Messenger]，宿主只收集一次并渲染。
 */
interface Messenger {

    val messages: SharedFlow<Message>

    /** 非译文/纯 ASCII 技术文案（仍保留给诊断与调试）。 */
    fun show(text: String, tone: MessageTone = MessageTone.INFO)

    /** 主路径：在 emit 时刻按当前语言解析，避免领域层持有已渲染文案。 */
    fun show(text: Strings.() -> String, tone: MessageTone = MessageTone.INFO)

    /** 以错误码的标题为准展示，避免各处手写文案。 */
    fun showError(code: ErrorCode, detail: String? = null)

    fun showError(error: DomainError)
}

/**
 * 默认实现：无回放、缓冲 8 条、丢弃最旧。
 *
 * 不做回放是有意为之：toast 属于瞬时反馈，界面重建时重放旧提示会制造困惑。
 */
class AppMessenger(
    private val strings: () -> Strings = { StringsHolder.current },
) : Messenger {

    private val _messages = MutableSharedFlow<Message>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val messages: SharedFlow<Message> = _messages.asSharedFlow()

    override fun show(text: String, tone: MessageTone) {
        if (text.isBlank()) return
        _messages.tryEmit(Message(text = text, tone = tone))
    }

    override fun show(text: Strings.() -> String, tone: MessageTone) {
        show(strings().text(), tone)
    }

    override fun showError(code: ErrorCode, detail: String?) {
        val strings = strings()
        val title = strings.error.title(code)
        val text = if (detail.isNullOrBlank()) title else "$title${strings.common.errorDetailSeparator}$detail"
        _messages.tryEmit(
            Message(text = text, tone = MessageTone.ERROR, errorCode = code),
        )
    }

    override fun showError(error: DomainError) {
        _messages.tryEmit(
            Message(
                text = strings().error.title(error.code),
                tone = MessageTone.ERROR,
                errorCode = error.code,
            ),
        )
    }
}
