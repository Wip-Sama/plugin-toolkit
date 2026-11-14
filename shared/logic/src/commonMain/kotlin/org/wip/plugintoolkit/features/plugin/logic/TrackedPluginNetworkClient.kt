package org.wip.plugintoolkit.features.plugin.logic

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.ByteArrayContent
import io.ktor.util.toMap
import org.wip.plugintoolkit.api.HttpMethod
import org.wip.plugintoolkit.api.HttpRequest
import org.wip.plugintoolkit.api.HttpResponse
import org.wip.plugintoolkit.api.PluginNetworkClient
import kotlin.time.Clock

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
        recordTransfer(bytesRead = bytes.size.toLong(), bytesWritten = 0L, startTimeMs = startTime)
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
        recordTransfer(
            bytesRead = responseBytes.size.toLong(),
            bytesWritten = body.size.toLong(),
            startTimeMs = startTime
        )
        return responseBytes
    }

    override suspend fun postText(url: String, body: String, headers: Map<String, String>): String {
        val reqBytes = body.encodeToByteArray()
        val resBytes = post(url, reqBytes, headers)
        return resBytes.decodeToString()
    }

    override suspend fun request(request: HttpRequest): HttpResponse {
        val client = httpClient ?: return HttpResponse(
            statusCode = 0,
            errorMessage = "HTTP client is not available"
        )
        val startTime = Clock.System.now().toEpochMilliseconds()
        return try {
            var writtenBytes = 0L
            val response = client.request(request.url) {
                method = when (request.method) {
                    HttpMethod.GET -> io.ktor.http.HttpMethod.Get
                    HttpMethod.POST -> io.ktor.http.HttpMethod.Post
                    HttpMethod.PUT -> io.ktor.http.HttpMethod.Put
                    HttpMethod.PATCH -> io.ktor.http.HttpMethod.Patch
                    HttpMethod.DELETE -> io.ktor.http.HttpMethod.Delete
                    HttpMethod.HEAD -> io.ktor.http.HttpMethod.Head
                }
                request.timeoutMs?.let { timeoutVal ->
                    timeout {
                        requestTimeoutMillis = timeoutVal
                    }
                }
                val hasMultipart = request.multipartParts.isNotEmpty()
                headers {
                    request.headers.forEach { (k, v) ->
                        if (!(hasMultipart && k.equals(HttpHeaders.ContentType, ignoreCase = true))) {
                            append(k, v)
                        }
                    }
                }
                when {
                    hasMultipart -> {
                        writtenBytes = request.multipartParts.sumOf { it.data.size.toLong() }
                        setBody(
                            MultiPartFormDataContent(
                                formData {
                                    request.multipartParts.forEach { part ->
                                        val partHeaders = Headers.build {
                                            if (part.filename != null) {
                                                append(
                                                    HttpHeaders.ContentDisposition,
                                                    "form-data; name=\"${part.name}\"; filename=\"${part.filename}\""
                                                )
                                            }
                                            if (part.contentType != null) {
                                                append(HttpHeaders.ContentType, part.contentType!!)
                                            }
                                        }
                                        append(part.name, part.data, partHeaders)
                                    }
                                }
                            )
                        )
                    }
                    request.body != null -> {
                        val rawBody = request.body!!
                        writtenBytes = rawBody.size.toLong()
                        val parsedType = request.headers.entries
                            .firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }
                            ?.value
                            ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                        setBody(ByteArrayContent(rawBody, contentType = parsedType))
                    }
                }
            }

            val responseBytes = if (request.method == HttpMethod.HEAD) {
                ByteArray(0)
            } else {
                response.body<ByteArray>()
            }
            recordTransfer(
                bytesRead = responseBytes.size.toLong(),
                bytesWritten = writtenBytes,
                startTimeMs = startTime
            )
            HttpResponse(
                statusCode = response.status.value,
                headers = response.headers.toMap(),
                body = responseBytes
            )
        } catch (t: Throwable) {
            HttpResponse(
                statusCode = 0,
                errorMessage = t.message ?: t::class.simpleName
            )
        }
    }

    private fun recordTransfer(bytesRead: Long, bytesWritten: Long, startTimeMs: Long) {
        val durationMs = (Clock.System.now().toEpochMilliseconds() - startTimeMs).coerceAtLeast(1L)
        val throughput = ((bytesRead + bytesWritten) * 1000L) / durationMs
        tracker.recordNetworkUsage(
            read = bytesRead,
            written = bytesWritten,
            throughputBytesPerSec = throughput
        )
    }
}
