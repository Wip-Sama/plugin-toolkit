package org.wip.plugintoolkit.shared.components.sidebar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.model.resolve
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import org.wip.plugintoolkit.shared.components.ToolkitTooltipPosition
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_toggle_section
import plugintoolkit.composeapp.generated.resources.section_general

private const val SECTION_LABEL_REVEAL_THRESHOLD = 0.01f

@Composable
@Preview
private fun SidebarSectionPreview() {
    MaterialTheme {
        SidebarSection(
            section = SidebarSectionData(
                title = Res.string.section_general.localized,
                elements = listOf()
            ),
            currentSelection = "main",
            onItemSelected = {}
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SidebarSection(
    section: SidebarSectionData<out T>,
    currentSelection: T,
    onItemSelected: (T) -> Unit,
    isExpanded: Boolean = true,
    expandProgress: Float = if (isExpanded) 1f else 0f
) {
    val visibleElements = remember(section.elements) {
        section.elements.filter { it.isVisible }
    }
    if (visibleElements.isEmpty()) return

    var isSectionCollapsed by remember { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    val chevronRotation by animateFloatAsState(
        targetValue = if (isSectionCollapsed) 180f else 0f,
        animationSpec = motion.fastSpatialSpec(),
        label = "sidebarSectionChevron"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        if (section.title != null) {
            val resolvedTitle = section.title.resolve()
            ToolkitTooltipBox(
                tooltip = if (!isExpanded) resolvedTitle else null,
                position = ToolkitTooltipPosition.Right
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { isSectionCollapsed = !isSectionCollapsed }
                        .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (expandProgress > SECTION_LABEL_REVEAL_THRESHOLD) {
                        Arrangement.SpaceBetween
                    } else {
                        Arrangement.Center
                    }
                ) {
                    if (expandProgress > SECTION_LABEL_REVEAL_THRESHOLD) {
                        Text(
                            text = resolvedTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .graphicsLayer { alpha = expandProgress.coerceIn(0f, 1f) }
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(Res.string.action_toggle_section),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.circularProgressSize)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
        } else {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
        }

        visibleElements.forEachIndexed { index, element ->
            val isSelected = element.id == currentSelection
            val isVisible = !isSectionCollapsed || isSelected

            val position = if (visibleElements.size == 1 || (isSectionCollapsed && isSelected)) {
                SidebarItemPosition.StandAlone
            } else {
                when (index) {
                    0 -> SidebarItemPosition.Start
                    visibleElements.lastIndex -> SidebarItemPosition.End
                    else -> SidebarItemPosition.Middle
                }
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
            ) {
                SidebarItem(
                    element = element,
                    isSelected = isSelected,
                    onClick = { onItemSelected(element.id) },
                    isExpanded = isExpanded,
                    position = position,
                    expandProgress = expandProgress
                )
            }
        }
    }
}
