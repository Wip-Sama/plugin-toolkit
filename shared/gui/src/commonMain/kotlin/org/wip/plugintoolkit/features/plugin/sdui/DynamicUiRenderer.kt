package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.material3.ExposedDropdownMenu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.AccordionNode
import org.wip.plugintoolkit.api.sdui.AlertBannerNode
import org.wip.plugintoolkit.api.sdui.AlignmentRole
import org.wip.plugintoolkit.api.sdui.BadgeNode
import org.wip.plugintoolkit.api.sdui.BoardNode
import org.wip.plugintoolkit.api.sdui.BoxNode
import org.wip.plugintoolkit.api.sdui.BreadcrumbsNode
import org.wip.plugintoolkit.api.sdui.ButtonGroupNode
import org.wip.plugintoolkit.api.sdui.ButtonNode
import org.wip.plugintoolkit.api.sdui.CardNode
import org.wip.plugintoolkit.api.sdui.CheckboxNode
import org.wip.plugintoolkit.api.sdui.ChipGroupNode
import org.wip.plugintoolkit.api.sdui.CodeBlockNode
import org.wip.plugintoolkit.api.sdui.ColorPickerNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.ControlSize
import org.wip.plugintoolkit.api.sdui.DatePickerNode
import org.wip.plugintoolkit.api.sdui.DialogNode
import org.wip.plugintoolkit.api.sdui.DividerNode
import org.wip.plugintoolkit.api.sdui.DropdownNode
import org.wip.plugintoolkit.api.sdui.EmptyStateNode
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.FabMenuNode
import org.wip.plugintoolkit.api.sdui.FabNode
import org.wip.plugintoolkit.api.sdui.FilePickerNode
import org.wip.plugintoolkit.api.sdui.FloatingAnchor
import org.wip.plugintoolkit.api.sdui.FloatingPanelNode
import org.wip.plugintoolkit.api.sdui.FloatingToolbarNode
import org.wip.plugintoolkit.api.sdui.FlowRowNode
import org.wip.plugintoolkit.api.sdui.GridNode
import org.wip.plugintoolkit.api.sdui.IconButtonNode
import org.wip.plugintoolkit.api.sdui.IconNode
import org.wip.plugintoolkit.api.sdui.ImageCompareNode
import org.wip.plugintoolkit.api.sdui.ImageNode
import org.wip.plugintoolkit.api.sdui.ImageScale
import org.wip.plugintoolkit.api.sdui.KeyValueListNode
import org.wip.plugintoolkit.api.sdui.ListItemNode
import org.wip.plugintoolkit.api.sdui.LoadingIndicatorNode
import org.wip.plugintoolkit.api.sdui.MarkdownTextNode
import org.wip.plugintoolkit.api.sdui.MediaPlayerNode
import org.wip.plugintoolkit.api.sdui.MenuNode
import org.wip.plugintoolkit.api.sdui.MultiSelectDropdownNode
import org.wip.plugintoolkit.api.sdui.NumberInputNode
import org.wip.plugintoolkit.api.sdui.PaginationNode
import org.wip.plugintoolkit.api.sdui.ProgressBarNode
import org.wip.plugintoolkit.api.sdui.ProgressBarStyle
import org.wip.plugintoolkit.api.sdui.RadioGroupNode
import org.wip.plugintoolkit.api.sdui.RangeSliderNode
import org.wip.plugintoolkit.api.sdui.RowNode
import org.wip.plugintoolkit.api.sdui.ScaffoldNode
import org.wip.plugintoolkit.api.sdui.ScrollEdge
import org.wip.plugintoolkit.api.sdui.ScrollableColumnNode
import org.wip.plugintoolkit.api.sdui.ScrollableRowNode
import org.wip.plugintoolkit.api.sdui.SearchBarNode
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.ShapeRole
import org.wip.plugintoolkit.api.sdui.SideSheetNode
import org.wip.plugintoolkit.api.sdui.SidebarAttachPoint
import org.wip.plugintoolkit.api.sdui.SidebarNode
import org.wip.plugintoolkit.api.sdui.SliderNode
import org.wip.plugintoolkit.api.sdui.SpacerNode
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.SplitButtonNode
import org.wip.plugintoolkit.api.sdui.SplitPaneNode
import org.wip.plugintoolkit.api.sdui.StepperNode
import org.wip.plugintoolkit.api.sdui.TableNode
import org.wip.plugintoolkit.api.sdui.TabsNode
import org.wip.plugintoolkit.api.sdui.TextInputNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.TimePickerNode
import org.wip.plugintoolkit.api.sdui.ToggleNode
import org.wip.plugintoolkit.api.sdui.TreeViewNode
import org.wip.plugintoolkit.api.sdui.TypographyRole
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNode
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.ui.graphics.Color
import org.wip.plugintoolkit.api.sdui.UnknownNode
import org.wip.plugintoolkit.shared.components.ToolkitEmptyState
import org.wip.plugintoolkit.shared.components.ToolkitStatusBadge
import org.wip.plugintoolkit.shared.components.sidebar.SequelSidebarMode
import org.wip.plugintoolkit.shared.components.sidebar.ToolkitFloatingSidebar
import org.wip.plugintoolkit.shared.components.sidebar.ToolkitSequelSidebarToggleButton
import org.wip.plugintoolkit.shared.components.sidebar.resolveSequelExpanded
import org.wip.plugintoolkit.shared.components.sidebar.toggleSequelSidebarMode
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformContextMenuArea
import org.wip.plugintoolkit.core.utils.PlatformContextMenuItem
import org.wip.plugintoolkit.core.utils.PlatformHorizontalScrollbar
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import org.wip.plugintoolkit.shared.components.ToolkitCircularProgressIndicator
import org.wip.plugintoolkit.shared.components.ToolkitLinearProgressIndicator
import org.wip.plugintoolkit.shared.components.ToolkitLoadingIndicator
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_cancel
import plugintoolkit.composeapp.generated.resources.sdui_confirm
import plugintoolkit.composeapp.generated.resources.sdui_error_boundary_title
import plugintoolkit.composeapp.generated.resources.sdui_hide_secret
import plugintoolkit.composeapp.generated.resources.sdui_image_zoom_open
import plugintoolkit.composeapp.generated.resources.sdui_loading_default
import plugintoolkit.composeapp.generated.resources.sdui_resize_pane
import plugintoolkit.composeapp.generated.resources.sdui_show_secret
import plugintoolkit.composeapp.generated.resources.sdui_toggle_panel
import plugintoolkit.composeapp.generated.resources.sdui_toggle_sidebar
import plugintoolkit.composeapp.generated.resources.sdui_unsupported_node

/**
 * Tracks whether the current composition subtree is already inside a vertical scroll container.
 */
internal val LocalInVerticalScroll = compositionLocalOf { false }

/**
 * Tracks whether SDUI root CompositionLocals (desktop touch target density) have been applied.
 */
private val LocalInSduiRoot = compositionLocalOf { false }

/**
 * Tracks compact spatial board containers where plugin-specified micro spacing must be preserved.
 */
internal val LocalInCompactSduiSurface = compositionLocalOf { false }

/**
 * Tracks dense action toolbars / trailing strips where icon buttons should use compact sizing.
 */
internal val LocalInDenseActionBar = compositionLocalOf { false }

/**
 * Tracks single-line chrome bars (topBar / bottomBar) so title text truncates cleanly.
 */
private val LocalInSingleLineHeader = compositionLocalOf { false }

/**
 * Root boundary renderer that enforces Material 3 desktop interactive density and delegates to [RenderUiNode].
 */
