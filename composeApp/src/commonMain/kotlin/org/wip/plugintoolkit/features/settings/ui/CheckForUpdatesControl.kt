package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.features.settings.model.UpdateState
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import org.wip.plugintoolkit.shared.components.settings.SettingsItem
import plugintoolkit.composeapp.generated.resources.*
import org.wip.plugintoolkit.core.theme.ToolkitTheme

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CheckForUpdatesControl(viewModel: SettingsViewModel) {
    val updateState by viewModel.updateState.collectAsState()
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (updateState is UpdateState.Checking) {
            CircularProgressIndicator(
                modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium).padding(end = ToolkitTheme.spacing.small),
                strokeWidth = 2.dp
            )
            Text(
                text = stringResource(Res.string.update_check_started),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            if (updateState is UpdateState.UpdateAvailable) {
                Text(
                    text = stringResource(Res.string.update_new_version_found, (updateState as UpdateState.UpdateAvailable).info.version),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                )
            }
            IconButton(onClick = { viewModel.checkForUpdates(isManual = true) }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
