package org.wip.plugintoolkit.features.plugin.sdui

import org.jetbrains.compose.resources.getString
import org.wip.plugintoolkit.core.utils.PlatformUtils
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.dialog_select_file
import plugintoolkit.composeapp.generated.resources.dialog_select_files

/**
 * Host GUI bridge connecting plugin SDUI file/directory picker and save requests
 * to native desktop FileKit dialogs.
 */
object PlatformUiHostBridge : PluginUiHostBridge {
    override suspend fun pickFile(title: String?, allowedExtensions: List<String>): String? {
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: getString(Res.string.dialog_select_file)
        return PlatformUtils.pickFile(resolvedTitle, allowedExtensions)
    }

    override suspend fun pickFiles(title: String?, allowedExtensions: List<String>): List<String> {
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: getString(Res.string.dialog_select_files)
        return PlatformUtils.pickFiles(resolvedTitle, allowedExtensions)
    }

    override suspend fun pickDirectory(title: String?): String? {
        val cleanTitle = title?.takeIf { it.isNotBlank() }
        return if (cleanTitle != null) {
            PlatformUtils.pickFolder(cleanTitle)
        } else {
            PlatformUtils.pickFolder()
        }
    }

    override suspend fun saveFile(defaultName: String, extension: String, bytes: ByteArray): String? {
        return PlatformUtils.saveFile(defaultName, extension, bytes)
    }
}
