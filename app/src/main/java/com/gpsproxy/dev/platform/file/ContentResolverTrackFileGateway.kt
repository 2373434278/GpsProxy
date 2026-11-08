package com.gpsproxy.dev.platform.file

import android.content.Context
import android.net.Uri
import com.gpsproxy.dev.domain.track.GpxCodec
import com.gpsproxy.dev.domain.track.TrackFileGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 ContentResolver 的轨迹文件读写。
 *
 * 使用 SAF（`OpenDocument`/`CreateDocument`）拿到的 URI，不需要存储权限；
 * IO 在 IO 调度器执行，失败统一返回 null/false，由用例层映射成错误码。
 *
 * 读取时先按 [GpxCodec.MAX_XML_CHARS] 截断，避免用户误选超大文件时
 * 在进入解析器之前就把内存吃光。
 */
class ContentResolverTrackFileGateway(private val context: Context) : TrackFileGateway {

    override suspend fun readText(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                val reader = input.bufferedReader(Charsets.UTF_8)
                val builder = StringBuilder()
                val buffer = CharArray(8 * 1024)
                var total = 0
                var tooLarge = false
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > GpxCodec.MAX_XML_CHARS) {
                        tooLarge = true
                        break
                    }
                    builder.append(buffer, 0, read)
                }
                if (tooLarge) null else builder.toString()
            }
        }.getOrNull()
    }

    override suspend fun writeText(uri: String, content: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { output ->
                output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                true
            } ?: false
        }.getOrDefault(false)
    }
}
