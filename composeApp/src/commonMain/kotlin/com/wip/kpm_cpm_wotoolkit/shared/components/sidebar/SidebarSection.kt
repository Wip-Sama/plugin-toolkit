package com.wip.kpm_cpm_wotoolkit.shared.components.sidebar

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.*
import androidx.compose.ui.tooling.preview.Preview

@Composable
@Preview
private fun SidebarSectionPreview() {
    MaterialTheme {
        SidebarSection(
            section = SidebarSectionData(
                title = Res.string.section_application,
                elements = listOf()
            ),
            currentSelection = "main",
            onItemSelected = {}
        )
    }
}

@Composable
fun SidebarSection(
    section: SidebarSectionData,
    currentSelection: Any,
    onItemSelected: (Any) -> Unit,
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(section.title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
                Icon(
                    imageVector = if (isSectionCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Toggle Section",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        section.elements.forEachIndexed { index, element ->
            val isSelected = element.id == currentSelection

            val isVisible = !isSectionCollapsed || isSelected

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
