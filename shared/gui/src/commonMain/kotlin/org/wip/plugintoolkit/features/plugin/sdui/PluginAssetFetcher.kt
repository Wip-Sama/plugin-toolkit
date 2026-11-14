package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.HttpMethod
import org.wip.plugintoolkit.api.HttpRequest
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginNetworkClient
import org.wip.plugintoolkit.api.RelativePath
import org.wip.plugintoolkit.api.sdui.AssetReference
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val MAX_REMOTE_IMAGE_BYTES = 25 * 1024 * 1024
private val WINDOWS_DRIVE_URI_REGEX = Regex("^/[a-zA-Z]:[/\\\\].*")
private val ABSOLUTE_LOCAL_PATH_REGEX = Regex("^([a-zA-Z]:[/\\\\]|/).+")

/**
 * Scoped asset context providing the active plugin session's storage, granted [HostFileSystem],
 * and [PluginNetworkClient] to SDUI image and board renderers.
 */
data class PluginAssetContext(
    val fileSystem: PluginFileSystem? = null,
    val cacheFileSystem: PluginFileSystem? = null,
    val hostFileSystem: HostFileSystem? = null,
    val networkClient: PluginNetworkClient? = null
)

val LocalPluginAssetContext = staticCompositionLocalOf { PluginAssetContext() }

/**
 * Shared remembered [ImageLoader] for the active SDUI session so grids of images reuse
 * memory/disk caches and decoder threads instead of instantiating per-image loaders.
 */
val LocalPluginImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

internal fun extractLocalFilePath(uri: String): String? {
    val trimmed = uri.trim()
    if (trimmed.startsWith("file:", ignoreCase = true)) {
        var stripped = trimmed.substring("file:".length)
        if (stripped.startsWith("//localhost/", ignoreCase = true)) {
            stripped = stripped.substring("//localhost".length)
        } else if (stripped.startsWith("//")) {
            stripped = stripped.substring(2)
        }
        if (WINDOWS_DRIVE_URI_REGEX.matches(stripped)) {
            stripped = stripped.drop(1)
        }
        return stripped.takeIf { it.isNotBlank() }
    }
    return if (ABSOLUTE_LOCAL_PATH_REGEX.matches(trimmed)) trimmed else null
}

private fun decodeDataUri(uri: String): ByteArray? {
    val commaIndex = uri.indexOf(',')
    if (commaIndex < 0) return null
    val header = uri.substring(0, commaIndex)
    val payload = uri.substring(commaIndex + 1)
    return if (header.contains(";base64", ignoreCase = true)) {
        @OptIn(ExperimentalEncodingApi::class)
        runCatching { Base64.decode(payload) }.getOrNull()
    } else {
        payload.encodeToByteArray()
    }
}

/**
 * Resolves raw bytes for an [AssetReference] from the plugin's [PluginFileSystem],
 * cache [PluginFileSystem], granted [HostFileSystem] (`file://` or absolute path),
 * remote `http(s)://` URLs via [PluginNetworkClient], or inline `data:` URI.
 */
