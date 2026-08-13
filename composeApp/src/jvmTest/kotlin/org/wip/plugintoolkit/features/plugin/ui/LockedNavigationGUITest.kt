package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LockedNavigationGUITest {

    @Test
    fun testClickingLockedElement_triggersNavigationDirectlyWhenNoUnsavedData() = runDesktopComposeUiTest {
        var navigatedToSetting by mutableStateOf<String?>(null)

        setContent {
            LockedElementNavigationWrapper(
                targetSettingKey = "secretToken",
                hasUnsavedChanges = false,
                onNavigate = { target -> navigatedToSetting = target }
            )
        }

        // Click locked element
        onNodeWithContentDescription("Locked capability icon").performClick()

        // Assert navigation triggered without popup
        kotlin.test.assertEquals("secretToken", navigatedToSetting)
    }

    @Test
    fun testClickingLockedElement_showsWarningPopupWhenUnsavedDataExists() = runDesktopComposeUiTest {
        var navigatedToSetting by mutableStateOf<String?>(null)

        setContent {
            LockedElementNavigationWrapper(
                targetSettingKey = "secretToken",
                hasUnsavedChanges = true,
                onNavigate = { target -> navigatedToSetting = target }
            )
        }

        // Click locked element
        onNodeWithContentDescription("Locked capability icon").performClick()

        // Verify warning popup appears
        onNodeWithText("All the unsaved data will be lost. Are you sure you want to exit?").assertExists()
        kotlin.test.assertNull(navigatedToSetting)

        // Confirm warning dialog
        onNodeWithText("Confirm").performClick()

        // Verify navigation now executed
        kotlin.test.assertEquals("secretToken", navigatedToSetting)
    }
}
