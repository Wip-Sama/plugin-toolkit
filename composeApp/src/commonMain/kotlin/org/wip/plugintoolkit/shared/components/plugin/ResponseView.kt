package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.shared.components.GlassCard
import org.wip.plugintoolkit.shared.components.SectionHeader

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

@Preview
@Composable
private fun ResponseViewPreview() {
    MaterialTheme {
        ResponseView(
            response = PluginResponse(
                result = JsonPrimitive(42.0),
                metadata = mapOf("status" to "success", "duration" to "120ms")
            )
        )
    }
}

