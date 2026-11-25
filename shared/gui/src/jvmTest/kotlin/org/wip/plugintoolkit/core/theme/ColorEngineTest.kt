package org.wip.plugintoolkit.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorEngineTest {

    private val testSeeds = listOf(
        Color(0xFF6750A4), // Purple
        Color(0xFFE53935), // Red
        Color(0xFFFDD835), // Yellow
        Color(0xFF1E88E5), // Blue
        Color(0xFF43A047)  // Green
    )

    @Test
    fun darkSchemesHaveStrictlyIncreasingPerceptualSurfaceTones() {
        for (seed in testSeeds) {
            assertDarkTonalOrder(ColorEngine.createStandardScheme(seed, isDark = true, isAmoled = false), "Standard Dark")
            assertDarkTonalOrder(ColorEngine.createExpressiveScheme(seed, isDark = true, isAmoled = false), "Expressive Dark")
        }
    }

    @Test
    fun lightSchemesHaveStrictlyDecreasingPerceptualSurfaceTones() {
        for (seed in testSeeds) {
            assertLightTonalOrder(ColorEngine.createStandardScheme(seed, isDark = false, isAmoled = false), "Standard Light")
            assertLightTonalOrder(ColorEngine.createExpressiveScheme(seed, isDark = false, isAmoled = false), "Expressive Light")
        }
    }

    @Test
    fun amoledSchemesUsePureBlackBaseAndIncreasingContainerTones() {
        for (seed in testSeeds) {
            listOf(
                "Standard AMOLED" to ColorEngine.createStandardScheme(seed, isDark = true, isAmoled = true),
                "Expressive AMOLED" to ColorEngine.createExpressiveScheme(seed, isDark = true, isAmoled = true)
            ).forEach { (label, scheme) ->
                assertEquals(Color.Black, scheme.background, "$label background should be pure Black")
                assertEquals(Color.Black, scheme.surface, "$label surface should be pure Black")
                assertEquals(Color.Black, scheme.surfaceContainerLowest, "$label lowest should be pure Black")

                val lowTone = ColorEngine.colorToHct(scheme.surfaceContainerLow).tone
                val containerTone = ColorEngine.colorToHct(scheme.surfaceContainer).tone
                val highTone = ColorEngine.colorToHct(scheme.surfaceContainerHigh).tone
                val highestTone = ColorEngine.colorToHct(scheme.surfaceContainerHighest).tone

                assertTrue(lowTone > 0f, "$label lowTone=$lowTone should be > 0")
                assertTrue(lowTone < containerTone, "$label low ($lowTone) < container ($containerTone)")
                assertTrue(containerTone < highTone, "$label container ($containerTone) < high ($highTone)")
                assertTrue(highTone < highestTone, "$label high ($highTone) < highest ($highestTone)")
            }
        }
    }

    @Test
    fun standardSchemeNeutralSurfacesRemainAchromaticAndMatchExpressiveToneLadder() {
        val seed = Color(0xFFE53935)
        for (isDark in listOf(true, false)) {
            val standard = ColorEngine.createStandardScheme(seed, isDark = isDark, isAmoled = false)
            val expressive = ColorEngine.createExpressiveScheme(seed, isDark = isDark, isAmoled = false)

            val standardSurfaces = listOf(
                standard.surfaceContainerLowest,
                standard.surface,
                standard.surfaceContainerLow,
                standard.surfaceContainer,
                standard.surfaceContainerHigh,
                standard.surfaceContainerHighest
            )
            val expressiveSurfaces = listOf(
                expressive.surfaceContainerLowest,
                expressive.surface,
                expressive.surfaceContainerLow,
                expressive.surfaceContainer,
                expressive.surfaceContainerHigh,
                expressive.surfaceContainerHighest
            )

            standardSurfaces.zip(expressiveSurfaces).forEachIndexed { idx, (stdColor, expColor) ->
                val stdHct = ColorEngine.colorToHct(stdColor)
                val expHct = ColorEngine.colorToHct(expColor)
                assertTrue(
                    stdHct.chroma <= 1.0f,
                    "Standard surface[$idx] (isDark=$isDark) should be achromatic, got chroma=${stdHct.chroma}"
                )
                assertTrue(
                    abs(stdHct.tone - expHct.tone) <= 1.5f,
                    "Surface[$idx] (isDark=$isDark) tone mismatch: standard=${stdHct.tone}, expressive=${expHct.tone}"
                )
            }
        }
    }

    @Test
    fun phaseOneDimensionsMatchUnifiedWorkspaceTokens() {
        val dims = Dimensions()
        assertEquals(0.dp, dims.cardElevation)
        assertEquals(12.dp, dims.workspaceStartPadding)
        assertEquals(16.dp, dims.workspaceEndPadding)
        assertEquals(12.dp, dims.workspaceVerticalPadding)
        assertEquals(12.dp, dims.workspacePaneGap)
        assertEquals(300.dp, dims.floatingSidebarDefaultWidth)
        assertEquals(240.dp, dims.floatingSidebarMinWidth)
        assertEquals(260.dp, dims.floatingSidebarSequelWidth)
        assertEquals(540.dp, dims.floatingSidebarSplitTotalWidth)
        assertEquals(1080.dp, dims.settingsContentMaxWidth)
        assertEquals(276.dp, dims.flowPaletteWidth)
    }

    private fun assertDarkTonalOrder(scheme: ColorScheme, label: String) {
        val lowest = ColorEngine.colorToHct(scheme.surfaceContainerLowest).tone
        val surface = ColorEngine.colorToHct(scheme.surface).tone
        val low = ColorEngine.colorToHct(scheme.surfaceContainerLow).tone
        val container = ColorEngine.colorToHct(scheme.surfaceContainer).tone
        val high = ColorEngine.colorToHct(scheme.surfaceContainerHigh).tone
        val highest = ColorEngine.colorToHct(scheme.surfaceContainerHighest).tone

        assertTrue(lowest < surface, "$label: lowest ($lowest) < surface ($surface)")
        assertTrue(surface < low, "$label: surface ($surface) < low ($low)")
        assertTrue(low < container, "$label: low ($low) < container ($container)")
        assertTrue(container < high, "$label: container ($container) < high ($high)")
        assertTrue(high < highest, "$label: high ($high) < highest ($highest)")
    }

    private fun assertLightTonalOrder(scheme: ColorScheme, label: String) {
        val lowest = ColorEngine.colorToHct(scheme.surfaceContainerLowest).tone
        val surface = ColorEngine.colorToHct(scheme.surface).tone
        val low = ColorEngine.colorToHct(scheme.surfaceContainerLow).tone
        val container = ColorEngine.colorToHct(scheme.surfaceContainer).tone
        val high = ColorEngine.colorToHct(scheme.surfaceContainerHigh).tone
        val highest = ColorEngine.colorToHct(scheme.surfaceContainerHighest).tone

        assertTrue(lowest > surface, "$label: lowest ($lowest) > surface ($surface)")
        assertTrue(surface > low, "$label: surface ($surface) > low ($low)")
        assertTrue(low > container, "$label: low ($low) > container ($container)")
        assertTrue(container > high, "$label: container ($container) > high ($high)")
        assertTrue(high > highest, "$label: high ($high) > highest ($highest)")
    }
}
