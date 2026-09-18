package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import org.wip.plugintoolkit.core.theme.ToolkitTheme

@Composable
fun ToolkitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(ToolkitTheme.dimensions.textFieldCornerRadius),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = ToolkitTheme.opacity.textFieldUnfocusedBorder),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.textFieldContainer),
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.textFieldContainer),
        errorBorderColor = MaterialTheme.colorScheme.error,
        errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = ToolkitTheme.opacity.textFieldContainer),
        errorLabelColor = MaterialTheme.colorScheme.error,
        errorSupportingTextColor = MaterialTheme.colorScheme.error
    ),
    compact: Boolean = false
) {
    if (compact) {
        var isFocused by remember { mutableStateOf(false) }
        val borderColor = when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline.copy(alpha = ToolkitTheme.opacity.textFieldUnfocusedBorder)
        }
        val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.textFieldContainer)
        val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.disabled)

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .height(ToolkitTheme.dimensions.textFieldHeightCompact)
                .onFocusChanged { isFocused = it.isFocused }
                .background(containerColor, shape)
                .border(
                    BorderStroke(ToolkitTheme.dimensions.strokeWidthThin, borderColor),
                    shape
                )
                .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.none),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = textColor,
                fontSize = 11.sp,
                lineHeight = 13.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leadingIcon != null) {
                        leadingIcon()
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            CompositionLocalProvider(
                                LocalTextStyle provides MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.disabled)
                                )
                            ) {
                                placeholder()
                            }
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                        trailingIcon()
                    }
                }
            }
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = label,
            placeholder = placeholder,
            singleLine = singleLine,
            enabled = enabled,
            readOnly = readOnly,
            isError = isError,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            shape = shape,
            colors = colors
        )
    }
}

