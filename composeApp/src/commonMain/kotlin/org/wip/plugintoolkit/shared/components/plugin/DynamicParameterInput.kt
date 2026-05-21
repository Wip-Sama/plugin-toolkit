package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterConstraints
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.colorpicker.utils.toHex
import org.wip.plugintoolkit.features.colorpicker.utils.toRGB
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import org.wip.plugintoolkit.shared.components.ToolkitTextField

@Composable
fun DynamicParameterInput(
    name: String,
    metadata: ParameterMetadata,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    val inferredSem = metadata.semanticType
    val category = getSemanticTypeCategory(inferredSem)
    val scope = rememberCoroutineScope()

    if (category == "image" || category == "audio" || category == "video") {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            val inputLabel = @Composable {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        color = if (metadata.required && value.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (metadata.required) {
                        Text(
                            text = " *",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${inferredSem?.uppercase()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            val isArray = metadata.type is DataType.Array
            val fileNames = getFileNames(value, isArray)
            val placeholderText = when (category) {
                "image" -> "No image selected"
                "audio" -> "No audio selected"
                else -> "No video selected"
            }

            StandardTextField(
                value = fileNames.ifEmpty { value },
                onValueChange = {},
                readOnly = true,
                placeholder = placeholderText,
                label = inputLabel,
                description = metadata.description,
                constraints = metadata.constraints,
                enabled = enabled,
                isRequired = metadata.required,
                isArray = isArray,
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (value.isNotEmpty() && enabled) {
                            IconButton(onClick = { onValueChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear file selection",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val allowedExtensions = getAllowedExtensions(inferredSem)
                                    val picked = PlatformUtils.pickFile("Select File", allowedExtensions)
                                    if (picked != null) {
                                        val newValue = appendPickedValue(value, picked, isArray)
                                        onValueChange(newValue)
                                    }
                                }
                            },
                            enabled = enabled
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Browse file",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    } else if (category == "color") {
        var showColorPicker by remember { mutableStateOf(false) }
        val parsedColor = remember(value) { parseColorString(value) }
        val isArray = metadata.type is DataType.Array

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            val inputLabel = @Composable {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        color = if (metadata.required && value.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (metadata.required) {
                        Text(
                            text = " *",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${inferredSem?.uppercase()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            StandardTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "Enter color code (e.g. #FFFFFF or rgb(255,255,255))",
                label = inputLabel,
                description = metadata.description,
                constraints = metadata.constraints,
                enabled = enabled,
                isSecret = metadata.secret,
                isRequired = metadata.required,
                isArray = isArray,
                trailingIcon = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(parsedColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            .clickable(enabled = enabled) {
                                showColorPicker = true
                            }
                    ) {
                        if (value.isEmpty() || parsedColor == Color.Transparent) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Choose color",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            )

            if (showColorPicker && enabled) {
                org.wip.plugintoolkit.features.colorpicker.ui.ColorPickerDialog(
                    show = showColorPicker,
                    onDismissRequest = { showColorPicker = false },
                    onPickedColor = { color ->
                        val hasAlpha = inferredSem?.contains("rgba", ignoreCase = true) == true
                        val formatted = if (inferredSem?.contains("rgb", ignoreCase = true) == true) {
                            color.toRGB(rgbPrefix = true, includeAlpha = hasAlpha)
                        } else {
                            color.toHex(hexPrefix = true, includeAlpha = hasAlpha)
                        }
                        val newValue = appendPickedValue(value, formatted, isArray)
                        onValueChange(newValue)
                        showColorPicker = false
                    }
                )
            }
        }
    } else if (category == "file") {
        val isArray = metadata.type is DataType.Array
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            val inputLabel = @Composable {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        color = if (metadata.required && value.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (metadata.required) {
                        Text(
                            text = " *",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${inferredSem?.uppercase()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            StandardTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "Enter file path",
                label = inputLabel,
                description = metadata.description,
                constraints = metadata.constraints,
                enabled = enabled,
                isSecret = metadata.secret,
                isRequired = metadata.required,
                isArray = isArray,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val allowedExtensions = getAllowedExtensions(inferredSem)
                                val picked = PlatformUtils.pickFile("Select File", allowedExtensions)
                                if (picked != null) {
                                    val newValue = appendPickedValue(value, picked, isArray)
                                    onValueChange(newValue)
                                }
                            }
                        },
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Browse file",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    } else if (category == "path") {
        val isArray = metadata.type is DataType.Array
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            val inputLabel = @Composable {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        color = if (metadata.required && value.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (metadata.required) {
                        Text(
                            text = " *",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${inferredSem?.uppercase()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            StandardTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "Enter folder path",
                label = inputLabel,
                description = metadata.description,
                constraints = metadata.constraints,
                enabled = enabled,
                isSecret = metadata.secret,
                isRequired = metadata.required,
                isArray = isArray,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val picked = PlatformUtils.pickFolder()
                                if (picked != null) {
                                    val newValue = appendPickedValue(value, picked, isArray)
                                    onValueChange(newValue)
                                }
                            }
                        },
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Browse folder",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    } else {
        val dataType = metadata.type
        val isBoolean = dataType is DataType.Primitive && dataType.primitiveType == PrimitiveType.BOOLEAN
        val isEnumDropdown = dataType is DataType.Enum && metadata.constraints?.multiSelect != true
        val showTopLabel = isBoolean || isEnumDropdown

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (showTopLabel) {
                // Label and Type info on top for Switch and Dropdown only
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (metadata.required && value.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (metadata.required) {
                        Text(
                            text = " *",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
            }

            val inputLabel = @Composable {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        color = if (metadata.required && value.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (metadata.required) {
                        Text(
                            text = " *",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${formatDataType(metadata.type).uppercase()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
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
                                label = inputLabel,
                                description = metadata.description,
                                constraints = metadata.constraints,
                                enabled = enabled,
                                isSecret = metadata.secret,
                                isRequired = metadata.required
                            )
                        }

                        PrimitiveType.DOUBLE -> {
                            NumericTextField(
                                value = value,
                                onValueChange = onValueChange,
                                allowDecimal = true,
                                placeholder = "Enter decimal number",
                                label = inputLabel,
                                description = metadata.description,
                                constraints = metadata.constraints,
                                enabled = enabled,
                                isSecret = metadata.secret,
                                isRequired = metadata.required
                            )
                        }

                        else -> {
                            StandardTextField(
                                value = value,
                                onValueChange = onValueChange,
                                placeholder = "Enter ${type.primitiveType.name.lowercase()}",
                                label = inputLabel,
                                description = metadata.description,
                                constraints = metadata.constraints,
                                enabled = enabled,
                                isSecret = metadata.secret,
                                isRequired = metadata.required,
                                isArray = false
                            )
                        }
                    }
                }

                is DataType.Array -> {
                    StandardTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = "Enter values separated by comma (,,)",
                        label = inputLabel,
                        description = metadata.description,
                        constraints = metadata.constraints,
                        enabled = enabled,
                        isSecret = metadata.secret,
                        isRequired = metadata.required,
                        isArray = true
                    )
                }

                is DataType.Enum -> {
                    val options = type.options
                    if (metadata.constraints?.multiSelect == true) {
                        StandardTextField(
                            value = value,
                            onValueChange = onValueChange,
                            placeholder = "Enter comma-separated options: ${options.joinToString()}",
                            label = inputLabel,
                            description = metadata.description,
                            constraints = metadata.constraints,
                            enabled = enabled,
                            isSecret = metadata.secret,
                            isRequired = metadata.required,
                            isArray = true
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
                        label = inputLabel,
                        description = metadata.description,
                        constraints = metadata.constraints,
                        enabled = enabled,
                        isSecret = metadata.secret,
                        isRequired = metadata.required,
                        isArray = metadata.type is DataType.Array
                    )
                }
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
    label: @Composable () -> Unit,
    description: String,
    constraints: ParameterConstraints? = null,
    enabled: Boolean = true,
    isSecret: Boolean = false,
    isRequired: Boolean = false
) {
    val errorMessage = remember(value, isRequired) {
        if (value.isNotEmpty() && value.toDoubleOrNull() == null) {
            "Invalid number"
        } else {
            org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.validateParameter(
                value = value,
                isRequired = isRequired,
                isArray = false,
                constraints = constraints
            )
        }
    }
    val isError = errorMessage != null
    var isVisible by remember { mutableStateOf(!isSecret) }

    ToolkitTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.isEmpty()) {
                onValueChange("")
            } else {
                val filtered = if (allowDecimal) {
                    val hasDot = newValue.count { it == '.' } <= 1
                    val validChars = newValue.all { it.isDigit() || it == '.' || it == '-' }
                    val validMinus = newValue.lastIndexOf('-') <= 0
                    if (hasDot && validChars && validMinus) newValue else null
                } else {
                    val validChars = newValue.all { it.isDigit() || it == '-' }
                    val validMinus = newValue.lastIndexOf('-') <= 0
                    if (validChars && validMinus) newValue else null
                }
                if (filtered != null) {
                    onValueChange(filtered)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = label,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        visualTransformation = if (isSecret && !isVisible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        trailingIcon = if (isSecret) {
            {
                IconButton(onClick = { isVisible = !isVisible }) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isVisible) "Hide" else "Show"
                    )
                }
            }
        } else null,
        singleLine = true,
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

@Composable
private fun StandardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: @Composable () -> Unit,
    description: String,
    constraints: ParameterConstraints? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isSecret: Boolean = false,
    isRequired: Boolean = false,
    isArray: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val errorMessage = remember(value, isRequired, isArray) {
        org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.validateParameter(
            value = value,
            isRequired = isRequired,
            isArray = isArray,
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

private fun parseColorString(colorStr: String): Color {
    val lastColor = colorStr.split(",").lastOrNull { it.trim().isNotEmpty() }?.trim() ?: colorStr
    val trimmed = lastColor.trim()
    if (trimmed.isEmpty()) return Color.Transparent
    if (trimmed.startsWith("#")) {
        return try {
            val hex = trimmed.substring(1)
            when (hex.length) {
                3 -> {
                    val r = hex[0].toString().repeat(2).toInt(16) / 255f
                    val g = hex[1].toString().repeat(2).toInt(16) / 255f
                    val b = hex[2].toString().repeat(2).toInt(16) / 255f
                    Color(r, g, b, 1f)
                }
                4 -> {
                    val r = hex[0].toString().repeat(2).toInt(16) / 255f
                    val g = hex[1].toString().repeat(2).toInt(16) / 255f
                    val b = hex[2].toString().repeat(2).toInt(16) / 255f
                    val a = hex[3].toString().repeat(2).toInt(16) / 255f
                    Color(r, g, b, a)
                }
                6 -> {
                    val r = hex.substring(0, 2).toInt(16) / 255f
                    val g = hex.substring(2, 4).toInt(16) / 255f
                    val b = hex.substring(4, 6).toInt(16) / 255f
                    Color(r, g, b, 1f)
                }
                8 -> {
                    val r = hex.substring(0, 2).toInt(16) / 255f
                    val g = hex.substring(2, 4).toInt(16) / 255f
                    val b = hex.substring(4, 6).toInt(16) / 255f
                    val a = hex.substring(6, 8).toInt(16) / 255f
                    Color(r, g, b, a)
                }
                else -> Color.Gray
            }
        } catch (e: Exception) {
            Color.Gray
        }
    }
    if (trimmed.startsWith("rgba", ignoreCase = true)) {
        return try {
            val parts = trimmed.substringAfter("(").substringBefore(")").split(",")
            val r = parts[0].trim().toFloat() / 255f
            val g = parts[1].trim().toFloat() / 255f
            val b = parts[2].trim().toFloat() / 255f
            val a = parts[3].trim().toFloat()
            Color(r, g, b, a)
        } catch (e: Exception) {
            Color.Gray
        }
    }
    if (trimmed.startsWith("rgb", ignoreCase = true)) {
        return try {
            val parts = trimmed.substringAfter("(").substringBefore(")").split(",")
            val r = parts[0].trim().toFloat() / 255f
            val g = parts[1].trim().toFloat() / 255f
            val b = parts[2].trim().toFloat() / 255f
            Color(r, g, b, 1f)
        } catch (e: Exception) {
            Color.Gray
        }
    }
    return when (trimmed.lowercase()) {
        "red" -> Color.Red
        "green" -> Color.Green
        "blue" -> Color.Blue
        "yellow" -> Color.Yellow
        "cyan" -> Color.Cyan
        "magenta" -> Color.Magenta
        "black" -> Color.Black
        "white" -> Color.White
        "gray" -> Color.Gray
        "transparent" -> Color.Transparent
        else -> Color.Gray
    }
}

private fun getFileName(path: String): String {
    if (path.isEmpty()) return ""
    return path.substringAfterLast('/').substringAfterLast('\\')
}

private fun getSemanticTypeCategory(semanticType: String?): String? {
    if (semanticType.isNullOrBlank()) return null
    val types = semanticType.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
    for (t in types) {
        val tLower = t.lowercase()
        when {
            tLower.startsWith("color") -> return "color"
            tLower.startsWith("file") -> return "file"
            tLower.startsWith("path") -> return "path"
            tLower.startsWith("image") -> return "image"
            tLower.startsWith("audio") -> return "audio"
            tLower.startsWith("video") -> return "video"
        }
    }
    return null
}

private fun getAllowedExtensions(semanticType: String?): List<String> {
    if (semanticType.isNullOrBlank()) return emptyList()
    val types = semanticType.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
    val extensions = mutableListOf<String>()
    var hasGenericImage = false
    var hasGenericAudio = false
    var hasGenericVideo = false
    
    for (t in types) {
        val tLower = t.lowercase()
        if (tLower.contains("/")) {
            val ext = tLower.substringAfter("/")
            if (ext.isNotEmpty() && ext != "*") {
                extensions.add(ext)
            } else {
                if (tLower.startsWith("image")) hasGenericImage = true
                if (tLower.startsWith("audio")) hasGenericAudio = true
                if (tLower.startsWith("video")) hasGenericVideo = true
            }
        } else {
            if (tLower.startsWith("image")) hasGenericImage = true
            if (tLower.startsWith("audio")) hasGenericAudio = true
            if (tLower.startsWith("video")) hasGenericVideo = true
        }
    }
    
    if (hasGenericImage) {
        extensions.addAll(listOf("png", "jpg", "jpeg", "gif", "webp", "bmp"))
    }
    if (hasGenericAudio) {
        extensions.addAll(listOf("mp3", "wav", "ogg", "aac", "flac", "m4a"))
    }
    if (hasGenericVideo) {
        extensions.addAll(listOf("mp4", "mkv", "avi", "mov", "webm", "flv"))
    }
    
    return extensions.distinct()
}

private fun appendPickedValue(existingValue: String, newValue: String, isArray: Boolean): String {
    if (!isArray) return newValue
    if (existingValue.isBlank()) return newValue
    val existingList = existingValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (newValue in existingList) return existingValue
    return (existingList + newValue).joinToString(", ")
}

private fun getFileNames(path: String, isArray: Boolean): String {
    if (path.isEmpty()) return ""
    if (!isArray) return getFileName(path)
    return path.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { getFileName(it) }.joinToString(", ")
}


