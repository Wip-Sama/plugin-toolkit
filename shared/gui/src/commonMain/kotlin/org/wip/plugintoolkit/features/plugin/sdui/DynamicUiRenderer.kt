package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.AlertBannerNode
import org.wip.plugintoolkit.api.sdui.AlignmentRole
import org.wip.plugintoolkit.api.sdui.BadgeNode
import org.wip.plugintoolkit.api.sdui.BoardNode
import org.wip.plugintoolkit.api.sdui.BoxNode
import org.wip.plugintoolkit.api.sdui.ButtonNode
import org.wip.plugintoolkit.api.sdui.CardNode
import org.wip.plugintoolkit.api.sdui.CheckboxNode
import org.wip.plugintoolkit.api.sdui.ChipGroupNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.ControlSize
import org.wip.plugintoolkit.api.sdui.DialogNode
import org.wip.plugintoolkit.api.sdui.DividerNode
import org.wip.plugintoolkit.api.sdui.DropdownNode
import org.wip.plugintoolkit.api.sdui.EmptyStateNode
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.FloatingAnchor
import org.wip.plugintoolkit.api.sdui.FloatingPanelNode
import org.wip.plugintoolkit.api.sdui.IconButtonNode
import org.wip.plugintoolkit.api.sdui.IconNode
import org.wip.plugintoolkit.api.sdui.ImageNode
import org.wip.plugintoolkit.api.sdui.ListItemNode
import org.wip.plugintoolkit.api.sdui.ProgressBarNode
import org.wip.plugintoolkit.api.sdui.RowNode
import org.wip.plugintoolkit.api.sdui.ScaffoldNode
import org.wip.plugintoolkit.api.sdui.ScrollableColumnNode
import org.wip.plugintoolkit.api.sdui.ScrollableRowNode
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SidebarAttachPoint
import org.wip.plugintoolkit.api.sdui.SidebarNode
import org.wip.plugintoolkit.api.sdui.SliderNode
import org.wip.plugintoolkit.api.sdui.SpacerNode
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.SplitPaneNode
import org.wip.plugintoolkit.api.sdui.TabsNode
import org.wip.plugintoolkit.api.sdui.TextInputNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.ToggleNode
import org.wip.plugintoolkit.api.sdui.TypographyRole
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNode
import org.wip.plugintoolkit.api.sdui.UnknownNode
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_cancel
import plugintoolkit.composeapp.generated.resources.sdui_confirm
import plugintoolkit.composeapp.generated.resources.sdui_error_boundary_title
import plugintoolkit.composeapp.generated.resources.sdui_toggle_panel
import plugintoolkit.composeapp.generated.resources.sdui_toggle_sidebar
import plugintoolkit.composeapp.generated.resources.sdui_unsupported_node

/**
 * Tracks whether the current composition subtree is already inside a vertical scroll container.
 */
private val LocalInVerticalScroll = compositionLocalOf { false }

/**
 * Root boundary renderer that delegates to [RenderUiNode].
 */
@Composable
fun SafeUiNodeRenderer(
    node: UiNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    RenderUiNode(node = node, onEvent = onEvent, modifier = modifier)
}

/**
 * Recursive dynamic UI component dispatcher.
 */