@Composable
fun SafeUiNodeRenderer(
    node: UiNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalInSduiRoot.current) {
        val nextDepth = LocalSduiNodeDepth.current + 1
        CompositionLocalProvider(LocalSduiNodeDepth provides nextDepth) {
            RenderUiNode(node = node, onEvent = onEvent, modifier = modifier)
        }
    } else {
        val textInputFocusTracker = remember { SduiTextInputFocusTracker() }
        val shortcutRegistry = remember { SduiShortcutRegistry() }
        val rootFocusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current

        LaunchedEffect(Unit) {
            runCatching { rootFocusRequester.requestFocus() }
        }

        val wrappedOnEvent: (UiEvent) -> Unit = remember(onEvent, textInputFocusTracker, rootFocusRequester, focusManager) {
            { event ->
                if (event !is UiEvent.TextChanged && textInputFocusTracker.isTyping) {
                    focusManager.clearFocus()
                    runCatching { rootFocusRequester.requestFocus() }
                }
                onEvent(event)
            }
        }

        CompositionLocalProvider(
            LocalInSduiRoot provides true,
            LocalMinimumInteractiveComponentSize provides ToolkitTheme.dimensions.desktopTouchTargetSize,
            LocalSduiTextInputFocusTracker provides textInputFocusTracker,
            LocalSduiShortcutRegistry provides shortcutRegistry,
            LocalSduiNodeDepth provides 0
        ) {
            RenderUiNode(
                node = node,
                onEvent = wrappedOnEvent,
                modifier = modifier
                    .focusRequester(rootFocusRequester)
                    .sduiRootShortcutHost(
                        registry = shortcutRegistry,
                        tracker = textInputFocusTracker,
                        onEvent = wrappedOnEvent
                    )
            )
        }
    }
}

/**
 * Shared Material 3 TooltipBox wrapper for icon controls and chrome toggles.
 */
@Composable
internal fun SduiTooltipBox(
    tooltip: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    ToolkitTooltipBox(tooltip = tooltip, modifier = modifier, content = content)
}

/**
 * Resolves container item spacing while enforcing Material 3 grid minimums unless inside a compact spatial widget.
 */
@Composable
internal fun resolveContainerSpacing(
    requested: SpacingSize,
    children: List<UiNode>,
    isVertical: Boolean
): Dp {
    if (children.size <= 1) return ToolkitTheme.spacing.none
    val requestedDp = requested.toDp()
    if (LocalInCompactSduiSurface.current) return requestedDp

    val isPureInlineText = children.all { it is TextNode || it is BadgeNode }
    val hasBlockChildren = isVertical && children.any {
        it is CardNode ||
            it is TextInputNode ||
            it is DropdownNode ||
            it is RadioGroupNode ||
            it is NumberInputNode ||
            it is SearchBarNode ||
            it is AccordionNode ||
            it is CodeBlockNode ||
            it is TableNode ||
            it is KeyValueListNode ||
            it is MarkdownTextNode ||
            it is MultiSelectDropdownNode ||
            it is RangeSliderNode ||
            it is FlowRowNode ||
            it is TreeViewNode ||
            it is FilePickerNode ||
            it is ColorPickerNode ||
            it is BreadcrumbsNode ||
            it is PaginationNode ||
            it is StepperNode ||
            it is AlertBannerNode ||
            it is SplitPaneNode ||
            it is TabsNode ||
            it is GridNode
    }
    val minimumEnforced = when {
        isPureInlineText -> ToolkitTheme.spacing.xs
        hasBlockChildren -> ToolkitTheme.dimensions.sduiCompactSpacing
        else -> ToolkitTheme.spacing.sm
    }
    return maxOf(requestedDp, minimumEnforced)
}

/**
 * Applies desktop form width normalization when a field does not specify explicit sizing constraints.
 */
