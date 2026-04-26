package com.wip.kpm_cpm_wotoolkit.features.settings.utils

import androidx.compose.runtime.compositionLocalOf

val LocalSettingsSearchQuery = compositionLocalOf { "" }
val LocalSettingsRegistry = compositionLocalOf<List<SearchableSetting>> { emptyList() }
