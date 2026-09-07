package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings

/**
 * A custom dropdown menu styled similarly to the Material 3 Expressive vertical menu.
 * 
 * Note: If you want to use the native new context menus in Compose Multiplatform,
 * you can enable it globally via:
 * `@OptIn(ExperimentalFoundationApi::class)`
 * `ComposeFoundationFlags.isNewContextMenuEnabled = true`
 */
@Composable
fun <T> ExpressiveMenu(
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(ToolkitTheme.shapes.large),
        shape = ToolkitTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = ToolkitTheme.spacing.none,
        shadowElevation = ToolkitTheme.dimensions.menuElevation
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = ToolkitTheme.spacing.xs,
                vertical = ToolkitTheme.spacing.xs
            ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ToolkitTheme.spacing.extraExtraSmall)
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ToolkitTheme.dimensions.standardButtonHeight)
                        .clip(ToolkitTheme.shapes.medium)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else ToolkitTheme.colors.transparent
                        )
                        .clickable { onOptionSelected(option) }
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                        }
                        .padding(horizontal = ToolkitTheme.spacing.mediumSmall),
                    contentAlignment = androidx.compose.ui.Alignment.CenterStart
                ) {
                    Text(
                        text = labelProvider(option),
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ExpressiveMenuPreview() {
    AppTheme(appearance = AppearanceSettings()) {
        Box(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            ExpressiveMenu(
                options = listOf("Option 1", "Option 2", "Option 3"),
                selectedOption = "Option 2",
                onOptionSelected = {},
                labelProvider = { it }
            )
        }
    }
}
