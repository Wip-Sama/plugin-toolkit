package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.theme.rememberMorphShape
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_close
import plugintoolkit.composeapp.generated.resources.sdui_accordion_collapse
import plugintoolkit.composeapp.generated.resources.sdui_accordion_expand
import plugintoolkit.composeapp.generated.resources.sdui_cancel
import plugintoolkit.composeapp.generated.resources.sdui_confirm
import plugintoolkit.composeapp.generated.resources.sdui_search_clear

/**
 * Standardized Material 3 status pill badge for desktop views.
 */
@Composable
fun ToolkitStatusBadge(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    shape: Shape = ToolkitTheme.shapes.pill
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = ToolkitTheme.opacity.subtleHighlight),
        shape = shape,
        border = BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            color.copy(alpha = ToolkitTheme.opacity.borderLow)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ToolkitTheme.spacing.sm,
                vertical = ToolkitTheme.spacing.xxs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
        ) {
            leadingIcon?.let { iconVector ->
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall)
                )
            }
            Text(
                text = text,
                style = ToolkitTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = ToolkitTheme.dimensions.sduiBadgeMaxWidth)
            )
        }
    }
}

private val EMPTY_STATE_SHAPE_BADGE_SIZE = 72.dp

/**
 * Standardized empty state view for desktop screens and panes with a restrained Material Shape
 * container backing its hero icon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolkitEmptyState(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val badgeShape = rememberMorphShape(
        from = MaterialShapes.Cookie7Sided,
        to = MaterialShapes.Cookie7Sided,
        progress = 0f
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ToolkitTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
    ) {
        Surface(
            modifier = Modifier.size(EMPTY_STATE_SHAPE_BADGE_SIZE),
            shape = badgeShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.disabled),
                    modifier = Modifier.size(ToolkitTheme.dimensions.emptyStateIconSize)
                )
            }
        }
        Text(
            text = title,
            style = ToolkitTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        description?.let { desc ->
            Text(
                text = desc,
                style = ToolkitTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Button(
                onClick = onAction,
                shape = ToolkitTheme.shapes.pill,
                modifier = Modifier
                    .padding(top = ToolkitTheme.spacing.xs)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(text = actionLabel, style = ToolkitTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Standardized desktop search field with clear affordance, optional result count pill,
 * and optional focus requester for Ctrl+F bindings.
 */
@Composable
fun ToolkitSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    resultCountText: String? = null,
    onSearchSubmit: ((String) -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true
) {
    val clearLabel = stringResource(Res.string.sdui_search_clear)
    val baseModifier = modifier.fillMaxWidth()
    val fieldModifier = if (focusRequester != null) {
        baseModifier.focusRequester(focusRequester)
    } else {
        baseModifier
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = fieldModifier,
        enabled = enabled,
        singleLine = true,
        shape = ToolkitTheme.shapes.large,
        label = label?.let { { Text(text = it, style = ToolkitTheme.typography.labelMedium) } },
        placeholder = placeholder?.let { { Text(text = it, style = ToolkitTheme.typography.bodyMedium) } },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
                modifier = Modifier.padding(end = ToolkitTheme.spacing.xs)
            ) {
                resultCountText?.let { countText ->
                    ToolkitStatusBadge(
                        text = countText,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (query.isNotEmpty() && enabled) {
                    ToolkitTooltipBox(tooltip = clearLabel) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = clearLabel,
                                modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                            )
                        }
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { onSearchSubmit?.invoke(query) }
        )
    )
}

/**
 * Reusable expandable accordion container using MotionScheme spring physics.
 */
@Composable
fun ToolkitAccordion(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
    )
    val toggleTooltip = stringResource(
        if (expanded) Res.string.sdui_accordion_collapse else Res.string.sdui_accordion_expand
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ToolkitTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ToolkitTooltipBox(tooltip = toggleTooltip) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onExpandedChange(!expanded) }
                        .padding(
                            horizontal = ToolkitTheme.spacing.md,
                            vertical = ToolkitTheme.spacing.sm
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
                ) {
                    leadingIcon?.let { iconVector ->
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
                    ) {
                        Text(
                            text = title,
                            style = ToolkitTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        subtitle?.let { sub ->
                            Text(
                                text = sub,
                                style = ToolkitTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    badgeText?.let { badge ->
                        ToolkitStatusBadge(text = badge, color = badgeColor)
                    }
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = toggleTooltip,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.iconSmall)
                            .graphicsLayer { rotationZ = rotation }
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        thickness = ToolkitTheme.dimensions.borderThin,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ToolkitTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm),
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * Standardized Material 3 confirmation dialog with optional destructive styling.
 */
@Composable
fun ToolkitConfirmDialog(
    isOpen: Boolean,
    title: String,
    message: String? = null,
    confirmLabel: String = stringResource(Res.string.sdui_confirm),
    dismissLabel: String? = stringResource(Res.string.sdui_cancel),
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    bodyContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ToolkitTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(text = title, style = ToolkitTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
            ) {
                message?.let { msg ->
                    Text(
                        text = msg,
                        style = ToolkitTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                bodyContent?.invoke(this)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = ToolkitTheme.shapes.pill,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(text = confirmLabel, style = ToolkitTheme.typography.labelLarge)
            }
        },
        dismissButton = dismissLabel?.let { label ->
            {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = ToolkitTheme.shapes.pill,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(text = label, style = ToolkitTheme.typography.labelLarge)
                }
            }
        }
    )
}

/**
 * Reusable Material 3 co-planar side sheet capped at 380dp (under the 400dp desktop spec).
 */
@Composable
fun ToolkitSideSheet(
    isOpen: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier
    ) {
        val closeLabel = stringResource(Res.string.action_close)
        val scrollState = rememberScrollState()
        val scrollbarReserve = if (scrollState.maxValue > 0) {
            ToolkitTheme.dimensions.sduiScrollbarEndPadding
        } else {
            ToolkitTheme.spacing.none
        }

        Surface(
            modifier = Modifier
                .width(ToolkitTheme.dimensions.sduiSideSheetWidth)
                .fillMaxHeight()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            shape = ToolkitTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                ToolkitTheme.dimensions.borderUnselected,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = ToolkitTheme.spacing.md,
                            vertical = ToolkitTheme.spacing.sm
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
                ) {
                    Text(
                        text = title,
                        style = ToolkitTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides ToolkitTheme.dimensions.desktopTouchTargetSize
                    ) {
                        ToolkitTooltipBox(tooltip = closeLabel) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = closeLabel,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = ToolkitTheme.dimensions.borderUnselected,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(
                                start = ToolkitTheme.spacing.md,
                                top = ToolkitTheme.spacing.md,
                                bottom = ToolkitTheme.spacing.md,
                                end = ToolkitTheme.spacing.md + scrollbarReserve
                            ),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm),
                        content = content
                    )
                    PlatformVerticalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
            }
        }
    }
}
