package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import org.wip.plugintoolkit.api.ExecutionFileSystem
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.RelativePath

/**
 * Tracking wrapper for [PluginFileSystem] that counts bytes read and written.
 */
class TrackingPluginFileSystem(
    private val delegate: PluginFileSystem,
    private val tracker: ResourceUsageTracker
) : PluginFileSystem {

    override suspend fun readFile(relativePath: RelativePath): ByteArray? {
        val bytes = delegate.readFile(relativePath)
        if (bytes != null) {
            tracker.recordFileRead(bytes.size.toLong())
        }
        return bytes
    }

    override suspend fun readTextFile(relativePath: RelativePath): String? {
        val text = delegate.readTextFile(relativePath)
        if (text != null) {
            tracker.recordFileRead(text.encodeToByteArray().size.toLong())
        }
        return text
    }

    override suspend fun writeFile(relativePath: RelativePath, data: ByteArray): Result<Unit> {
        val result = delegate.writeFile(relativePath, data)
        if (result.isSuccess) {
            tracker.recordFileWrite(data.size.toLong())
        }
        return result
    }

    override suspend fun writeTextFile(relativePath: RelativePath, text: String): Result<Unit> {
        val result = delegate.writeTextFile(relativePath, text)
        if (result.isSuccess) {
            tracker.recordFileWrite(text.encodeToByteArray().size.toLong())
        }
        return result
    }

    override suspend fun exists(relativePath: RelativePath): Boolean =
        delegate.exists(relativePath)

    override suspend fun listFiles(relativePath: RelativePath): List<String> =
        delegate.listFiles(relativePath)

    override suspend fun deleteFile(relativePath: RelativePath): Result<Unit> =
        delegate.deleteFile(relativePath)

    override fun getBasePath(): String =
        delegate.getBasePath()

    override suspend fun readStream(relativePath: RelativePath): Flow<ByteArray> {
        return delegate.readStream(relativePath).transform { chunk ->
            tracker.recordFileRead(chunk.size.toLong())
            emit(chunk)
        }
    }

    override suspend fun writeStream(relativePath: RelativePath, stream: Flow<ByteArray>): Result<Unit> {
        val trackingStream = stream.transform { chunk ->
            tracker.recordFileWrite(chunk.size.toLong())
            emit(chunk)
        }
        return delegate.writeStream(relativePath, trackingStream)
    }

    override suspend fun copyFile(source: RelativePath, destination: RelativePath): Result<Unit> {
        val result = delegate.copyFile(source, destination)
        if (result.isSuccess) {
            val size = delegate.readFile(destination)?.size?.toLong() ?: 0L
            if (size > 0L) {
                tracker.recordFileRead(size)
                tracker.recordFileWrite(size)
            }
        }
        return result
    }

    override suspend fun moveFile(source: RelativePath, destination: RelativePath): Result<Unit> =
        delegate.moveFile(source, destination)

    override suspend fun extractResource(resourcePath: String, targetRelativePath: RelativePath): Result<Unit> {
        val result = delegate.extractResource(resourcePath, targetRelativePath)
        if (result.isSuccess) {
            val size = delegate.readFile(targetRelativePath)?.size?.toLong() ?: 0L
            if (size > 0L) {
                tracker.recordFileWrite(size)
            }
        }
        return result
    }
}

/**
 * Tracking wrapper for [ExecutionFileSystem] that counts bytes read and written.
 */
