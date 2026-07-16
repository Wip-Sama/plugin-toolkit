package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.wip.plugintoolkit.api.FileAccess
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.ToolkitChip
import org.wip.plugintoolkit.shared.components.ToolkitChipStyle
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.*

@Composable
fun FileAccessChips(
    fileAccess: FileAccess?,
    modifier: Modifier = Modifier
) {
    if (fileAccess == null || (!fileAccess.readsFiles && !fileAccess.writesFiles && !fileAccess.isDestructive)) {
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (fileAccess.readsFiles) {
            ToolkitChip(
                text = "Read", //TODO: localize
                icon = {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = stringResource(Res.string.plugin_access_read), //TODO: localize
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                style = ToolkitChipStyle.Filled,
                fontWeight = FontWeight.Medium
            )
        }
        if (fileAccess.writesFiles) {
            ToolkitChip(
                text = "Write", //TODO: localize
                icon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.plugin_access_write), //TODO: localize
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                style = ToolkitChipStyle.Filled,
                fontWeight = FontWeight.Medium
            )
        }
        if (fileAccess.isDestructive) {
            ToolkitChip(
                text = "Delete", //TODO: localize
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.plugin_access_delete), //TODO: localize
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                style = ToolkitChipStyle.Filled,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
