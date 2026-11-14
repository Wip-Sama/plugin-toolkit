package org.wip.plugintoolkit.features.plugin.logic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.api.HttpMethod
import org.wip.plugintoolkit.api.HttpRequest
import org.wip.plugintoolkit.api.MultipartPart
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.core.utils.RealFileSystem
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TEST_SECRET_KEY_TOKEN = "mangadex_refresh_token"
private const val TEST_SECRET_KEY_KEY = "aruba_api_key"
private const val TEST_SECRET_VALUE_TOKEN = "refresh-secret-abc-999"
private const val TEST_SECRET_VALUE_KEY = "aruba-key-xyz-123"

/**
 * Unit tests verifying encrypted [DefaultPluginSecretStorage], dynamic path grants on
 * [HostFileSystemImpl]/[TrackingHostFileSystem], and full [HttpRequest]/[MultipartPart]
 * execution on [TrackedPluginNetworkClient].
 */
class PluginNewServicesTest {

    private lateinit var tempRootDir: File
    private lateinit var initialAllowedDir: File
    private lateinit var outsideDir: File

    @BeforeTest
    fun setUp() {
        tempRootDir = File(System.getProperty("java.io.tmpdir"), "plugin_services_test_${System.nanoTime()}").apply {
            mkdirs()
        }
        initialAllowedDir = File(tempRootDir, "allowed").apply { mkdirs() }
        outsideDir = File(tempRootDir, "outside").apply { mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        tempRootDir.deleteRecursively()
    }

    @Test
    fun `DefaultPluginSecretStorage encrypts secrets on disk and round-trips across instances`() = runTest {
        val pluginInstallDir = File(tempRootDir, "plugin_install").apply { mkdirs() }
        val fileSystem = RealFileSystem()
        val storage = DefaultPluginSecretStorage(pluginInstallDir.canonicalPath, fileSystem)

        storage.putSecret(TEST_SECRET_KEY_TOKEN, TEST_SECRET_VALUE_TOKEN)
        storage.putSecret(TEST_SECRET_KEY_KEY, TEST_SECRET_VALUE_KEY)

        val secretsFile = File(pluginInstallDir, "secrets.json")
        assertTrue(secretsFile.exists(), "secrets.json should be created in installPath")
        val rawFileContent = secretsFile.readText()
        assertFalse(rawFileContent.contains(TEST_SECRET_VALUE_TOKEN), "Raw secret plaintext must never appear on disk")
        assertFalse(rawFileContent.contains(TEST_SECRET_VALUE_KEY), "Raw secret plaintext must never appear on disk")

        val reloadedStorage = DefaultPluginSecretStorage(pluginInstallDir.canonicalPath, fileSystem)
        assertEquals(TEST_SECRET_VALUE_TOKEN, reloadedStorage.getSecret(TEST_SECRET_KEY_TOKEN))
        assertEquals(TEST_SECRET_VALUE_KEY, reloadedStorage.getSecret(TEST_SECRET_KEY_KEY))
        assertEquals(
            setOf(TEST_SECRET_KEY_TOKEN, TEST_SECRET_KEY_KEY),
            reloadedStorage.listSecretKeys().toSet()
        )

        reloadedStorage.removeSecret(TEST_SECRET_KEY_TOKEN)
        val afterRemoval = DefaultPluginSecretStorage(pluginInstallDir.canonicalPath, fileSystem)
        assertNull(afterRemoval.getSecret(TEST_SECRET_KEY_TOKEN))
        assertEquals(listOf(TEST_SECRET_KEY_KEY), afterRemoval.listSecretKeys())
    }

    @Test
    fun `DefaultPluginSecretStorage falls back to memory when installPath is blank`() = runTest {
        val failingFileSystem = object : FileSystem {
            override fun exists(path: String): Boolean = error("Disk access unexpected")
            override fun mkdirs(path: String): Boolean = error("Disk access unexpected")
            override fun copyFile(source: String, destination: String) = error("Disk access unexpected")
            override fun deleteDirectory(path: String): Boolean = error("Disk access unexpected")
            override fun readFileFromZip(zipPath: String, fileName: String): String? = error("Disk access unexpected")
            override fun listFiles(path: String): List<String> = error("Disk access unexpected")
            override fun saveFile(path: String, content: ByteArray) = error("Disk access unexpected")
            override fun readFile(path: String): String? = error("Disk access unexpected")
            override fun writeFile(path: String, content: String) = error("Disk access unexpected")
        }

        val memoryStorage = DefaultPluginSecretStorage(installPath = "   ", fileSystem = failingFileSystem)
        memoryStorage.putSecret("session_token", "ephemeral-secret")
        assertEquals("ephemeral-secret", memoryStorage.getSecret("session_token"))
        assertEquals(listOf("session_token"), memoryStorage.listSecretKeys())

        memoryStorage.removeSecret("session_token")
        assertNull(memoryStorage.getSecret("session_token"))
        assertTrue(memoryStorage.listSecretKeys().isEmpty())
    }

    @Test
    fun `TrackingHostFileSystem blocks ungranted paths until grantAccess is invoked`() = runTest {
        val outsideReadFile = File(outsideDir, "page_01.png").apply { writeBytes(byteArrayOf(10, 20, 30, 40)) }
        val outsideWriteFile = File(outsideDir, "exported.json")
        val tracker = ResourceUsageTracker()
        val hostFs = HostFileSystemImpl(
            allowedPaths = listOf(initialAllowedDir.canonicalPath),
            isDestructiveAllowed = true
        )
        val trackingFs = TrackingHostFileSystem(hostFs, tracker)

        assertFailsWith<SecurityException> {
            trackingFs.readFile(outsideReadFile.canonicalPath)
        }
        val deniedWriteResult = trackingFs.writeFile(outsideWriteFile.canonicalPath, "{}".encodeToByteArray())
        assertTrue(deniedWriteResult.isFailure)
        assertIs<SecurityException>(deniedWriteResult.exceptionOrNull())

        trackingFs.grantAccess(outsideReadFile.canonicalPath)
        trackingFs.grantAccess(outsideWriteFile.canonicalPath)

        val readBytes = trackingFs.readFile(outsideReadFile.canonicalPath)
        assertContentEquals(byteArrayOf(10, 20, 30, 40), readBytes)
        assertEquals(4L, tracker.bytesRead)

        val payload = "{\"cubari\":true}".encodeToByteArray()
        val writeResult = trackingFs.writeFile(outsideWriteFile.canonicalPath, payload)
        assertTrue(writeResult.isSuccess)
        assertEquals(payload.size.toLong(), tracker.bytesWritten)
        assertContentEquals(payload, outsideWriteFile.readBytes())
    }

    @Test
    fun `TrackedPluginNetworkClient supports HTTP verbs multipart uploads custom headers and non-2xx responses`() =
        runTest {
            val capturedMethods = mutableListOf<String>()
            val capturedHeaders = mutableListOf<String?>()
            var sawMultipartBody = false

            val mockEngine = MockEngine { request ->
                capturedMethods += request.method.value
                capturedHeaders += request.headers["X-Plugin-Client"]

                when {
                    request.url.encodedPath.endsWith("/upload") -> {
                        sawMultipartBody = request.body is MultiPartFormDataContent
                        respond(
                            content = "{\"status\":\"uploaded\"}",
                            status = HttpStatusCode.Created,
                            headers = headersOf(
                                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                "X-Upload-Id" to listOf("upl_77")
                            )
                        )
                    }
                    request.url.encodedPath.endsWith("/unauthorized") -> {
                        respond(
                            content = "{\"error\":\"expired_token\"}",
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf("X-Retry-Auth" to listOf("true"))
                        )
                    }
                    request.url.encodedPath.endsWith("/not-found") -> {
                        respond(
                            content = "missing",
                            status = HttpStatusCode.NotFound
                        )
                    }
                    else -> {
                        respond(
                            content = "ok:${request.method.value}",
                            status = HttpStatusCode.OK,
                            headers = headersOf("X-Method-Echo" to listOf(request.method.value))
                        )
                    }
                }
            }

            val tracker = ResourceUsageTracker()
            val networkClient = TrackedPluginNetworkClient(
                httpClient = HttpClient(mockEngine),
                tracker = tracker
            )

            val uploadPayload = byteArrayOf(1, 2, 3, 4, 5, 6)
            val multipartResp = networkClient.request(
                HttpRequest(
                    url = "https://api.example.com/upload",
                    method = HttpMethod.POST,
                    headers = mapOf("X-Plugin-Client" to "manga-uploader"),
                    multipartParts = listOf(
                        MultipartPart(
                            name = "page",
                            data = uploadPayload,
                            filename = "01.png",
                            contentType = "image/png"
                        ),
                        MultipartPart(
                            name = "chapterNumber",
                            data = "42".encodeToByteArray()
                        )
                    )
                )
            )
            assertTrue(sawMultipartBody, "Multipart request should use MultiPartFormDataContent")
            assertEquals(201, multipartResp.statusCode)
            assertTrue(multipartResp.isSuccess)
            assertEquals("upl_77", multipartResp.header("x-upload-id"))
            assertTrue(multipartResp.bodyAsText().contains("uploaded"))

            val putResp = networkClient.request(
                HttpRequest(
                    url = "https://api.example.com/series",
                    method = HttpMethod.PUT,
                    headers = mapOf("Content-Type" to "application/json", "X-Plugin-Client" to "manga-uploader"),
                    body = "{\"title\":\"New\"}".encodeToByteArray()
                )
            )
            assertEquals(200, putResp.statusCode)
            assertEquals("ok:PUT", putResp.bodyAsText())

            val patchResp = networkClient.request(
                HttpRequest(
                    url = "https://api.example.com/series",
                    method = HttpMethod.PATCH,
                    body = "{\"title\":\"Patched\"}".encodeToByteArray()
                )
            )
            assertEquals("ok:PATCH", patchResp.bodyAsText())

            val deleteResp = networkClient.request(
                HttpRequest(url = "https://api.example.com/series", method = HttpMethod.DELETE)
            )
            assertEquals("ok:DELETE", deleteResp.bodyAsText())

            val headResp = networkClient.request(
                HttpRequest(url = "https://api.example.com/series", method = HttpMethod.HEAD)
            )
            assertEquals("HEAD", headResp.header("X-Method-Echo"))

            val unauthorizedResp = networkClient.request(
                HttpRequest(url = "https://api.example.com/unauthorized", method = HttpMethod.GET)
            )
            assertEquals(401, unauthorizedResp.statusCode)
            assertFalse(unauthorizedResp.isSuccess)
            assertEquals("true", unauthorizedResp.header("X-Retry-Auth"))
            assertTrue(unauthorizedResp.bodyAsText().contains("expired_token"))

            val notFoundResp = networkClient.request(
                HttpRequest(url = "https://api.example.com/not-found", method = HttpMethod.GET)
            )
            assertEquals(404, notFoundResp.statusCode)
            assertFalse(notFoundResp.isSuccess)
            assertEquals("missing", notFoundResp.bodyAsText())

            assertEquals(
                listOf("POST", "PUT", "PATCH", "DELETE", "HEAD", "GET", "GET"),
                capturedMethods
            )
            assertEquals("manga-uploader", capturedHeaders[0])
            assertEquals("manga-uploader", capturedHeaders[1])
            assertTrue(tracker.networkBytesWritten > 0L, "Outgoing bytes should be recorded")
            assertTrue(tracker.networkBytesRead > 0L, "Incoming bytes should be recorded")
        }
}
