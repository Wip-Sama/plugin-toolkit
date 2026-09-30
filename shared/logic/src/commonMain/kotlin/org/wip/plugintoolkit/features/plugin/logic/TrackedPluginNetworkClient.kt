package org.wip.plugintoolkit.features.plugin.logic

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlin.time.Clock
import org.wip.plugintoolkit.api.PluginNetworkClient

/**
 * Implementation of [PluginNetworkClient] that performs real HTTP requests via [HttpClient]
 * and records transfer metrics (downloaded/uploaded bytes and throughput) in [ResourceUsageTracker].
 */
class TrackedPluginNetworkClient(
    private val httpClient: HttpClient?,
    private val tracker: ResourceUsageTracker
) : PluginNetworkClient {

    override suspend fun get(url: String, headers: Map<String, String>): ByteArray {
        val client = httpClient ?: return ByteArray(0)
        val startTime = Clock.System.now().toEpochMilliseconds()
        val response = client.get(url) {
            headers {
                headers.forEach { (k, v) -> append(k, v) }
            }
        }
        val bytes = response.body<ByteArray>()
        val durationMs = (Clock.System.now().toEpochMilliseconds() - startTime).coerceAtLeast(1L)
        val throughput = (bytes.size * 1000L) / durationMs
        tracker.recordNetworkUsage(
            read = bytes.size.toLong(),
            written = 0L,
            throughputBytesPerSec = throughput
        )
        return bytes
    }

    override suspend fun getText(url: String, headers: Map<String, String>): String {
        val bytes = get(url, headers)
        return bytes.decodeToString()
    }

    override suspend fun post(url: String, body: ByteArray, headers: Map<String, String>): ByteArray {
        val client = httpClient ?: return ByteArray(0)
        val startTime = Clock.System.now().toEpochMilliseconds()
        val response = client.post(url) {
            headers {
                headers.forEach { (k, v) -> append(k, v) }
            }
            setBody(body)
        }
        val responseBytes = response.body<ByteArray>()
        val durationMs = (Clock.System.now().toEpochMilliseconds() - startTime).coerceAtLeast(1L)
        val totalBytes = responseBytes.size + body.size
        val throughput = (totalBytes * 1000L) / durationMs
        tracker.recordNetworkUsage(
            read = responseBytes.size.toLong(),
            written = body.size.toLong(),
            throughputBytesPerSec = throughput
        )
        return responseBytes
    }

    override suspend fun postText(url: String, body: String, headers: Map<String, String>): String {
        val reqBytes = body.encodeToByteArray()
        val resBytes = post(url, reqBytes, headers)
        return resBytes.decodeToString()
    }
}
