package org.wip.plugintoolkit.features.repository.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.viewmodel.FlowState
import org.wip.plugintoolkit.features.repository.model.ExtensionFlow
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.shared.components.GlassCard
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_install
import plugintoolkit.composeapp.generated.resources.repo_action_update_version
import plugintoolkit.composeapp.generated.resources.repo_flow_filename_version_format

@Composable
fun FlowListItem(
    flow: ExtensionFlow,
    currentRepo: ExtensionRepo,
    flowState: FlowState,
    isRefreshing: Boolean,
    onInstall: (ExtensionFlow) -> Unit,
) {
    val installedFlow = flowState.flows.find { it.name == flow.name }
    val isInstalled = installedFlow != null
    // We don't have versioning for flows in the same way, but we could check signature/hash
    val hasUpdate = false // Simplification for flows for now

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flow Icon
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(ToolkitTheme.dimensions.listIconSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(ToolkitTheme.dimensions.listIconContentSize)
                    )
                }
            }

            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

            // Flow Information
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = flow.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

                    if (isInstalled) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Installed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    } else {
                        Text(
                            stringResource(Res.string.repo_flow_filename_version_format, flow.fileName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!flow.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                    Text(
                        text = flow.description ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

            // Action Buttons
            if (hasUpdate) {
                Button(
                    onClick = { onInstall(flow) },
                    enabled = !isRefreshing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                    Text(stringResource(Res.string.repo_action_update_version, flow.version))
                }
            } else if (!isInstalled) {
                Button(
                    onClick = { onInstall(flow) },
                    enabled = !isRefreshing
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                    Text(stringResource(Res.string.action_install))
                }
            }
        }
    }
}
