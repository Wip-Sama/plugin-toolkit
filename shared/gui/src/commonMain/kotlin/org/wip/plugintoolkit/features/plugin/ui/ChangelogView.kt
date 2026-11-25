package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import org.wip.plugintoolkit.shared.components.ToolkitCard
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenu
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontWeight
import org.wip.plugintoolkit.api.Release
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.ui.MarkdownText
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_close
import plugintoolkit.composeapp.generated.resources.changelog_filter_everything
import plugintoolkit.composeapp.generated.resources.changelog_filter_general
import plugintoolkit.composeapp.generated.resources.changelog_filter_major
import plugintoolkit.composeapp.generated.resources.changelog_filter_minor
import plugintoolkit.composeapp.generated.resources.changelog_filter_patch
import plugintoolkit.composeapp.generated.resources.changelog_title_prefix
import plugintoolkit.composeapp.generated.resources.changelog_version_title

enum class FilterLevel {
    Everything, Major, Minor, Patch
}

@Composable
fun ChangelogView(
    pluginName: String,
    versions: List<Release>,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ToolkitTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
    ) {
        // Header matching UpdateDialog
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(ToolkitTheme.dimensions.updateIconContainerSize),
                shape = ToolkitTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumLarge)
                    )
                }
            }

            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.changelog_title_prefix, pluginName),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                versions.firstOrNull()?.let { latest ->
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                    ) {
                        Text(
                            text = stringResource(Res.string.changelog_version_title, latest.version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (latest.date.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = ToolkitTheme.shapes.small
                            ) {
                                Text(
                                    text = latest.date,
                                    modifier = Modifier.padding(
                                        horizontal = ToolkitTheme.spacing.small,
                                        vertical = ToolkitTheme.spacing.extraSmall
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            val closeLabel = stringResource(Res.string.action_close)
            ToolkitTooltipBox(tooltip = closeLabel) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = closeLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Tonal well containing filters & changelog release cards
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = ToolkitTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            ChangelogContent(
                versions = versions,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = ToolkitTheme.spacing.small),
                showLevelFilter = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangelogContent(
    versions: List<Release>,
    modifier: Modifier = Modifier,
    showLevelFilter: Boolean = true,
    useCards: Boolean = true,
    showVersionHeader: Boolean = true
) {
    var selectedLevel by remember { mutableStateOf(FilterLevel.Everything) }
    var selectedCategory by remember { mutableStateOf("General") }

    // Version Level Logic
    val allVersions = remember(versions) { versions.map { it.version } }
    val majors = remember(allVersions) { allVersions.map { it.split(".").first() }.distinct() }
    val minors = remember(allVersions) {
        allVersions.map { v ->
            val parts = v.split(".")
            if (parts.size >= 2) "${parts[0]}.${parts[1]}" else parts[0]
        }.distinct()
    }

    var selectedMajor by remember(majors) { mutableStateOf(majors.firstOrNull() ?: "") }
    var selectedMinor by remember(minors) { mutableStateOf(minors.firstOrNull() ?: "") }
    var selectedPatch by remember(allVersions) { mutableStateOf(allVersions.firstOrNull() ?: "") }

    val filteredByLevel = when (selectedLevel) {
        FilterLevel.Everything -> versions
        FilterLevel.Major -> versions.filter { it.version.startsWith("$selectedMajor.") || it.version == selectedMajor }
        FilterLevel.Minor -> versions.filter { it.version.startsWith("$selectedMinor.") || it.version == selectedMinor }
        FilterLevel.Patch -> versions.filter { it.version == selectedPatch }
    }

    // Category Logic
    val allCategories = remember(filteredByLevel) {
        val cats = mutableSetOf<String>()
        filteredByLevel.forEach { v -> cats.addAll(v.categories.keys) }
        listOf("General") + cats.toList().sorted()
    }

    if (selectedCategory !in allCategories) {
        selectedCategory = "General"
    }

    val shouldShowLevelFilter = showLevelFilter && versions.size > 1
    val shouldShowCategoryFilter = allCategories.size > 1

    Column(modifier = modifier) {
        if (shouldShowLevelFilter || shouldShowCategoryFilter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ToolkitTheme.spacing.extraSmall)
            ) {
                if (shouldShowLevelFilter) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ToolkitTheme.spacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterLevel.entries.forEach { level ->
                            FilterChip(
                                selected = selectedLevel == level,
                                onClick = { selectedLevel = level },
                                label = {
                                    Text(
                                        when (level) {
                                            FilterLevel.Everything -> stringResource(Res.string.changelog_filter_everything)
                                            FilterLevel.Major -> stringResource(Res.string.changelog_filter_major)
                                            FilterLevel.Minor -> stringResource(Res.string.changelog_filter_minor)
                                            FilterLevel.Patch -> stringResource(Res.string.changelog_filter_patch)
                                        }
                                    )
                                },
                                leadingIcon = if (selectedLevel == level) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                        )
                                    }
                                } else null
                            )
                        }

                        // Dropdown for the selected level
                        if (selectedLevel != FilterLevel.Everything) {
                            var expanded by remember { mutableStateOf(false) }
                            val options = when (selectedLevel) {
                                FilterLevel.Major -> majors.map { "v$it.x.x" }
                                FilterLevel.Minor -> minors.map { "v$it.x" }
                                FilterLevel.Patch -> versions.map {
                                    if (!it.versionName.isNullOrBlank()) "v${it.version} (${it.versionName})" else "v${it.version}"
                                }
                                else -> emptyList()
                            }
                            val currentText = when (selectedLevel) {
                                FilterLevel.Major -> "v$selectedMajor.x.x"
                                FilterLevel.Minor -> "v$selectedMinor.x"
                                FilterLevel.Patch -> {
                                    val rel = versions.firstOrNull { it.version == selectedPatch }
                                    if (rel != null && !rel.versionName.isNullOrBlank()) "v$selectedPatch (${rel.versionName})" else "v$selectedPatch"
                                }
                                else -> ""
                            }

                            Box {
                                Surface(
                                    onClick = { expanded = true },
                                    shape = ToolkitTheme.shapes.pill,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = ToolkitTheme.spacing.mediumSmall,
                                            vertical = ToolkitTheme.spacing.small
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(currentText, style = MaterialTheme.typography.labelLarge)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }

                                ToolkitDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    options.forEachIndexed { index, option ->
                                        val isSelected = option == currentText
                                        ToolkitDropdownMenuItem(
                                            text = { Text(option) },
                                            isSelected = isSelected,
                                            onClick = {
                                                when (selectedLevel) {
                                                    FilterLevel.Major -> selectedMajor = majors[index]
                                                    FilterLevel.Minor -> selectedMinor = minors[index]
                                                    FilterLevel.Patch -> selectedPatch = versions[index].version
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
                }

                if (shouldShowLevelFilter && shouldShowCategoryFilter) {
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                }

                // Filters Row 2: Categories
                if (shouldShowCategoryFilter) {
                    SecondaryScrollableTabRow(
                        selectedTabIndex = allCategories.indexOf(selectedCategory).coerceAtLeast(0),
                        edgePadding = ToolkitTheme.spacing.none,
                        containerColor = ToolkitTheme.colors.transparent,
                        divider = {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                            )
                        },
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
                                        if (category == "General") stringResource(Res.string.changelog_filter_general) else category,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selectedCategory == category) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Changelog List
        val displayVersions = filteredByLevel.filter { v ->
            selectedCategory == "General" || v.categories.containsKey(selectedCategory)
        }

        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = Triple(selectedLevel, selectedCategory, displayVersions.size),
            transitionSpec = {
                fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "changelogContentFilter"
        ) {
            val listState = rememberLazyListState()
            val scrollbarReserve = if (listState.canScrollForward || listState.canScrollBackward) {
                ToolkitTheme.dimensions.sduiScrollbarEndPadding
            } else {
                ToolkitTheme.spacing.none
            }
            Box(modifier = Modifier.fillMaxSize()) {
                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    val horizontalPadding = if (useCards) ToolkitTheme.spacing.medium else ToolkitTheme.spacing.none
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = horizontalPadding,
                            end = horizontalPadding + scrollbarReserve,
                            top = ToolkitTheme.spacing.medium,
                            bottom = ToolkitTheme.spacing.large
                        ),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                    ) {
                        items(displayVersions, key = { it.version }) { version ->
                            if (useCards) {
                                VersionCard(version, selectedCategory)
                            } else {
                                VersionContent(version, selectedCategory, showVersionHeader = showVersionHeader)
                            }
                        }
                    }
                }
                PlatformVerticalScrollbar(
                    lazyListState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
fun VersionCard(
    version: Release,
    selectedCategory: String
) {
    ToolkitCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ToolkitTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        VersionContent(version, selectedCategory, showVersionHeader = true)
    }
}

@Composable
fun VersionContent(
    version: Release,
    selectedCategory: String,
    showVersionHeader: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showVersionHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        stringResource(Res.string.changelog_version_title, version.version),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!version.versionName.isNullOrBlank()) {
                        Text(
                            "— \"${version.versionName}\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                if (version.date.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = ToolkitTheme.shapes.small
                    ) {
                        Text(
                            version.date,
                            modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
        }

        if (selectedCategory == "General") {
            version.categories.forEach { (tag, voices) ->
                CategoryGroup(tag, voices)
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.mediumSmall))
            }
        } else {
            version.categories[selectedCategory]?.let { voices ->
                CategoryGroup(selectedCategory, voices, showHeader = false)
            }
        }
    }
}

@Composable
fun CategoryGroup(name: String, voices: List<String>, showHeader: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showHeader) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.textFieldContainer),
                shape = ToolkitTheme.shapes.small,
                modifier = Modifier.padding(bottom = ToolkitTheme.spacing.small)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        horizontal = ToolkitTheme.spacing.small,
                        vertical = ToolkitTheme.spacing.extraSmall
                    )
                )
            }
        }
        voices.forEach { voice ->
            Row(
                modifier = Modifier.padding(
                    start = ToolkitTheme.spacing.extraSmall,
                    bottom = ToolkitTheme.spacing.small
                )
            ) {
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                )
                MarkdownText(
                    text = voice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
