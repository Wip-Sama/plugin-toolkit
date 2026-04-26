package com.wip.kpm_cpm_wotoolkit.features.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel.PluginViewModel
import com.wip.kpm_cpm_wotoolkit.shared.components.SectionHeader
import com.wip.plugin.api.PluginEntry
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun PluginSidebar(
    viewModel: PluginViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Text(
            "Plugin Center",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "LOAD NEW JAR", icon = Icons.Default.Add)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = viewModel.jarPath,
            onValueChange = { viewModel.jarPath = it },
            label = { Text("Path to .jar") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        Button(
            onClick = { viewModel.loadPlugin() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Load & Initialize")
        }
        Button(
            onClick = { viewModel.unloadPlugin() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Unload")
        }

        Spacer(modifier = Modifier.height(32.dp))
        SectionHeader(title = "LOADED MODULES", icon = Icons.Default.List)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.loadedPlugins) { plugin ->
                val manifest = plugin.getManifest()
                val isSelected = viewModel.selectedPlugin == plugin
                
                Surface(
                    onClick = { viewModel.selectPlugin(plugin) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsInputComponent,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                manifest.module.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "v${manifest.module.version}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PluginSidebarPreview() {
    MaterialTheme {
        PluginSidebar(PluginViewModel())
    }
}

