package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.AccordionNode
import org.wip.plugintoolkit.api.sdui.AssetReference
import org.wip.plugintoolkit.api.sdui.CodeBlockNode
import org.wip.plugintoolkit.api.sdui.NumberInputNode
import org.wip.plugintoolkit.api.sdui.RadioGroupNode
import org.wip.plugintoolkit.api.sdui.RadioOptionItem
import org.wip.plugintoolkit.api.sdui.SearchBarNode
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformHorizontalScrollbar
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_accordion_collapse
import plugintoolkit.composeapp.generated.resources.sdui_accordion_expand
import plugintoolkit.composeapp.generated.resources.sdui_code_copied
import plugintoolkit.composeapp.generated.resources.sdui_code_copy
import plugintoolkit.composeapp.generated.resources.sdui_code_default_title
import plugintoolkit.composeapp.generated.resources.sdui_image_zoom_close
import plugintoolkit.composeapp.generated.resources.sdui_image_zoom_in
import plugintoolkit.composeapp.generated.resources.sdui_image_zoom_out
import plugintoolkit.composeapp.generated.resources.sdui_image_zoom_reset
import plugintoolkit.composeapp.generated.resources.sdui_number_decrement
import plugintoolkit.composeapp.generated.resources.sdui_number_increment
import plugintoolkit.composeapp.generated.resources.sdui_search_clear
import plugintoolkit.composeapp.generated.resources.sdui_search_submit

private const val CODE_COPIED_FEEDBACK_MS = 1800L
private const val DECIMAL_PRECISION_SCALE = 1_000_000.0
private const val DISPLAY_DECIMAL_SCALE = 10_000.0
private const val IMAGE_ZOOM_MIN = 0.5f
private const val IMAGE_ZOOM_MAX = 4.0f
private const val IMAGE_ZOOM_STEP = 0.25f

/**
 * Exclusive single-choice radio group control supporting vertical or horizontal flow layouts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RenderRadioGroup(
    node: RadioGroupNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedKey by remember(node.id, node.selectedKey) { mutableStateOf(node.selectedKey) }
    val groupEnabled = node.modifier.enabled

    Column(
        modifier = modifier.withFormWidthConstraint(node.modifier),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
    ) {
        node.label?.let { groupLabel ->
            Text(
                text = groupLabel,
                style = ToolkitTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides ToolkitTheme.dimensions.desktopTouchTargetSize
        ) {
            if (node.horizontal) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
                ) {
                    node.options.forEach { option ->
                        RadioOptionCard(
                            option = option,
                            selected = selectedKey == option.key,
                            enabled = groupEnabled && option.enabled,
                            fillWidth = false,
                            onSelect = {
                                selectedKey = option.key
                                onEvent(UiEvent.OptionSelected(node.id, option.key))
                            }
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)) {
                    node.options.forEach { option ->
                        RadioOptionCard(
                            option = option,
                            selected = selectedKey == option.key,
                            enabled = groupEnabled && option.enabled,
                            fillWidth = true,
                            onSelect = {
                                selectedKey = option.key
                                onEvent(UiEvent.OptionSelected(node.id, option.key))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioOptionCard(
    option: RadioOptionItem,
    selected: Boolean,
    enabled: Boolean,
    fillWidth: Boolean,
    onSelect: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val containerColor = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        isHovered && enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderStroke = when {
        selected -> BorderStroke(ToolkitTheme.dimensions.borderUnselected, MaterialTheme.colorScheme.primary)
        isHovered && enabled -> BorderStroke(ToolkitTheme.dimensions.borderUnselected, MaterialTheme.colorScheme.outline)
        else -> BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
        )
    }

    Surface(
        shape = ToolkitTheme.shapes.small,
        color = containerColor,
        border = borderStroke,
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.sm, vertical = ToolkitTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled
            )
            Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)) {
                Text(
                    text = option.label,
                    style = ToolkitTheme.typography.bodyMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                option.subtitle?.takeIf { it.isNotBlank() }?.let { subtitleText ->
                    Text(
                        text = subtitleText,
                        style = ToolkitTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = ToolkitTheme.opacity.secondaryText)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/**
 * Bounded numeric stepper field supporting direct entry, +/- buttons, and ArrowUp/ArrowDown keys.
 */
