package com.gpsproxy.dev.domain.container

/**
 * 容器内一个已导入的应用。
 *
 * 这些模型放在领域层：UI 与用例只需要"有哪些应用、处于什么模式"，
 * 不应该依赖 BlackBox 的数据类型。
 *
 * @param packageName 包名，容器内唯一标识
 * @param label 展示名，优先取容器内 ApplicationInfo，取不到时回落到宿主同名应用
 * @param locationMode 该应用当前的定位模拟模式
 * @param hasCellFingerprint 是否已下发基站指纹（未下发时应用侧读到的是真实基站）
 */
data class ContainerApp(
    val packageName: String,
    val label: String,
    val locationMode: LocationMode = LocationMode.CLOSE,
    val hasCellFingerprint: Boolean = false,
)

/**
 * 容器应用的定位模拟模式。
 *
 * [raw] 与 BlackBox `BLocationManager` 的常量一一对应，映射由平台适配器负责；
 * 领域层不直接引用第三方常量，避免测试需要 Android/BlackBox 运行时。
 */
enum class LocationMode(val raw: Int) {
    CLOSE(0),
    GLOBAL(1),
    OWN(2);

    companion object {
        fun fromRaw(raw: Int): LocationMode = entries.firstOrNull { it.raw == raw } ?: CLOSE
    }
}

data class ContainerImportResult(
    val success: Boolean,
    val packageName: String? = null,
    val message: String = "",
)
