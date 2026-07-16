package org.wip.plugintoolkit.shared.components.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.*

@Deprecated(
    message = "This is a workaround for the Material 3 Expressive Dropdown menu. Migrate to native when JetBrains drops support.",
    level = DeprecationLevel.WARNING
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExpressiveMenu(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    enabled: Boolean = true,
    disabledOptions: Set<T> = emptySet(),
    gapAfter: (T) -> Boolean = { false }
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        Surface(
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.medium, vertical = ToolkitTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = labelProvider(selectedOption),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }


        // Determine container shape
        val containerRadius = ToolkitTheme.spacing.medium
        val itemNormalRadius = ToolkitTheme.spacing.small
        val itemInnerRadius = ToolkitTheme.spacing.small

        val groups = mutableListOf<List<T>>()
        var currentGroup = mutableListOf<T>()
        options.forEach { option ->
            currentGroup.add(option)
            if (gapAfter(option)) {
                groups.add(currentGroup)
                currentGroup = mutableListOf<T>()
            }
        }
        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup)
        }

        ExposedDropdownMenu(
            expanded = expanded,
            modifier = Modifier,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(containerRadius),
            shadowElevation = ToolkitTheme.spacing.none,
            containerColor = ToolkitTheme.colors.transparent
        ) {
            groups.forEachIndexed { groupIndex, group ->
                val topRadius = if (groupIndex == 0) containerRadius else itemInnerRadius
                val bottomRadius = if (groupIndex == groups.lastIndex) containerRadius else itemInnerRadius
                val surfaceShape = RoundedCornerShape(
                    topStart = topRadius,
                    topEnd = topRadius,
                    bottomStart = bottomRadius,
                    bottomEnd = bottomRadius
                )

                Surface(
                    shape = surfaceShape,
                    color = MenuDefaults.containerColor,
                    shadowElevation = ToolkitTheme.spacing.medium,
                    modifier = Modifier.padding(bottom = if (groupIndex != groups.lastIndex) ToolkitTheme.spacing.extraSmall else ToolkitTheme.spacing.none)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = ToolkitTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.badgeVertical)
                    ) {
                        group.forEachIndexed { index, option ->
                            val isDisabled = option in disabledOptions
                            val isSelected = option == selectedOption
                            
                            val itemShape = RoundedCornerShape(itemNormalRadius)
            
                            DropdownMenuItem(
                                text = { Text(labelProvider(option)) },
                                onClick = {
                                    if (!isDisabled) {
                                        onOptionSelected(option)
                                        expanded = false
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = ToolkitTheme.spacing.small)
                                    .height(ToolkitTheme.dimensions.menuItem)
                                    .clip(itemShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else ToolkitTheme.colors.transparent
                                    ),
                                colors = MenuDefaults.itemColors(
                                    textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                                else MaterialTheme.colorScheme.onSurface
                                ),
                                enabled = !isDisabled,
                                contentPadding = PaddingValues(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = ToolkitTheme.spacing.none)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ExpressiveMenuPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(ToolkitTheme.spacing.extraLarge)) {
            ExpressiveMenu(
                options = listOf("System", "Light", "Dark", "Amoled"),
                selectedOption = "Amoled",
                onOptionSelected = {},
                labelProvider = { it },
                gapAfter = { it == "Light" }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun ExpandedExpressiveMenuPreview() {
    MaterialTheme {
        Box() {
            Column {
                // First Menu Block
                Surface(
                    shape = RoundedCornerShape(
                        topStart = ToolkitTheme.spacing.medium, 
                        topEnd = ToolkitTheme.spacing.medium, 
                        bottomStart = ToolkitTheme.spacing.extraSmall, 
                        bottomEnd = ToolkitTheme.spacing.extraSmall
                    ),
                    color = MenuDefaults.containerColor,
                    shadowElevation = ToolkitTheme.spacing.medium,
                    modifier = Modifier.padding(bottom = ToolkitTheme.spacing.extraSmall)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = ToolkitTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.badgeVertical)
                    ) {
                        // 1. Normal Item
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.theme_system)) },
                            onClick = {},
                            modifier = Modifier
                                .padding(horizontal = ToolkitTheme.spacing.small)
                                .height(ToolkitTheme.dimensions.menuItem)
                                .clip(RoundedCornerShape(ToolkitTheme.spacing.extraSmall)),
                            contentPadding = PaddingValues(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = ToolkitTheme.spacing.none)
                        )
                        
                        HorizontalDivider()
                        
                        // 2. Hovered Item
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.theme_light)) },
                            onClick = {},
                            modifier = Modifier
                                .padding(horizontal = ToolkitTheme.spacing.small)
                                .height(ToolkitTheme.dimensions.menuItem)
                                .clip(RoundedCornerShape(ToolkitTheme.spacing.extraSmall))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.subtleHighlight)),
                            contentPadding = PaddingValues(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = ToolkitTheme.spacing.none)
                        )
                    }
                }
                
                // Second Menu Block
                Surface(
                    shape = RoundedCornerShape(
                        topStart = ToolkitTheme.spacing.extraSmall, 
                        topEnd = ToolkitTheme.spacing.extraSmall, 
                        bottomStart = ToolkitTheme.spacing.medium, 
                        bottomEnd = ToolkitTheme.spacing.medium
                    ),
                    color = MenuDefaults.containerColor,
                    shadowElevation = ToolkitTheme.spacing.medium
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = ToolkitTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.badgeVertical)
                    ) {
                        // 3. Normal Item
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.theme_dark)) },
                            onClick = {},
                            modifier = Modifier
                                .padding(horizontal = ToolkitTheme.spacing.small)
                                .height(ToolkitTheme.dimensions.menuItem)
                                .clip(RoundedCornerShape(ToolkitTheme.spacing.extraSmall)),
                            contentPadding = PaddingValues(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = ToolkitTheme.spacing.none)
                        )
                        
                        // 4. Selected Item
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.theme_amoled)) },
                            onClick = {},
                            modifier = Modifier
                                .padding(horizontal = ToolkitTheme.spacing.small)
                                .height(ToolkitTheme.dimensions.menuItem)
                                .clip(RoundedCornerShape(ToolkitTheme.spacing.extraSmall))
                                .background(MaterialTheme.colorScheme.primary),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = ToolkitTheme.spacing.none)
                        )
                    }
                }
            }
        }
    }
}


