package org.wip.plugintoolkit.features.repository.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_refresh
import plugintoolkit.composeapp.generated.resources.action_remove
import plugintoolkit.composeapp.generated.resources.repo_managed_title
import plugintoolkit.composeapp.generated.resources.repo_refresh_all
import plugintoolkit.composeapp.generated.resources.repo_share_link_desc

@Composable
fun PluginRepoSidebar(
    repositories: List<ExtensionRepo>,
    selectedRepo: ExtensionRepo?,
    isRefreshing: Boolean,
    onRepoSelected: (ExtensionRepo) -> Unit,
    onRemoveRepo: (ExtensionRepo) -> Unit,
    onCopyLink: (String) -> Unit,

    onRefreshRepo: (ExtensionRepo) -> Unit,
    onRefreshAll: () -> Unit,
    repoUrlInput: String,
    onRepoUrlInputChange: (String) -> Unit,
    onAddRepository: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    Column(
        modifier = Modifier
            .width(ToolkitTheme.dimensions.repositorySidebarWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.sidebarBackground))
            .padding(ToolkitTheme.spacing.medium)
    ) {
        Text(
            text = stringResource(Res.string.repo_managed_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = ToolkitTheme.spacing.small)
        )

        // Add Repository Input & Button
        AddRepoInput(
            repoUrlInput = repoUrlInput,
            onValueChange = onRepoUrlInputChange,
            onAddRepository = onAddRepository
        )

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

        // Repository List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            items(repositories) { repo ->
                val isSelected = selectedRepo?.url == repo.url
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onRepoSelected(repo) },
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.cardBackground)
                        } else {
                            ToolkitTheme.colors.transparent
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected) ToolkitTheme.dimensions.borderSelected else ToolkitTheme.dimensions.borderUnselected,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = ToolkitTheme.opacity.sidebarBackground
                        )
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = ToolkitTheme.spacing.medium,
                                vertical = ToolkitTheme.spacing.small
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = repo.name,
                                style = MaterialTheme.typography.titleMedium,
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

                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(PlatformUtils.clipEntryOf(repo.url))
                                    }
                                    onCopyLink(repo.url)
                                },
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(Res.string.repo_share_link_desc),
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            }
                            IconButton(
                                onClick = { onRefreshRepo(repo) },
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(Res.string.action_refresh),
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            }
                            IconButton(
                                onClick = { onRemoveRepo(repo) },
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(Res.string.action_remove),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

        // Refresh All Button
        Button(
            onClick = { onRefreshAll() },
            enabled = !isRefreshing,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall),
                    strokeWidth = ToolkitTheme.dimensions.progressIndicatorStroke,
                    color = MaterialTheme.colorScheme.onPrimary
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