@OptIn(ExperimentalEncodingApi::class)
suspend fun resolveAssetBytes(
    data: AssetReference,
    fileSystem: PluginFileSystem?,
    cacheFileSystem: PluginFileSystem? = null,
    hostFileSystem: HostFileSystem? = null,
    networkClient: PluginNetworkClient? = null
): ByteArray? {
    return when (data) {
        is AssetReference.Relative -> {
            val relPath = RelativePath.from(data.path).getOrNull() ?: return null
            fileSystem?.readFile(relPath) ?: cacheFileSystem?.readFile(relPath)
        }
        is AssetReference.Cached -> {
            val relPath = RelativePath.from(data.assetId).getOrNull() ?: return null
            cacheFileSystem?.readFile(relPath) ?: fileSystem?.readFile(relPath)
        }
        is AssetReference.Uri -> {
            val uri = data.uriString.trim()
            when {
                uri.startsWith("data:", ignoreCase = true) -> decodeDataUri(uri)
                uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) -> {
                    if (networkClient == null) return null
                    val response = try {
                        networkClient.request(HttpRequest(url = uri, method = HttpMethod.GET))
                    } catch (e: Throwable) {
                        Logger.w(e) { "Failed to fetch remote SDUI image $uri" }
                        return null
                    }
                    if (!response.isSuccess || response.body.isEmpty() || response.body.size > MAX_REMOTE_IMAGE_BYTES) {
                        null
                    } else {
                        response.body
                    }
                }
                else -> {
                    val localPath = extractLocalFilePath(uri)
                    if (localPath != null && hostFileSystem != null) {
                        val hostBytes = try {
                            hostFileSystem.readFile(localPath)
                        } catch (_: SecurityException) {
                            null
                        } catch (e: Throwable) {
                            Logger.w(e) { "Failed reading host asset $localPath" }
                            null
                        }
                        if (hostBytes != null) return hostBytes
                    }
                    val relPath = RelativePath.from(uri.removePrefix("file://").removePrefix("/")).getOrNull()
                    if (relPath != null) {
                        fileSystem?.readFile(relPath) ?: cacheFileSystem?.readFile(relPath)
                    } else {
                        null
                    }
                }
            }
        }
    }
}

/**
 * Custom Coil 3 fetcher that loads images from the plugin's [PluginFileSystem],
 * cache [PluginFileSystem], granted [HostFileSystem], or [PluginNetworkClient]
 * out-of-band without serializing image bytes over SDUI JSON streams.
 */
class PluginAssetFetcher(
    private val data: AssetReference,
    private val options: Options,
    private val fileSystem: PluginFileSystem?,
    private val cacheFileSystem: PluginFileSystem? = null,
    private val hostFileSystem: HostFileSystem? = null,
    private val networkClient: PluginNetworkClient? = null
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = resolveAssetBytes(data, fileSystem, cacheFileSystem, hostFileSystem, networkClient) ?: return null
        val buffer = Buffer().apply { write(bytes) }
        val isNetwork = data is AssetReference.Uri &&
            (data.uriString.trim().startsWith("http://", ignoreCase = true) ||
                data.uriString.trim().startsWith("https://", ignoreCase = true))
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = options.fileSystem),
            mimeType = null,
            dataSource = if (isNetwork) DataSource.NETWORK else DataSource.DISK
        )
    }

    class Factory(
        private val fileSystem: PluginFileSystem?,
        private val cacheFileSystem: PluginFileSystem? = null,
        private val hostFileSystem: HostFileSystem? = null,
        private val networkClient: PluginNetworkClient? = null
    ) : Fetcher.Factory<AssetReference> {
        override fun create(
            data: AssetReference,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return PluginAssetFetcher(data, options, fileSystem, cacheFileSystem, hostFileSystem, networkClient)
        }
    }
}

/**
 * Creates a remembered Coil [ImageLoader] scoped to [assetContext].
 */
@Composable
fun rememberPluginAssetImageLoader(assetContext: PluginAssetContext): ImageLoader {
    val platformContext = LocalPlatformContext.current
    return remember(
        platformContext,
        assetContext.fileSystem,
        assetContext.cacheFileSystem,
        assetContext.hostFileSystem,
        assetContext.networkClient
    ) {
        ImageLoader.Builder(platformContext)
            .components {
                add(
                    PluginAssetFetcher.Factory(
                        fileSystem = assetContext.fileSystem,
                        cacheFileSystem = assetContext.cacheFileSystem,
                        hostFileSystem = assetContext.hostFileSystem,
                        networkClient = assetContext.networkClient
                    )
                )
            }
            .build()
    }
}

/**
 * Renders an [AssetReference] using a shared session Coil [ImageLoader] wired to [LocalPluginAssetContext].
 */
@Composable
fun PluginAssetImage(
    asset: AssetReference,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f
) {
    val assetContext = LocalPluginAssetContext.current
    val imageLoader = LocalPluginImageLoader.current ?: rememberPluginAssetImageLoader(assetContext)

    AsyncImage(
        model = asset,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        contentScale = contentScale,
        alpha = alpha,
        modifier = modifier
    )
}
