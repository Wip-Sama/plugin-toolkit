package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.ParameterConstraints
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.plugin.inputs.ParameterInputLabel

@Composable
fun StandardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: @Composable () -> Unit,
    description: String,
    constraints: ParameterConstraints? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isSecret: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isRequired: Boolean = false,
    type: DataType = DataType.Primitive(PrimitiveType.ANY),
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val errorMessage = remember(value, isRequired, type) {
        org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.validateParameter(
            value = value,
            isRequired = isRequired,
            type = type,
            constraints = constraints
        )
    }
    val isError = errorMessage != null
    var isVisible by remember { mutableStateOf(!isSecret) }

    ToolkitTextField(
        value = value,
        onValueChange = {
            onValueChange(it)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        label = label,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
            visualTransformation = if (isSecret && !isVisible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        trailingIcon = trailingIcon ?: if (isSecret) {
            {
                IconButton(onClick = { isVisible = !isVisible }) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isVisible) "Hide" else "Show"
                    )
                }
            }
        } else null,
        isError = isError,
        supportingText = if (isError || description.isNotEmpty()) {
            {
                if (isError) {
                    Text(errorMessage ?: "")
                } else {
                    Text(description)
                }
            }
        } else null
    )
}
