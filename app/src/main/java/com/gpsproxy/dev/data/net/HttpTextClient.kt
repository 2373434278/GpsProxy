package com.gpsproxy.dev.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 文本 HTTP 客户端端口。
 *
 * 抽出后 POI / 路径规划的数据源可以在单测里注入固定响应，
 * 不需要真实网络，也不需要 Robolectric。
 */
interface HttpTextClient {

    suspend fun get(baseUrl: String, params: Map<String, String>): String
}

/**
 * 基于 `HttpURLConnection` 的实现。
 *
 * 这些是旧仓库里已有但硬编码的行为，现在显式化：
 * - 连接/读取超时各 8 秒；
 * - 失败自动重试一次（网络抖动在移动端很常见）；
 * - 只接受 2xx，其余抛 [IOException]，由上层决定回退策略。
 */
class UrlConnectionHttpTextClient(
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val retries: Int = DEFAULT_RETRIES,
    private val userAgent: String = "GpsProxy/0.1 (developer test tool)",
) : HttpTextClient {

    override suspend fun get(baseUrl: String, params: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            repeat(retries + 1) {
                try {
                    return@withContext execute(baseUrl, params)
                } catch (error: Throwable) {
                    lastError = error
                }
            }
            throw lastError ?: IOException("request failed")
        }

    private fun execute(baseUrl: String, params: Map<String, String>): String {
        val query = params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val connection = (URL("$baseUrl?$query").openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", this@UrlConnectionHttpTextClient.userAgent)
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000
        const val DEFAULT_RETRIES = 1
    }
}
