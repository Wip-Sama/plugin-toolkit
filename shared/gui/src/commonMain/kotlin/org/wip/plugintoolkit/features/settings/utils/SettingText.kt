package org.wip.plugintoolkit.features.settings.utils

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Represents string content for settings UI titles, subtitles, and section headers.
 * Supports both type-safe Compose [StringResource] (localized) and plain [Raw] strings.
 */
sealed interface SettingText {
    /**
     * Localized string backed by a Compose [StringResource].
     */
    data class Resource(val res: StringResource) : SettingText

    /**
     * Unlocalized raw string. Used for dynamic plugin settings or un-translated fallbacks.
     */
    data class Raw(val text: String) : SettingText {
        init {
            Logger.w(tag = "SettingText") {
                "String '$text' should be localized. Use SettingText.Resource for production settings."
            }
        }
    }
}

/**
 * Resolves a [SettingText] instance into a concrete [String] within a Compose hierarchy.
 */
@Composable
fun SettingText.resolve(): String {
    return when (this) {
        is SettingText.Resource -> stringResource(res)
        is SettingText.Raw -> text
    }
}
