package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.wip.plugintoolkit.api.Release
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ChangelogViewGUITest {

    private val testReleases = listOf(
        Release(
            version = "2.0.0",
            date = "2026-06-01",
            categories = mapOf(
                "Features" to listOf("Dynamic Locks", "Storage System"),
                "Fixes" to listOf("Resolved memory leak")
            ),
            versionName = "Major Redesign"
        ),
        Release(
            version = "1.5.0",
            date = "2026-04-15",
            categories = mapOf(
                "General" to listOf("Performance improvements")
            ),
            versionName = null
        )
    )

    @Test
    fun testChangelogView_displaysReleasesAndVersionNames() = runDesktopComposeUiTest {
        var closed = false

        setContent {
            ChangelogView(
                pluginName = "SamplePlugin",
                versions = testReleases,
                onClose = { closed = true }
            )
        }

        // Header check
        onNodeWithText("Changelog: SamplePlugin").assertIsDisplayed()

        // Release 1 with versionName
        onNodeWithText("Version 2.0.0").assertIsDisplayed()
        onNodeWithText("— \"Major Redesign\"").assertIsDisplayed()
        onNodeWithText("2026-06-01").assertIsDisplayed()
        onNodeWithText("Dynamic Locks").assertIsDisplayed()

        // Release 2 without versionName
        onNodeWithText("Version 1.5.0").assertIsDisplayed()
        onNodeWithText("2026-04-15").assertIsDisplayed()

        // Filter chips exist
        onNodeWithText("Everything").assertIsDisplayed()
        onNodeWithText("Major").assertIsDisplayed()
        onNodeWithText("Minor").assertIsDisplayed()
        onNodeWithText("Patch").assertIsDisplayed()
    }

    @Test
    fun testChangelogView_filterCategories() = runDesktopComposeUiTest {
        setContent {
            ChangelogView(
                pluginName = "SamplePlugin",
                versions = testReleases,
                onClose = {}
            )
        }

        // Click on "Features" category tab (matching node with Tab role)
        val tabMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        onNode(hasText("Features").and(tabMatcher)).performClick()

        // Should display items in Features
        onNodeWithText("Dynamic Locks").assertIsDisplayed()
        onNodeWithText("Resolved memory leak").assertDoesNotExist()

        // Click on "Fixes" category tab
        onNode(hasText("Fixes").and(tabMatcher)).performClick()
        onNodeWithText("Resolved memory leak").assertIsDisplayed()
        onNodeWithText("Dynamic Locks").assertDoesNotExist()
    }

    @Test
    fun testChangelogView_filterByLevel() = runDesktopComposeUiTest {
        setContent {
            ChangelogView(
                pluginName = "SamplePlugin",
                versions = testReleases,
                onClose = {}
            )
        }

        // Click on "Patch" filter chip
        onNodeWithText("Patch").performClick()

        // Initially selectedPatch defaults to first version (2.0.0)
        onNodeWithText("Version 2.0.0").assertIsDisplayed()
        onNodeWithText("— \"Major Redesign\"").assertIsDisplayed()
        onNodeWithText("Version 1.5.0").assertDoesNotExist()
    }
}