class TrackingExecutionFileSystem(
    private val delegate: ExecutionFileSystem,
    private val tracker: ResourceUsageTracker
) : ExecutionFileSystem {

    override suspend fun readFile(relativePath: RelativePath): ByteArray? {
        val bytes = delegate.readFile(relativePath)
        if (bytes != null) {
            tracker.recordFileRead(bytes.size.toLong())
        }
        return bytes
    }

    override suspend fun readTextFile(relativePath: RelativePath): String? {
        val text = delegate.readTextFile(relativePath)
        if (text != null) {
            tracker.recordFileRead(text.encodeToByteArray().size.toLong())
        }
        return text
    }

    override suspend fun writeFile(relativePath: RelativePath, data: ByteArray): Result<Unit> {
        val result = delegate.writeFile(relativePath, data)
        if (result.isSuccess) {
            tracker.recordFileWrite(data.size.toLong())
        }
        return result
    }

    override suspend fun writeTextFile(relativePath: RelativePath, text: String): Result<Unit> {
        val result = delegate.writeTextFile(relativePath, text)
        if (result.isSuccess) {
            tracker.recordFileWrite(text.encodeToByteArray().size.toLong())
        }
        return result
    }

    override suspend fun exists(relativePath: RelativePath): Boolean =
        delegate.exists(relativePath)

    override suspend fun listFiles(relativePath: RelativePath): List<String> =
        delegate.listFiles(relativePath)

    override suspend fun deleteFile(relativePath: RelativePath): Result<Unit> =
        delegate.deleteFile(relativePath)

    override fun getBasePath(): String =
        delegate.getBasePath()

    override suspend fun readStream(relativePath: RelativePath): Flow<ByteArray> {
        return delegate.readStream(relativePath).transform { chunk ->
            tracker.recordFileRead(chunk.size.toLong())
            emit(chunk)
        }
    }

    override suspend fun writeStream(relativePath: RelativePath, stream: Flow<ByteArray>): Result<Unit> {
        val trackingStream = stream.transform { chunk ->
            tracker.recordFileWrite(chunk.size.toLong())
            emit(chunk)
        }
        return delegate.writeStream(relativePath, trackingStream)
    }

    override suspend fun copyFile(source: RelativePath, destination: RelativePath): Result<Unit> {
        val result = delegate.copyFile(source, destination)
        if (result.isSuccess) {
            val size = delegate.readFile(destination)?.size?.toLong() ?: 0L
            if (size > 0L) {
                tracker.recordFileRead(size)
                tracker.recordFileWrite(size)
            }
        }
        return result
    }

    override suspend fun moveFile(source: RelativePath, destination: RelativePath): Result<Unit> =
        delegate.moveFile(source, destination)
}

/**
 * Tracking wrapper for [HostFileSystem] that counts bytes read and written.
 */
class TrackingHostFileSystem(
    private val delegate: HostFileSystem,
    private val tracker: ResourceUsageTracker
) : HostFileSystem {

    override suspend fun readFile(absolutePath: String): ByteArray? {
        val bytes = delegate.readFile(absolutePath)
        if (bytes != null) {
            tracker.recordFileRead(bytes.size.toLong())
        }
        return bytes
    }

    override suspend fun readTextFile(absolutePath: String): String? {
        val text = delegate.readTextFile(absolutePath)
        if (text != null) {
            tracker.recordFileRead(text.encodeToByteArray().size.toLong())
        }
        return text
    }

    override suspend fun writeFile(absolutePath: String, data: ByteArray): Result<Unit> {
        val result = delegate.writeFile(absolutePath, data)
        if (result.isSuccess) {
            tracker.recordFileWrite(data.size.toLong())
        }
        return result
    }

    override suspend fun writeTextFile(absolutePath: String, text: String): Result<Unit> {
        val result = delegate.writeTextFile(absolutePath, text)
        if (result.isSuccess) {
            tracker.recordFileWrite(text.encodeToByteArray().size.toLong())
        }
        return result
    }

    override suspend fun exists(absolutePath: String): Boolean =
        delegate.exists(absolutePath)

    override suspend fun listFiles(absolutePath: String): List<String> =
        delegate.listFiles(absolutePath)

    override suspend fun deleteFile(absolutePath: String): Result<Unit> =
        delegate.deleteFile(absolutePath)

    override suspend fun createDirectory(absolutePath: String): Result<Unit> =
        delegate.createDirectory(absolutePath)

    override suspend fun readStream(absolutePath: String): Flow<ByteArray> {
        return delegate.readStream(absolutePath).transform { chunk ->
            tracker.recordFileRead(chunk.size.toLong())
            emit(chunk)
        }
    }

    override suspend fun writeStream(absolutePath: String, stream: Flow<ByteArray>): Result<Unit> {
        val trackingStream = stream.transform { chunk ->
            tracker.recordFileWrite(chunk.size.toLong())
            emit(chunk)
        }
        return delegate.writeStream(absolutePath, trackingStream)
    }

    override suspend fun copyFile(sourceAbsolutePath: String, destinationAbsolutePath: String): Result<Unit> {
        val result = delegate.copyFile(sourceAbsolutePath, destinationAbsolutePath)
        if (result.isSuccess) {
            val size = delegate.readFile(destinationAbsolutePath)?.size?.toLong() ?: 0L
            if (size > 0L) {
                tracker.recordFileRead(size)
                tracker.recordFileWrite(size)
            }
        }
        return result
    }

    override suspend fun moveFile(sourceAbsolutePath: String, destinationAbsolutePath: String): Result<Unit> =
        delegate.moveFile(sourceAbsolutePath, destinationAbsolutePath)
}
