package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.ControlSize
import org.wip.plugintoolkit.api.sdui.LoadingIndicatorNode
import org.wip.plugintoolkit.api.sdui.ProgressBarNode
import org.wip.plugintoolkit.api.sdui.ProgressBarStyle
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.core.theme.StandardMotionScope
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.theme.rememberMorphShape
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import org.wip.plugintoolkit.shared.components.ToolkitCircularProgressIndicator
import org.wip.plugintoolkit.shared.components.ToolkitConnectedToggleGroup
import org.wip.plugintoolkit.shared.components.ToolkitLinearProgressIndicator
import org.wip.plugintoolkit.shared.components.ToolkitLoadingIndicator
import org.wip.plugintoolkit.shared.components.ToolkitSplitButton
import org.wip.plugintoolkit.shared.components.ToolkitSplitMenuItem
import org.wip.plugintoolkit.shared.components.ToolkitToggleOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ExpressiveThemeAndProgressTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appThemeConfiguresExpressiveThemeShapesTypographyAndMotionScope() {
        var largeIncreasedDp = 0.dp
        var extraExtraLargeDp = 0.dp
        var titleEmphasizedWeight: androidx.compose.ui.text.font.FontWeight? = null
        var expressiveDamping = 0f
        var standardDamping = 0f

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                largeIncreasedDp = ToolkitTheme.shapes.largeIncreased.topStart.toPx(Size(100f, 100f), Density(1f)).dp
                extraExtraLargeDp = MaterialTheme.shapes.extraExtraLarge.topStart.toPx(Size(100f, 100f), Density(1f)).dp
                titleEmphasizedWeight = MaterialTheme.typography.titleMediumEmphasized.fontWeight
                val expressiveSpec = assertIs<SpringSpec<Float>>(ToolkitTheme.motionScheme.fastSpatialSpec<Float>())
                expressiveDamping = expressiveSpec.dampingRatio

                StandardMotionScope {
                    val standardSpec = assertIs<SpringSpec<Float>>(MaterialTheme.motionScheme.fastSpatialSpec<Float>())
                    standardDamping = standardSpec.dampingRatio
                }

                val morphShape = rememberMorphShape(
                    from = MaterialShapes.Circle,
                    to = MaterialShapes.Cookie9Sided,
                    progress = 0.5f
                )
                val outline = morphShape.createOutline(Size(48f, 48f), LayoutDirection.Ltr, Density(1f))
                assertIs<Outline.Generic>(outline)
                assertTrue(!outline.path.getBounds().isEmpty)
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(20.dp, largeIncreasedDp)
        assertEquals(48.dp, extraExtraLargeDp)
        assertTrue(titleEmphasizedWeight != null)
        assertTrue(
            expressiveDamping < standardDamping,
            "Expressive fastSpatialSpec damping ($expressiveDamping) must be bouncier than StandardMotionScope ($standardDamping)"
        )
    }

    @Test
    fun toolkitSplitButtonAndConnectedToggleGroupWorkEndToEnd() {
        var primaryClicks = 0
        var lastMenuKey: String? = null
        var selectedMode = "design"

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                var activeKey by remember { mutableStateOf(selectedMode) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolkitSplitButton(
                        label = "Run Flow",
                        leadingIcon = Icons.Default.PlayArrow,
                        menuTooltip = "Flow run options",
                        onPrimaryClick = { primaryClicks++ },
                        menuItems = listOf(
                            ToolkitSplitMenuItem(key = "debug", label = "Run in Debug"),
                            ToolkitSplitMenuItem(key = "clean", label = "Clean Cache", startsSection = true)
                        ),
                        onMenuItemClick = { lastMenuKey = it.key }
                    )
                    ToolkitConnectedToggleGroup(
                        options = listOf(
                            ToolkitToggleOption(key = "design", label = "Design"),
                            ToolkitToggleOption(key = "inspect", label = "Inspect")
                        ),
                        selectedKey = activeKey,
                        onSelect = {
                            activeKey = it
                            selectedMode = it
                        }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Run Flow").performClick()
        assertEquals(1, primaryClicks)

        composeTestRule.onNodeWithContentDescription("Flow run options").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Run in Debug").assertIsDisplayed().performClick()
        assertEquals("debug", lastMenuKey)

        composeTestRule.onNodeWithText("Inspect").performClick()
        assertEquals("inspect", selectedMode)
    }

    @Test
    fun expressiveProgressAndLoadingNodesRenderAllStyles() {
        val treeRoot = ColumnNode(
            id = "progress_showcase",
            children = listOf(
                ProgressBarNode(
                    id = "pb_wavy_linear",
                    progress = 0.65f,
                    label = "Wavy Sync",
                    statusText = "65%",
                    style = ProgressBarStyle.WAVY
                ),
                ProgressBarNode(
                    id = "pb_wavy_circular",
                    progress = 0.4f,
                    isCircular = true,
                    label = "Circular Wavy",
                    style = ProgressBarStyle.WAVY
                ),
                ProgressBarNode(
                    id = "pb_morph",
                    label = "Morphing Task",
                    style = ProgressBarStyle.MORPHING_INDICATOR
                ),
                LoadingIndicatorNode(
                    id = "li_contained",
                    label = "Indexing Chapters",
                    contained = true,
                    color = SemanticColor.PRIMARY,
                    size = ControlSize.MEDIUM
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(640.dp, 480.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SafeUiNodeRenderer(node = treeRoot, onEvent = {})
                        ToolkitLoadingIndicator(progress = 0.5f)
                        ToolkitLinearProgressIndicator(progress = 0.5f, wavy = true)
                        ToolkitCircularProgressIndicator(progress = 0.5f, wavy = true)
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Wavy Sync").assertIsDisplayed()
        composeTestRule.onNodeWithText("Circular Wavy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Morphing Task").assertIsDisplayed()
        composeTestRule.onNodeWithText("Indexing Chapters").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Indexing Chapters").assertIsDisplayed()
    }
}
