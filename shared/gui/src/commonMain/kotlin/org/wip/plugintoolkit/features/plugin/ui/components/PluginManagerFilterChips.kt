package org.wip.plugintoolkit.features.plugin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.model.PluginManagerChipFilter
import org.wip.plugintoolkit.features.plugin.model.PluginManagerSortMode
import org.wip.plugintoolkit.shared.components.horizontalFadingEdges
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenu
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenuItem
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.plugin_filter_all
import plugintoolkit.composeapp.generated.resources.plugin_filter_disabled
import plugintoolkit.composeapp.generated.resources.plugin_filter_enabled
import plugintoolkit.composeapp.generated.resources.plugin_filter_label
import plugintoolkit.composeapp.generated.resources.plugin_filter_requires_setup
import plugintoolkit.composeapp.generated.resources.plugin_filter_update_available
import plugintoolkit.composeapp.generated.resources.plugin_sort_name_asc
import plugintoolkit.composeapp.generated.resources.plugin_sort_name_desc
import plugintoolkit.composeapp.generated.resources.plugin_sort_status
import plugintoolkit.composeapp.generated.resources.plugin_sort_version

@Composable
fun PluginManagerFilterChipsRow(
    selectedFilter: PluginManagerChipFilter,
    onFilterSelected: (PluginManagerChipFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalFadingEdges(
                scrollState = scrollState,
                leftFadeLength = ToolkitTheme.spacing.small,
                rightFadeLength = ToolkitTheme.spacing.small
            )
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.smallMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.plugin_filter_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Medium
        )

        val statusFilters = listOf(
            Triple(PluginManagerChipFilter.All, stringResource(Res.string.plugin_filter_all), null as ImageVector?),
            Triple(PluginManagerChipFilter.Enabled, stringResource(Res.string.plugin_filter_enabled), Icons.Default.Check),
            Triple(PluginManagerChipFilter.Disabled, stringResource(Res.string.plugin_filter_disabled), Icons.Default.Block),
            Triple(PluginManagerChipFilter.UpdateAvailable, stringResource(Res.string.plugin_filter_update_available), Icons.Default.Upgrade),
            Triple(PluginManagerChipFilter.RequiresSetup, stringResource(Res.string.plugin_filter_requires_setup), Icons.Default.Build)
        )

        statusFilters.forEach { (filter, label, icon) ->
            val isSelected = selectedFilter == filter
            Surface(
                modifier = Modifier
                    .height(ToolkitTheme.dimensions.filterChipHeight)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onFilterSelected(filter) },
                shape = MaterialTheme.shapes.small,
                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else ToolkitTheme.colors.transparent,
                border = if (isSelected) null else BorderStroke(
                    ToolkitTheme.dimensions.borderUnselected,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                )
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = ToolkitTheme.spacing.small,
                        vertical = ToolkitTheme.spacing.badgeVertical
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall),
                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PluginManagerSortDropdownChip(
    sortMode: PluginManagerSortMode,
    onSortModeChange: (PluginManagerSortMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val sortLabel = when (sortMode) {
        PluginManagerSortMode.NameAsc -> stringResource(Res.string.plugin_sort_name_asc)
        PluginManagerSortMode.NameDesc -> stringResource(Res.string.plugin_sort_name_desc)
        PluginManagerSortMode.StatusEnabledFirst -> stringResource(Res.string.plugin_sort_status)
        PluginManagerSortMode.LatestVersion -> stringResource(Res.string.plugin_sort_version)
    }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .height(ToolkitTheme.dimensions.filterChipHeight)
                .clip(MaterialTheme.shapes.small)
                .clickable { expanded = !expanded },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                ToolkitTheme.dimensions.borderUnselected,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
            )
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = ToolkitTheme.spacing.smallMedium,
                    vertical = ToolkitTheme.spacing.badgeVertical
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                Text(
                    text = sortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ToolkitDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            PluginManagerSortMode.entries.forEach { mode ->
                val label = when (mode) {
                    PluginManagerSortMode.NameAsc -> stringResource(Res.string.plugin_sort_name_asc)
                    PluginManagerSortMode.NameDesc -> stringResource(Res.string.plugin_sort_name_desc)
                    PluginManagerSortMode.StatusEnabledFirst -> stringResource(Res.string.plugin_sort_status)
                    PluginManagerSortMode.LatestVersion -> stringResource(Res.string.plugin_sort_version)
                }
                val isSelected = mode == sortMode
                ToolkitDropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    isSelected = isSelected,
                    onClick = {
                        expanded = false
                        onSortModeChange(mode)
                    }
                )
            }
        }
    }
}
