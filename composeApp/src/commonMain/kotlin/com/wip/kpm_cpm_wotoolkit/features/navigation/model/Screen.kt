package com.wip.kpm_cpm_wotoolkit.features.navigation.model

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data object Main : Screen

    @Serializable
    data object Board : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object JobDashboard : Screen

    @Serializable
    data object Modules : Screen

    @Serializable
    data class Module(val id: String) : Screen
}

/** Polymorphic serialization config required by rememberNavBackStack. */
val ScreenNavConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Screen.Main::class, Screen.Main.serializer())
            subclass(Screen.Board::class, Screen.Board.serializer())
            subclass(Screen.Settings::class, Screen.Settings.serializer())
            subclass(Screen.JobDashboard::class, Screen.JobDashboard.serializer())
            subclass(Screen.Modules::class, Screen.Modules.serializer())
            subclass(Screen.Module::class, Screen.Module.serializer())
        }
    }
}
