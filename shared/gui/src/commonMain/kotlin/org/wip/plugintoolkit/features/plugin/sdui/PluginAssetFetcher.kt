package org.wip.plugintoolkit.features.plugin.sdui

import coil3.ImageLoader
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

/**
 * Custom Coil 3 fetcher that loads images directly from the plugin's isolated [PluginFileSystem]
 * out-of-band without serializing image bytes over SDUI JSON coroutine streams.
 */
class PluginAssetFetcher(
    private val data: AssetReference,
    private val options: Options,
    private val fileSystem: PluginFileSystem?
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val fs = fileSystem ?: return null
        val bytes = when (data) {
            is AssetReference.Relative -> {
                val relPath = RelativePath.from(data.path).getOrNull() ?: return null
                fs.readFile(relPath) ?: return null
            }
            is AssetReference.Uri -> return null
            is AssetReference.Cached -> return null
        }
        val buffer = Buffer().apply { write(bytes) }
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory(
        private val fileSystem: PluginFileSystem?
    ) : Fetcher.Factory<AssetReference> {
        override fun create(
            data: AssetReference,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return PluginAssetFetcher(data, options, fileSystem)
        }
    }
}
