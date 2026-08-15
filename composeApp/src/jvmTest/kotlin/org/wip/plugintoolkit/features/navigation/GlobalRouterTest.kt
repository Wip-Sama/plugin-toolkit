package org.wip.plugintoolkit.features.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavKey
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.plugin.ui.lockedClickInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GlobalRouterTest {

    @Test
    fun testDeprecatedNavigateToPluginSetting_mapsToScreenPluginManager() {
        var lastNavKey: NavKey? = null
        val testRouter = object : GlobalRouter {
            override fun navigateTo(screen: NavKey) {
                lastNavKey = screen
            }

            override fun navigateUp() {}

            override fun clearAndNavigateTo(screen: NavKey) {
                lastNavKey = screen
            }
        }

        @Suppress("DEPRECATION")
        testRouter.navigateToPluginSetting("testPlugin", "testSettingKey")

        val expected = Screen.PluginManager(pluginId = "testPlugin", scrollToSetting = "testSettingKey")
        assertEquals(expected, lastNavKey)
    }

    @Test
    fun testLockedClickInterceptor_withTypeSafeTargetScreen_navigatesViaGlobalRouter() = runDesktopComposeUiTest {
        var navigatedScreen: NavKey? = null
        val testRouter = object : GlobalRouter {
            override fun navigateTo(screen: NavKey) {
                navigatedScreen = screen
            }

            override fun navigateUp() {}

            override fun clearAndNavigateTo(screen: NavKey) {
                navigatedScreen = screen
            }
        }

        val targetDestination = Screen.Plugin(id = "myAwesomePlugin", scrollToSetting = "token")

        setContent {
            CompositionLocalProvider(LocalGlobalRouter provides testRouter) {
                Button(
                    onClick = { /* disabled */ },
                    enabled = false,
                    modifier = Modifier
                        .testTag("locked-btn")
                        .lockedClickInterceptor(
                            isLocked = true,
                            targetScreen = targetDestination
                        )
                ) {
                    Text("Execute")
                }
            }
        }

        onNodeWithTag("locked-btn").performClick()

        assertEquals(targetDestination, navigatedScreen)
    }

    @Test
    fun testLockedClickInterceptor_withUnsavedChanges_navigatesAfterConfirm() = runDesktopComposeUiTest {
        var navigatedScreen: NavKey? = null
        val testRouter = object : GlobalRouter {
            override fun navigateTo(screen: NavKey) {
                navigatedScreen = screen
            }

            override fun navigateUp() {}

            override fun clearAndNavigateTo(screen: NavKey) {
                navigatedScreen = screen
            }
        }

        val targetDestination = Screen.PluginManager(pluginId = "dirtyPlugin", scrollToSetting = "apiKey")

        setContent {
            CompositionLocalProvider(LocalGlobalRouter provides testRouter) {
                Button(
                    onClick = { /* disabled */ },
                    enabled = false,
                    modifier = Modifier
                        .testTag("locked-dirty-btn")
                        .lockedClickInterceptor(
                            isLocked = true,
                            targetScreen = targetDestination,
                            hasUnsavedChanges = true
                        )
                ) {
                    Text("Execute Dirty")
                }
            }
        }

        onNodeWithTag("locked-dirty-btn").performClick()
        assertNull(navigatedScreen, "Navigation must be deferred until dialog confirmation")

        onNodeWithText("Confirm").performClick()
        assertEquals(targetDestination, navigatedScreen)
    }
}
