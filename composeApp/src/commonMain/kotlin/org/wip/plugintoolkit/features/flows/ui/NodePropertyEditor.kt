package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.background
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.features.flows.viewmodel.ValidationError
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import org.wip.plugintoolkit.features.flows.logic.PathPatternResolver
import org.wip.plugintoolkit.core.utils.PlatformUtils
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Port
import org.wip.plugintoolkit.features.plugin.utils.SettingsUtils
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.auto_generated_path_format

@Composable
fun NodePropertyEditor(
    node: Node,
    input: Port,
    currentPortValue: Any?,
    isConnected: Boolean,
    isReadOnly: Boolean,
    portErrors: List<ValidationError>,
    inferredSem: List<SemanticType>,
    onUpdateValue: (Long, String, Any?) -> Unit,
    onFocusLost: () -> Unit,
    onShowColorPicker: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val valueModifier = if (isConnected) Modifier.alpha(0.5f) else Modifier

    Box(modifier = Modifier.width(120.dp).then(valueModifier)) {
        val category = SemanticRegistry.getCategory(inferredSem)?.name?.lowercase()
        when (category) {
            "color" -> {
                val rawValue = getPortValueString(currentPortValue, input.dataType)
                val parsedColor = parseColorString(rawValue)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                        .then(
                            if (portErrors.isNotEmpty() && !isConnected) {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.error,
                                    MaterialTheme.shapes.small
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(
                            start = ToolkitTheme.spacing.small,
                            end = ToolkitTheme.spacing.extraSmall
                        )
                        .padding(vertical = ToolkitTheme.spacing.extraSmall)
                ) {
                    BasicTextField(
                        value = rawValue,
                        onValueChange = { onUpdateValue(node.id, input.id, SettingsUtils.stringToJson(it, input.dataType)) },
                        enabled = !isConnected && !isReadOnly,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.5f
                            )
                            else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(parsedColor)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.extraSmall
                            )
                            .pointerInput(isConnected, isReadOnly) {
                                if (!isConnected && !isReadOnly) {
                                    detectTapGestures {
                                        onShowColorPicker(input.id)
                                    }
                                }
                            }
                    )
                }
            }

            "file" -> {
                val rawValue = getPortValueString(currentPortValue, input.dataType)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                        .then(
                            if (portErrors.isNotEmpty() && !isConnected) {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.error,
                                    MaterialTheme.shapes.small
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(
                            start = ToolkitTheme.spacing.small,
                            end = ToolkitTheme.spacing.extraSmall
                        )
                        .padding(vertical = ToolkitTheme.spacing.extraSmall)
                ) {
                    BasicTextField(
                        value = rawValue,
                        onValueChange = { onUpdateValue(node.id, input.id, SettingsUtils.stringToJson(it, input.dataType)) },
                        enabled = !isConnected && !isReadOnly,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.5f
                            )
                            else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }.weight(1f),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (rawValue.isEmpty() && !isConnected) {
                                    val pattern = (node as? Node.CapabilityNode)?.capability?.parameters?.get(input.id)?.autogeneratedPattern
                                    if (pattern != null) {
                                        val currentValues = node.inputs.associate {
                                            it.id to getPortValueString(it.value ?: it.defaultValue, it.dataType)
                                        }
                                        val dependenciesReady = PathPatternResolver.canResolve(
                                            pattern, 
                                            currentValues.filter { it.value.isNotBlank() }.keys
                                        )
                                        
                                        if (dependenciesReady) {
                                            val resolved = PathPatternResolver.tryResolve(pattern, currentValues)
                                            Text(
                                                text = if (resolved != null) stringResource(Res.string.auto_generated_path_format, resolved) else "Auto-generated at runtime",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text(
                                                text = "Auto-generated at runtime",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                innerTextField()
                            }
                        }
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                val allowedExtensions =
                                    SemanticRegistry.getAllowedExtensions(inferredSem)
                                val pickedPath =
                                    PlatformUtils.pickFile("Select File", allowedExtensions)
                                if (pickedPath != null) {
                                    val isArray = input.dataType is DataType.Array
                                    val newValue =
                                        appendPickedValue(rawValue, pickedPath, isArray)
                                    onUpdateValue(node.id, input.id, newValue)
                                }
                            }
                        },
                        enabled = !isConnected && !isReadOnly,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Pick File",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            "path" -> {
                val rawValue = getPortValueString(currentPortValue, input.dataType)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                        .then(
                            if (portErrors.isNotEmpty() && !isConnected) {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.error,
                                    MaterialTheme.shapes.small
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(
                            start = ToolkitTheme.spacing.small,
                            end = ToolkitTheme.spacing.extraSmall
                        )
                        .padding(vertical = ToolkitTheme.spacing.extraSmall)
                ) {
                    BasicTextField(
                        value = rawValue,
                        onValueChange = { onUpdateValue(node.id, input.id, SettingsUtils.stringToJson(it, input.dataType)) },
                        enabled = !isConnected && !isReadOnly,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.5f
                            )
                            else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }.weight(1f),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (rawValue.isEmpty() && !isConnected) {
                                    val pattern = (node as? Node.CapabilityNode)?.capability?.parameters?.get(input.id)?.autogeneratedPattern
                                    if (pattern != null) {
                                        val currentValues = node.inputs.associate {
                                            it.id to getPortValueString(it.value ?: it.defaultValue, it.dataType)
                                        }
                                        val dependenciesReady = PathPatternResolver.canResolve(
                                            pattern, 
                                            currentValues.filter { it.value.isNotBlank() }.keys
                                        )
                                        
                                        if (dependenciesReady) {
                                            val resolved = PathPatternResolver.tryResolve(pattern, currentValues)
                                            Text(
                                                text = if (resolved != null) stringResource(Res.string.auto_generated_path_format, resolved) else "Auto-generated at runtime",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text(
                                                text = "Auto-generated at runtime", //TODO: localize
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                innerTextField()
                            }
                        }
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                val pickedPath = PlatformUtils.pickFolder()
                                if (pickedPath != null) {
                                    val isArray = input.dataType is DataType.Array
                                    val newValue =
                                        appendPickedValue(rawValue, pickedPath, isArray)
                                    onUpdateValue(node.id, input.id, newValue)
                                }
                            }
                        },
                        enabled = !isConnected && !isReadOnly,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Pick Folder",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            "image", "audio", "video" -> {
                val rawValue = getPortValueString(currentPortValue, input.dataType)
                val isArray = input.dataType is DataType.Array
                val fileNames = getFileNames(rawValue, isArray)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                        .then(
                            if (portErrors.isNotEmpty() && !isConnected) {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.error,
                                    MaterialTheme.shapes.small
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(
                            start = ToolkitTheme.spacing.small,
                            end = ToolkitTheme.spacing.extraSmall
                        )
                        .padding(vertical = ToolkitTheme.spacing.extraSmall)
                ) {
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (fileNames.isNotEmpty()) fileNames else {
                                when (category) {
                                    "image" -> "No image"
                                    "audio" -> "No audio"
                                    else -> "No video"
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (fileNames.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (fileNames.isNotEmpty() && !isConnected && !isReadOnly) {
                        IconButton(
                            onClick = { onUpdateValue(node.id, input.id, null) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val allowedExtensions =
                                    SemanticRegistry.getAllowedExtensions(inferredSem)
                                val pickedPath =
                                    PlatformUtils.pickFile("Select File", allowedExtensions)
                                if (pickedPath != null) {
                                    val newValue =
                                        appendPickedValue(rawValue, pickedPath, isArray)
                                    onUpdateValue(node.id, input.id, newValue)
                                }
                            }
                        },
                        enabled = !isConnected && !isReadOnly,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Pick File",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            else -> {
                when (val type = input.dataType) {
                    is DataType.Primitive -> {
                        when (type.primitiveType) {
                            PrimitiveType.BOOLEAN -> {
                                val checked = getBooleanValue(currentPortValue)
                                Switch(
                                    checked = checked,
                                    onCheckedChange = {
                                        onUpdateValue(
                                            node.id,
                                            input.id,
                                            it
                                        )
                                    },
                                    enabled = !isConnected && !isReadOnly,
                                    modifier = Modifier.scale(0.8f)
                                )
                            }

                            PrimitiveType.INT -> {
                                val rawValue = getPortValueString(currentPortValue, input.dataType)
                                BasicTextField(
                                    value = rawValue,
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty()) {
                                            onUpdateValue(node.id, input.id, null)
                                        } else {
                                            val filtered =
                                                newValue.filterIndexed { index, c ->
                                                    c.isDigit() || (c == '-' && index == 0)
                                                }
                                            if (filtered == newValue || filtered.isNotEmpty()) {
                                                val parsed = filtered.toIntOrNull()
                                                onUpdateValue(
                                                    node.id,
                                                    input.id,
                                                    parsed ?: filtered
                                                )
                                            }
                                        }
                                    },
                                    enabled = !isConnected && !isReadOnly,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.5f
                                        )
                                        else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.shapes.small
                                        )
                                        .then(
                                            if (portErrors.isNotEmpty() && !isConnected) {
                                                Modifier.border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.error,
                                                    MaterialTheme.shapes.small
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(
                                            horizontal = ToolkitTheme.spacing.small,
                                            vertical = ToolkitTheme.spacing.extraSmall
                                        )
                                        .fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            PrimitiveType.DOUBLE -> {
                                val rawValue = getPortValueString(currentPortValue, input.dataType)
                                BasicTextField(
                                    value = rawValue,
                                    onValueChange = { newValue ->
                                        if (newValue.isEmpty()) {
                                            onUpdateValue(node.id, input.id, null)
                                        } else {
                                            val hasDot = newValue.count { it == '.' } <= 1
                                            val validMinus = newValue.lastIndexOf('-') <= 0
                                            val validChars =
                                                newValue.all { it.isDigit() || it == '.' || it == '-' }
                                            if (hasDot && validMinus && validChars) {
                                                val parsed = newValue.toDoubleOrNull()
                                                onUpdateValue(
                                                    node.id,
                                                    input.id,
                                                    parsed ?: newValue
                                                )
                                            }
                                        }
                                    },
                                    enabled = !isConnected && !isReadOnly,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.5f
                                        )
                                        else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.shapes.small
                                        )
                                        .then(
                                            if (portErrors.isNotEmpty() && !isConnected) {
                                                Modifier.border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.error,
                                                    MaterialTheme.shapes.small
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(
                                            horizontal = ToolkitTheme.spacing.small,
                                            vertical = ToolkitTheme.spacing.extraSmall
                                        )
                                        .fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            else -> {
                                val rawValue = getPortValueString(currentPortValue, input.dataType)
                                BasicTextField(
                                    value = rawValue,
                                    onValueChange = {
                                        onUpdateValue(
                                            node.id,
                                            input.id,
                                            it
                                        )
                                    },
                                    enabled = !isConnected && !isReadOnly,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.5f
                                        )
                                        else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.shapes.small
                                        )
                                        .then(
                                            if (portErrors.isNotEmpty() && !isConnected) {
                                                Modifier.border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.error,
                                                    MaterialTheme.shapes.small
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(
                                            horizontal = ToolkitTheme.spacing.small,
                                            vertical = ToolkitTheme.spacing.extraSmall
                                        )
                                        .fillMaxWidth(),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (rawValue.isEmpty() && !isConnected) {
                                                val pattern = (node as? Node.CapabilityNode)?.capability?.parameters?.get(input.id)?.autogeneratedPattern
                                                if (pattern != null) {
                                                    val currentValues = node.inputs.associate {
                                                        it.id to getPortValueString(it.value ?: it.defaultValue, it.dataType)
                                                    }
                                                    val dependenciesReady = PathPatternResolver.canResolve(
                                                        pattern, 
                                                        currentValues.filter { it.value.isNotBlank() }.keys
                                                    )
                                                    
                                                    if (dependenciesReady) {
                                                        val resolved = PathPatternResolver.tryResolve(pattern, currentValues)
                                                        Text(
                                                            text = if (resolved != null) stringResource(Res.string.auto_generated_path_format, resolved) else "Auto-generated at runtime", //TODO: localize
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "Auto-generated at runtime", //TODO: localize
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    is DataType.Enum -> {
                        val options = type.options
                        val rawValue = getPortValueString(currentPortValue, input.dataType)
                        val selected = rawValue.ifEmpty { options.firstOrNull() ?: "" }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ExpressiveMenu(
                                options = options,
                                selectedOption = selected,
                                onOptionSelected = { onUpdateValue(node.id, input.id, it) },
                                labelProvider = { it },
                                enabled = !isConnected && !isReadOnly
                            )
                        }
                    }

                    else -> {
                        val rawValue = getPortValueString(currentPortValue, input.dataType)
                        BasicTextField(
                            value = rawValue,
                            onValueChange = { onUpdateValue(node.id, input.id, SettingsUtils.stringToJson(it, input.dataType)) },
                            enabled = !isConnected && !isReadOnly,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = if (isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.5f
                                )
                                else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.shapes.small
                                )
                                .then(
                                    if (portErrors.isNotEmpty() && !isConnected) {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.error,
                                            MaterialTheme.shapes.small
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(
                                    horizontal = ToolkitTheme.spacing.small,
                                    vertical = ToolkitTheme.spacing.extraSmall
                                )
                                .fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}
