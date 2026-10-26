package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.AlignmentRole
import org.wip.plugintoolkit.api.sdui.BoardNode
import org.wip.plugintoolkit.api.sdui.BoxNode
import org.wip.plugintoolkit.api.sdui.ButtonNode
import org.wip.plugintoolkit.api.sdui.CardNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.DividerNode
import org.wip.plugintoolkit.api.sdui.DropdownNode
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.FloatingAnchor
import org.wip.plugintoolkit.api.sdui.FloatingPanelNode
import org.wip.plugintoolkit.api.sdui.ImageNode
import org.wip.plugintoolkit.api.sdui.RowNode
import org.wip.plugintoolkit.api.sdui.ScaffoldNode
import org.wip.plugintoolkit.api.sdui.ScrollableColumnNode
import org.wip.plugintoolkit.api.sdui.SidebarAttachPoint
import org.wip.plugintoolkit.api.sdui.SidebarNode
import org.wip.plugintoolkit.api.sdui.SliderNode
import org.wip.plugintoolkit.api.sdui.SpacingSize
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
import plugintoolkit.composeapp.generated.resources.sdui_error_boundary_title
import plugintoolkit.composeapp.generated.resources.sdui_unsupported_node

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
    val appliedModifier = modifier.applyUiModifier(node.modifier)

    when (node) {
        is ColumnNode -> {
            Column(
                modifier = appliedModifier,
                verticalArrangement = Arrangement.spacedBy(node.spacing.toDp()),
                horizontalAlignment = node.modifier.alignment?.toHorizontalAlignment() ?: Alignment.Start
            ) {
                node.children.forEach { child ->
                    key(child.id) {
                        SafeUiNodeRenderer(node = child, onEvent = onEvent)
                    }
                }
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
                        SafeUiNodeRenderer(node = child, onEvent = onEvent)
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
                        SafeUiNodeRenderer(node = child, onEvent = onEvent)
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
                    node.children.forEach { child ->
                        key(child.id) {
                            SafeUiNodeRenderer(node = child, onEvent = onEvent)
                        }
                    }
                }
            }
        }

        is ScrollableColumnNode -> {
            Column(
                modifier = appliedModifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(node.spacing.toDp())
            ) {
                node.children.forEach { child ->
                    key(child.id) {
                        SafeUiNodeRenderer(node = child, onEvent = onEvent)
                    }
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
                    onClick = { onEvent(UiEvent.Click(node.id)) },
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
                    onClick = { onEvent(UiEvent.Click(node.id)) },
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
            AsyncImage(
                model = node.asset,
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

        is org.wip.plugintoolkit.api.sdui.BadgeNode -> {
            Surface(
                modifier = appliedModifier,
                color = node.color.toComposeColor().copy(alpha = 0.2f),
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

        is org.wip.plugintoolkit.api.sdui.IconNode -> {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = node.name,
                modifier = appliedModifier.size(ToolkitTheme.dimensions.iconMedium)
            )
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
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = node.label?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
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
    SpacingSize.NONE -> 0.dp
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
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            node.topBar?.let { top ->
                SafeUiNodeRenderer(node = top, onEvent = onEvent, modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                node.leftSidebar?.let { left ->
                    SafeUiNodeRenderer(node = left, onEvent = onEvent, modifier = Modifier.fillMaxHeight())
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
    var isCollapsed by remember(node.id) { mutableStateOf(node.isCollapsed) }

    Surface(
        modifier = modifier.padding(ToolkitTheme.spacing.medium),
        shape = RoundedCornerShape(ToolkitTheme.dimensions.expressiveCardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
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
                                contentDescription = "Toggle panel",
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
    var isCollapsed by remember(node.id) { mutableStateOf(node.isCollapsed) }
    val isHorizontal = node.attachPoint == SidebarAttachPoint.LEFT || node.attachPoint == SidebarAttachPoint.RIGHT

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
            modifier = Modifier.padding(ToolkitTheme.spacing.mediumSmall),
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
                            contentDescription = "Toggle sidebar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
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
    if (uiModifier.padding != SpacingSize.NONE) {
        mod = mod.padding(uiModifier.padding.toDp())
    }
    uiModifier.aspectRatio?.let { ratio ->
        mod = mod.aspectRatio(ratio)
    }
    return mod
}
