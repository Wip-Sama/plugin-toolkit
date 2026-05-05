package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterConstraints
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu

@Composable
fun DynamicParameterInput(
    name: String,
    metadata: ParameterMetadata,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // Label and Type info
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = formatDataType(metadata.type).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (metadata.description.isNotEmpty()) {
            Text(
                text = metadata.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
        }

        // Input based on type
        when (val type = metadata.type) {
            is DataType.Primitive -> {
                when (type.primitiveType) {
                    PrimitiveType.BOOLEAN -> {
                        Switch(
                            checked = value.lowercase().toBooleanStrictOrNull() ?: false,
                            onCheckedChange = { onValueChange(it.toString()) },
                            modifier = Modifier.padding(vertical = 4.dp),
                            enabled = enabled
                        )
                    }

                    PrimitiveType.INT -> {
                        NumericTextField(
                            value = value,
                            onValueChange = onValueChange,
                            allowDecimal = false,
                            placeholder = "Enter integer",
                            constraints = metadata.constraints,
                            enabled = enabled
                        )
                    }

                    PrimitiveType.DOUBLE -> {
                        NumericTextField(
                            value = value,
                            onValueChange = onValueChange,
                            allowDecimal = true,
                            placeholder = "Enter decimal number",
                            constraints = metadata.constraints,
                            enabled = enabled
                        )
                    }

                    else -> {
                        StandardTextField(
                            value = value,
                            onValueChange = onValueChange,
                            placeholder = "Enter ${type.primitiveType.name.lowercase()}",
                            constraints = metadata.constraints,
                            enabled = enabled
                        )
                    }
                }
            }

            is DataType.Array -> {
                StandardTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = "Enter values separated by comma (,)",
                    constraints = metadata.constraints,
                    enabled = enabled
                )
            }

            is DataType.Enum -> {
                // Determine options to show. For enums, use type.options.
                val options = type.options
                if (metadata.constraints?.multiSelect == true) {
                    // Simple text field for multi-select for now or comma-separated
                    StandardTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = "Enter comma-separated options: ${options.joinToString()}",
                        constraints = metadata.constraints,
                        enabled = enabled
                    )
                } else {
                    ExpressiveMenu(
                        options = options,
                        selectedOption = value.ifEmpty { options.firstOrNull() ?: "" },
                        onOptionSelected = onValueChange,
                        labelProvider = { it },
                        enabled = enabled
                    )
                }
            }

            else -> {
                StandardTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = "Enter value",
                    constraints = metadata.constraints,
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
private fun NumericTextField(
    value: String,
    onValueChange: (String) -> Unit,
    allowDecimal: Boolean,
    placeholder: String,
    constraints: ParameterConstraints? = null,
    enabled: Boolean = true
) {
    var isError by remember(value) { mutableStateOf(false) }
    var errorMessage by remember(value) { mutableStateOf("") }

    // Validate on value change
    val validate: (String) -> Unit = { newValue ->
        if (newValue.isNotEmpty()) {
            val numValue = newValue.toDoubleOrNull()
            if (numValue != null && constraints != null) {
                val minValue = constraints.minValue
                val maxValue = constraints.maxValue
                if (minValue != null && numValue < minValue) {
                    isError = true
                    errorMessage = "Value must be >= $minValue"
                } else if (maxValue != null && numValue > maxValue) {
                    isError = true
                    errorMessage = "Value must be <= $maxValue"
                } else {
                    isError = false
                    errorMessage = ""
                }
            } else if (numValue == null) {
                isError = true
                errorMessage = "Invalid number"
            }
        } else {
            isError = false
            errorMessage = ""
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.isEmpty()) {
                onValueChange("")
            } else {
                val filtered = if (allowDecimal) {
                    // Allow digits, at most one dot, and leading minus
                    val hasDot = newValue.count { it == '.' } <= 1
                    val validChars = newValue.all { it.isDigit() || it == '.' || it == '-' }
                    // Check if minus is only at start
                    val validMinus = newValue.lastIndexOf('-') <= 0
                    if (hasDot && validChars && validMinus) newValue else null
                } else {
                    // Allow digits and leading minus
                    val validChars = newValue.all { it.isDigit() || it == '-' }
                    val validMinus = newValue.lastIndexOf('-') <= 0
                    if (validChars && validMinus) newValue else null
                }
                if (filtered != null) {
                    onValueChange(filtered)
                    validate(filtered)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        singleLine = true,
        isError = isError,
        supportingText = if (isError) {
            { Text(errorMessage) }
        } else null
    )
}

@Composable
private fun StandardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    constraints: ParameterConstraints? = null,
    enabled: Boolean = true
) {
    var isError by remember(value) { mutableStateOf(false) }
    var errorMessage by remember(value) { mutableStateOf("") }

    val validate: (String) -> Unit = { newValue ->
        isError = false
        errorMessage = ""
        if (constraints != null) {
            val minLength = constraints.minLength
            val maxLength = constraints.maxLength
            val regex = constraints.regex
            if (minLength != null && newValue.length < minLength) {
                isError = true
                errorMessage = "Minimum length is $minLength"
            } else if (maxLength != null && newValue.length > maxLength) {
                isError = true
                errorMessage = "Maximum length is $maxLength"
            } else if (!regex.isNullOrEmpty()) {
                if (!Regex(regex).matches(newValue)) {
                    isError = true
                    errorMessage = "Does not match required format"
                }
            }
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = {
            onValueChange(it)
            validate(it)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        isError = isError,
        supportingText = if (isError) {
            { Text(errorMessage) }
        } else null
    )
}

private fun formatDataType(type: DataType): String {
    return when (type) {
        is DataType.Primitive -> type.primitiveType.name.lowercase()
        is DataType.Array -> "list<${formatDataType(type.items)}>"
        is DataType.Enum -> type.className.substringAfterLast('.')
        is DataType.Object -> type.className.substringAfterLast('.')
    }
}

@Preview
@Composable
private fun DynamicParameterInputPreview() {
    MaterialTheme {
        Column(Modifier.padding(16.dp)) {
            DynamicParameterInput(
                name = "Boolean Test",
                metadata = ParameterMetadata(
                    description = "A switch for boolean",
                    type = DataType.Primitive(PrimitiveType.BOOLEAN)
                ),
                value = "true",
                onValueChange = {}
            )
            DynamicParameterInput(
                name = "Int Test",
                metadata = ParameterMetadata(
                    description = "Numbers only",
                    type = DataType.Primitive(PrimitiveType.INT)
                ),
                value = "42",
                onValueChange = {}
            )
        }
    }
}


