package org.wip.plugintoolkit.shared.components.sidebar

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.model.localized
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.section_general
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.*

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

@Composable
fun <T> SidebarSection(
    section: SidebarSectionData<out T>,
    currentSelection: T,
    onItemSelected: (T) -> Unit,
    isExpanded: Boolean = true
) {
    var isSectionCollapsed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isExpanded && section.title != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { isSectionCollapsed = !isSectionCollapsed }
                    .padding(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = ToolkitTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = section.title.resolve(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
                Icon(
                    imageVector = if (isSectionCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(Res.string.action_toggle_section),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ToolkitTheme.dimensions.circularProgressSize)
                )
            }
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
        } else {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
        }

        section.elements.forEachIndexed { index, element ->
            val isSelected = element.id == currentSelection

            val isVisible = (!isSectionCollapsed || isSelected) && element.isVisible

            val position = if (section.elements.size == 1 || (isSectionCollapsed && isSelected)) {
                SidebarItemPosition.StandAlone
            } else {
                when (index) {
                    0 -> SidebarItemPosition.Start
                    section.elements.lastIndex -> SidebarItemPosition.End
                    else -> SidebarItemPosition.Middle
                }
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SidebarItem(
                    element = element,
                    isSelected = isSelected,
                    onClick = { onItemSelected(element.id) },
                    isExpanded = isExpanded,
                    position = position
                )
            }
        }
    }
}
