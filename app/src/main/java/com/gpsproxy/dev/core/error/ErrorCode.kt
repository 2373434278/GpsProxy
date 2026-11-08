package com.gpsproxy.dev.core.error

/**
 * 全部可展示错误的唯一身份。
 *
 * 设计取舍：
 * - 错误码是**类型**而不是字符串。历史实现里 `fail("COORD-0101", …)` 与
 *   UI 侧 `ErrorCatalog.find(code)` 各自维护一套字符串，目录里还存在从未被
 *   使用的条目，调用方无法在编译期发现拼错或遗漏。
 * - 展示文案（标题/原因/建议）不在这里，而在 `core/i18n` 的 `ErrorTexts`：
 *   领域层不持有已渲染语言，UI、通知与诊断导出共用同一份按当前语言解析的文案。
 * - [code] 保留原有字符串格式，便于日志检索与用户报障时对号入座，
 *   属于对外契约的一部分，不可随意修改。
 *
 * 新增错误码的步骤：在枚举里加一项 → 在抛出点使用它 →
 * 两个语言的 `ErrorTexts` 穷举 `when` 会强制补齐文案。
 */
enum class ErrorCode(val code: String) {
    ENG_0102("ENG-0102"),

    PERM_0102("PERM-0102"),

    PERM_0104("PERM-0104"),

    INJ_0101("INJ-0101"),

    INJ_0202("INJ-0202"),

    COORD_0101("COORD-0101"),

    ROUTE_0201("ROUTE-0201"),

    ROUTE_0302("ROUTE-0302"),

    ROUTE_0401("ROUTE-0401"),

    FILE_0101("FILE-0101"),

    DIAG_0101("DIAG-0101"),

    BOX_0101("BOX-0101"),

    BOX_0103("BOX-0103"),

    BOX_0102("BOX-0102");

    companion object {
        private val byCode: Map<String, ErrorCode> = entries.associateBy { it.code }

        /** 兼容诊断包、旧配置等以字符串形式保存错误码的场景。 */
        fun fromCode(code: String?): ErrorCode? = code?.let(byCode::get)
    }
}
