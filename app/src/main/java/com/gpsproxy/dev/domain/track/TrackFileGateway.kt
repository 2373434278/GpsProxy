package com.gpsproxy.dev.domain.track

/**
 * 轨迹文件的读写端口。
 *
 * 领域层只认识 URI 字符串与文本内容，不依赖 Android 的 ContentResolver/SAF；
 * 平台实现负责实际 IO，单测用内存实现即可覆盖导入导出逻辑。
 */
interface TrackFileGateway {

    /** 读取失败（授权失效、文件不存在、IO 异常）返回 null。 */
    suspend fun readText(uri: String): String?

    /** 写入成功返回 true。 */
    suspend fun writeText(uri: String, content: String): Boolean
}
