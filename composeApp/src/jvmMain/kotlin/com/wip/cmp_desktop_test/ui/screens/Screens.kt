package com.wip.cmp_desktop_test.ui.screens

import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable
    data object Main : Screen
    
    @Serializable
    data object Board : Screen
    
    @Serializable
    data object Settings : Screen
}
