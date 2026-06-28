package org.wip.plugintoolkit.features.repository.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.repo_add_button
import plugintoolkit.composeapp.generated.resources.repo_url_placeholder

@Composable
fun AddRepoInput(
    repoUrlInput: String,
    onValueChange: (String) -> Unit,
    onAddRepository: () -> Unit
) {
                Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolkitTextField(
                    value = repoUrlInput,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            stringResource(Res.string.repo_url_placeholder),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                FilledIconButton(
                    onClick = { onAddRepository() },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(ToolkitTheme.dimensions.textFieldHeight)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.repo_add_button))
                }
            }

}
