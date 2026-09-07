package org.wip.plugintoolkit.shared.components.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import org.wip.plugintoolkit.core.theme.ToolkitTheme

/**
 * Material 3 Expressive Dropdown Menu container.
 *
 * Implements M3 Expressive specifications:
 * - 16dp rounded corner container ([ToolkitTheme.shapes.large])
 * - Container color: [MaterialTheme.colorScheme.surfaceContainerHigh]
 * - Shadow elevation: [ToolkitTheme.dimensions.menuElevation] (4dp)
 * - Tonal elevation: 0dp
 * - Internal padding: 4dp ([ToolkitTheme.spacing.xs])
 * - Vertical item spacing: 2dp ([ToolkitTheme.spacing.extraExtraSmall])
 * - Natural unconstrained width expansion beyond trigger
 */
@Composable
fun ToolkitDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .widthIn(min = ToolkitTheme.dimensions.menuMinWidth)
            .clip(ToolkitTheme.shapes.large),
        offset = offset,
        properties = properties,
        shape = ToolkitTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = ToolkitTheme.spacing.none,
        shadowElevation = ToolkitTheme.dimensions.menuElevation
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = ToolkitTheme.spacing.xs,
                vertical = ToolkitTheme.spacing.xs
            ),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraExtraSmall)
        ) {
            content()
        }
    }
}

/**
 * Material 3 Expressive Menu Item.
 *
 * Conforms to M3 Expressive guidelines:
 * - 12dp rounded corner pill ([ToolkitTheme.shapes.medium])
 * - 40dp height ([ToolkitTheme.dimensions.standardButtonHeight])
 * - Tonal selection highlighting using `secondaryContainer`
 * - Subtle hover state highlight
 * - Semantic error styling for destructive actions
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ToolkitDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    isDestructive: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = ToolkitTheme.spacing.mediumSmall,
        vertical = ToolkitTheme.spacing.none
    )
) {
    var isHovered by remember { mutableStateOf(false) }
    val itemShape = ToolkitTheme.shapes.medium

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.subtleHighlight)
        else -> ToolkitTheme.colors.transparent
    }

    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.settingsItemHover)
        isDestructive -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val leadingIconColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.settingsItemHover)
        isDestructive -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    DropdownMenuItem(
        text = {
            CompositionLocalProvider(
                LocalContentColor provides contentColor,
                LocalTextStyle provides MaterialTheme.typography.bodyMedium
            ) {
                text()
            }
        },
        onClick = onClick,
        modifier = modifier
            .height(ToolkitTheme.dimensions.standardButtonHeight)
            .clip(itemShape)
            .background(backgroundColor)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
        leadingIcon = leadingIcon?.let { icon ->
            {
                CompositionLocalProvider(LocalContentColor provides leadingIconColor) {
                    icon()
                }
            }
        },
        trailingIcon = trailingIcon?.let { icon ->
            {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    icon()
                }
            }
        },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = contentColor,
            leadingIconColor = leadingIconColor,
            trailingIconColor = contentColor,
            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.settingsItemHover),
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.settingsItemHover),
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.settingsItemHover)
        ),
        contentPadding = contentPadding
    )
}

/**
 * Divider item designed for use inside [ToolkitDropdownMenu].
 */
@Composable
fun ToolkitDropdownDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(
            horizontal = ToolkitTheme.spacing.small,
            vertical = ToolkitTheme.spacing.extraSmall
        ),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
    )
}
