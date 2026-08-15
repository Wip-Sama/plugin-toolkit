package org.wip.plugintoolkit.features.repository.ui.components

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import org.wip.plugintoolkit.features.repository.viewmodel.PluginChipFilter
import org.wip.plugintoolkit.features.repository.viewmodel.PluginSortMode
import org.wip.plugintoolkit.shared.components.horizontalFadingEdges
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.repo_filter_chip_all
import plugintoolkit.composeapp.generated.resources.repo_filter_chip_available
import plugintoolkit.composeapp.generated.resources.repo_filter_chip_incompatible
import plugintoolkit.composeapp.generated.resources.repo_filter_chip_installed
import plugintoolkit.composeapp.generated.resources.repo_filter_chip_update
import plugintoolkit.composeapp.generated.resources.repo_filter_label
import plugintoolkit.composeapp.generated.resources.repo_sort_name_asc
import plugintoolkit.composeapp.generated.resources.repo_sort_name_desc
import plugintoolkit.composeapp.generated.resources.repo_sort_status
import plugintoolkit.composeapp.generated.resources.repo_sort_version

@Composable
fun PluginFilterChipsRow(
    selectedFilter: PluginChipFilter,
    onFilterSelected: (PluginChipFilter) -> Unit,
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
            text = stringResource(Res.string.repo_filter_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Medium
        )

        val statusFilters = listOf(
            Triple(PluginChipFilter.All, stringResource(Res.string.repo_filter_chip_all), null as ImageVector?),
            Triple(PluginChipFilter.Installed, stringResource(Res.string.repo_filter_chip_installed), Icons.Default.Check),
            Triple(PluginChipFilter.UpdateAvailable, stringResource(Res.string.repo_filter_chip_update), Icons.Default.Upgrade),
            Triple(PluginChipFilter.NotInstalled, stringResource(Res.string.repo_filter_chip_available), null),
            Triple(PluginChipFilter.Incompatible, stringResource(Res.string.repo_filter_chip_incompatible), Icons.Default.Block)
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
fun SortDropdownChip(
    sortMode: PluginSortMode,
    onSortModeChange: (PluginSortMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val sortLabel = when (sortMode) {
        PluginSortMode.NameAsc -> stringResource(Res.string.repo_sort_name_asc)
        PluginSortMode.NameDesc -> stringResource(Res.string.repo_sort_name_desc)
        PluginSortMode.StatusInstalledFirst -> stringResource(Res.string.repo_sort_status)
        PluginSortMode.LatestVersion -> stringResource(Res.string.repo_sort_version)
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

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            PluginSortMode.entries.forEach { mode ->
                val label = when (mode) {
                    PluginSortMode.NameAsc -> stringResource(Res.string.repo_sort_name_asc)
                    PluginSortMode.NameDesc -> stringResource(Res.string.repo_sort_name_desc)
                    PluginSortMode.StatusInstalledFirst -> stringResource(Res.string.repo_sort_status)
                    PluginSortMode.LatestVersion -> stringResource(Res.string.repo_sort_version)
                }
                val isSelected = mode == sortMode
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        expanded = false
                        onSortModeChange(mode)
                    }
                )
            }
        }
    }
}
