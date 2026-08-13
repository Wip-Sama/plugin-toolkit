package org.wip.plugintoolkit.features.settings

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Test
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.ExtensionSettings
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginSettingsNavigationTest {

    @Test
    fun testDefaultNavigation_pushesPluginScreen() {
        val backStack = NavBackStack<NavKey>(Screen.Main)
        val settings = AppSettings(extensions = ExtensionSettings(pluginSettingsInPlace = false))
        
        var popupTarget: Pair<String, String>? = null
        
        val onNavigate: (String, String) -> Unit = { pluginId, settingKey ->
            if (settings.extensions.pluginSettingsInPlace) {
                popupTarget = Pair(pluginId, settingKey)
            } else {
                backStack.add(Screen.PluginManager(pluginId, settingKey))
            }
        }

        onNavigate("org.wip.complete", "apiKey")

        assertNull(popupTarget, "Popup should not be shown when in-place setting is false")
        assertEquals(2, backStack.size)
        val targetScreen = backStack.last() as Screen.PluginManager
        assertEquals("org.wip.complete", targetScreen.pluginId)
        assertEquals("apiKey", targetScreen.scrollToSetting)
    }

    @Test
    fun testInPlaceNavigation_showsPopupWithoutChangingBackStack() {
        val backStack = NavBackStack<NavKey>(Screen.Main)
        val settings = AppSettings(extensions = ExtensionSettings(pluginSettingsInPlace = true))
        
        var popupTarget: Pair<String, String>? = null
        
        val onNavigate: (String, String) -> Unit = { pluginId, settingKey ->
            if (settings.extensions.pluginSettingsInPlace) {
                popupTarget = Pair(pluginId, settingKey)
            } else {
                backStack.add(Screen.PluginManager(pluginId, settingKey))
            }
        }

        onNavigate("org.wip.complete", "apiKey")

        assertEquals(Pair("org.wip.complete", "apiKey"), popupTarget)
        assertEquals(1, backStack.size, "Backstack size should remain 1 (Screen.Main)")
    }
}
