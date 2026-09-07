package org.wip.plugintoolkit.features.job.logic

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.core.utils.SemanticRegistry

/**
 * Executor for the "save" system node.
 * Writes data to a file. If the file path is relative, it's resolved against the app data directory.
 */
class SaveNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val data = context.getInputValue("data", "")
        val filePath = context.getInputValue("file_path", "output.txt") as String
        val isDestructive = when (val isDestructiveVal = context.getInputValue("is_destructive", false)) {
            is Boolean -> isDestructiveVal
            is String -> isDestructiveVal.toBoolean()
            is Number -> isDestructiveVal.toInt() != 0
            else -> false
        }

        val path = Path(filePath)
        val fullPath = if (path.isAbsolute) {
            path
        } else {
            Path(context.appDataDir, filePath)
        }

        val parent = fullPath.parent
        if (parent != null && !SystemFileSystem.exists(parent)) {
            SystemFileSystem.createDirectories(parent)
        }

        val dataString = when (data) {
            is JsonElement -> {
                if (data is JsonPrimitive && data.isString) data.content else data.toString()
            }

            else -> data?.toString() ?: ""
        }

        val possibleSourcePath = Path(dataString)
        if (possibleSourcePath.isAbsolute && SystemFileSystem.exists(possibleSourcePath) && !SystemFileSystem.metadataOrNull(
                possibleSourcePath
            )!!.isDirectory
        ) {
            // It's a file, perform copy
            val metadata = SystemFileSystem.metadataOrNull(fullPath)
            if (metadata?.isDirectory == true) {
                throw Exception("Cannot save to a directory: $fullPath")
            }
            val sourceBytes = SystemFileSystem.source(possibleSourcePath).buffered().use { it.readByteArray() }
            SystemFileSystem.sink(fullPath).buffered().use { it.write(sourceBytes) }
            context.addLog("Copied file from $possibleSourcePath to $fullPath")

            if (isDestructive) {
                try {
                    SystemFileSystem.delete(possibleSourcePath)
                    context.addLog("Deleted source file $possibleSourcePath (is_destructive=true)")
                } catch (e: Exception) {
                    context.addLog("Warning: failed to delete source file $possibleSourcePath: ${e.message}", "WARN")
                }
            }
        } else {
            // It's plain text data
            val metadata = SystemFileSystem.metadataOrNull(fullPath)
            if (metadata?.isDirectory == true) {
                throw Exception("Cannot save to a directory: $fullPath")
            }
            SystemFileSystem.sink(fullPath).buffered().use { it.writeString(dataString) }
            context.addLog("Saved data to file: $filePath")
        }

        context.setOutputValue("success", true)
        context.setOutputValue("saved_path", fullPath.toString())
    }
}

/**
 * Executor for the "save_file" system node.
 * Copies or moves a file from a source path to a destination folder.
 */
class SaveFileNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val dataVal = context.getInputValue("data", "")
        val sourcePathStr =
            if (dataVal is JsonPrimitive && dataVal.isString) dataVal.content else dataVal?.toString() ?: ""

        val destinationFolderVal = context.getInputValue("destination_folder", "")
        val destinationFolderStr =
            if (destinationFolderVal is JsonPrimitive && destinationFolderVal.isString) destinationFolderVal.content else destinationFolderVal?.toString()
                ?: ""

        val fileNameVal = context.getInputValue("file_name", "")
        val providedFileName =
            if (fileNameVal is JsonPrimitive && fileNameVal.isString) fileNameVal.content else fileNameVal?.toString()
                ?: ""

        val isDestructive = when (val isDestructiveVal = context.getInputValue("is_destructive", false)) {
            is Boolean -> isDestructiveVal
            is String -> isDestructiveVal.toBoolean()
            is Number -> isDestructiveVal.toInt() != 0
            else -> false
        }

        if (sourcePathStr.isBlank()) {
            throw Exception("Source data (file path) is empty.")
        }
        if (destinationFolderStr.isBlank()) {
            throw Exception("Destination folder path is empty.")
        }

        val sourcePath = Path(sourcePathStr)
        if (!SystemFileSystem.exists(sourcePath)) {
            throw Exception("Source file does not exist: $sourcePath")
        }
        if (SystemFileSystem.metadataOrNull(sourcePath)?.isDirectory == true) {
            throw Exception("Source path is a directory, not a file: $sourcePath")
        }

        val destFolderPath = Path(destinationFolderStr)
        val fullDestFolderPath = if (destFolderPath.isAbsolute) {
            destFolderPath
        } else {
            Path(context.appDataDir, destinationFolderStr)
        }

        if (!SystemFileSystem.exists(fullDestFolderPath)) {
            SystemFileSystem.createDirectories(fullDestFolderPath)
        } else if (SystemFileSystem.metadataOrNull(fullDestFolderPath)?.isDirectory != true) {
            throw Exception("Destination path exists but is not a directory: $fullDestFolderPath")
        }

        val finalFileName = providedFileName.ifBlank { sourcePath.name }
        val fullDestFilePath = Path(fullDestFolderPath, finalFileName)

        val sourceBytes = SystemFileSystem.source(sourcePath).buffered().use { it.readByteArray() }
        SystemFileSystem.sink(fullDestFilePath).buffered().use { it.write(sourceBytes) }
        context.addLog("Copied file from $sourcePath to $fullDestFilePath")

        if (isDestructive) {
            try {
                SystemFileSystem.delete(sourcePath)
                context.addLog("Deleted source file $sourcePath (is_destructive=true)")
            } catch (e: Exception) {
                context.addLog("Warning: failed to delete source file $sourcePath: ${e.message}", "WARN")
            }
        }

        context.setOutputValue("success", true)
        context.setOutputValue("saved_path", fullDestFilePath.toString())
    }
}

private fun copyRecursively(source: Path, dest: Path) {
    if (!SystemFileSystem.exists(source)) return

    val metadata = SystemFileSystem.metadataOrNull(source)
    if (metadata?.isDirectory == true) {
        if (!SystemFileSystem.exists(dest)) {
            SystemFileSystem.createDirectories(dest)
        }
        SystemFileSystem.list(source).forEach { child ->
            val childDest = Path(dest, child.name)
            copyRecursively(child, childDest)
        }
    } else {
        val sourceBytes = SystemFileSystem.source(source).buffered().use { it.readByteArray() }
        SystemFileSystem.sink(dest).buffered().use { it.write(sourceBytes) }
    }
}

/**
 * Executor for the "save_folder" system node.
 * Copies or moves a folder from a source path to a destination folder.
 */
class SaveFolderNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val dataVal = context.getInputValue("data", "")
        val sourcePathStr =
            if (dataVal is JsonPrimitive && dataVal.isString) dataVal.content else dataVal?.toString() ?: ""

        val destinationFolderVal = context.getInputValue("destination_folder", "")
        val destinationFolderStr =
            if (destinationFolderVal is JsonPrimitive && destinationFolderVal.isString) destinationFolderVal.content else destinationFolderVal?.toString()
                ?: ""

        val folderNameVal = context.getInputValue("folder_name", "")
        val providedFolderName =
            if (folderNameVal is JsonPrimitive && folderNameVal.isString) folderNameVal.content else folderNameVal?.toString()
                ?: ""

        val isDestructive = when (val isDestructiveVal = context.getInputValue("is_destructive", false)) {
            is Boolean -> isDestructiveVal
            is String -> isDestructiveVal.toBoolean()
            is Number -> isDestructiveVal.toInt() != 0
            else -> false
        }

        if (sourcePathStr.isBlank()) {
            throw Exception("Source data (folder path) is empty.")
        }
        if (destinationFolderStr.isBlank()) {
            throw Exception("Destination folder path is empty.")
        }

        val sourcePath = Path(sourcePathStr)
        if (!SystemFileSystem.exists(sourcePath)) {
            throw Exception("Source folder does not exist: $sourcePath")
        }
        if (SystemFileSystem.metadataOrNull(sourcePath)?.isDirectory != true) {
            throw Exception("Source path is a file, not a directory: $sourcePath")
        }

        val destFolderPath = Path(destinationFolderStr)
        val fullDestFolderPath = if (destFolderPath.isAbsolute) {
            destFolderPath
        } else {
            Path(context.appDataDir, destinationFolderStr)
        }

        if (!SystemFileSystem.exists(fullDestFolderPath)) {
            SystemFileSystem.createDirectories(fullDestFolderPath)
        } else if (SystemFileSystem.metadataOrNull(fullDestFolderPath)?.isDirectory != true) {
            throw Exception("Destination path exists but is not a directory: $fullDestFolderPath")
        }

        val finalFolderName = providedFolderName.ifBlank { sourcePath.name }
        val fullDestFolderPathFinal = Path(fullDestFolderPath, finalFolderName)

        copyRecursively(sourcePath, fullDestFolderPathFinal)
        context.addLog("Copied folder from $sourcePath to $fullDestFolderPathFinal")

        if (isDestructive) {
            try {
                deleteRecursively(sourcePath)
                context.addLog("Deleted source folder $sourcePath (is_destructive=true)")
            } catch (e: Exception) {
                context.addLog("Warning: failed to delete source folder $sourcePath: ${e.message}", "WARN")
            }
        }

        context.setOutputValue("success", true)
        context.setOutputValue("saved_path", fullDestFolderPathFinal.toString())
    }
}

