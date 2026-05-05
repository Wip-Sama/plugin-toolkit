package org.wip.plugintoolkit.features.settings.utils

import androidx.compose.runtime.compositionLocalOf
import org.wip.plugintoolkit.features.settings.model.SettingDefinition

val LocalSettingsSearchQuery = compositionLocalOf { "" }
val LocalSettingsRegistry = compositionLocalOf<List<SettingDefinition>> { emptyList() }
val LocalSettingsResolvedStrings =
    compositionLocalOf<Map<org.wip.plugintoolkit.features.settings.utils.SettingText, String>> { emptyMap() }
