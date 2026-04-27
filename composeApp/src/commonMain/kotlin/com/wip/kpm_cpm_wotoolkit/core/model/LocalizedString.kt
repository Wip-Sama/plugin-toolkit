package com.wip.kpm_cpm_wotoolkit.core.model

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

sealed interface LocalizedString {
    data class Resource(val res: StringResource) : LocalizedString
    data class Raw(val text: String) : LocalizedString

    @Composable
    fun resolve(): String = when (this) {
        is Resource -> stringResource(res)
        is Raw -> text
    }
}

// Extensions for easy creation
val StringResource.localized get() = LocalizedString.Resource(this)
val String.localized get() = LocalizedString.Raw(this)