/**
 * Executor for the "load" system node.
 * Reads data from a file. If the file path is relative, it's resolved against the app data directory.
 * Returns the file content as a string.
 */
class LoadNodeExecutor(
    private val semanticRegistry: SemanticRegistry
) : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val filePathVal = context.getInputValue("file_path", "")
        val filePath =
            if (filePathVal is JsonPrimitive && filePathVal.isString) filePathVal.content else filePathVal?.toString()
                ?: ""
        if (filePath.isBlank()) {
            throw Exception("File path is required")
        }
        val dataPort = context.node.outputs.find { it.id == "data" }
        val semanticTypes = dataPort?.semanticTypes ?: emptyList()
        if (semanticTypes.isNotEmpty()) {
            val allowedExtensions = semanticRegistry.getAllowedExtensions(semanticTypes)
            if (allowedExtensions.isNotEmpty()) {
                val filename = filePath.substringAfterLast('/').substringAfterLast('\\')
                val ext = filename.substringAfterLast('.', "").lowercase()
                if (ext !in allowedExtensions) {
                    throw Exception("File '$filePath' has unsupported extension '$ext'")
                }
            }
        }

        val path = Path(filePath)
        val fullPath = if (path.isAbsolute) {
            path
        } else {
            Path(context.appDataDir, filePath)
        }

        val fileContent = if (SystemFileSystem.exists(fullPath)) {
            val metadata = SystemFileSystem.metadataOrNull(fullPath)
            if (metadata?.isDirectory == true) {
                throw Exception("Cannot load a directory: $fullPath")
            }
            SystemFileSystem.source(fullPath).buffered().use { it.readString() }
        } else {
            context.addLog("Warning: file to load not found at $fullPath, returning empty: $filePath", "WARN")
            ""
        }

        context.setOutputValue("data", fileContent)
        context.addLog("Loaded content from file: $filePath (Size: ${fileContent.length} chars)")
    }
}

/**
 * Executor for the "create_folder" system node.
 * Creates a folder at the specified path.
 */
class CreateFolderNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        try {
            val basePath = context.getInputValue("path", "") as String
            val folderName = context.getInputValue("folder_name", "") as String

            val path = Path(basePath)
            val fullBasePath = if (path.isAbsolute) {
                path
            } else {
                Path(context.appDataDir, basePath)
            }

            val newFolderPath = Path(fullBasePath, folderName)

            if (!SystemFileSystem.exists(newFolderPath)) {
                SystemFileSystem.createDirectories(newFolderPath)
                context.addLog("Created folder: $newFolderPath")
            } else {
                context.addLog("Folder already exists: $newFolderPath")
            }

            context.setOutputValue("created_path", newFolderPath.toString())
            context.setOutputValue("success", true)
        } catch (e: Exception) {
            context.addLog("Failed to create folder: ${e.message}", "ERROR")
            context.setOutputValue("created_path", null)
            context.setOutputValue("success", false)
        }
    }
}