@Composable
internal fun RenderNumberInput(
    node: NumberInputNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastRevision by remember(node.id) { mutableStateOf(node.revision) }
    var lastServerValue by remember(node.id) { mutableStateOf(node.value) }
    var isFocused by remember(node.id) { mutableStateOf(false) }
    var textValue by remember(node.id) { mutableStateOf(formatNumberDisplay(node.value, node.isDecimal)) }

    val serverChanged = node.revision != lastRevision ||
        node.forceOverwrite ||
        (!isFocused && node.value != lastServerValue)
    if (serverChanged) {
        textValue = formatNumberDisplay(node.value, node.isDecimal)
        lastRevision = node.revision
        lastServerValue = node.value
    }

    val currentNumeric = parseNumberOrNull(textValue) ?: node.value
    val isEnabled = node.modifier.enabled
    val canDecrement = isEnabled && (node.min == null || currentNumeric > node.min!!)
    val canIncrement = isEnabled && (node.max == null || currentNumeric < node.max!!)

    val applyStep: (Int) -> Unit = { direction ->
        val base = parseNumberOrNull(textValue) ?: node.value
        val next = adjustNumberByStep(
            current = base,
            step = node.step,
            direction = direction,
            min = node.min,
            max = node.max,
            isDecimal = node.isDecimal
        )
        textValue = formatNumberDisplay(next, node.isDecimal)
        onEvent(UiEvent.ValueChanged(node.id, next))
    }

    Row(
        modifier = modifier.withFormWidthConstraint(node.modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
    ) {
        val decrementLabel = stringResource(Res.string.sdui_number_decrement)
        SduiTooltipBox(tooltip = decrementLabel) {
            IconButton(
                onClick = { applyStep(-1) },
                enabled = canDecrement,
                modifier = Modifier
                    .semantics { contentDescription = decrementLabel }
                    .then(if (canDecrement) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                )
            }
        }

        OutlinedTextField(
            value = textValue,
            onValueChange = { raw ->
                val filtered = filterNumericText(
                    raw = raw,
                    isDecimal = node.isDecimal,
                    allowNegative = node.min == null || node.min!! < 0.0
                )
                textValue = filtered
                parseNumberOrNull(filtered)?.let { parsed ->
                    val clamped = clampNumber(parsed, node.min, node.max, node.isDecimal)
                    onEvent(UiEvent.ValueChanged(node.id, clamped))
                }
            },
            enabled = isEnabled,
            singleLine = true,
            label = node.label?.let { { Text(it, style = ToolkitTheme.typography.labelMedium) } },
            placeholder = node.placeholder?.let { { Text(it, style = ToolkitTheme.typography.bodyMedium) } },
            suffix = node.unitSuffix?.takeIf { it.isNotBlank() }?.let { unit ->
                {
                    Text(
                        text = unit,
                        style = ToolkitTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (node.isDecimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            shape = ToolkitTheme.shapes.medium,
            colors = sduiOutlinedTextFieldColors(),
            modifier = Modifier
                .weight(1f)
                .widthIn(min = ToolkitTheme.dimensions.sduiNumberInputMinWidth)
                .trackSduiTextInputFocus(node.id)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (!focusState.isFocused) {
                        val parsed = parseNumberOrNull(textValue)
                        if (parsed != null) {
                            val clamped = clampNumber(parsed, node.min, node.max, node.isDecimal)
                            textValue = formatNumberDisplay(clamped, node.isDecimal)
                        } else {
                            textValue = formatNumberDisplay(node.value, node.isDecimal)
                        }
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (!isEnabled || keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (canIncrement) applyStep(1)
                            true
                        }
                        Key.DirectionDown -> {
                            if (canDecrement) applyStep(-1)
                            true
                        }
                        else -> false
                    }
                }
        )

        val incrementLabel = stringResource(Res.string.sdui_number_increment)
        SduiTooltipBox(tooltip = incrementLabel) {
            IconButton(
                onClick = { applyStep(1) },
                enabled = canIncrement,
                modifier = Modifier
                    .semantics { contentDescription = incrementLabel }
                    .then(if (canIncrement) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                )
            }
        }
    }
}

/**
 * Search field with clear affordance, Enter submit handling, and optional suggestion menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RenderSearchBar(
    node: SearchBarNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastRevision by remember(node.id) { mutableStateOf(node.revision) }
    var localQuery by remember(node.id) { mutableStateOf(node.query) }
    var isFocused by remember(node.id) { mutableStateOf(false) }
    var suggestionsExpanded by remember(node.id) { mutableStateOf(false) }

    if (node.revision != lastRevision || node.forceOverwrite) {
        localQuery = node.query
        lastRevision = node.revision
    }

    val filteredSuggestions = remember(node.suggestions, localQuery) {
        val trimmed = localQuery.trim()
        if (trimmed.isEmpty()) {
            node.suggestions
        } else {
            node.suggestions.filter { it.contains(trimmed, ignoreCase = true) }
        }
    }

    val isEnabled = node.modifier.enabled
    val submitAction = {
        suggestionsExpanded = false
        onEvent(UiEvent.Click(node.id, node.action))
    }

    ExposedDropdownMenuBox(
        expanded = isEnabled && isFocused && suggestionsExpanded && filteredSuggestions.isNotEmpty(),
        onExpandedChange = { requested ->
            suggestionsExpanded = requested && filteredSuggestions.isNotEmpty()
        },
        modifier = modifier.withFormWidthConstraint(node.modifier)
    ) {
        OutlinedTextField(
            value = localQuery,
            onValueChange = { updated ->
                localQuery = updated
                suggestionsExpanded = node.suggestions.isNotEmpty()
                onEvent(UiEvent.TextChanged(node.id, updated))
            },
            enabled = isEnabled,
            singleLine = true,
            label = node.label?.let { { Text(it, style = ToolkitTheme.typography.labelMedium) } },
            placeholder = node.placeholder?.let { { Text(it, style = ToolkitTheme.typography.bodyMedium) } },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(Res.string.sdui_search_submit),
                    modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                )
            },
            trailingIcon = if (node.showClearButton && localQuery.isNotEmpty() && isEnabled) {
                {
                    val clearHint = stringResource(Res.string.sdui_search_clear)
                    SduiTooltipBox(tooltip = clearHint) {
                        IconButton(
                            onClick = {
                                localQuery = ""
                                suggestionsExpanded = node.suggestions.isNotEmpty()
                                onEvent(UiEvent.TextChanged(node.id, ""))
                            },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = clearHint,
                                modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                            )
                        }
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitAction() }),
            shape = ToolkitTheme.shapes.pill,
            colors = sduiOutlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, isEnabled)
                .fillMaxWidth()
                .trackSduiTextInputFocus(node.id)
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                    suggestionsExpanded = state.isFocused && node.suggestions.isNotEmpty()
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (!isEnabled || keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (keyEvent.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            submitAction()
                            true
                        }
                        Key.Escape -> {
                            suggestionsExpanded = false
                            true
                        }
                        else -> false
                    }
                }
        )

        ExposedDropdownMenu(
            expanded = isEnabled && isFocused && suggestionsExpanded && filteredSuggestions.isNotEmpty(),
            onDismissRequest = { suggestionsExpanded = false },
            shape = ToolkitTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            filteredSuggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion, style = ToolkitTheme.typography.bodyMedium) },
                    onClick = {
                        localQuery = suggestion
                        suggestionsExpanded = false
                        onEvent(UiEvent.TextChanged(node.id, suggestion))
                        if (node.action != null) {
                            onEvent(UiEvent.Click(node.id, node.action))
                        }
                    }
                )
            }
        }
    }
}

/**
 * Collapsible container panel with M3 spring reveal transition.
 */
@Composable
internal fun RenderAccordion(
    node: AccordionNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(node.id, node.expanded) { mutableStateOf(node.expanded) }
    val isEnabled = node.modifier.enabled
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val toggleAccordion = {
        if (isEnabled) {
            val nextExpanded = !expanded
            expanded = nextExpanded
            onEvent(UiEvent.ToggleChanged(node.id, nextExpanded))
        }
    }

    val actionLabel = stringResource(
        if (expanded) Res.string.sdui_accordion_collapse else Res.string.sdui_accordion_expand
    )

    Surface(
        modifier = modifier,
        shape = ToolkitTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isHovered && isEnabled) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
                    .hoverable(interactionSource = interactionSource, enabled = isEnabled)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = isEnabled,
                        onClick = toggleAccordion
                    )
                    .then(if (isEnabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                    .padding(horizontal = ToolkitTheme.spacing.md, vertical = ToolkitTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
            ) {
                node.leadingIcon?.let { iconName ->
                    Icon(
                        imageVector = SduiIconRegistry.resolve(iconName),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
                ) {
                    Text(
                        text = node.title,
                        style = ToolkitTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    node.subtitle?.takeIf { it.isNotBlank() }?.let { subtitleText ->
                        Text(
                            text = subtitleText,
                            style = ToolkitTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                node.badgeText?.takeIf { it.isNotBlank() }?.let { badge ->
                    val (bgColor, fgColor) = (node.badgeColor ?: SemanticColor.PRIMARY).toContainerPair()
                    Surface(
                        shape = ToolkitTheme.shapes.pill,
                        color = bgColor
                    ) {
                        Text(
                            text = badge,
                            style = ToolkitTheme.typography.labelSmall,
                            color = fgColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                horizontal = ToolkitTheme.spacing.sm,
                                vertical = ToolkitTheme.spacing.xxs
                            )
                        )
                    }
                }

                SduiTooltipBox(tooltip = actionLabel) {
                    IconButton(
                        onClick = toggleAccordion,
                        enabled = isEnabled,
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.denseIconButtonSize)
                            .then(if (isEnabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = actionLabel,
                            modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                    )
                    val contentSpacing = resolveContainerSpacing(SpacingSize.SMALL, node.children, isVertical = true)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = ToolkitTheme.dimensions.sduiAccordionIndent,
                                top = ToolkitTheme.spacing.sm,
                                end = ToolkitTheme.spacing.md,
                                bottom = ToolkitTheme.spacing.md
                            ),
                        verticalArrangement = Arrangement.spacedBy(contentSpacing)
                    ) {
                        RenderColumnChildren(
                            children = node.children,
                            onEvent = onEvent,
                            allowWeight = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Monospace source code / JSON block with line numbers, scrollbars, and clipboard copy feedback.
 */
@Composable
internal fun RenderCodeBlock(
    node: CodeBlockNode,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copiedRecently by remember(node.id) { mutableStateOf(false) }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(CODE_COPIED_FEEDBACK_MS)
            copiedRecently = false
        }
    }

    val lines = remember(node.code) {
        if (node.code.isEmpty()) listOf("") else node.code.lines()
    }
    val verticalState = rememberScrollState()
    val horizontalState = rememberScrollState()
    val maxHeight = node.maxHeightDp?.dp ?: ToolkitTheme.dimensions.sduiCodeDefaultMaxHeight

    Surface(
        modifier = modifier,
        shape = ToolkitTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ToolkitTheme.spacing.md, vertical = ToolkitTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
                ) {
                    val headerText = node.title
                        ?: node.language?.uppercase()
                        ?: stringResource(Res.string.sdui_code_default_title)
                    Text(
                        text = headerText,
                        style = ToolkitTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (!node.title.isNullOrBlank() && !node.language.isNullOrBlank()) {
                        Surface(
                            shape = ToolkitTheme.shapes.pill,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = node.language!!.uppercase(),
                                style = ToolkitTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(
                                    horizontal = ToolkitTheme.spacing.sm,
                                    vertical = ToolkitTheme.spacing.xxs
                                )
                            )
                        }
                    }

                    if (node.showCopyButton) {
                        val copyDescription = stringResource(
                            if (copiedRecently) Res.string.sdui_code_copied else Res.string.sdui_code_copy
                        )
                        SduiTooltipBox(tooltip = copyDescription) {
                            IconButton(
                                onClick = {
                                    copiedRecently = true
                                    scope.launch {
                                        clipboard.setClipEntry(PlatformUtils.clipEntryOf(node.code))
                                    }
                                },
                                modifier = Modifier
                                    .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = if (copiedRecently) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = copyDescription,
                                    tint = if (copiedRecently) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(verticalState)
                        .padding(
                            start = ToolkitTheme.spacing.sm,
                            top = ToolkitTheme.spacing.sm,
                            end = ToolkitTheme.dimensions.sduiScrollbarEndPadding,
                            bottom = if (node.wrapLines) ToolkitTheme.spacing.sm else ToolkitTheme.dimensions.sduiScrollbarEndPadding
                        )
                ) {
                    if (node.showLineNumbers) {
                        Column(
                            modifier = Modifier
                                .widthIn(min = ToolkitTheme.dimensions.sduiCodeLineNumberWidth)
                                .padding(end = ToolkitTheme.spacing.sm),
                            horizontalAlignment = Alignment.End
                        ) {
                            lines.forEachIndexed { index, _ ->
                                Text(
                                    text = (index + 1).toString(),
                                    style = ToolkitTheme.codeMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = ToolkitTheme.opacity.disabledContent
                                    ),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                        VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider),
                            modifier = Modifier
                                .padding(end = ToolkitTheme.spacing.sm)
                                .width(ToolkitTheme.dimensions.borderUnselected)
                        )
                    }

                    val codeTextModifier = if (node.wrapLines) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.horizontalScroll(horizontalState)
                    }
                    SelectionContainer(
                        modifier = Modifier
                            .weight(1f)
                            .pointerHoverIcon(PointerIcon.Text)
                            .then(codeTextModifier)
                    ) {
                        Text(
                            text = node.code,
                            style = ToolkitTheme.codeMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = node.wrapLines
                        )
                    }
                }

                PlatformVerticalScrollbar(
                    scrollState = verticalState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
                if (!node.wrapLines) {
                    PlatformHorizontalScrollbar(
                        scrollState = horizontalState,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Interactive lightbox dialog rendered when a zoomable [org.wip.plugintoolkit.api.sdui.ImageNode] is magnified.
 */
@Composable
internal fun SduiImageZoomDialog(
    asset: AssetReference,
    contentDescription: String?,
    onDismiss: () -> Unit
) {
    var zoomScale by remember { mutableStateOf(1.0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = ToolkitTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                ToolkitTheme.dimensions.borderUnselected,
                MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .sizeIn(
                    minWidth = ToolkitTheme.dimensions.sduiZoomDialogMinWidth,
                    minHeight = ToolkitTheme.dimensions.sduiZoomDialogMinHeight,
                    maxWidth = ToolkitTheme.dimensions.dialogMaxWidth,
                    maxHeight = ToolkitTheme.dimensions.dialogMaxHeight
                )
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = ToolkitTheme.spacing.md, vertical = ToolkitTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
                ) {
                    Text(
                        text = contentDescription ?: "",
                        style = ToolkitTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    val zoomOutLabel = stringResource(Res.string.sdui_image_zoom_out)
                    SduiTooltipBox(tooltip = zoomOutLabel) {
                        IconButton(
                            onClick = { zoomScale = (zoomScale - IMAGE_ZOOM_STEP).coerceIn(IMAGE_ZOOM_MIN, IMAGE_ZOOM_MAX) },
                            enabled = zoomScale > IMAGE_ZOOM_MIN,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = zoomOutLabel)
                        }
                    }

                    Text(
                        text = "${(zoomScale * 100f).toInt()}%",
                        style = ToolkitTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.xs)
                    )

                    val zoomInLabel = stringResource(Res.string.sdui_image_zoom_in)
                    SduiTooltipBox(tooltip = zoomInLabel) {
                        IconButton(
                            onClick = { zoomScale = (zoomScale + IMAGE_ZOOM_STEP).coerceIn(IMAGE_ZOOM_MIN, IMAGE_ZOOM_MAX) },
                            enabled = zoomScale < IMAGE_ZOOM_MAX,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = zoomInLabel)
                        }
                    }

                    val resetLabel = stringResource(Res.string.sdui_image_zoom_reset)
                    SduiTooltipBox(tooltip = resetLabel) {
                        IconButton(
                            onClick = { zoomScale = 1.0f },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = resetLabel)
                        }
                    }

                    val closeLabel = stringResource(Res.string.sdui_image_zoom_close)
                    SduiTooltipBox(tooltip = closeLabel) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = closeLabel)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center
                ) {
                    PluginAssetImage(
                        asset = asset,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ToolkitTheme.spacing.md)
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale
                            )
                    )
                }
            }
        }
    }
}

internal fun formatNumberDisplay(value: Double, isDecimal: Boolean): String {
    if (!isDecimal) return value.roundToLong().toString()
    val scaled = (value * DISPLAY_DECIMAL_SCALE).roundToLong()
    val rounded = scaled / DISPLAY_DECIMAL_SCALE
    val asLong = rounded.toLong()
    return if (rounded == asLong.toDouble()) {
        asLong.toString()
    } else {
        rounded.toString()
    }
}

private fun parseNumberOrNull(text: String): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed == "-" || trimmed == "." || trimmed == "-.") return null
    return trimmed.toDoubleOrNull()
}

private fun filterNumericText(raw: String, isDecimal: Boolean, allowNegative: Boolean): String {
    val builder = StringBuilder(raw.length)
    var hasDot = false
    for (i in raw.indices) {
        val ch = raw[i]
        when {
            ch.isDigit() -> builder.append(ch)
            ch == '-' && allowNegative && builder.isEmpty() -> builder.append(ch)
            ch == '.' && isDecimal && !hasDot -> {
                hasDot = true
                builder.append(ch)
            }
        }
    }
    return builder.toString()
}

private fun clampNumber(value: Double, min: Double?, max: Double?, isDecimal: Boolean): Double {
    var clamped = value
    if (min != null && clamped < min) clamped = min
    if (max != null && clamped > max) clamped = max
    return if (isDecimal) {
        (clamped * DECIMAL_PRECISION_SCALE).roundToLong() / DECIMAL_PRECISION_SCALE
    } else {
        clamped.roundToLong().toDouble()
    }
}

private fun adjustNumberByStep(
    current: Double,
    step: Double,
    direction: Int,
    min: Double?,
    max: Double?,
    isDecimal: Boolean
): Double {
    val candidate = current + (step * direction)
    return clampNumber(candidate, min, max, isDecimal)
}
