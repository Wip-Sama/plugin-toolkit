package com.wip.kpm_cpm_wotoolkit.shared.components.sidebar

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource

data class SidebarElement<out T>(
    val id: T,
    val icon: ImageVector,
    val title: StringResource
)

enum class SidebarItemPosition {
    StandAlone, Start, Middle, End
}

data class SidebarSectionData<out T>(
    val title: StringResource?,
    val elements: List<SidebarElement<T>>
)
