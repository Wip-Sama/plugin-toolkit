package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.viewmodel.ReadOnlyReason
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_editor_btn_exit
import plugintoolkit.composeapp.generated.resources.flow_editor_flow_selected
import plugintoolkit.composeapp.generated.resources.flow_editor_no_flow_selected
import plugintoolkit.composeapp.generated.resources.flow_editor_read_only
import plugintoolkit.composeapp.generated.resources.flow_editor_read_only_reason
import plugintoolkit.composeapp.generated.resources.flow_editor_save_changes
import plugintoolkit.composeapp.generated.resources.flow_readonly_reason_running
import plugintoolkit.composeapp.generated.resources.flow_readonly_reason_used_in_other

@Composable
internal fun FlowEditorTopBar(
    flowName: String,
    isReadOnly: Boolean,
    readOnlyReasons: List<ReadOnlyReason>,
    hasUnsavedChanges: Boolean,
    onSave: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(ToolkitTheme.spacing.medium),
        shape = RoundedCornerShape(ToolkitTheme.spacing.large),
        color = MaterialTheme.colorScheme.surface.copy(alpha = ToolkitTheme.opacity.almostOpaque),
        tonalElevation = ToolkitTheme.spacing.small,
        shadowElevation = ToolkitTheme.spacing.extraSmall
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ToolkitTheme.spacing.medium,
                vertical = ToolkitTheme.spacing.small
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                if (flowName.isBlank()) {
                    Text(
                        text = stringResource(Res.string.flow_editor_no_flow_selected),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                    ) {
                        Column {
                            Text(
                                text = stringResource(Res.string.flow_editor_flow_selected),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = flowName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isReadOnly) {
                            @Suppress("SimplifiableCallChain")
                            val reasonString = readOnlyReasons.map { reason ->
                                when (reason) {
                                    ReadOnlyReason.Running -> stringResource(Res.string.flow_readonly_reason_running)
                                    ReadOnlyReason.UsedInOtherFlows -> stringResource(Res.string.flow_readonly_reason_used_in_other)
                                }
                            }.joinToString(", ")

                            val displayText = if (reasonString.isNotEmpty()) {
                                stringResource(Res.string.flow_editor_read_only_reason, reasonString)
                            } else {
                                stringResource(Res.string.flow_editor_read_only)
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(
                                        horizontal = ToolkitTheme.spacing.small,
                                        vertical = ToolkitTheme.spacing.extraSmall
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onSave,
                enabled = hasUnsavedChanges && !isReadOnly,
                contentPadding = PaddingValues(
                    horizontal = ToolkitTheme.spacing.medium,
                    vertical = ToolkitTheme.spacing.small
                )
            ) {
                Text(stringResource(Res.string.flow_editor_save_changes))
            }

            OutlinedButton(
                onClick = onExit,
                contentPadding = PaddingValues(
                    horizontal = ToolkitTheme.spacing.medium,
                    vertical = ToolkitTheme.spacing.small
                )
            ) {
                Text(stringResource(Res.string.flow_editor_btn_exit))
            }
        }
    }
}
