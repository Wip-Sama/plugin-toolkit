package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_collapse_menu
import plugintoolkit.composeapp.generated.resources.action_expand_menu

interface OverlayHost {
    fun show(bounds: Rect, onDismiss: () -> Unit, content: @Composable () -> Unit)
    fun hide()
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost?> { null }

@Composable
fun <T> UnscaledExpressiveMenu(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    enabled: Boolean = true,
    disabledOptions: Set<T> = emptySet()
) {
    var isExpanded by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val overlay = LocalOverlayHost.current
    val density = LocalDensity.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                bounds = coordinates.boundsInRoot()
            }
            .clickable(enabled = enabled) {
                isExpanded = true
            }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.medium, vertical = ToolkitTheme.spacing.small).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = labelProvider(selectedOption),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (isExpanded) Res.string.action_collapse_menu
                        else Res.string.action_expand_menu
                    )
                )
            }
        }
    }
    
    LaunchedEffect(isExpanded, bounds) {
        if (isExpanded && bounds != null && overlay != null) {
            overlay.show(
                bounds = bounds!!,
                onDismiss = { isExpanded = false }
            ) {
                val menuWidthDp = with(density) { bounds!!.width.toDp() }
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = ToolkitTheme.dimensions.menuElevation),
                    shape = ToolkitTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.widthIn(min = maxOf(menuWidthDp, ToolkitTheme.dimensions.menuMinWidth)).clip(ToolkitTheme.shapes.large)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = ToolkitTheme.spacing.xs,
                            vertical = ToolkitTheme.spacing.xs
                        ),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraExtraSmall)
                    ) {
                        options.forEach { option ->
                            val isDisabled = disabledOptions.contains(option)
                            val isSelected = option == selectedOption
                            DropdownMenuItem(
                                text = { Text(labelProvider(option), style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    if (!isDisabled) {
                                        onOptionSelected(option)
                                        isExpanded = false
                                    }
                                },
                                modifier = Modifier
                                    .height(ToolkitTheme.dimensions.standardButtonHeight)
                                    .clip(ToolkitTheme.shapes.medium)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                        else ToolkitTheme.colors.transparent
                                    ),
                                colors = MenuDefaults.itemColors(
                                    textColor = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.settingsItemHover)
                                                else if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                ),
                                enabled = !isDisabled,
                                contentPadding = PaddingValues(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = ToolkitTheme.spacing.none)
                            )
                        }
                    }
                }
            }
        } else if (!isExpanded && overlay != null) {
            overlay.hide()
        }
    }
}