@Composable
fun RenderUiNode(
    node: UiNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!node.modifier.visible) return

    val appliedModifier = modifier.applyUiModifier(node.modifier)

    when (node) {
        is ColumnNode -> {
            Column(
                modifier = appliedModifier,
                verticalArrangement = Arrangement.spacedBy(node.spacing.toDp()),
                horizontalAlignment = node.modifier.alignment?.toHorizontalAlignment() ?: Alignment.Start
            ) {
                RenderColumnChildren(children = node.children, onEvent = onEvent, allowWeight = !LocalInVerticalScroll.current)
            }
        }

        is RowNode -> {
            Row(
                modifier = appliedModifier,
                horizontalArrangement = Arrangement.spacedBy(node.spacing.toDp()),
                verticalAlignment = node.modifier.alignment?.toVerticalAlignment() ?: Alignment.Top
            ) {
                node.children.forEach { child ->
                    key(child.id) {
                        var childModifier: Modifier = Modifier
                        child.modifier.weight?.let { weight ->
                            if (weight > 0f) {
                                childModifier = childModifier.weight(weight)
                            }
                        }
                        child.modifier.alignment?.let { align ->
                            childModifier = childModifier.align(align.toVerticalAlignment())
                        }
                        SafeUiNodeRenderer(node = child, onEvent = onEvent, modifier = childModifier)
                    }
                }
            }
        }

        is BoxNode -> {
            Box(
                modifier = appliedModifier,
                contentAlignment = node.modifier.alignment?.toBoxAlignment() ?: Alignment.TopStart
            ) {
                node.children.forEach { child ->
                    key(child.id) {
                        var childModifier: Modifier = Modifier
                        child.modifier.alignment?.let { align ->
                            childModifier = childModifier.align(align.toBoxAlignment())
                        }
                        SafeUiNodeRenderer(node = child, onEvent = onEvent, modifier = childModifier)
                    }
                }
            }
        }

        is CardNode -> {
            Card(
                modifier = appliedModifier,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(ToolkitTheme.dimensions.expressiveCardCornerRadius)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ToolkitTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
                ) {
                    node.title?.let { titleText ->
                        Text(
                            text = titleText,
                            style = ToolkitTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    RenderColumnChildren(children = node.children, onEvent = onEvent, allowWeight = false)
                }
            }
        }

        is ScrollableColumnNode -> {
            val inScroll = LocalInVerticalScroll.current
            val scrollModifier = if (inScroll) {
                appliedModifier
            } else {
                appliedModifier.verticalScroll(rememberScrollState())
            }
            CompositionLocalProvider(LocalInVerticalScroll provides true) {
                Column(
                    modifier = scrollModifier,
                    verticalArrangement = Arrangement.spacedBy(node.spacing.toDp()),
                    horizontalAlignment = node.modifier.alignment?.toHorizontalAlignment() ?: Alignment.Start
                ) {
                    RenderColumnChildren(children = node.children, onEvent = onEvent, allowWeight = false)
                }
            }
        }

        is ScaffoldNode -> {
            RenderScaffold(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is FloatingPanelNode -> {
            RenderFloatingPanel(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is SidebarNode -> {
            RenderSidebar(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is TextNode -> {
            Text(
                text = node.text,
                style = node.role.toTextStyle(),
                color = node.color?.toComposeColor() ?: MaterialTheme.colorScheme.onSurface,
                modifier = appliedModifier
            )
        }

        is ButtonNode -> {
            if (node.isPrimary) {
                Button(
                    onClick = { onEvent(UiEvent.Click(node.id, node.action)) },
                    enabled = node.modifier.enabled,
                    modifier = appliedModifier,
                    colors = if (node.isDestructive) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(text = node.label, style = ToolkitTheme.typography.labelMedium)
                }
            } else {
                OutlinedButton(
                    onClick = { onEvent(UiEvent.Click(node.id, node.action)) },
                    enabled = node.modifier.enabled,
                    modifier = appliedModifier,
                    colors = if (node.isDestructive) {
                        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    }
                ) {
                    Text(text = node.label, style = ToolkitTheme.typography.labelMedium)
                }
            }
        }

        is TextInputNode -> {
            RenderTextInput(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is ToggleNode -> {
            Row(
                modifier = appliedModifier,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = node.checked,
                    enabled = node.modifier.enabled,
                    onCheckedChange = { isChecked ->
                        onEvent(UiEvent.ToggleChanged(node.id, isChecked))
                    }
                )
                node.label?.let { labelText ->
                    Text(text = labelText, style = ToolkitTheme.typography.bodyMedium)
                }
            }
        }

        is SliderNode -> {
            Column(
                modifier = appliedModifier,
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
            ) {
                node.label?.let { labelText ->
                    Text(text = labelText, style = ToolkitTheme.typography.bodySmall)
                }
                Slider(
                    value = node.value.toFloat(),
                    enabled = node.modifier.enabled,
                    onValueChange = { newValue ->
                        onEvent(UiEvent.ValueChanged(node.id, newValue.toDouble()))
                    },
                    valueRange = node.min.toFloat()..node.max.toFloat(),
                    steps = node.steps
                )
            }
        }

        is DropdownNode -> {
            RenderDropdown(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is ImageNode -> {
            PluginAssetImage(
                asset = node.asset,
                contentDescription = node.contentDescription,
                modifier = appliedModifier
            )
        }

        is DividerNode -> {
            HorizontalDivider(
                modifier = appliedModifier,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        is BoardNode -> {
            BoardNodeRenderer(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is ErrorBoundaryNode -> {
            RenderErrorBoundaryNode(node = node, modifier = appliedModifier)
        }

        is UnknownNode -> {
            Surface(
                modifier = appliedModifier,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(ToolkitTheme.dimensions.buttonGroupInnerCorner)
            ) {
                Text(
                    text = stringResource(Res.string.sdui_unsupported_node, node.rawType),
                    style = ToolkitTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(ToolkitTheme.spacing.xs)
                )
            }
        }

        is BadgeNode -> {
            Surface(
                modifier = appliedModifier,
                color = node.color.toComposeColor().copy(alpha = ToolkitTheme.opacity.borderLow),
                shape = RoundedCornerShape(ToolkitTheme.dimensions.buttonGroupInnerCorner)
            ) {
                Text(
                    text = node.text,
                    style = ToolkitTheme.typography.labelSmall,
                    color = node.color.toComposeColor(),
                    modifier = Modifier.padding(
                        horizontal = ToolkitTheme.spacing.xs,
                        vertical = ToolkitTheme.spacing.xxs
                    )
                )
            }
        }

        is IconNode -> {
            val iconSize = when (node.size) {
                ControlSize.SMALL -> ToolkitTheme.dimensions.iconSmall
                ControlSize.MEDIUM -> ToolkitTheme.dimensions.iconMedium
                ControlSize.LARGE -> ToolkitTheme.dimensions.iconLarge
            }
            val tintColor = node.tint?.toComposeColor() ?: LocalContentColor.current
            Icon(
                imageVector = SduiIconRegistry.resolve(node.name),
                contentDescription = node.name,
                tint = tintColor,
                modifier = appliedModifier.size(iconSize)
            )
        }

        is IconButtonNode -> {
            RenderIconButton(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is ProgressBarNode -> {
            RenderProgressBar(node = node, modifier = appliedModifier)
        }

        is TabsNode -> {
            RenderTabs(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is ListItemNode -> {
            RenderListItem(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is CheckboxNode -> {
            RenderCheckbox(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is ChipGroupNode -> {
            RenderChipGroup(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is AlertBannerNode -> {
            RenderAlertBanner(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is EmptyStateNode -> {
            RenderEmptyState(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is DialogNode -> {
            RenderDialog(node = node, onEvent = onEvent)
        }

        is SplitPaneNode -> {
            RenderSplitPane(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is SpacerNode -> {
            RenderSpacer(node = node, modifier = appliedModifier)
        }

        is ScrollableRowNode -> {
            RenderScrollableRow(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        else -> {
            Surface(
                modifier = appliedModifier,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(ToolkitTheme.dimensions.buttonGroupInnerCorner)
            ) {
                Text(
                    text = stringResource(Res.string.sdui_unsupported_node, node::class.simpleName ?: "unknown"),
                    style = ToolkitTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(ToolkitTheme.spacing.xs)
                )
            }
        }
    }
}

/**
 * Text input field implementing the revision-tracking synchronization pattern
 * to prevent cursor jumping and asynchronous typing overwrite races.
 */
@Composable
private fun RenderTextInput(
    node: TextInputNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var localText by remember(node.id) { mutableStateOf(node.text) }
    var lastRevision by remember(node.id) { mutableStateOf(node.revision) }

    // When the plugin explicitly increments revision, apply the new server text
    if (node.revision != lastRevision || node.forceOverwrite) {
        localText = node.text
        lastRevision = node.revision
    }

    OutlinedTextField(
        value = localText,
        onValueChange = { newText ->
            localText = newText
            onEvent(UiEvent.TextChanged(node.id, newText))
        },
        enabled = node.modifier.enabled,
        label = node.label?.let { { Text(it) } },
        placeholder = node.placeholder?.let { { Text(it) } },
        singleLine = node.singleLine,
        modifier = modifier
    )
}

/**
 * Dropdown selector renderer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenderDropdown(
    node: DropdownNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = node.options[node.selectedKey] ?: node.selectedKey

    ExposedDropdownMenuBox(
        expanded = expanded && node.modifier.enabled,
        onExpandedChange = {
            if (node.modifier.enabled) {
                expanded = !expanded
            }
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = node.modifier.enabled,
            label = node.label?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && node.modifier.enabled) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, node.modifier.enabled)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded && node.modifier.enabled,
            onDismissRequest = { expanded = false }
        ) {
            node.options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = ToolkitTheme.typography.bodyMedium) },
                    onClick = {
                        expanded = false
                        onEvent(UiEvent.OptionSelected(node.id, key))
                    }
                )
            }
        }
    }
}

/**
 * Live capability and system progress indicator renderer.
 */
@Composable
private fun RenderProgressBar(
    node: ProgressBarNode,
    modifier: Modifier = Modifier
) {
    val progressColor = node.color.toComposeColor()
    val label = node.label
    val statusText = node.statusText
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
    ) {
        if (!label.isNullOrBlank() || !statusText.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!label.isNullOrBlank()) {
                    Text(
                        text = label,
                        style = ToolkitTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!statusText.isNullOrBlank()) {
                    Text(
                        text = statusText,
                        style = ToolkitTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (node.isCircular) {
            val progressVal = node.progress
            if (progressVal != null) {
                CircularProgressIndicator(
                    progress = { progressVal.coerceIn(0f, 1f) },
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = ToolkitTheme.opacity.borderLow),
                    strokeWidth = ToolkitTheme.dimensions.progressIndicatorStroke
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium),
                    color = progressColor,
                    strokeWidth = ToolkitTheme.dimensions.progressIndicatorStroke
                )
            }
        } else {
            val progressVal = node.progress
            if (progressVal != null) {
                LinearProgressIndicator(
                    progress = { progressVal.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(ToolkitTheme.dimensions.capabilityProgressBarHeight),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = ToolkitTheme.opacity.borderLow)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(ToolkitTheme.dimensions.capabilityProgressBarHeight),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = ToolkitTheme.opacity.borderLow)
                )
            }
            node.secondaryProgress?.let { secProgress ->
                LinearProgressIndicator(
                    progress = { secProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(ToolkitTheme.dimensions.progressIndicatorStroke),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = ToolkitTheme.opacity.subtleHighlight)
                )
            }
        }
    }
}

/**
 * Tab container renderer supporting icons, badges, and view switching.
 */
@Composable
private fun RenderTabs(
    node: TabsNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = node.tabs.indexOfFirst { it.key == node.selectedTabKey }.coerceAtLeast(0)
    BoxWithConstraints(modifier = modifier) {
        val hasBoundedHeight = constraints.hasBoundedHeight
        Column(
            modifier = if (hasBoundedHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = ToolkitTheme.spacing.none
            ) {
                node.tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onEvent(UiEvent.TabSelected(node.id, tab.key)) },
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                tab.iconName?.let { iconName ->
                                    Icon(
                                        imageVector = SduiIconRegistry.resolve(iconName),
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                    )
                                }
                                Text(text = tab.label, style = ToolkitTheme.typography.labelMedium)
                                tab.badgeText?.let { badge ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(ToolkitTheme.dimensions.buttonGroupInnerCorner)
                                    ) {
                                        Text(
                                            text = badge,
                                            style = ToolkitTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(
                                                horizontal = ToolkitTheme.spacing.xs,
                                                vertical = ToolkitTheme.spacing.xxs
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
            val contentModifier = if (hasBoundedHeight) {
                Modifier.weight(1f).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }
            SafeUiNodeRenderer(node = node.content, onEvent = onEvent, modifier = contentModifier)
        }
    }
}

/**
 * Structured list row renderer with leading controls/icons, titles, subtitles, badges, and trailing actions.
 */
@Composable
private fun RenderListItem(
    node: ListItemNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceColor = if (node.selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = ToolkitTheme.opacity.buttonBackground)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = if (node.selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (node.selectable) {
                    Modifier.clickable { onEvent(UiEvent.ToggleChanged(node.id, !node.selected)) }
                } else {
                    Modifier
                }
            ),
        color = surfaceColor,
        shape = ToolkitTheme.shapes.small,
        border = BorderStroke(ToolkitTheme.dimensions.borderUnselected, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ToolkitTheme.spacing.medium,
                    vertical = ToolkitTheme.spacing.small
                ),
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.selectable) {
                Checkbox(
                    checked = node.selected,
                    onCheckedChange = { isChecked ->
                        onEvent(UiEvent.ToggleChanged(node.id, isChecked))
                    }
                )
            }

            node.leadingIcon?.let { iconName ->
                Icon(
                    imageVector = SduiIconRegistry.resolve(iconName),
                    contentDescription = node.title,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = node.title,
                        style = ToolkitTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    node.badgeText?.let { badge ->
                        val bColor = node.badgeColor?.toComposeColor() ?: MaterialTheme.colorScheme.primary
                        Surface(
                            color = bColor.copy(alpha = ToolkitTheme.opacity.borderLow),
                            shape = RoundedCornerShape(ToolkitTheme.dimensions.buttonGroupInnerCorner)
                        ) {
                            Text(
                                text = badge,
                                style = ToolkitTheme.typography.labelSmall,
                                color = bColor,
                                modifier = Modifier.padding(
                                    horizontal = ToolkitTheme.spacing.xs,
                                    vertical = ToolkitTheme.spacing.xxs
                                )
                            )
                        }
                    }
                }
                node.subtitle?.let { sub ->
                    Text(
                        text = sub,
                        style = ToolkitTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (node.trailingContent.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    node.trailingContent.forEach { child ->
                        SafeUiNodeRenderer(node = child, onEvent = onEvent)
                    }
                }
            }
        }
    }
}

/**
 * Checkbox control with accompanying label.
 */
@Composable
private fun RenderCheckbox(
    node: CheckboxNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable(enabled = node.modifier.enabled) {
            onEvent(UiEvent.ToggleChanged(node.id, !node.checked))
        },
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = node.checked,
            enabled = node.modifier.enabled,
            onCheckedChange = { isChecked ->
                onEvent(UiEvent.ToggleChanged(node.id, isChecked))
            }
        )
        node.label?.let { labelText ->
            Text(text = labelText, style = ToolkitTheme.typography.bodyMedium)
        }
    }
}

/**
 * Chip group filter selector renderer.
 */
@Composable
private fun RenderChipGroup(
    node: ChipGroupNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        node.chips.forEach { chip ->
            val isSelected = chip.key in node.selectedKeys
            FilterChip(
                selected = isSelected,
                onClick = {
                    onEvent(UiEvent.ChipSelected(node.id, chip.key, !isSelected))
                },
                label = { Text(text = chip.label, style = ToolkitTheme.typography.labelMedium) },
                leadingIcon = chip.iconName?.let { iconName ->
                    {
                        Icon(
                            imageVector = SduiIconRegistry.resolve(iconName),
                            contentDescription = chip.label,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                    }
                }
            )
        }
    }
}

/**
 * Compact icon action button.
 */
@Composable
private fun RenderIconButton(
    node: IconButtonNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val action = node.action ?: node.id
    val tint = node.tint?.toComposeColor() ?: MaterialTheme.colorScheme.onSurface
    IconButton(
        onClick = { onEvent(UiEvent.Click(node.id, action)) },
        enabled = node.modifier.enabled,
        modifier = modifier,
        colors = if (node.isTonal) {
            IconButtonDefaults.filledTonalIconButtonColors()
        } else {
            IconButtonDefaults.iconButtonColors()
        }
    ) {
        Icon(
            imageVector = SduiIconRegistry.resolve(node.iconName),
            contentDescription = node.tooltip ?: node.iconName,
            tint = tint,
            modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
        )
    }
}

/**
 * Semantic inline alert banner for warnings, info, and success messages.
 */
@Composable
private fun RenderAlertBanner(
    node: AlertBannerNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val severityColor = node.severity.toComposeColor()
    val defaultIcon = when (node.severity) {
        SemanticColor.ERROR -> Icons.Default.Error
        SemanticColor.WARNING -> Icons.Default.Warning
        SemanticColor.SUCCESS -> Icons.Default.CheckCircle
        else -> Icons.Default.Info
    }
    val iconVector = node.iconName?.let { SduiIconRegistry.resolve(it) } ?: defaultIcon

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = severityColor.copy(alpha = ToolkitTheme.opacity.cardBackground),
        shape = ToolkitTheme.shapes.small,
        border = BorderStroke(ToolkitTheme.dimensions.borderUnselected, severityColor.copy(alpha = ToolkitTheme.opacity.divider))
    ) {
        Row(
            modifier = Modifier.padding(ToolkitTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = node.severity.name,
                tint = severityColor,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
            ) {
                val title = node.title
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = ToolkitTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = node.message,
                    style = ToolkitTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val actionLabel = node.actionLabel
            if (!actionLabel.isNullOrBlank()) {
                OutlinedButton(
                    onClick = { onEvent(UiEvent.Click(node.id, "banner_action")) },
                    modifier = Modifier.padding(start = ToolkitTheme.spacing.xs)
                ) {
                    Text(text = actionLabel, style = ToolkitTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Empty state placeholder when data or queues are empty.
 */
@Composable
private fun RenderEmptyState(
    node: EmptyStateNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ToolkitTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
    ) {
        Icon(
            imageVector = SduiIconRegistry.resolve(node.iconName),
            contentDescription = node.title,
            modifier = Modifier.size(ToolkitTheme.dimensions.emptyStateIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.disabled)
        )
        Text(
            text = node.title,
            style = ToolkitTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        node.description?.let { desc ->
            Text(
                text = desc,
                style = ToolkitTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        node.actionLabel?.let { actionLabel ->
            Button(
                onClick = { onEvent(UiEvent.Click(node.id, "empty_state_action")) },
                modifier = Modifier.padding(top = ToolkitTheme.spacing.small)
            ) {
                Text(text = actionLabel, style = ToolkitTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Modal dialog renderer for confirmations and forms.
 */
@Composable
private fun RenderDialog(
    node: DialogNode,
    onEvent: (UiEvent) -> Unit
) {
    if (!node.isOpen) return

    AlertDialog(
        onDismissRequest = { onEvent(UiEvent.DialogAction(node.id, isConfirmed = false)) },
        title = {
            Text(text = node.title, style = ToolkitTheme.typography.titleLarge)
        },
        text = {
            CompositionLocalProvider(LocalInVerticalScroll provides true) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    RenderColumnChildren(children = node.children, onEvent = onEvent, allowWeight = false)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(UiEvent.DialogAction(node.id, isConfirmed = true)) },
                colors = if (node.isDestructive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(text = node.confirmLabel, style = ToolkitTheme.typography.labelMedium)
            }
        },
        dismissButton = node.dismissLabel?.let { label ->
            {
                OutlinedButton(
                    onClick = { onEvent(UiEvent.DialogAction(node.id, isConfirmed = false)) }
                ) {
                    Text(text = label, style = ToolkitTheme.typography.labelMedium)
                }
            }
        }
    )
}

/**
 * Desktop master-detail layout split pane with weighted containers.
 */
@Composable
private fun RenderSplitPane(
    node: SplitPaneNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val fraction1 = node.splitFraction.coerceIn(0.05f, 0.95f)
    val fraction2 = (1f - fraction1).coerceIn(0.05f, 0.95f)

    if (node.isHorizontal) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(fraction1)
                    .fillMaxHeight()
            ) {
                SafeUiNodeRenderer(node = node.first, onEvent = onEvent, modifier = Modifier.fillMaxSize())
            }
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Box(
                modifier = Modifier
                    .weight(fraction2)
                    .fillMaxHeight()
            ) {
                SafeUiNodeRenderer(node = node.second, onEvent = onEvent, modifier = Modifier.fillMaxSize())
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(fraction1)
                    .fillMaxWidth()
            ) {
                SafeUiNodeRenderer(node = node.first, onEvent = onEvent, modifier = Modifier.fillMaxSize())
            }
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Box(
                modifier = Modifier
                    .weight(fraction2)
                    .fillMaxWidth()
            ) {
                SafeUiNodeRenderer(node = node.second, onEvent = onEvent, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Structural spacing element.
 */
@Composable
private fun RenderSpacer(
    node: SpacerNode,
    modifier: Modifier = Modifier
) {
    Spacer(modifier = modifier.size(node.size.toDp()))
}

/**
 * Horizontally scrollable row for image strips, horizontal chips, or cards.
 */
@Composable
private fun RenderScrollableRow(
    node: ScrollableRowNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(node.spacing.toDp()),
        verticalAlignment = node.modifier.alignment?.toVerticalAlignment() ?: Alignment.Top
    ) {
        node.children.forEach { child ->
            key(child.id) {
                SafeUiNodeRenderer(node = child, onEvent = onEvent)
            }
        }
    }
}

/**
 * Visual error boundary component.
 */
@Composable
private fun RenderErrorBoundaryNode(
    node: ErrorBoundaryNode,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ToolkitTheme.dimensions.buttonGroupInnerCorner)),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(ToolkitTheme.dimensions.buttonGroupInnerCorner)
    ) {
        Row(
            modifier = Modifier.padding(ToolkitTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(Res.string.sdui_error_boundary_title),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
            )
            Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)) {
                Text(
                    text = stringResource(Res.string.sdui_error_boundary_title),
                    style = ToolkitTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = node.errorMessage,
                    style = ToolkitTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * Map [SpacingSize] to theme Dp.
 */
@Composable
fun SpacingSize.toDp(): Dp = when (this) {
    SpacingSize.NONE -> ToolkitTheme.spacing.none
    SpacingSize.EXTRA_SMALL -> ToolkitTheme.spacing.xxs
    SpacingSize.SMALL -> ToolkitTheme.spacing.xs
    SpacingSize.MEDIUM_SMALL -> ToolkitTheme.spacing.sm
    SpacingSize.MEDIUM -> ToolkitTheme.spacing.sm
    SpacingSize.LARGE -> ToolkitTheme.spacing.md
    SpacingSize.EXTRA_LARGE -> ToolkitTheme.spacing.lg
}

/**
 * Map [TypographyRole] to theme TextStyle.
 */
@Composable
fun TypographyRole.toTextStyle(): TextStyle = when (this) {
    TypographyRole.DISPLAY -> ToolkitTheme.typography.displayLarge
    TypographyRole.HEADLINE -> ToolkitTheme.typography.headlineMedium
    TypographyRole.TITLE_LARGE -> ToolkitTheme.typography.titleLarge
    TypographyRole.TITLE_MEDIUM -> ToolkitTheme.typography.titleMedium
    TypographyRole.BODY_LARGE -> ToolkitTheme.typography.bodyLarge
    TypographyRole.BODY_MEDIUM -> ToolkitTheme.typography.bodyMedium
    TypographyRole.BODY_SMALL -> ToolkitTheme.typography.bodySmall
    TypographyRole.LABEL -> ToolkitTheme.typography.labelMedium
}

/**
 * Map [AlignmentRole] to Horizontal alignment.
 */
fun AlignmentRole.toHorizontalAlignment(): Alignment.Horizontal = when (this) {
    AlignmentRole.START, AlignmentRole.TOP_START, AlignmentRole.BOTTOM_START, AlignmentRole.CENTER_START -> Alignment.Start
    AlignmentRole.CENTER, AlignmentRole.TOP_CENTER, AlignmentRole.BOTTOM_CENTER -> Alignment.CenterHorizontally
    AlignmentRole.END, AlignmentRole.TOP_END, AlignmentRole.BOTTOM_END, AlignmentRole.CENTER_END -> Alignment.End
    else -> Alignment.Start
}

/**
 * Map [AlignmentRole] to Vertical alignment.
 */
fun AlignmentRole.toVerticalAlignment(): Alignment.Vertical = when (this) {
    AlignmentRole.TOP, AlignmentRole.TOP_START, AlignmentRole.TOP_CENTER, AlignmentRole.TOP_END -> Alignment.Top
    AlignmentRole.CENTER, AlignmentRole.START, AlignmentRole.END, AlignmentRole.CENTER_START, AlignmentRole.CENTER_END -> Alignment.CenterVertically
    AlignmentRole.BOTTOM, AlignmentRole.BOTTOM_START, AlignmentRole.BOTTOM_CENTER, AlignmentRole.BOTTOM_END -> Alignment.Bottom
}

/**
 * Map [AlignmentRole] to Box alignment.
 */
fun AlignmentRole.toBoxAlignment(): Alignment = when (this) {
    AlignmentRole.START, AlignmentRole.TOP_START -> Alignment.TopStart
    AlignmentRole.TOP, AlignmentRole.TOP_CENTER -> Alignment.TopCenter
    AlignmentRole.TOP_END -> Alignment.TopEnd
    AlignmentRole.CENTER_START -> Alignment.CenterStart
    AlignmentRole.CENTER -> Alignment.Center
    AlignmentRole.CENTER_END -> Alignment.CenterEnd
    AlignmentRole.BOTTOM_START -> Alignment.BottomStart
    AlignmentRole.BOTTOM, AlignmentRole.BOTTOM_CENTER -> Alignment.BottomCenter
    AlignmentRole.END, AlignmentRole.BOTTOM_END -> Alignment.BottomEnd
}

/**
 * Map [FloatingAnchor] to Compose Box alignment.
 */
fun FloatingAnchor.toAlignment(): Alignment = when (this) {
    FloatingAnchor.TOP_START -> Alignment.TopStart
    FloatingAnchor.TOP_CENTER -> Alignment.TopCenter
    FloatingAnchor.TOP_END -> Alignment.TopEnd
    FloatingAnchor.CENTER_START -> Alignment.CenterStart
    FloatingAnchor.CENTER -> Alignment.Center
    FloatingAnchor.CENTER_END -> Alignment.CenterEnd
    FloatingAnchor.BOTTOM_START -> Alignment.BottomStart
    FloatingAnchor.BOTTOM_CENTER -> Alignment.BottomCenter
    FloatingAnchor.BOTTOM_END -> Alignment.BottomEnd
}

@Composable
private fun RenderScaffold(
    node: ScaffoldNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        Column(modifier = Modifier.fillMaxSize()) {
            node.topBar?.let { top ->
                SafeUiNodeRenderer(node = top, onEvent = onEvent, modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                node.leftSidebar?.let { left ->
                    SafeUiNodeRenderer(node = left, onEvent = onEvent, modifier = Modifier.fillMaxHeight())
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                    SafeUiNodeRenderer(node = node.content, onEvent = onEvent, modifier = Modifier.fillMaxSize())
                }
                node.rightSidebar?.let { right ->
                    SafeUiNodeRenderer(node = right, onEvent = onEvent, modifier = Modifier.fillMaxHeight())
                }
            }
            node.bottomBar?.let { bottom ->
                SafeUiNodeRenderer(node = bottom, onEvent = onEvent, modifier = Modifier.fillMaxWidth())
            }
        }

        // Floating overlay panels positioned according to their anchors
        node.floatingPanels.forEach { panel ->
            key(panel.id) {
                SafeUiNodeRenderer(
                    node = panel,
                    onEvent = onEvent,
                    modifier = Modifier.align(panel.anchor.toAlignment())
                )
            }
        }
    }
}

@Composable
private fun RenderFloatingPanel(
    node: FloatingPanelNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var isCollapsed by remember(node.id, node.isCollapsed) { mutableStateOf(node.isCollapsed) }

    Surface(
        modifier = modifier.padding(ToolkitTheme.spacing.medium),
        shape = RoundedCornerShape(ToolkitTheme.dimensions.expressiveCardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = ToolkitTheme.opacity.almostOpaque),
        shadowElevation = ToolkitTheme.dimensions.elevationMedium,
        border = BorderStroke(ToolkitTheme.dimensions.borderThin, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(ToolkitTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            val showHeader = node.title != null || node.isCollapsible
            if (showHeader) {
                Row(
                    modifier = Modifier
                        .then(if (node.isCollapsible) Modifier.clip(ToolkitTheme.shapes.small).clickable { isCollapsed = !isCollapsed } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    node.title?.let { titleText ->
                        Text(
                            text = titleText,
                            style = ToolkitTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                        )
                    }
                    if (node.isCollapsible) {
                        IconButton(
                            onClick = { isCollapsed = !isCollapsed },
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                        ) {
                            Icon(
                                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(Res.string.sdui_toggle_panel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    node.children.forEach { child ->
                        key(child.id) {
                            SafeUiNodeRenderer(node = child, onEvent = onEvent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderSidebar(
    node: SidebarNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var isCollapsed by remember(node.id, node.isCollapsed) { mutableStateOf(node.isCollapsed) }
    val isHorizontal = node.attachPoint == SidebarAttachPoint.LEFT || node.attachPoint == SidebarAttachPoint.RIGHT
    val delegatesScrolling = remember(node.children) { node.children.any { it.containsScrollableOrWeightedContent() } }
    val applySidebarScroll = isHorizontal && !delegatesScrolling && !LocalInVerticalScroll.current

    Surface(
        modifier = if (isHorizontal) {
            modifier.width(if (isCollapsed) ToolkitTheme.dimensions.sidebarCollapsedWidth else ToolkitTheme.dimensions.sidebarExpandedWidth).fillMaxHeight()
        } else {
            modifier.fillMaxWidth()
        },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(ToolkitTheme.dimensions.borderThin, MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider))
    ) {
        Column(
            modifier = (if (isHorizontal) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(ToolkitTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!isCollapsed) {
                    node.title?.let { titleText ->
                        Text(
                            text = titleText,
                            style = ToolkitTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (node.isCollapsible) {
                    IconButton(
                        onClick = { isCollapsed = !isCollapsed },
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                    ) {
                        Icon(
                            imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(Res.string.sdui_toggle_sidebar),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !isCollapsed,
                modifier = if (isHorizontal) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()
            ) {
                val bodyModifier = (if (isHorizontal) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .then(if (applySidebarScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                CompositionLocalProvider(LocalInVerticalScroll provides (LocalInVerticalScroll.current || applySidebarScroll)) {
                    Column(
                        modifier = bodyModifier,
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                    ) {
                        RenderColumnChildren(
                            children = node.children,
                            onEvent = onEvent,
                            allowWeight = !applySidebarScroll
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RenderColumnChildren(
    children: List<UiNode>,
    onEvent: (UiEvent) -> Unit,
    allowWeight: Boolean
) {
    children.forEach { child ->
        key(child.id) {
            var childModifier: Modifier = Modifier
            if (allowWeight) {
                child.modifier.weight?.let { weight ->
                    if (weight > 0f) {
                        childModifier = childModifier.weight(weight)
                    }
                }
            }
            child.modifier.alignment?.let { align ->
                childModifier = childModifier.align(align.toHorizontalAlignment())
            }
            SafeUiNodeRenderer(node = child, onEvent = onEvent, modifier = childModifier)
        }
    }
}

private fun UiNode.containsScrollableOrWeightedContent(): Boolean {
    if ((modifier.weight ?: 0f) > 0f) return true
    return when (this) {
        is ScrollableColumnNode, is SplitPaneNode, is BoardNode -> true
        is ColumnNode -> children.any { it.containsScrollableOrWeightedContent() }
        is BoxNode -> children.any { it.containsScrollableOrWeightedContent() }
        is RowNode -> children.any { it.containsScrollableOrWeightedContent() }
        is TabsNode -> content.containsScrollableOrWeightedContent()
        else -> false
    }
}

/**
 * Apply layout modifiers defined in [UiModifier].
 */
@Composable
fun Modifier.applyUiModifier(uiModifier: UiModifier): Modifier {
    var mod = this
    uiModifier.fillMaxWidth?.let { factor ->
        mod = mod.fillMaxWidth(factor)
    }
    uiModifier.fillMaxHeight?.let { factor ->
        mod = mod.fillMaxHeight(factor)
    }
    uiModifier.widthDp?.let { width ->
        mod = mod.width(width.dp)
    }
    uiModifier.heightDp?.let { height ->
        mod = mod.height(height.dp)
    }
    val minW = uiModifier.minWidthDp?.dp ?: Dp.Unspecified
    val maxW = uiModifier.maxWidthDp?.dp ?: Dp.Unspecified
    if (minW != Dp.Unspecified || maxW != Dp.Unspecified) {
        mod = mod.widthIn(min = minW, max = maxW)
    }
    val minH = uiModifier.minHeightDp?.dp ?: Dp.Unspecified
    val maxH = uiModifier.maxHeightDp?.dp ?: Dp.Unspecified
    if (minH != Dp.Unspecified || maxH != Dp.Unspecified) {
        mod = mod.heightIn(min = minH, max = maxH)
    }
    uiModifier.aspectRatio?.let { ratio ->
        mod = mod.aspectRatio(ratio)
    }
    val cornerShape = if (uiModifier.clipRounded) {
        ToolkitTheme.shapes.medium
    } else {
        RoundedCornerShape(ToolkitTheme.spacing.none)
    }
    if (uiModifier.clipRounded) {
        mod = mod.clip(cornerShape)
    }
    uiModifier.backgroundColor?.let { bgColor ->
        mod = mod.background(bgColor.toComposeColor(), cornerShape)
    }
    uiModifier.borderColor?.let { bColor ->
        mod = mod.border(ToolkitTheme.dimensions.borderUnselected, bColor.toComposeColor(), cornerShape)
    }
    if (uiModifier.padding != SpacingSize.NONE) {
        mod = mod.padding(uiModifier.padding.toDp())
    }
    return mod
}
