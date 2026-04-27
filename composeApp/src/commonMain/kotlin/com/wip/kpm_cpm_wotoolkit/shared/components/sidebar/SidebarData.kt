package com.wip.kpm_cpm_wotoolkit.shared.components.sidebar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource

import com.wip.kpm_cpm_wotoolkit.core.model.LocalizedString

data class SidebarElement<out T>(
    val id: T,
    val icon: ImageVector,
    val title: LocalizedString,
    val trailingContent: @Composable (isExpanded: Boolean) -> Unit = {}
)

enum class SidebarItemPosition {
    StandAlone, Start, Middle, End
}

data class SidebarSectionData<out T>(
    val title: LocalizedString?,
    val elements: List<SidebarElement<T>>
)
