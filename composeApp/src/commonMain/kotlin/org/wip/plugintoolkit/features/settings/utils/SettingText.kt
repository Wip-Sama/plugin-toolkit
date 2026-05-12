package org.wip.plugintoolkit.features.settings.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * An abstraction to handle both Compose StringResources (for static UI)
 * and plain Strings (for dynamic plugin settings).
 */
sealed interface SettingText {
    data class Resource(val res: StringResource) : SettingText
    data class Raw(val text: String) : SettingText
}

@Composable
fun SettingText.resolve(): String {
    return when (this) {
        is SettingText.Resource -> stringResource(res)
        is SettingText.Raw -> text
    }
}
