package com.wip.kpm_cpm_wotoolkit.shared.components.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wip.plugin.api.*
import androidx.compose.ui.tooling.preview.Preview

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

@Preview
@Composable
private fun DynamicParameterInputPreview() {
    MaterialTheme {
        DynamicParameterInput(
            name = "Test Parameter",
            metadata = ParameterMetadata(
                description = "This is a test parameter description",
                type = DataType.Primitive(PrimitiveType.DOUBLE)
            ),
            value = "10.5",
            onValueChange = {}
        )
    }
}

