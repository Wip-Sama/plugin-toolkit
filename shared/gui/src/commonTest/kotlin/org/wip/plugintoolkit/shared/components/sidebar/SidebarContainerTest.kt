package org.wip.plugintoolkit.shared.components.sidebar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ColorEngine
import org.wip.plugintoolkit.core.theme.Dimensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SidebarContainerTest {

    @Test
    fun testSidebarDimensionsTokens() {
        val dimensions = Dimensions()
        assertEquals(250.dp, dimensions.sidebarExpandedWidth, "Default sidebarExpandedWidth should be 250.dp")
        assertEquals(80.dp, dimensions.sidebarCollapsedWidth, "Default sidebarCollapsedWidth should be 80.dp")
        assertEquals(16.dp, dimensions.contentCanvasCornerRadius, "Default contentCanvasCornerRadius should be 16.dp")
    }

    @Test
    fun testSecondaryNavigationColorDistinctionInDarkTheme() {
        val seed = Color(0xFF6750A4)
        val scheme = ColorEngine.createStandardScheme(seed, isDark = true, isAmoled = false)

        // surfaceContainer (used for secondary navigation) must be distinct from surfaceContainerLow (primary navigation)
        assertNotEquals(
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            "surfaceContainer and surfaceContainerLow must be distinct in dark theme for corner contrast"
        )
    }

    @Test
    fun testSecondaryNavigationColorDistinctionInLightTheme() {
        val seed = Color(0xFF6750A4)
        val scheme = ColorEngine.createStandardScheme(seed, isDark = false, isAmoled = false)

        assertNotEquals(
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            "surfaceContainer and surfaceContainerLow must be distinct in light theme for corner contrast"
        )
    }

    @Test
    fun testSecondaryNavigationColorDistinctionInAmoledTheme() {
        val seed = Color(0xFF6750A4)
        val scheme = ColorEngine.createStandardScheme(seed, isDark = true, isAmoled = true)

        assertNotEquals(
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            "surfaceContainer and surfaceContainerLow must be distinct in AMOLED theme for corner contrast"
        )
    }
}
