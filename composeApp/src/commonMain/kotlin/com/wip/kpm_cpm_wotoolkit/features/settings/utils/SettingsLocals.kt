package com.wip.kpm_cpm_wotoolkit.features.settings.utils

import androidx.compose.runtime.compositionLocalOf
import com.wip.kpm_cpm_wotoolkit.features.settings.model.SettingDefinition

val LocalSettingsSearchQuery = compositionLocalOf { "" }
val LocalSettingsRegistry = compositionLocalOf<List<SettingDefinition>> { emptyList() }
val LocalSettingsResolvedStrings =
    compositionLocalOf<Map<com.wip.kpm_cpm_wotoolkit.features.settings.utils.SettingText, String>> { emptyMap() }
