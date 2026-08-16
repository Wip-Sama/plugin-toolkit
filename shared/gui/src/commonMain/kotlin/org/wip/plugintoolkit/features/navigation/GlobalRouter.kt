package org.wip.plugintoolkit.features.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import org.wip.plugintoolkit.features.navigation.model.Screen

/**
 * Framework-agnostic navigation contract adhering to Navigation 3 best practices.
 * State is hoisted to the top level while deeply nested components interact solely
 * through this contract without direct coupling to NavBackStack.
 */
interface GlobalRouter {
    /**
     * Navigates forward to a type-safe [NavKey] destination.
     */
    fun navigateTo(screen: NavKey)

    /**
     * Navigates up in the back stack.
     */
    fun navigateUp()

    /**
     * Clears the back stack and navigates to the specified [NavKey].
     */
    fun clearAndNavigateTo(screen: NavKey)

    /**
     * Deprecated navigation method retained for smooth migration and compile-time linting.
     */
    @Deprecated(
        message = "Stai usando la navigazione via parametri multipli. Migra al Type-Safe usando gli oggetti @Serializable.",
        replaceWith = ReplaceWith(
            "navigateTo(Screen.PluginManager(pluginId = pluginId, scrollToSetting = settingKey))",
            "org.wip.plugintoolkit.features.navigation.model.Screen"
        )
    )
    fun navigateToPluginSetting(pluginId: String, settingKey: String) {
        navigateTo(Screen.PluginManager(pluginId = pluginId, scrollToSetting = settingKey))
    }
}

/**
 * CompositionLocal providing access to the hoisted [GlobalRouter].
 */
val LocalGlobalRouter = staticCompositionLocalOf<GlobalRouter> {
    object : GlobalRouter {
        override fun navigateTo(screen: NavKey) {}
        override fun navigateUp() {}
        override fun clearAndNavigateTo(screen: NavKey) {}
    }
}
