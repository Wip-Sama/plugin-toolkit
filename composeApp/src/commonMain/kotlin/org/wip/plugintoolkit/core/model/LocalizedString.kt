package org.wip.plugintoolkit.core.model

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import org.wip.plugintoolkit.core.ui.LocalLanguage

sealed interface LocalizedString {
    data class Resource(val res: StringResource) : LocalizedString
    data class Raw(val text: String) : LocalizedString {
        init {
            println("WARN: String '$text' should be localized")
        }
    }

    @Composable
    fun resolve(): String = when (this) {
        is Resource -> {
            LocalLanguage.current // Register dependency to trigger recomposition when language changes
            stringResource(res)
        }
        is Raw -> text
    }

    /**
     * Non-composable resolver for use in platform / non-UI code.
     * Uses org.jetbrains.compose.resources.getString for Resource entries.
     */
    fun resolveNonComposable(vararg args: Any): String = when (this) {
        is Resource -> kotlinx.coroutines.runBlocking { org.jetbrains.compose.resources.getString(res, *args) }
        is Raw -> text
    }
}

// Extensions for easy creation
val StringResource.localized get() = LocalizedString.Resource(this)
val String.localized get() = LocalizedString.Raw(this).also {
    println("WARN: .localized used on String '$this' - should be localized")
}
