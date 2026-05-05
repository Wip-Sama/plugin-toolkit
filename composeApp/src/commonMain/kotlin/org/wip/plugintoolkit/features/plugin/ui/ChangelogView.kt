package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.logic.ChangelogVersion

enum class FilterLevel {
    Everything, Major, Minor, Patch
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogView(
    pluginName: String,
    versions: List<ChangelogVersion>,
    onClose: () -> Unit
) {
    var selectedLevel by remember { mutableStateOf(FilterLevel.Everything) }
    var selectedCategory by remember { mutableStateOf("General") }

    // Version Level Logic
    val allVersions = versions.map { it.version }
    val majors = allVersions.map { it.split(".").first() }.distinct()
    val minors = allVersions.map { v ->
        val parts = v.split(".")
        if (parts.size >= 2) "${parts[0]}.${parts[1]}" else parts[0]
    }.distinct()

    var selectedMajor by remember { mutableStateOf(majors.firstOrNull() ?: "") }
    var selectedMinor by remember { mutableStateOf(minors.firstOrNull() ?: "") }
    var selectedPatch by remember { mutableStateOf(allVersions.firstOrNull() ?: "") }

    val filteredByLevel = when (selectedLevel) {
        FilterLevel.Everything -> versions
        FilterLevel.Major -> versions.filter { it.version.startsWith("$selectedMajor.") || it.version == selectedMajor }
        FilterLevel.Minor -> versions.filter { it.version.startsWith("$selectedMinor.") || it.version == selectedMinor }
        FilterLevel.Patch -> versions.filter { it.version == selectedPatch }
    }

    // Category Logic
    val allCategories = remember(filteredByLevel) {
        val cats = mutableSetOf<String>()
        filteredByLevel.forEach { v -> cats.addAll(v.tags.keys) }
        listOf("General") + cats.toList().sorted()
    }

    if (selectedCategory !in allCategories) {
        selectedCategory = "General"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ToolkitTheme.spacing.medium)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                Text(
                    "Changelog: $pluginName",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Filters Row 1: Level + Dropdown
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    FilterLevel.entries.forEach { level ->
                        FilterChip(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            label = { Text(level.name) },
                            leadingIcon = if (selectedLevel == level) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Dropdown for the selected level
                    if (selectedLevel != FilterLevel.Everything) {
                        var expanded by remember { mutableStateOf(false) }
                        val options = when (selectedLevel) {
                            FilterLevel.Major -> majors.map { "v$it.x.x" }
                            FilterLevel.Minor -> minors.map { "v$it.x" }
                            FilterLevel.Patch -> allVersions.map { "v$it" }
                            else -> emptyList()
                        }
                        val currentText = when (selectedLevel) {
                            FilterLevel.Major -> "v$selectedMajor.x.x"
                            FilterLevel.Minor -> "v$selectedMinor.x"
                            FilterLevel.Patch -> "v$selectedPatch"
                            else -> ""
                        }

                        Box {
                            OutlinedCard(
                                onClick = { expanded = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(currentText, style = MaterialTheme.typography.labelLarge)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }

                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                options.forEachIndexed { index, option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            when (selectedLevel) {
                                                FilterLevel.Major -> selectedMajor = majors[index]
                                                FilterLevel.Minor -> selectedMinor = minors[index]
                                                FilterLevel.Patch -> selectedPatch = allVersions[index]
                                                else -> {}
                                            }
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

                // Filters Row 2: Categories
                SecondaryScrollableTabRow(
                    selectedTabIndex = allCategories.indexOf(selectedCategory).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = {
                        val index = allCategories.indexOf(selectedCategory).coerceAtLeast(0)
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(index),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    allCategories.forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            text = {
                                Text(
                                    category,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selectedCategory == category) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }
        }

        // Changelog List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ToolkitTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
        ) {
            val displayVersions = filteredByLevel.filter { v ->
                selectedCategory == "General" || v.tags.containsKey(selectedCategory)
            }

            items(displayVersions) { version ->
                VersionCard(version, selectedCategory)
            }
        }
    }
}

@Composable
fun VersionCard(
    version: ChangelogVersion,
    selectedCategory: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Version ${version.version}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        version.date,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

            if (selectedCategory == "General") {
                version.tags.forEach { (tag, voices) ->
                    CategoryGroup(tag, voices)
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                }
            } else {
                version.tags[selectedCategory]?.let { voices ->
                    CategoryGroup(selectedCategory, voices, showHeader = false)
                }
            }
        }
    }
}

@Composable
fun CategoryGroup(name: String, voices: List<String>, showHeader: Boolean = true) {
    Column {
        if (showHeader) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        voices.forEach { voice ->
            Row(modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                Text(
                    voice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
