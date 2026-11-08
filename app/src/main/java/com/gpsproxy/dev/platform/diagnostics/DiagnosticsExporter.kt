package com.gpsproxy.dev.platform.diagnostics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.gpsproxy.dev.core.i18n.StringsHolder
import com.gpsproxy.dev.core.log.LogBus
import com.gpsproxy.dev.domain.diagnostics.DiagnosticsReport
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把诊断报告打成 zip 并交给系统分享。
 *
 * 从 ViewModel 里抽出的原因：文件 IO、FileProvider 授权、Intent 组装都是
 * 平台细节；ViewModel 只应该决定"用户点了导出"。
 */
class DiagnosticsExporter(private val context: Context) {

    /** @return 成功时返回生成的文件名，失败返回 null */
    fun export(report: DiagnosticsReport): String? = runCatching {
        val directory = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("MMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "diag-$stamp.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
            report.sections.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, StringsHolder.text { channel.export }).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        file.name
    }.getOrElse {
        LogBus.error("diagnostics export failed: ${it.javaClass.simpleName}")
        null
    }

    private companion object {
        const val EXPORT_DIR = "exports"
    }
}
