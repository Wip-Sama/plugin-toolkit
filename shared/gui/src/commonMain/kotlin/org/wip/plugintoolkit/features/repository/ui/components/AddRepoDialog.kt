package org.wip.plugintoolkit.features.repository.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.repository.model.RepoValidationResult
import org.wip.plugintoolkit.features.repository.viewmodel.AddRepoDialogState
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.repo_add_local_desc
import plugintoolkit.composeapp.generated.resources.repo_add_local_title
import plugintoolkit.composeapp.generated.resources.repo_add_remote_desc
import plugintoolkit.composeapp.generated.resources.repo_add_remote_title
import plugintoolkit.composeapp.generated.resources.repo_btn_add_local
import plugintoolkit.composeapp.generated.resources.repo_btn_add_remote
import plugintoolkit.composeapp.generated.resources.repo_btn_add_repository
import plugintoolkit.composeapp.generated.resources.repo_btn_browse
import plugintoolkit.composeapp.generated.resources.repo_btn_verify
import plugintoolkit.composeapp.generated.resources.repo_local_path_label
import plugintoolkit.composeapp.generated.resources.repo_remote_url_label
import plugintoolkit.composeapp.generated.resources.repo_val_checking_title
import plugintoolkit.composeapp.generated.resources.repo_val_invalid_title
import plugintoolkit.composeapp.generated.resources.repo_val_ready_desc
import plugintoolkit.composeapp.generated.resources.repo_val_ready_title
import plugintoolkit.composeapp.generated.resources.repo_val_valid_desc
import plugintoolkit.composeapp.generated.resources.repo_val_valid_title

@Composable
fun AddRepoDialog(
    state: AddRepoDialogState,
    onTargetChange: (String) -> Unit,
    onModeChange: (Boolean) -> Unit,
    onVerify: () -> Unit,
    onBrowse: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.isOpen) return

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(ToolkitTheme.dimensions.dialogMaxWidthMedium),
            shape = ToolkitTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = ToolkitTheme.dimensions.cardElevation,
            border = BorderStroke(ToolkitTheme.dimensions.borderUnselected, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(ToolkitTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                // Header with icon and title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                ) {
                    Surface(
                        modifier = Modifier.size(ToolkitTheme.dimensions.settingsIconContainerSize),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isLocalMode) Icons.Default.FolderOpen else Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (state.isLocalMode) Res.string.repo_add_local_title
                                else Res.string.repo_add_remote_title
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                if (state.isLocalMode) Res.string.repo_add_local_desc
                                else Res.string.repo_add_remote_desc
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Mode switch: Remote vs Local
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.cardBackground))
                        .padding(ToolkitTheme.spacing.extraExtraSmall),
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                ) {
                    val remoteSelected = !state.isLocalMode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(ToolkitTheme.dimensions.segmentedButtonHeight)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onModeChange(false) },
                        color = if (remoteSelected) MaterialTheme.colorScheme.secondaryContainer else ToolkitTheme.colors.transparent,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small)
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                                tint = if (remoteSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            Text(
                                text = stringResource(Res.string.repo_btn_add_remote),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (remoteSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (remoteSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val localSelected = state.isLocalMode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(ToolkitTheme.dimensions.segmentedButtonHeight)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onModeChange(true) },
                        color = if (localSelected) MaterialTheme.colorScheme.secondaryContainer else ToolkitTheme.colors.transparent,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small)
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                                tint = if (localSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            Text(
                                text = stringResource(Res.string.repo_btn_add_local),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (localSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (localSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Input box & actions
                Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                    Text(
                        text = stringResource(
                            if (state.isLocalMode) Res.string.repo_local_path_label
                            else Res.string.repo_remote_url_label
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolkitTextField(
                            value = state.targetInput,
                            onValueChange = onTargetChange,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    if (state.isLocalMode) "D:\\Path\\To\\index.json" else "https://.../index.json",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true
                        )

                        if (state.isLocalMode) {
                            Button(
                                onClick = onBrowse,
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.filledTonalButtonColors()
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                                Text(stringResource(Res.string.repo_btn_browse))
                            }
                        }

                        Button(
                            onClick = onVerify,
                            enabled = state.targetInput.isNotBlank() && state.validationResult !is RepoValidationResult.Checking,
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(stringResource(Res.string.repo_btn_verify))
                        }
                    }
                }

                // Validation Status Banner
                RepoValidationStatusBanner(validationResult = state.validationResult)

                // Dialog Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.dialog_cancel))
                    }

                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

                    Button(
                        onClick = onConfirm,
                        enabled = state.validationResult is RepoValidationResult.Valid,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        Text(stringResource(Res.string.repo_btn_add_repository))
                    }
                }
            }
        }
    }
}

@Composable
fun RepoValidationStatusBanner(validationResult: RepoValidationResult) {
    when (validationResult) {
        is RepoValidationResult.Idle -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.cardBackground)
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                ) {
                    Icon(
                        Icons.Default.Pending,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                    )
                    Column {
                        Text(
                            text = stringResource(Res.string.repo_val_ready_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(Res.string.repo_val_ready_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is RepoValidationResult.Checking -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.cardBackground)
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium),
                        strokeWidth = ToolkitTheme.dimensions.progressIndicatorStroke
                    )
                    Column {
                        Text(
                            text = stringResource(Res.string.repo_val_checking_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        is RepoValidationResult.Valid -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = ToolkitTheme.colors.success.copy(alpha = ToolkitTheme.opacity.buttonBackground)
                ),
                border = BorderStroke(ToolkitTheme.dimensions.borderUnselected, ToolkitTheme.colors.success.copy(alpha = ToolkitTheme.opacity.divider)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ToolkitTheme.colors.success,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                    )
                    Column {
                        Text(
                            text = stringResource(Res.string.repo_val_valid_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ToolkitTheme.colors.success
                        )
                        Text(
                            text = stringResource(
                                Res.string.repo_val_valid_desc,
                                validationResult.name,
                                validationResult.pluginCount,
                                validationResult.flowCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        is RepoValidationResult.Invalid -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                    )
                    Column {
                        Text(
                            text = stringResource(Res.string.repo_val_invalid_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = validationResult.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
