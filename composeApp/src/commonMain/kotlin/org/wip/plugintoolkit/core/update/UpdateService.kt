package org.wip.plugintoolkit.core.update

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.AppConfig
import org.wip.plugintoolkit.core.utils.PlatformUtils

class UpdateService(
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
) {
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress

    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val response = client.get("https://api.github.com/repos/Wip-Sama/plugin-toolkit/releases/latest")
            if (response.status.value in 200..299) {
                val release: GithubRelease = response.body()
                val latestVersion = release.tagName.removePrefix("v")
                val currentVersion = AppConfig.VERSION

                if (isNewer(latestVersion, currentVersion)) {
                    val asset = findBestAsset(release.assets)
                    if (asset != null) {
                        return@withContext UpdateInfo(
                            version = latestVersion,
                            changelog = release.body,
                            downloadUrl = asset.downloadUrl,
                            fileName = asset.name,
                            size = asset.size
                        )
                    }
                }
            } else {
                Logger.w { "Failed to check for updates: GitHub API returned ${response.status}" }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to check for updates" }
        }
        null
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until minOf(latestParts.size, currentParts.size)) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }

    private fun findBestAsset(assets: List<GithubAsset>): GithubAsset? {
        return when {
            PlatformUtils.isWindows -> assets.find { it.name.endsWith(".msi") || it.name.endsWith(".exe") }
            PlatformUtils.isLinux -> assets.find { it.name.endsWith(".deb") || it.name.endsWith(".AppImage") }
            // Add macOS detection if needed in PlatformUtils
            else -> assets.firstOrNull()
        }
    }

    suspend fun downloadUpdate(info: UpdateInfo, destinationPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = client.get(info.downloadUrl) {
                onDownload { bytesSentTotal, contentLength ->
                    contentLength?.let {
                        if (it > 0) {
                            _downloadProgress.value = bytesSentTotal.toFloat() / contentLength
                        } else {
                            _downloadProgress.value = 0f
                        }
                    }
                }
            }

            if (response.status.value in 200..299) {
                val bytes = response.body<ByteArray>()
                // Note: For very large files, streaming to disk is better.
                // But for installers (~50-100MB), this might be okay.
                // Let's use streaming just in case.
                val file = java.io.File(destinationPath)
                file.writeBytes(bytes)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to download: ${response.status}"))
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error downloading update" }
            Result.failure(e)
        }
    }
}
