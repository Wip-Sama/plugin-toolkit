package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.core.theme.ToolkitTheme

@Composable
fun CapabilitySidebar(
    plugin: PluginEntry,
    selectedCapability: Capability?,
    onCapabilitySelected: (Capability) -> Unit,
    modifier: Modifier = Modifier
) {
    val manifest = plugin.getManifest().getOrThrow()

    Surface(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        border = BorderStroke(ToolkitTheme.dimensions.borderUnselected, MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ToolkitTheme.spacing.mediumSmall)
        ) {
            // Plugin Info Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = ToolkitTheme.spacing.large, top = ToolkitTheme.spacing.small)
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.mediumSmall))
                Column {
                    Text(
                        manifest.plugin.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "v${manifest.plugin.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Text(
                "CAPABILITIES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = ToolkitTheme.spacing.extraSmall, bottom = ToolkitTheme.spacing.small)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
            ) {
                manifest.capabilities.forEach { capability ->
                    val isSelected = selectedCapability == capability

                    Surface(
                        onClick = { onCapabilitySelected(capability) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else ToolkitTheme.colors.transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.mediumSmall))
                            Text(
                                capability.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
