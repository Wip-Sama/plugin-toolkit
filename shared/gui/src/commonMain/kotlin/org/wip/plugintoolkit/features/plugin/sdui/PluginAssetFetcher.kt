package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.RelativePath
import org.wip.plugintoolkit.api.sdui.AssetReference
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Scoped asset context providing the active plugin session's isolated [PluginFileSystem]
 * and cache [PluginFileSystem] to SDUI image and board renderers.
 */
data class PluginAssetContext(
    val fileSystem: PluginFileSystem? = null,
    val cacheFileSystem: PluginFileSystem? = null
)

val LocalPluginAssetContext = staticCompositionLocalOf { PluginAssetContext() }

/**
 * Resolves raw bytes for an [AssetReference] from the plugin's [PluginFileSystem],
 * cache [PluginFileSystem], or inline `data:` URI.
 */
@OptIn(ExperimentalEncodingApi::class)
suspend fun resolveAssetBytes(
    data: AssetReference,
    fileSystem: PluginFileSystem?,
    cacheFileSystem: PluginFileSystem? = null
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
                uri.startsWith("data:") -> {
                    val commaIndex = uri.indexOf(',')
                    if (commaIndex < 0) return null
                    val header = uri.substring(0, commaIndex)
                    val payload = uri.substring(commaIndex + 1)
                    if (header.contains(";base64", ignoreCase = true)) {
                        runCatching { Base64.decode(payload) }.getOrNull()
                    } else {
                        payload.encodeToByteArray()
                    }
                }
                else -> {
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
 * Custom Coil 3 fetcher that loads images directly from the plugin's isolated [PluginFileSystem]
 * or cache [PluginFileSystem] out-of-band without serializing image bytes over SDUI JSON streams.
 */
class PluginAssetFetcher(
    private val data: AssetReference,
    private val options: Options,
    private val fileSystem: PluginFileSystem?,
    private val cacheFileSystem: PluginFileSystem? = null
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bytes = resolveAssetBytes(data, fileSystem, cacheFileSystem) ?: return null
        val buffer = Buffer().apply { write(bytes) }
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(
        private val fileSystem: PluginFileSystem?,
        private val cacheFileSystem: PluginFileSystem? = null
    ) : Fetcher.Factory<AssetReference> {
        override fun create(
            data: AssetReference,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return PluginAssetFetcher(data, options, fileSystem, cacheFileSystem)
        }
    }
}

/**
 * Renders an [AssetReference] using a scoped Coil [ImageLoader] wired to [LocalPluginAssetContext].
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
    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext, assetContext.fileSystem, assetContext.cacheFileSystem) {
        ImageLoader.Builder(platformContext)
            .components {
                add(PluginAssetFetcher.Factory(assetContext.fileSystem, assetContext.cacheFileSystem))
            }
            .build()
    }

    AsyncImage(
        model = asset,
        contentDescription = contentDescription,
        imageLoader = imageLoader,
        contentScale = contentScale,
        alpha = alpha,
        modifier = modifier
    )
}
