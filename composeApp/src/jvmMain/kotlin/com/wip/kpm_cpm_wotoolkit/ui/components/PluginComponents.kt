package com.wip.kpm_cpm_wotoolkit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wip.plugin.api.*

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val baseModifier = modifier
        .clip(MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                )
            ),
            shape = MaterialTheme.shapes.large
        )
    
    val finalModifier = if (onClick != null) baseModifier.clickable(onClick = onClick) else baseModifier

    Column(
        modifier = finalModifier.padding(16.dp),
        content = content
    )
}

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun DynamicParameterInput(
    name: String,
    metadata: ParameterMetadata,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "(${formatDataType(metadata.type)})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        
        if (metadata.description.isNotEmpty()) {
            Text(
                text = metadata.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            placeholder = { 
                val currentType = metadata.type
                Text(
                    text = when(currentType) {
                        is DataType.Array -> "e.g. 1, 2, 3"
                        is DataType.Primitive -> "Enter ${currentType.primitiveType}"
                        else -> "Enter value"
                    },
                    style = MaterialTheme.typography.bodySmall
                ) 
            }
        )
    }
}

private fun formatDataType(type: DataType): String {
    return when (type) {
        is DataType.Primitive -> type.primitiveType.name.lowercase()
        is DataType.Array -> "list<${formatDataType(type.items)}>"
        is DataType.Object -> type.className.substringAfterLast('.')
    }
}

@Composable
fun ResponseView(response: PluginResponse) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Execution Result", icon = Icons.Default.CheckCircle)
        Spacer(modifier = Modifier.height(12.dp))
        
        SelectionContainer {
            Text(
                text = response.result.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        val metadata = response.metadata
        if (!metadata.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            SectionHeader(title = "Metadata", icon = Icons.Default.Info)
            metadata.forEach { (k, v) ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("${k}: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text(v, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun SelectionContainer(content: @Composable () -> Unit) {
    // Basic wrapper for selectable text if needed, otherwise just content
    content()
}