@Composable
internal fun Modifier.withFormWidthConstraint(uiModifier: UiModifier): Modifier {
    val hasExplicitWidth = uiModifier.widthDp != null ||
        uiModifier.fillMaxWidth != null ||
        uiModifier.maxWidthDp != null
    return if (hasExplicitWidth) {
        this
    } else {
        widthIn(max = ToolkitTheme.dimensions.sduiFormMaxWidth).fillMaxWidth()
    }
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

    // Sidebars manage their own collapse-aware animated width from node.modifier.widthDp.
    val modifierSpec = if (node is SidebarNode) node.modifier.copy(widthDp = null) else node.modifier
    val appliedModifier = modifier
        .applyUiModifier(modifierSpec)
        .sduiShortcuts(
            nodeId = node.id,
            shortcuts = modifierSpec.shortcuts,
            enabled = modifierSpec.enabled,
            onEvent = onEvent
        )
        .sduiDropTarget(
            nodeId = node.id,
            dropTarget = modifierSpec.dropTarget,
            enabled = modifierSpec.enabled,
            clipRounded = modifierSpec.clipRounded,
            onEvent = onEvent
        )

    val contextMenuEntries = remember(node.id, modifierSpec.contextMenu, onEvent) {
        modifierSpec.contextMenu
            .filter { it.enabled }
            .map { item ->
                PlatformContextMenuItem(label = item.label) {
                    onEvent(UiEvent.Click(node.id, item.action ?: item.key))
                }
            }
    }
    val effectiveTooltip = when {
        node is DialogNode -> null
        node is IconButtonNode && (!node.tooltip.isNullOrBlank() || !modifierSpec.tooltip.isNullOrBlank()) -> null
        else -> modifierSpec.tooltip
    }

    PlatformContextMenuArea(items = contextMenuEntries, enabled = modifierSpec.enabled) {
        SduiTooltipBox(tooltip = effectiveTooltip) {
            when (node) {
        is ColumnNode -> {
            val spacingDp = resolveContainerSpacing(node.spacing, node.children, isVertical = true)
            val horizontalAlignment = node.modifier.alignment?.toHorizontalAlignment() ?: Alignment.Start
            if (node.reorderable) {
                RenderReorderableColumnNode(
                    nodeId = node.id,
                    children = node.children,
                    spacing = spacingDp,
                    horizontalAlignment = horizontalAlignment,
                    scrollable = false,
                    onEvent = onEvent,
                    modifier = appliedModifier
                )
            } else {
                Column(
                    modifier = appliedModifier,
                    verticalArrangement = Arrangement.spacedBy(spacingDp),
                    horizontalAlignment = horizontalAlignment
                ) {
                    RenderColumnChildren(
                        children = node.children,
                        onEvent = onEvent,
                        allowWeight = !LocalInVerticalScroll.current
                    )
                }
            }
        }

        is RowNode -> {
            Row(
                modifier = appliedModifier,
                horizontalArrangement = Arrangement.spacedBy(
                    resolveContainerSpacing(node.spacing, node.children, isVertical = false)
                ),
                verticalAlignment = node.modifier.alignment?.toVerticalAlignment() ?: Alignment.CenterVertically
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

        is FlowRowNode -> {
            FlowRow(
                modifier = appliedModifier,
                horizontalArrangement = Arrangement.spacedBy(node.horizontalSpacing.toDp()),
                verticalArrangement = Arrangement.spacedBy(node.verticalSpacing.toDp()),
                maxItemsInEachRow = node.maxItemsInEachRow ?: Int.MAX_VALUE
            ) {
                node.children.forEach { child ->
                    key(child.id) {
                        SafeUiNodeRenderer(node = child, onEvent = onEvent)
                    }
                }
            }
        }

        is CardNode -> {
            val (containerColor, contentColor) = node.surfaceRole?.toContainerPair()
                ?: (MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurface)
            val cardBorder = if (node.outlined) {
                BorderStroke(
                    ToolkitTheme.dimensions.borderUnselected,
                    MaterialTheme.colorScheme.outlineVariant
                )
            } else {
                null
            }
            Card(
                modifier = appliedModifier,
                colors = CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                border = cardBorder,
                shape = ToolkitTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ToolkitTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.dimensions.sduiCompactSpacing)
                ) {
                    node.title?.let { titleText ->
                        Text(
                            text = titleText,
                            style = ToolkitTheme.typography.titleMedium,
                            color = contentColor
                        )
                    }
                    RenderColumnChildren(children = node.children, onEvent = onEvent, allowWeight = false)
                }
            }
        }

        is ScrollableColumnNode -> {
            RenderScrollableColumnNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is GridNode -> {
            RenderGridNode(node = node, onEvent = onEvent, modifier = appliedModifier)
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
            val singleLineHeader = LocalInSingleLineHeader.current
            val resolvedMaxLines = when {
                singleLineHeader -> 1
                node.maxLines != null -> node.maxLines!!.coerceAtLeast(1)
                else -> Int.MAX_VALUE
            }
            val resolvedOverflow = if (singleLineHeader || node.overflowEllipsis) {
                TextOverflow.Ellipsis
            } else {
                TextOverflow.Clip
            }
            val textColor = node.color?.toComposeColor() ?: MaterialTheme.colorScheme.onSurface
            if (node.selectable) {
                SelectionContainer(modifier = appliedModifier) {
                    Text(
                        text = node.text,
                        style = node.role.toTextStyle(),
                        color = textColor,
                        maxLines = resolvedMaxLines,
                        overflow = resolvedOverflow,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Text)
                    )
                }
            } else {
                Text(
                    text = node.text,
                    style = node.role.toTextStyle(),
                    color = textColor,
                    maxLines = resolvedMaxLines,
                    overflow = resolvedOverflow,
                    modifier = appliedModifier
                )
            }
        }

        is ButtonNode -> {
            val compactSurface = LocalInCompactSduiSurface.current
            val buttonPadding = PaddingValues(
                horizontal = if (compactSurface) ToolkitTheme.dimensions.sduiCompactSpacing else ToolkitTheme.spacing.md,
                vertical = ToolkitTheme.spacing.sm
            )
            val buttonModifier = appliedModifier
                .then(
                    if (compactSurface) Modifier else Modifier.heightIn(min = ToolkitTheme.dimensions.standardButtonHeight)
                )
                .then(if (node.modifier.enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)

            when {
                node.isTonal && !node.isDestructive -> {
                    FilledTonalButton(
                        onClick = { onEvent(UiEvent.Click(node.id, node.action)) },
                        enabled = node.modifier.enabled,
                        shape = ToolkitTheme.shapes.pill,
                        contentPadding = buttonPadding,
                        modifier = buttonModifier
                    ) {
                        RenderButtonLabel(label = node.label, iconName = node.iconName)
                    }
                }

                node.isPrimary -> {
                    Button(
                        onClick = { onEvent(UiEvent.Click(node.id, node.action)) },
                        enabled = node.modifier.enabled,
                        shape = ToolkitTheme.shapes.pill,
                        contentPadding = buttonPadding,
                        modifier = buttonModifier,
                        colors = if (node.isDestructive) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        RenderButtonLabel(label = node.label, iconName = node.iconName)
                    }
                }

                else -> {
                    OutlinedButton(
                        onClick = { onEvent(UiEvent.Click(node.id, node.action)) },
                        enabled = node.modifier.enabled,
                        shape = ToolkitTheme.shapes.pill,
                        contentPadding = buttonPadding,
                        border = BorderStroke(
                            ToolkitTheme.dimensions.borderUnselected,
                            if (node.isDestructive) {
                                MaterialTheme.colorScheme.error.copy(alpha = ToolkitTheme.opacity.borderLow)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = buttonModifier,
                        colors = if (node.isDestructive) {
                            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        RenderButtonLabel(label = node.label, iconName = node.iconName)
                    }
                }
            }
        }

        is ButtonGroupNode -> {
            RenderButtonGroupNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is SplitButtonNode -> {
            RenderSplitButtonNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is TextInputNode -> {
            RenderTextInput(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is ToggleNode -> {
            Row(
                modifier = appliedModifier
                    .then(if (node.modifier.enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier),
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
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
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

        is MultiSelectDropdownNode -> {
            RenderMultiSelectDropdownNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is RangeSliderNode -> {
            RenderRangeSliderNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is RadioGroupNode -> {
            RenderRadioGroup(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is NumberInputNode -> {
            RenderNumberInput(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is SearchBarNode -> {
            RenderSearchBar(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is AccordionNode -> {
            RenderAccordion(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is CodeBlockNode -> {
            RenderCodeBlock(node = node, modifier = appliedModifier)
        }

        is TableNode -> {
            RenderTableNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is KeyValueListNode -> {
            RenderKeyValueListNode(node = node, modifier = appliedModifier)
        }

        is MarkdownTextNode -> {
            RenderMarkdownTextNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is TreeViewNode -> {
            RenderTreeViewNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is FilePickerNode -> {
            RenderFilePickerNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is ColorPickerNode -> {
            RenderColorPickerNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is BreadcrumbsNode -> {
            RenderBreadcrumbsNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is PaginationNode -> {
            RenderPaginationNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is StepperNode -> {
            RenderStepperNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is MenuNode -> {
            RenderMenuNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is FloatingToolbarNode -> {
            RenderFloatingToolbarNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is FabNode -> {
            RenderFabNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is FabMenuNode -> {
            RenderFabMenuNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is SideSheetNode -> {
            RenderSideSheetNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is DatePickerNode -> {
            RenderDatePickerNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is TimePickerNode -> {
            RenderTimePickerNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is ImageCompareNode -> {
            RenderImageCompareNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is MediaPlayerNode -> {
            RenderMediaPlayerNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is ImageNode -> {
            RenderImageNode(node = node, onEvent = onEvent, modifier = appliedModifier)
        }

        is DividerNode -> {
            HorizontalDivider(
                modifier = appliedModifier,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
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
                shape = ToolkitTheme.shapes.small
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
            ToolkitStatusBadge(
                text = node.text,
                color = node.color.toComposeColor(),
                modifier = appliedModifier
            )
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

        is LoadingIndicatorNode -> {
            RenderLoadingIndicator(node = node, modifier = appliedModifier)
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
            }
        }
    }
}

/**
 * Text input field implementing the revision-tracking synchronization pattern
 * to prevent cursor jumping and asynchronous typing overwrite races.
 */
@Composable
internal fun sduiOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun RenderTextInput(
    node: TextInputNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var localText by remember(node.id) { mutableStateOf(node.text) }
    var lastRevision by remember(node.id) { mutableStateOf(node.revision) }
    var secretRevealed by remember(node.id) { mutableStateOf(false) }

    // When the plugin explicitly increments revision, apply the new server text
    if (node.revision != lastRevision || node.forceOverwrite) {
        localText = node.text
        lastRevision = node.revision
    }

    val visualTransformation = if (node.isSecret && !secretRevealed) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }

    OutlinedTextField(
        value = localText,
        onValueChange = { newText ->
            localText = newText
            onEvent(UiEvent.TextChanged(node.id, newText))
        },
        enabled = node.modifier.enabled,
        label = node.label?.let { { Text(it, style = ToolkitTheme.typography.labelMedium) } },
        placeholder = node.placeholder?.let { { Text(it, style = ToolkitTheme.typography.bodyMedium) } },
        singleLine = if (node.isSecret) true else node.singleLine,
        minLines = if (node.isSecret || node.singleLine) 1 else 2,
        visualTransformation = visualTransformation,
        trailingIcon = if (node.isSecret) {
            {
                val toggleHint = stringResource(
                    if (secretRevealed) Res.string.sdui_hide_secret else Res.string.sdui_show_secret
                )
                SduiTooltipBox(tooltip = toggleHint) {
                    IconButton(
                        onClick = { secretRevealed = !secretRevealed },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = if (secretRevealed) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = toggleHint,
                            modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                        )
                    }
                }
            }
        } else {
            null
        },
        shape = ToolkitTheme.shapes.medium,
        colors = sduiOutlinedTextFieldColors(),
        modifier = modifier
            .trackSduiTextInputFocus(node.id)
            .withFormWidthConstraint(node.modifier)
    )
}

/**
 * Interactive image renderer supporting aspect scaling, selection highlight, badges, and click actions.
 */
@Composable
private fun RenderImageNode(
    node: ImageNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val contentScale = when (node.contentScale) {
        ImageScale.FIT -> ContentScale.Fit
        ImageScale.CROP -> ContentScale.Crop
        ImageScale.FILL_BOUNDS -> ContentScale.FillBounds
        ImageScale.INSIDE -> ContentScale.Inside
    }
    var zoomDialogOpen by remember(node.id) { mutableStateOf(false) }
    val isInteractive = node.clickable || node.action != null
    val hasOverlayOrFrame = isInteractive || node.selected || !node.badgeText.isNullOrBlank() || node.zoomable
    val hasBoundedExtent = node.modifier.heightDp != null ||
        node.modifier.fillMaxHeight != null ||
        node.modifier.aspectRatio != null
    val imageFillModifier = if (hasBoundedExtent) Modifier.fillMaxSize() else Modifier.fillMaxWidth()

    if (!hasOverlayOrFrame) {
        PluginAssetImage(
            asset = node.asset,
            contentDescription = node.contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val borderStroke = when {
        node.selected -> BorderStroke(ToolkitTheme.dimensions.borderSelected, MaterialTheme.colorScheme.primary)
        isHovered && (isInteractive || node.zoomable) -> BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.high)
        )
        isInteractive || node.zoomable -> BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
        )
        else -> null
    }
    val shape = if (node.modifier.cornerRadius != null || node.modifier.clipRounded) {
        resolveUiModifierShape(node.modifier)
    } else {
        ToolkitTheme.shapes.small
    }

    val onImageClick: (() -> Unit)? = when {
        isInteractive -> { { onEvent(UiEvent.Click(node.id, node.action)) } }
        node.zoomable -> { { zoomDialogOpen = true } }
        else -> null
    }

    Surface(
        modifier = modifier.then(
            if (onImageClick != null) {
                Modifier
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onImageClick)
                    .pointerHoverIcon(PointerIcon.Hand)
            } else {
                Modifier.hoverable(interactionSource = interactionSource)
            }
        ),
        shape = shape,
        border = borderStroke,
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Box {
            PluginAssetImage(
                asset = node.asset,
                contentDescription = node.contentDescription,
                contentScale = contentScale,
                modifier = imageFillModifier
            )
            if (node.zoomable) {
                val zoomLabel = stringResource(Res.string.sdui_image_zoom_open)
                SduiTooltipBox(
                    tooltip = zoomLabel,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(ToolkitTheme.spacing.xs)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                            alpha = ToolkitTheme.opacity.almostOpaque
                        ),
                        border = BorderStroke(
                            ToolkitTheme.dimensions.borderUnselected,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                        )
                    ) {
                        IconButton(
                            onClick = { zoomDialogOpen = true },
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = zoomLabel,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                        }
                    }
                }
            }
            node.badgeText?.takeIf { it.isNotBlank() }?.let { badge ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(ToolkitTheme.spacing.xs),
                    shape = ToolkitTheme.shapes.pill,
                    color = if (node.selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                            alpha = ToolkitTheme.opacity.almostOpaque
                        )
                    },
                    border = BorderStroke(
                        ToolkitTheme.dimensions.borderUnselected,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                    )
                ) {
                    Text(
                        text = badge,
                        style = ToolkitTheme.typography.labelSmall,
                        color = if (node.selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            horizontal = ToolkitTheme.spacing.sm,
                            vertical = ToolkitTheme.spacing.xxs
                        )
                    )
                }
            }
        }
    }

    if (zoomDialogOpen && node.zoomable) {
        SduiImageZoomDialog(
            asset = node.asset,
            contentDescription = node.contentDescription,
            onDismiss = { zoomDialogOpen = false }
        )
    }
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
        modifier = modifier.withFormWidthConstraint(node.modifier)
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = node.modifier.enabled,
            label = node.label?.let { { Text(it, style = ToolkitTheme.typography.labelMedium) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && node.modifier.enabled) },
            shape = ToolkitTheme.shapes.medium,
            colors = sduiOutlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, node.modifier.enabled)
                .fillMaxWidth()
                .then(if (node.modifier.enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
        )

        ExposedDropdownMenu(
            expanded = expanded && node.modifier.enabled,
            onDismissRequest = { expanded = false },
            shape = ToolkitTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
        val trackColor = progressColor.copy(alpha = ToolkitTheme.opacity.borderLow)
        when (node.style) {
            ProgressBarStyle.MORPHING_INDICATOR, ProgressBarStyle.CONTAINED_INDICATOR -> {
                ToolkitLoadingIndicator(
                    contained = node.style == ProgressBarStyle.CONTAINED_INDICATOR,
                    progress = node.progress,
                    color = progressColor,
                    modifier = Modifier.size(ToolkitTheme.dimensions.loadingIndicatorMediumSize)
                )
            }

            ProgressBarStyle.WAVY -> {
                if (node.isCircular) {
                    ToolkitCircularProgressIndicator(
                        progress = node.progress,
                        wavy = true,
                        color = progressColor,
                        trackColor = trackColor
                    )
                } else {
                    ToolkitLinearProgressIndicator(
                        progress = node.progress,
                        wavy = true,
                        color = progressColor,
                        trackColor = trackColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            ProgressBarStyle.STANDARD -> {
                if (node.isCircular) {
                    val progressVal = node.progress
                    if (progressVal != null) {
                        CircularProgressIndicator(
                            progress = { progressVal.coerceIn(0f, 1f) },
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium),
                            color = progressColor,
                            trackColor = trackColor,
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
                            trackColor = trackColor
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(ToolkitTheme.dimensions.capabilityProgressBarHeight),
                            color = progressColor,
                            trackColor = trackColor
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
    }
}

/**
 * Material 3 Expressive morphing [LoadingIndicatorNode] renderer with semantic sizing and optional label.
 */
@Composable
private fun RenderLoadingIndicator(
    node: LoadingIndicatorNode,
    modifier: Modifier = Modifier
) {
    val indicatorSize = when (node.size) {
        ControlSize.SMALL -> ToolkitTheme.dimensions.loadingIndicatorSmallSize
        ControlSize.MEDIUM -> ToolkitTheme.dimensions.loadingIndicatorMediumSize
        ControlSize.LARGE -> ToolkitTheme.dimensions.loadingIndicatorLargeSize
    }
    val accessibleText = node.label?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.sdui_loading_default)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
    ) {
        ToolkitLoadingIndicator(
            contained = node.contained,
            progress = node.progress,
            color = node.color.toComposeColor(),
            modifier = Modifier
                .size(indicatorSize)
                .semantics { contentDescription = accessibleText }
        )
        node.label?.takeIf { it.isNotBlank() }?.let { labelText ->
            Text(
                text = labelText,
                style = ToolkitTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Tab container renderer supporting icons, badges, and view switching.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.md)
        ) {
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedIndex,
                containerColor = ToolkitTheme.colors.transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = ToolkitTheme.spacing.none,
                divider = {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                    )
                },
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedIndex),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                node.tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex
                    Tab(
                        selected = isSelected,
                        onClick = { onEvent(UiEvent.TabSelected(node.id, tab.key)) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                tab.iconName?.let { iconName ->
                                    Icon(
                                        imageVector = SduiIconRegistry.resolve(iconName),
                                        contentDescription = tab.label,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                    )
                                }
                                Text(
                                    text = tab.label,
                                    style = ToolkitTheme.typography.labelLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                tab.badgeText?.let { badge ->
                                    Surface(
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        shape = ToolkitTheme.shapes.pill
                                    ) {
                                        Text(
                                            text = badge,
                                            style = ToolkitTheme.typography.labelSmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(
                                                horizontal = ToolkitTheme.spacing.sm,
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
 * Structured list row renderer with leading controls/icons, protected text width, tonal state,
 * and adaptive trailing action placement so narrow sidebars never crush titles.
 */
@Composable
private fun RenderListItem(
    node: ListItemNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val containerColor = when {
        node.selected -> MaterialTheme.colorScheme.secondaryContainer
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val titleColor = if (node.selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (node.selected) {
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = ToolkitTheme.opacity.secondaryText)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderStroke = when {
        node.selected -> BorderStroke(
            ToolkitTheme.dimensions.borderThin,
            MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.high)
        )
        isHovered -> BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.high)
        )
        else -> BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.subtleHighlight)
        )
    }

    val hasRowAction = node.action != null || node.selectable

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (hasRowAction) {
                    Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            if (node.action != null) {
                                onEvent(UiEvent.Click(node.id, node.action))
                            } else if (node.selectable) {
                                onEvent(UiEvent.ToggleChanged(node.id, !node.selected))
                            }
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                } else {
                    Modifier.hoverable(interactionSource = interactionSource)
                }
            ),
        color = containerColor,
        shape = ToolkitTheme.shapes.medium,
        border = borderStroke
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ToolkitTheme.dimensions.sduiCompactSpacing,
                    vertical = ToolkitTheme.spacing.sm
                )
        ) {
            val trailingCount = node.trailingContent.size
            val estimatedTrailingWidth = if (trailingCount > 0) {
                (ToolkitTheme.dimensions.denseIconButtonSize * trailingCount) +
                    (ToolkitTheme.spacing.xs * (trailingCount - 1)) +
                    ToolkitTheme.spacing.sm
            } else {
                ToolkitTheme.spacing.none
            }
            val leadingVisualSize = when {
                node.leadingAsset != null -> ToolkitTheme.dimensions.sduiListThumbnailSize + ToolkitTheme.dimensions.sduiCompactSpacing
                node.leadingIcon != null -> ToolkitTheme.dimensions.iconLarge + ToolkitTheme.dimensions.sduiCompactSpacing
                else -> ToolkitTheme.spacing.none
            }
            val leadingReserve = (if (node.selectable) {
                ToolkitTheme.dimensions.desktopTouchTargetSize + ToolkitTheme.dimensions.sduiCompactSpacing
            } else {
                ToolkitTheme.spacing.none
            }) + leadingVisualSize
            val stackTrailingBelow = trailingCount >= 3 &&
                (maxWidth - leadingReserve - estimatedTrailingWidth) < ToolkitTheme.dimensions.sduiTextColumnMinWidth

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.dimensions.sduiCompactSpacing),
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

                    val leadingAsset = node.leadingAsset
                    if (leadingAsset != null) {
                        Box(
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.sduiListThumbnailSize)
                                .clip(ToolkitTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            PluginAssetImage(
                                asset = leadingAsset,
                                contentDescription = node.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        node.leadingIcon?.let { iconName ->
                            Box(
                                modifier = Modifier
                                    .size(ToolkitTheme.dimensions.iconLarge)
                                    .clip(ToolkitTheme.shapes.small)
                                    .background(
                                        if (node.selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = SduiIconRegistry.resolve(iconName),
                                    contentDescription = node.title,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall),
                                    tint = if (node.selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = ToolkitTheme.dimensions.sduiTextColumnMinWidth),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = node.title,
                                style = ToolkitTheme.typography.titleSmall,
                                color = titleColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            node.badgeText?.let { badge ->
                                val bColor = node.badgeColor?.toComposeColor() ?: MaterialTheme.colorScheme.primary
                                Surface(
                                    color = bColor.copy(alpha = ToolkitTheme.opacity.subtleHighlight),
                                    shape = ToolkitTheme.shapes.pill
                                ) {
                                    Text(
                                        text = badge,
                                        style = ToolkitTheme.typography.labelSmall,
                                        color = bColor,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(
                                            horizontal = ToolkitTheme.spacing.sm,
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
                                color = subtitleColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!stackTrailingBelow && node.trailingContent.isNotEmpty()) {
                        ListItemTrailingActions(
                            trailingContent = node.trailingContent,
                            onEvent = onEvent
                        )
                    }
                }

                if (stackTrailingBelow && node.trailingContent.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ListItemTrailingActions(
                            trailingContent = node.trailingContent,
                            onEvent = onEvent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListItemTrailingActions(
    trailingContent: List<UiNode>,
    onEvent: (UiEvent) -> Unit
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
        LocalInDenseActionBar provides true
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            trailingContent.forEach { child ->
                key(child.id) {
                    SafeUiNodeRenderer(node = child, onEvent = onEvent)
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
        modifier = modifier
            .clip(ToolkitTheme.shapes.small)
            .clickable(enabled = node.modifier.enabled) {
                onEvent(UiEvent.ToggleChanged(node.id, !node.checked))
            }
            .then(if (node.modifier.enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier),
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
 * Wrapping Material 3 chip group filter selector renderer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderChipGroup(
    node: ChipGroupNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
    ) {
        node.chips.forEach { chip ->
            val isSelected = chip.key in node.selectedKeys
            FilterChip(
                selected = isSelected,
                shape = ToolkitTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                onClick = {
                    onEvent(UiEvent.ChipSelected(node.id, chip.key, !isSelected))
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                label = {
                    Text(
                        text = chip.label,
                        style = ToolkitTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                },
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
 * Compact icon action button wrapped with Material 3 hover/focus tooltip.
 */
@Composable
private fun RenderIconButton(
    node: IconButtonNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val action = node.action ?: node.id
    val isDense = LocalInDenseActionBar.current
    val defaultTint = if (isDense) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val tint = node.tint?.toComposeColor() ?: defaultTint
    val buttonModifier = if (isDense) {
        modifier.size(ToolkitTheme.dimensions.denseIconButtonSize)
    } else {
        modifier
    }
    val iconSize = if (isDense) ToolkitTheme.dimensions.denseIconSize else ToolkitTheme.dimensions.iconMedium

    val tooltipText = node.tooltip ?: node.modifier.tooltip
    SduiTooltipBox(tooltip = tooltipText) {
        IconButton(
            onClick = { onEvent(UiEvent.Click(node.id, action)) },
            enabled = node.modifier.enabled,
            modifier = buttonModifier.then(
                if (node.modifier.enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
            ),
            colors = if (node.isTonal) {
                IconButtonDefaults.filledTonalIconButtonColors()
            } else {
                IconButtonDefaults.iconButtonColors()
            }
        ) {
            Icon(
                imageVector = SduiIconRegistry.resolve(node.iconName),
                contentDescription = tooltipText ?: node.iconName,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ToolkitTheme.shapes.medium,
        border = BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            severityColor.copy(alpha = ToolkitTheme.opacity.borderLow)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(severityColor.copy(alpha = ToolkitTheme.opacity.subtleHighlight))
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = ToolkitTheme.spacing.md,
                    vertical = ToolkitTheme.dimensions.sduiCompactSpacing
                ),
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.dimensions.sduiCompactSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(ToolkitTheme.dimensions.desktopTouchTargetSize)
                        .clip(CircleShape)
                        .background(severityColor.copy(alpha = ToolkitTheme.opacity.borderLow)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = node.severity.name,
                        tint = severityColor,
                        modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                    )
                }
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
                    FilledTonalButton(
                        onClick = { onEvent(UiEvent.Click(node.id, "banner_action")) },
                        shape = ToolkitTheme.shapes.pill,
                        contentPadding = PaddingValues(
                            horizontal = ToolkitTheme.dimensions.sduiCompactSpacing,
                            vertical = ToolkitTheme.spacing.xs
                        ),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text(
                            text = actionLabel,
                            style = ToolkitTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
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
    ToolkitEmptyState(
        title = node.title,
        icon = SduiIconRegistry.resolve(node.iconName),
        modifier = modifier,
        description = node.description,
        actionLabel = node.actionLabel,
        onAction = node.actionLabel?.let {
            { onEvent(UiEvent.Click(node.id, "empty_state_action")) }
        }
    )
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
        shape = ToolkitTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(text = node.title, style = ToolkitTheme.typography.titleLarge)
        },
        text = {
            val dialogScrollState = rememberScrollState()
            val scrollbarReserve = if (dialogScrollState.maxValue > 0) {
                ToolkitTheme.dimensions.sduiScrollbarEndPadding
            } else {
                ToolkitTheme.spacing.none
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ToolkitTheme.dimensions.sduiFloatingPanelMaxHeight)
            ) {
                CompositionLocalProvider(LocalInVerticalScroll provides true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(dialogScrollState)
                            .padding(end = scrollbarReserve),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.dimensions.sduiCompactSpacing)
                    ) {
                        RenderColumnChildren(children = node.children, onEvent = onEvent, allowWeight = false)
                    }
                }
                PlatformVerticalScrollbar(
                    scrollState = dialogScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(UiEvent.DialogAction(node.id, isConfirmed = true)) },
                shape = ToolkitTheme.shapes.pill,
                colors = if (node.isDestructive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(text = node.confirmLabel, style = ToolkitTheme.typography.labelLarge)
            }
        },
        dismissButton = node.dismissLabel?.let { label ->
            {
                OutlinedButton(
                    onClick = { onEvent(UiEvent.DialogAction(node.id, isConfirmed = false)) },
                    shape = ToolkitTheme.shapes.pill
                ) {
                    Text(text = label, style = ToolkitTheme.typography.labelLarge)
                }
            }
        }
    )
}

/**
 * Desktop master-detail layout split pane separating tonal containers with Material 3 gutters
 * and optional interactive pointer drag resizing.
 */
@Composable
private fun RenderSplitPane(
    node: SplitPaneNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var fraction1 by remember(node.id, node.splitFraction) {
        mutableStateOf(node.splitFraction.coerceIn(0.1f, 0.9f))
    }
    val fraction2 = (1f - fraction1).coerceIn(0.1f, 0.9f)
    val resizeLabel = stringResource(Res.string.sdui_resize_pane)
    val density = LocalDensity.current

    @Composable
    fun SplitPaneContainer(
        paneNode: UiNode,
        paneModifier: Modifier,
        containerColor: Color
    ) {
        val innerPadding = if (paneNode.modifier.padding == SpacingSize.NONE) {
            ToolkitTheme.spacing.md
        } else {
            ToolkitTheme.spacing.sm
        }
        Surface(
            modifier = paneModifier,
            shape = ToolkitTheme.shapes.large,
            color = containerColor,
            border = BorderStroke(
                ToolkitTheme.dimensions.borderUnselected,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.subtleHighlight)
            )
        ) {
            SafeUiNodeRenderer(
                node = paneNode,
                onEvent = onEvent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun SplitResizeHandle(totalPx: Float) {
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        val handleColor = if (isHovered) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
        }
        val pointerIcon = if (node.isHorizontal) {
            PlatformUtils.horizontalResizePointerIcon()
        } else {
            PlatformUtils.verticalResizePointerIcon()
        }
        Box(
            modifier = (if (node.isHorizontal) {
                Modifier.fillMaxHeight().width(ToolkitTheme.dimensions.sduiSplitHandleThickness)
            } else {
                Modifier.fillMaxWidth().height(ToolkitTheme.dimensions.sduiSplitHandleThickness)
            })
                .hoverable(interactionSource)
                .pointerHoverIcon(pointerIcon)
                .semantics { contentDescription = resizeLabel }
                .pointerInput(node.id, node.isHorizontal, totalPx) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (totalPx > 1f) {
                                val delta = if (node.isHorizontal) dragAmount.x else dragAmount.y
                                fraction1 = (fraction1 + delta / totalPx).coerceIn(0.1f, 0.9f)
                            }
                        },
                        onDragEnd = {
                            onEvent(UiEvent.ValueChanged(node.id, fraction1.toDouble()))
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (node.isHorizontal) {
                VerticalDragHandle(
                    interactionSource = interactionSource
                )
            } else {
                Box(
                    modifier = Modifier
                        .height(ToolkitTheme.dimensions.sduiSplitHandlePillThickness)
                        .width(ToolkitTheme.dimensions.sduiSplitHandlePillLength)
                        .clip(ToolkitTheme.shapes.pill)
                        .background(handleColor)
                )
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalPx = with(density) {
            if (node.isHorizontal) maxWidth.toPx() else maxHeight.toPx()
        }
        if (node.isHorizontal) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = if (node.resizable) {
                    Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
                } else {
                    Arrangement.spacedBy(ToolkitTheme.spacing.md)
                }
            ) {
                SplitPaneContainer(
                    paneNode = node.first,
                    paneModifier = Modifier.weight(fraction1).fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
                if (node.resizable) {
                    SplitResizeHandle(totalPx = totalPx)
                }
                SplitPaneContainer(
                    paneNode = node.second,
                    paneModifier = Modifier.weight(fraction2).fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = if (node.resizable) {
                    Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
                } else {
                    Arrangement.spacedBy(ToolkitTheme.spacing.md)
                }
            ) {
                SplitPaneContainer(
                    paneNode = node.first,
                    paneModifier = Modifier.weight(fraction1).fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
                if (node.resizable) {
                    SplitResizeHandle(totalPx = totalPx)
                }
                SplitPaneContainer(
                    paneNode = node.second,
                    paneModifier = Modifier.weight(fraction2).fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
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
    val scrollState = rememberScrollState()
    val scrollbarReserve = if (scrollState.maxValue > 0) {
        ToolkitTheme.dimensions.sduiCompactSpacing
    } else {
        ToolkitTheme.spacing.none
    }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(bottom = scrollbarReserve),
            horizontalArrangement = Arrangement.spacedBy(
                resolveContainerSpacing(node.spacing, node.children, isVertical = false)
            ),
            verticalAlignment = node.modifier.alignment?.toVerticalAlignment() ?: Alignment.CenterVertically
        ) {
            node.children.forEach { child ->
                key(child.id) {
                    SafeUiNodeRenderer(node = child, onEvent = onEvent)
                }
            }
        }
        PlatformHorizontalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        )
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
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = ToolkitTheme.shapes.medium
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
 * Map [SpacingSize] to Material 3 4dp/8dp grid theme tokens.
 */
@Composable
fun SpacingSize.toDp(): Dp = when (this) {
    SpacingSize.NONE -> ToolkitTheme.spacing.none
    SpacingSize.EXTRA_SMALL -> ToolkitTheme.spacing.xs
    SpacingSize.SMALL -> ToolkitTheme.spacing.sm
    SpacingSize.MEDIUM_SMALL -> ToolkitTheme.dimensions.sduiCompactSpacing
    SpacingSize.MEDIUM -> ToolkitTheme.spacing.md
    SpacingSize.LARGE -> ToolkitTheme.spacing.lg
    SpacingSize.EXTRA_LARGE -> ToolkitTheme.spacing.xl
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
    val chromeShape = ToolkitTheme.shapes.large
    val boardContent = node.content as? BoardNode

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(ToolkitTheme.spacing.sm)
            .clipToBounds()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
        ) {
            node.topBar?.let { top ->
                val barPadding = if (top.modifier.padding == SpacingSize.NONE) {
                    Modifier.padding(horizontal = ToolkitTheme.spacing.md, vertical = ToolkitTheme.spacing.sm)
                } else {
                    Modifier.padding(horizontal = ToolkitTheme.spacing.sm, vertical = ToolkitTheme.spacing.xxs)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = chromeShape,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    CompositionLocalProvider(
                        LocalInSingleLineHeader provides true,
                        LocalInDenseActionBar provides true
                    ) {
                        SafeUiNodeRenderer(
                            node = top,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxWidth().then(barPadding)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
            ) {
                node.leftSidebar?.let { left ->
                    SafeUiNodeRenderer(node = left, onEvent = onEvent, modifier = Modifier.fillMaxHeight())
                }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = chromeShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        val isFullBleedNode = node.content is BoardNode ||
                            node.content is SplitPaneNode ||
                            node.content is ScaffoldNode
                        val contentPadding = when {
                            isFullBleedNode -> ToolkitTheme.spacing.none
                            node.content.modifier.padding == SpacingSize.NONE -> ToolkitTheme.spacing.md
                            else -> ToolkitTheme.spacing.sm
                        }
                        SafeUiNodeRenderer(
                            node = node.content,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxSize().padding(contentPadding)
                        )

                        // Floating overlay panels stay scoped inside the workspace pane so they
                        // never cover top/bottom bars or sidebars, and clear any active board bars.
                        val topToolbarClearance = if (boardContent?.showToolbar == true) {
                            ToolkitTheme.dimensions.standardButtonHeight + ToolkitTheme.spacing.sm
                        } else {
                            ToolkitTheme.spacing.none
                        }
                        val bottomZoomClearance = if (boardContent?.showZoomControls == true) {
                            ToolkitTheme.dimensions.standardButtonHeight + ToolkitTheme.spacing.sm
                        } else {
                            ToolkitTheme.spacing.none
                        }

                        node.floatingPanels.forEach { panel ->
                            key(panel.id) {
                                val needsTopClearance = panel.anchor == FloatingAnchor.TOP_START ||
                                    panel.anchor == FloatingAnchor.TOP_CENTER ||
                                    panel.anchor == FloatingAnchor.TOP_END
                                val needsBottomClearance = panel.anchor == FloatingAnchor.BOTTOM_END
                                SafeUiNodeRenderer(
                                    node = panel,
                                    onEvent = onEvent,
                                    modifier = Modifier
                                        .align(panel.anchor.toAlignment())
                                        .padding(
                                            start = ToolkitTheme.spacing.md,
                                            top = ToolkitTheme.spacing.md + if (needsTopClearance) topToolbarClearance else ToolkitTheme.spacing.none,
                                            end = ToolkitTheme.spacing.md,
                                            bottom = ToolkitTheme.spacing.md + if (needsBottomClearance) bottomZoomClearance else ToolkitTheme.spacing.none
                                        )
                                )
                            }
                        }
                    }
                }

                node.rightSidebar?.let { right ->
                    SafeUiNodeRenderer(node = right, onEvent = onEvent, modifier = Modifier.fillMaxHeight())
                }
            }

            node.bottomBar?.let { bottom ->
                val barPadding = if (bottom.modifier.padding == SpacingSize.NONE) {
                    Modifier.padding(horizontal = ToolkitTheme.spacing.md, vertical = ToolkitTheme.spacing.sm)
                } else {
                    Modifier.padding(horizontal = ToolkitTheme.spacing.sm, vertical = ToolkitTheme.spacing.xxs)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = chromeShape,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    CompositionLocalProvider(
                        LocalInSingleLineHeader provides true,
                        LocalInDenseActionBar provides true
                    ) {
                        SafeUiNodeRenderer(
                            node = bottom,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxWidth().then(barPadding)
                        )
                    }
                }
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
    val widthConstraint = when {
        node.compact -> Modifier
        isCollapsed -> Modifier.widthIn(
            min = ToolkitTheme.dimensions.menuMinWidth,
            max = ToolkitTheme.dimensions.sduiFloatingPanelMaxWidth
        )
        else -> Modifier.widthIn(
            min = ToolkitTheme.dimensions.sduiFloatingPanelMinWidth,
            max = ToolkitTheme.dimensions.sduiFloatingPanelMaxWidth
        )
    }

    Surface(
        modifier = modifier.then(widthConstraint),
        shape = if (node.compact) ToolkitTheme.shapes.pill else ToolkitTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = ToolkitTheme.dimensions.elevationMedium
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = ToolkitTheme.spacing.md,
                vertical = if (isCollapsed) ToolkitTheme.spacing.sm else ToolkitTheme.spacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.dimensions.sduiCompactSpacing)
        ) {
            val showHeader = node.title != null || node.isCollapsible
            if (showHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ToolkitTheme.shapes.small)
                        .then(
                            if (node.isCollapsible) {
                                Modifier
                                    .clickable { isCollapsed = !isCollapsed }
                                    .pointerHoverIcon(PointerIcon.Hand)
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = ToolkitTheme.spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    node.title?.let { titleText ->
                        Text(
                            text = titleText,
                            style = ToolkitTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = ToolkitTheme.spacing.sm)
                        )
                    } ?: Spacer(modifier = Modifier.weight(1f))

                    if (node.isCollapsible) {
                        SduiTooltipBox(tooltip = stringResource(Res.string.sdui_toggle_panel)) {
                            IconButton(
                                onClick = { isCollapsed = !isCollapsed },
                                modifier = Modifier
                                    .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = stringResource(Res.string.sdui_toggle_panel),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.dimensions.sduiCompactSpacing)
                ) {
                    if (showHeader) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                        )
                    }
                    val panelScrollState = rememberScrollState()
                    val scrollbarReserve = if (panelScrollState.maxValue > 0) {
                        ToolkitTheme.dimensions.sduiScrollbarEndPadding
                    } else {
                        ToolkitTheme.spacing.none
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = ToolkitTheme.dimensions.sduiFloatingPanelMaxHeight)
                    ) {
                        CompositionLocalProvider(LocalInVerticalScroll provides true) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(panelScrollState)
                                    .padding(end = scrollbarReserve),
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.dimensions.sduiCompactSpacing)
                            ) {
                                RenderColumnChildren(
                                    children = node.children,
                                    onEvent = onEvent,
                                    allowWeight = false
                                )
                            }
                        }
                        PlatformVerticalScrollbar(
                            scrollState = panelScrollState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    val sequelNodes = node.sequelChildren.orEmpty()
    val hasSequel = isHorizontal && sequelNodes.isNotEmpty()
    var sequelMode by remember(node.id, node.sequelVisible) {
        mutableStateOf(
            if (node.sequelVisible) SequelSidebarMode.Auto else SequelSidebarMode.ForcedCompact
        )
    }
    val isSequelShown = hasSequel && !isCollapsed && resolveSequelExpanded(
        mode = sequelMode,
        isWideViewport = node.sequelVisible
    )
    val sequelWidth = (node.sequelWidthDp?.dp ?: ToolkitTheme.dimensions.floatingSidebarSequelWidth)
        .coerceIn(ToolkitTheme.dimensions.floatingSidebarMinWidth, ToolkitTheme.dimensions.sduiSidebarMaxWidth)

    val requestedPrimaryWidth = (node.modifier.widthDp?.dp ?: ToolkitTheme.dimensions.sduiSidebarDefaultWidth)
        .coerceIn(ToolkitTheme.dimensions.sduiSidebarMinWidth, ToolkitTheme.dimensions.sduiSidebarMaxWidth)
    val targetTotalWidth = when {
        isCollapsed -> ToolkitTheme.dimensions.sidebarCollapsedWidth
        isSequelShown -> requestedPrimaryWidth + sequelWidth
        else -> requestedPrimaryWidth
    }
    val animatedSidebarWidth by animateDpAsState(
        targetValue = targetTotalWidth,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "sduiSidebarWidth"
    )

    val sidebarBorder = BorderStroke(
        ToolkitTheme.dimensions.borderUnselected,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
    )

    if (isHorizontal) {
        ToolkitFloatingSidebar(
            navigationKey = Unit,
            animateNavigation = false,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sequelContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = ToolkitTheme.shapes.large,
            border = sidebarBorder,
            sequelVisible = isSequelShown,
            sequelWidth = sequelWidth,
            sequelContent = if (hasSequel) {
                {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ToolkitTheme.dimensions.sduiCompactSpacing),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.dimensions.sduiCompactSpacing)
                    ) {
                        node.sequelTitle?.takeIf { it.isNotBlank() }?.let { titleText ->
                            Text(
                                text = titleText,
                                style = ToolkitTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val sequelScroll = rememberScrollState()
                        val sequelScrollbarReserve = if (sequelScroll.maxValue > 0) {
                            ToolkitTheme.dimensions.sduiScrollbarEndPadding
                        } else {
                            ToolkitTheme.spacing.none
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            CompositionLocalProvider(LocalInVerticalScroll provides true) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(sequelScroll)
                                        .padding(end = sequelScrollbarReserve),
                                    verticalArrangement = Arrangement.spacedBy(
                                        resolveContainerSpacing(SpacingSize.SMALL, sequelNodes, isVertical = true)
                                    )
                                ) {
                                    RenderColumnChildren(
                                        children = sequelNodes,
                                        onEvent = onEvent,
                                        allowWeight = false
                                    )
                                }
                            }
                            if (sequelScroll.maxValue > 0) {
                                PlatformVerticalScrollbar(
                                    scrollState = sequelScroll,
                                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            } else null,
            modifier = modifier.width(animatedSidebarWidth).fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ToolkitTheme.dimensions.sduiCompactSpacing),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.dimensions.sduiCompactSpacing)
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
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = ToolkitTheme.spacing.xs)
                            )
                        } ?: Spacer(modifier = Modifier.weight(1f))

                        if (hasSequel) {
                            ToolkitSequelSidebarToggleButton(
                                isExpanded = isSequelShown,
                                onToggle = { sequelMode = toggleSequelSidebarMode(isSequelShown) }
                            )
                        }
                    }
                    if (node.isCollapsible) {
                        SduiTooltipBox(tooltip = stringResource(Res.string.sdui_toggle_sidebar)) {
                            IconButton(
                                onClick = { isCollapsed = !isCollapsed },
                                modifier = Modifier
                                    .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = stringResource(Res.string.sdui_toggle_sidebar),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !isCollapsed,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    if (applySidebarScroll) {
                        val sidebarScrollState = rememberScrollState()
                        val scrollbarReserve = if (sidebarScrollState.maxValue > 0) {
                            ToolkitTheme.dimensions.sduiScrollbarEndPadding
                        } else {
                            ToolkitTheme.spacing.none
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            CompositionLocalProvider(LocalInVerticalScroll provides true) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(sidebarScrollState)
                                        .padding(end = scrollbarReserve),
                                    verticalArrangement = Arrangement.spacedBy(
                                        resolveContainerSpacing(SpacingSize.SMALL, node.children, isVertical = true)
                                    )
                                ) {
                                    RenderColumnChildren(
                                        children = node.children,
                                        onEvent = onEvent,
                                        allowWeight = false
                                    )
                                }
                            }
                            PlatformVerticalScrollbar(
                                scrollState = sidebarScrollState,
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(
                                resolveContainerSpacing(SpacingSize.SMALL, node.children, isVertical = true)
                            )
                        ) {
                            RenderColumnChildren(
                                children = node.children,
                                onEvent = onEvent,
                                allowWeight = true
                            )
                        }
                    }
                }
            }
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = ToolkitTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = sidebarBorder
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ToolkitTheme.dimensions.sduiCompactSpacing),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.dimensions.sduiCompactSpacing)
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
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = ToolkitTheme.spacing.xs)
                            )
                        } ?: Spacer(modifier = Modifier.weight(1f))
                    }
                    if (node.isCollapsible) {
                        SduiTooltipBox(tooltip = stringResource(Res.string.sdui_toggle_sidebar)) {
                            IconButton(
                                onClick = { isCollapsed = !isCollapsed },
                                modifier = Modifier
                                    .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = stringResource(Res.string.sdui_toggle_sidebar),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !isCollapsed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(
                            resolveContainerSpacing(SpacingSize.SMALL, node.children, isVertical = true)
                        )
                    ) {
                        RenderColumnChildren(
                            children = node.children,
                            onEvent = onEvent,
                            allowWeight = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ColumnScope.RenderColumnChildren(
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
        is ScrollableColumnNode, is GridNode, is SplitPaneNode, is BoardNode -> true
        is ColumnNode -> children.any { it.containsScrollableOrWeightedContent() }
        is BoxNode -> children.any { it.containsScrollableOrWeightedContent() }
        is RowNode -> children.any { it.containsScrollableOrWeightedContent() }
        is FlowRowNode -> children.any { it.containsScrollableOrWeightedContent() }
        is AccordionNode -> children.any { it.containsScrollableOrWeightedContent() }
        is TabsNode -> content.containsScrollableOrWeightedContent()
        else -> false
    }
}

/**
 * Resolves the active Compose [Shape] from a [UiModifier]'s semantic corner radius or `clipRounded` flag.
 */
@Composable
internal fun resolveUiModifierShape(uiModifier: UiModifier): Shape = when (uiModifier.cornerRadius) {
    ShapeRole.NONE -> RoundedCornerShape(ToolkitTheme.spacing.none)
    ShapeRole.EXTRA_SMALL -> ToolkitTheme.shapes.extraSmall
    ShapeRole.SMALL -> ToolkitTheme.shapes.small
    ShapeRole.MEDIUM -> ToolkitTheme.shapes.medium
    ShapeRole.LARGE -> ToolkitTheme.shapes.large
    ShapeRole.EXTRA_LARGE -> ToolkitTheme.shapes.extraLarge
    ShapeRole.PILL -> ToolkitTheme.shapes.pill
    null -> if (uiModifier.clipRounded) {
        ToolkitTheme.shapes.medium
    } else {
        RoundedCornerShape(ToolkitTheme.spacing.none)
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
    val cornerShape = resolveUiModifierShape(uiModifier)
    if (uiModifier.clipRounded || uiModifier.cornerRadius != null) {
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
