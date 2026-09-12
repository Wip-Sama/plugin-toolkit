package org.wip.plugintoolkit.features.shortcuts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutSituation
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.debug_zoning_conflict_detected
import plugintoolkit.composeapp.generated.resources.debug_zoning_multi_zone
import plugintoolkit.composeapp.generated.resources.debug_zoning_pointer
import plugintoolkit.composeapp.generated.resources.debug_zoning_title

/**
 * Non-interactive, interaction-transparent HUD overlay that displays currently active
 * shortcut zones, multi-zone overlap warnings, and the current mouse pointer zone.
 *
 * NOTE: This component intentionally has NO pointerInput or clickable modifiers, ensuring
 * all mouse clicks, drags, and hover events pass directly through to underlying canvas and UI elements.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveShortcutZoningOverlay(
    shortcutManager: ShortcutManager,
    showPointerZone: Boolean,
    modifier: Modifier = Modifier
) {
    val activeZones by shortcutManager.activeSituations.collectAsState()
    val pointerZone by shortcutManager.pointerSituation.collectAsState()
    val lastEaten by shortcutManager.lastEatenEvent.collectAsState()

    val conflicts = remember(activeZones) {
        shortcutManager.findActiveZoneConflicts()
    }
    val unshadowedConflicts = remember(conflicts) { conflicts.filter { !it.isShadowed } }
    val shadowedOverrides = remember(conflicts) { conflicts.filter { it.isShadowed } }

    val nonGlobalZonesCount = remember(activeZones) {
        activeZones.count { it != ShortcutSituation.Global }
    }
    val isMultiZone = nonGlobalZonesCount > 1

    val shape = ToolkitTheme.shapes.medium
    val badgeShape = ToolkitTheme.shapes.extraSmall
    val spacing = ToolkitTheme.spacing
    val dimensions = ToolkitTheme.dimensions
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .padding(spacing.medium)
            .shadow(elevation = dimensions.cardElevation, shape = shape)
            .clip(shape)
            .background(colors.surfaceContainerHighest)
            .border(
                width = dimensions.borderUnselected,
                color = if (unshadowedConflicts.isNotEmpty()) colors.error else colors.outlineVariant,
                shape = shape
            )
            .padding(horizontal = spacing.medium, vertical = spacing.small)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            // Header: Title & Multi-zone indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(dimensions.iconSmall),
                    tint = colors.primary
                )
                Text(
                    text = stringResource(Res.string.debug_zoning_title),
                    style = ToolkitTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Spacer(modifier = Modifier.width(spacing.xs))

                if (isMultiZone) {
                    Box(
                        modifier = Modifier
                            .clip(badgeShape)
                            .background(colors.tertiaryContainer)
                            .padding(horizontal = spacing.xs, vertical = spacing.xxs)
                    ) {
                        Text(
                            text = "${stringResource(Res.string.debug_zoning_multi_zone)} ($nonGlobalZonesCount)",
                            style = ToolkitTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onTertiaryContainer
                        )
                    }
                }

                val priorityMode = shortcutManager.getPriorityMode()
                val modeLabel = if (priorityMode == org.wip.plugintoolkit.features.shortcuts.model.ShortcutPriorityMode.ZIndexAndPriority) {
                    "Z-Index"
                } else {
                    "Static"
                }
                Box(
                    modifier = Modifier
                        .clip(badgeShape)
                        .background(colors.surfaceContainerHighest)
                        .border(dimensions.borderUnselected, colors.outlineVariant, badgeShape)
                        .padding(horizontal = spacing.xs, vertical = spacing.xxs)
                ) {
                    Text(
                        text = modeLabel,
                        style = ToolkitTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // Active Zones List
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs)
            ) {
                activeZones.sortedBy { it.ordinal }.forEach { zone ->
                    val isGlobal = zone == ShortcutSituation.Global
                    val zoneBg = if (isGlobal) colors.surfaceContainerLow else colors.secondaryContainer
                    val zoneFg = if (isGlobal) colors.onSurfaceVariant else colors.onSecondaryContainer

                    Box(
                        modifier = Modifier
                            .clip(badgeShape)
                            .background(zoneBg)
                            .border(
                                width = dimensions.borderUnselected,
                                color = colors.outlineVariant,
                                shape = badgeShape
                            )
                            .padding(horizontal = spacing.xs, vertical = spacing.xxs)
                    ) {
                        Text(
                            text = zone.displayLabel,
                            style = ToolkitTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = zoneFg
                        )
                    }
                }
            }

            // Pointer Hover Zone (if enabled)
            if (showPointerZone) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = null,
                        modifier = Modifier.size(dimensions.iconSmall),
                        tint = colors.secondary
                    )
                    Text(
                        text = "${stringResource(Res.string.debug_zoning_pointer)}: ",
                        style = ToolkitTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = pointerZone?.displayLabel ?: "—",
                        style = ToolkitTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (pointerZone != null) colors.primary else colors.onSurfaceVariant
                    )
                }
            }

            // Last Eaten Telemetry
            if (lastEaten != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(dimensions.iconSmall),
                        tint = colors.primary
                    )
                    Text(
                        text = "Eaten by: ",
                        style = ToolkitTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = "${lastEaten!!.situation.displayLabel} (${lastEaten!!.actionId})",
                        style = ToolkitTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
            }

            // Unshadowed / Peer Conflicts
            if (unshadowedConflicts.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .clip(badgeShape)
                        .background(colors.errorContainer)
                        .padding(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                ) {
                    unshadowedConflicts.forEach { conflict ->
                        val conflictTriggerStr = if (conflict.isExact) {
                            conflict.triggerA.format()
                        } else {
                            "${conflict.triggerA.format()} ~ ${conflict.triggerB.format()}"
                        }
                        val conflictDesc = if (conflict.actionA.situation == conflict.actionB.situation) {
                            "${conflict.actionA.situation.displayLabel}: ${conflict.actionA.id} vs ${conflict.actionB.id} ($conflictTriggerStr)"
                        } else {
                            stringResource(
                                Res.string.debug_zoning_conflict_detected,
                                conflict.actionA.situation.displayLabel,
                                conflict.actionB.situation.displayLabel,
                                conflictTriggerStr
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(dimensions.iconSmall),
                                tint = colors.onErrorContainer
                            )
                            Text(
                                text = conflictDesc,
                                style = ToolkitTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Hierarchical Shadowing Overrides (Higher Priority Eating Trigger)
            if (shadowedOverrides.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .clip(badgeShape)
                        .background(colors.surfaceContainerLow)
                        .padding(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                ) {
                    shadowedOverrides.forEach { conflict ->
                        val conflictTriggerStr = if (conflict.isExact) {
                            conflict.triggerA.format()
                        } else {
                            "${conflict.triggerA.format()} ~ ${conflict.triggerB.format()}"
                        }
                        val overrideDesc = if (conflict.actionA.situation == conflict.actionB.situation) {
                            "${conflict.dominantAction.situation.displayLabel}: ${conflict.dominantAction.id} eats $conflictTriggerStr → shadows ${conflict.shadowedAction.id}"
                        } else {
                            "${conflict.dominantAction.situation.displayLabel} (${conflict.dominantAction.id}) eats $conflictTriggerStr → shadows ${conflict.shadowedAction.situation.displayLabel}"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(dimensions.iconSmall),
                                tint = colors.onSurfaceVariant
                            )
                            Text(
                                text = overrideDesc,
                                style = ToolkitTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
