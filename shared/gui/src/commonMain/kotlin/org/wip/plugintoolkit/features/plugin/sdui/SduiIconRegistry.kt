package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Registry resolving semantic icon identifier strings from SDUI schemas into Compose Material icons.
 */
object SduiIconRegistry {

    private val iconMap: Map<String, ImageVector> = mapOf(
        "upload" to Icons.Default.Upload,
        "download" to Icons.Default.Download,
        "sync" to Icons.Default.Sync,
        "cloud_done" to Icons.Default.CloudDone,
        "cloud_upload" to Icons.Default.CloudUpload,
        "cloud_download" to Icons.Default.CloudDownload,
        "check" to Icons.Default.Check,
        "done" to Icons.Default.Check,
        "check_circle" to Icons.Default.CheckCircle,
        "warning" to Icons.Default.Warning,
        "error" to Icons.Default.Error,
        "info" to Icons.Default.Info,
        "folder" to Icons.Default.Folder,
        "image" to Icons.Default.Image,
        "text_fields" to Icons.Default.TextFields,
        "text" to Icons.Default.TextFields,
        "edit" to Icons.Default.Edit,
        "delete" to Icons.Default.Delete,
        "trash" to Icons.Default.Delete,
        "add" to Icons.Default.Add,
        "plus" to Icons.Default.Add,
        "refresh" to Icons.Default.Refresh,
        "play" to Icons.Default.PlayArrow,
        "pause" to Icons.Default.Pause,
        "stop" to Icons.Default.Stop,
        "layers" to Icons.Default.Layers,
        "zoom_in" to Icons.Default.Add,
        "zoom_out" to Icons.Default.Remove,
        "fit_screen" to Icons.Default.FitScreen,
        "pan_tool" to Icons.Default.PanTool,
        "pan" to Icons.Default.PanTool,
        "select" to Icons.Default.NearMe,
        "brush" to Icons.Default.Brush,
        "crop_square" to Icons.Default.CropSquare,
        "rectangle" to Icons.Default.CropSquare,
        "chat_bubble" to Icons.Default.ChatBubble,
        "callout" to Icons.Default.ChatBubble,
        "undo" to Icons.AutoMirrored.Filled.Undo,
        "redo" to Icons.AutoMirrored.Filled.Redo,
        "save" to Icons.Default.Save,
        "settings" to Icons.Default.Settings,
        "search" to Icons.Default.Search,
        "filter" to Icons.Default.FilterList,
        "filter_list" to Icons.Default.FilterList,
        "visibility" to Icons.Default.Visibility,
        "eye" to Icons.Default.Visibility,
        "visibility_off" to Icons.Default.VisibilityOff,
        "lock" to Icons.Default.Lock,
        "unlock" to Icons.Default.LockOpen,
        "lock_open" to Icons.Default.LockOpen,
        "book" to Icons.Default.Book,
        "chapter" to Icons.Default.Book,
        "publish" to Icons.Default.Publish,
        "close" to Icons.Default.Close,
        "list" to Icons.AutoMirrored.Filled.List,
        "arrow_back" to Icons.AutoMirrored.Filled.ArrowBack,
        "arrow_forward" to Icons.AutoMirrored.Filled.ArrowForward,
        "arrow_upward" to Icons.Default.ArrowUpward,
        "arrow_downward" to Icons.Default.ArrowDownward,
        "more_vert" to Icons.Default.MoreVert,
        "send" to Icons.AutoMirrored.Filled.Send,
        "history" to Icons.Default.History,
        "code" to Icons.Default.Code,
        "star" to Icons.Default.Star
    )

    /**
     * Resolves a semantic icon name into a Compose [ImageVector].
     * Falls back to [Icons.Default.Info] if the name is unrecognized.
     */
    fun resolve(name: String?): ImageVector {
        if (name.isNullOrBlank()) return Icons.Default.Info
        val normalized = name.trim().lowercase().replace('-', '_').replace(' ', '_')
        return iconMap[normalized] ?: Icons.Default.Info
    }
}
