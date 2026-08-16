package org.wip.plugintoolkit.features.repository.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.repository.viewmodel.RepoTypeTab
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_refresh
import plugintoolkit.composeapp.generated.resources.action_remove
import plugintoolkit.composeapp.generated.resources.repo_action_open_folder
import plugintoolkit.composeapp.generated.resources.repo_btn_add_repository
import plugintoolkit.composeapp.generated.resources.repo_filter_repositories_placeholder
import plugintoolkit.composeapp.generated.resources.repo_refresh_all
import plugintoolkit.composeapp.generated.resources.repo_share_link_desc

@Composable
fun PluginRepoSidebar(
    repositories: List<ExtensionRepo>,
    selectedRepo: ExtensionRepo?,
    isRefreshing: Boolean,
    repoTypeTab: RepoTypeTab,
    totalRepoCount: Int,
    remoteRepoCount: Int,
    localRepoCount: Int,
    repoSearchQuery: String,
    onRepoTypeTabChange: (RepoTypeTab) -> Unit,
    onRepoSearchQueryChange: (String) -> Unit,
    onRepoSelected: (ExtensionRepo) -> Unit,
    onRemoveRepo: (ExtensionRepo) -> Unit,
    onCopyLink: (String) -> Unit,
    onRefreshRepo: (ExtensionRepo) -> Unit,
    onRefreshAll: () -> Unit,
    onOpenAddDialog: () -> Unit,
    onOpenLocalFolder: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    Column(
        modifier = Modifier
            .width(ToolkitTheme.dimensions.repositorySidebarWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.sidebarBackground))
            .padding(ToolkitTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.smallMedium)
    ) {
        // Material 3 Segmented Tabs: All, Remote, Local
        RepoSegmentedButton(
            selectedTab = repoTypeTab,
            totalCount = totalRepoCount,
            remoteCount = remoteRepoCount,
            localCount = localRepoCount,
            onTabSelected = onRepoTypeTabChange
        )

        // Single Full-Width Add Repository Button
        Button(
            onClick = onOpenAddDialog,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
            Text(
                stringResource(Res.string.repo_btn_add_repository),
                style = MaterialTheme.typography.labelMedium
            )
        }

        // Mini Search Box
        ToolkitTextField(
            value = repoSearchQuery,
            onValueChange = onRepoSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    stringResource(Res.string.repo_filter_repositories_placeholder),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))

        // Repository List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            items(repositories) { repo ->
                val isSelected = selectedRepo?.url == repo.url
                var showMenu by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onRepoSelected(repo) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        ToolkitTheme.colors.transparent
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = ToolkitTheme.spacing.smallMedium,
                                vertical = ToolkitTheme.spacing.small
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon Badge (Cloud vs Folder)
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.cardBackground)
                            },
                            modifier = Modifier.size(ToolkitTheme.dimensions.progressBoxSize)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (repo.isLocal) Icons.Default.FolderOpen else Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.smallMedium))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = repo.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = repo.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.secondaryText),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (repo.isLocal) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.repo_action_open_folder)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            onOpenLocalFolder(repo.url)
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.repo_share_link_desc)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            clipboard.setClipEntry(PlatformUtils.clipEntryOf(repo.url))
                                        }
                                        onCopyLink(repo.url)
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.action_refresh)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onRefreshRepo(repo)
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(Res.string.action_remove),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onRemoveRepo(repo)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

        // Refresh All Button
        OutlinedButton(
            onClick = onRefreshAll,
            enabled = !isRefreshing,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall),
                    strokeWidth = ToolkitTheme.dimensions.progressIndicatorStroke,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                )
            }
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
            Text(stringResource(Res.string.repo_refresh_all))
        }
    }
}
