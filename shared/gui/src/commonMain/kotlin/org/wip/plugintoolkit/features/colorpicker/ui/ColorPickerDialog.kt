package org.wip.plugintoolkit.features.colorpicker.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ColorEngine
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.colorpicker.logic.ColorPickerHelper
import org.wip.plugintoolkit.features.colorpicker.model.ColorPickerType
import org.wip.plugintoolkit.features.colorpicker.model.Colors
import org.wip.plugintoolkit.features.colorpicker.logic.HslColor
import org.wip.plugintoolkit.features.colorpicker.logic.HsvColor
import org.wip.plugintoolkit.features.colorpicker.utils.argb
import org.wip.plugintoolkit.features.colorpicker.utils.toHex
import org.wip.plugintoolkit.shared.components.GroupOrientation
import org.wip.plugintoolkit.shared.components.SelectedButtonGroup
import org.wip.plugintoolkit.shared.components.computeGroupedShape
import org.wip.plugintoolkit.shared.components.horizontalFadingEdges
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenu
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenuItem
import org.wip.plugintoolkit.shared.components.tooltip
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.color_picker_add_saved_color
import plugintoolkit.composeapp.generated.resources.color_picker_close
import plugintoolkit.composeapp.generated.resources.color_picker_delete_saved_color
import plugintoolkit.composeapp.generated.resources.color_picker_format_select
import plugintoolkit.composeapp.generated.resources.color_picker_paste_clipboard
import plugintoolkit.composeapp.generated.resources.color_picker_saved_colors
import plugintoolkit.composeapp.generated.resources.color_picker_select
import plugintoolkit.composeapp.generated.resources.color_picker_title
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_alpha
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_black
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_blue
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_chroma
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_cyan
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_delete_color
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_format
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_green
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_hex
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_hue
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_lightness
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_magenta
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_red
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_saturation
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_slider_alpha
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_slider_chroma
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_slider_hue
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_slider_lightness
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_slider_saturation
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_slider_tone
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_tone
import plugintoolkit.composeapp.generated.resources.color_picker_tooltip_yellow
import plugintoolkit.composeapp.generated.resources.color_picker_type_circle
import plugintoolkit.composeapp.generated.resources.color_picker_type_classic
import plugintoolkit.composeapp.generated.resources.color_picker_type_ring
import plugintoolkit.composeapp.generated.resources.color_picker_type_simple

private const val FORMAT_RGB = "RGB"
private const val FORMAT_HEX = "HEX"
private const val FORMAT_HSL = "HSL"
private const val FORMAT_CMYK = "CMYK"
private const val FORMAT_HCT = "HCT"

private val DEFAULT_PALETTE = listOf(
    Color(0xFF6750A4),
    Color(0xFF625B71),
    Color(0xFF7D5260),
    Color(0xFF006C50),
    Color(0xFFB3261E),
    Color(0xFFFF8F00),
    Color(0xFFFBC02D),
    Color(0xFF00838F),
    Color(0xFF1976D2),
    Color(0xFF5E35B1)
)

/**
 * Material 3 Expressive Color Picker Dialog.
 *
 * @param show            Whether the dialog is visible.
 * @param onDismissRequest Called when the user dismisses the dialog.
 * @param initialColor    Initial color to be displayed (defaults to White).
 * @param initialType     Initial picker style — defaults to [ColorPickerType.Classic].
 * @param onPickedColor   Callback invoked when the user confirms a color selection.
 */
@Composable
fun ColorPickerDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    initialColor: Color = Color.White,
    initialType: ColorPickerType = ColorPickerType.Classic(),
    onPickedColor: (Color) -> Unit
) {
    if (!show) return

    var color by remember(initialColor, show) { mutableStateOf(initialColor) }
    var selectedFormat by remember { mutableStateOf(FORMAT_RGB) }
    var type by remember { mutableStateOf(initialType) }
    var formatMenuExpanded by remember { mutableStateOf(false) }

    val initialHsv = remember(initialColor, show) { ColorPickerHelper.colorToHsv(initialColor) }
    val initialHsl = remember(initialColor, show) { ColorPickerHelper.colorToHsl(initialColor) }
    val initialHct = remember(initialColor, show) { ColorEngine.colorToHct(initialColor) }

    var currentHue by remember(initialColor, show) {
        mutableStateOf(if (initialHsv.saturation > 0.01f && initialHsv.value > 0.01f) initialHsv.hue else 0f)
    }
    var hsvSaturation by remember(initialColor, show) { mutableStateOf(initialHsv.saturation) }
    var hsvValue by remember(initialColor, show) { mutableStateOf(initialHsv.value) }

    var hslHue by remember(initialColor, show) {
        mutableStateOf(if (initialHsl.saturation > 0.01f && initialHsl.lightness > 0.01f && initialHsl.lightness < 0.99f) initialHsl.hue else currentHue)
    }
    var hslSaturation by remember(initialColor, show) { mutableStateOf(initialHsl.saturation) }
    var hslLightness by remember(initialColor, show) { mutableStateOf(initialHsl.lightness) }

    var hctHue by remember(initialColor, show) {
        mutableStateOf(if (initialHct.chroma > 1f) initialHct.hue else currentHue)
    }
    var hctChroma by remember(initialColor, show) { mutableStateOf(initialHct.chroma) }
    var hctTone by remember(initialColor, show) { mutableStateOf(initialHct.tone) }

    fun onExternalColorSelected(newColor: Color, newHue: Float? = null) {
        color = newColor
        val newHsv = ColorPickerHelper.colorToHsv(newColor)
        val effectiveHue = newHue ?: if (newHsv.saturation > 0.01f && newHsv.value > 0.01f) newHsv.hue else currentHue
        currentHue = effectiveHue
        if (newHsv.value > 0.01f) {
            hsvSaturation = newHsv.saturation
            hsvValue = newHsv.value
        } else {
            hsvValue = 0f
        }

        val newHsl = ColorPickerHelper.colorToHsl(newColor)
        hslHue = if (newHsl.saturation > 0.01f && newHsl.lightness > 0.01f && newHsl.lightness < 0.99f) newHsl.hue else effectiveHue
        hslSaturation = newHsl.saturation
        hslLightness = newHsl.lightness

        val newHct = ColorEngine.colorToHct(newColor)
        hctHue = if (newHct.chroma > 1f) newHct.hue else effectiveHue
        hctChroma = newHct.chroma
        hctTone = newHct.tone
    }

    fun updateHsl(h: Float, s: Float, l: Float) {
        hslHue = ((h % 360f) + 360f) % 360f
        hslSaturation = s.coerceIn(0f, 1f)
        hslLightness = l.coerceIn(0f, 1f)
        currentHue = hslHue
        val newColor = ColorPickerHelper.parseHsl(hslHue, hslSaturation * 100f, hslLightness * 100f, color.alpha * 100f)
        color = newColor
        val newHsv = ColorPickerHelper.colorToHsv(newColor)
        if (newHsv.value > 0.01f) {
            hsvSaturation = newHsv.saturation
            hsvValue = newHsv.value
        } else {
            hsvValue = 0f
        }
        val newHct = ColorEngine.colorToHct(newColor)
        hctHue = hslHue
        hctChroma = newHct.chroma
        hctTone = newHct.tone
    }

    fun updateHct(h: Float, c: Float, t: Float) {
        hctHue = ((h % 360f) + 360f) % 360f
        hctChroma = c.coerceAtLeast(0f)
        hctTone = t.coerceIn(0f, 100f)
        currentHue = hctHue
        val newColor = ColorEngine.hctToColor(hctHue, hctChroma, hctTone).copy(alpha = color.alpha)
        color = newColor
        val newHsv = ColorPickerHelper.colorToHsv(newColor)
        if (newHsv.value > 0.01f) {
            hsvSaturation = newHsv.saturation
            hsvValue = newHsv.value
        } else {
            hsvValue = 0f
        }
        val newHsl = ColorPickerHelper.colorToHsl(newColor)
        hslHue = hctHue
        hslSaturation = newHsl.saturation
        hslLightness = newHsl.lightness
    }

    val savedColors = remember {
        mutableStateListOf<Color>().apply {
            addAll(DEFAULT_PALETTE)
            if (!DEFAULT_PALETTE.any { it.toArgb() == initialColor.toArgb() }) {
                add(0, initialColor)
            }
        }
    }

    val classicTitle = stringResource(Res.string.color_picker_type_classic)
    val circleTitle = stringResource(Res.string.color_picker_type_circle)
    val ringTitle = stringResource(Res.string.color_picker_type_ring)
    val simpleTitle = stringResource(Res.string.color_picker_type_simple)

    val pickerTypeTitles = remember(classicTitle, circleTitle, ringTitle, simpleTitle) {
        listOf(classicTitle, circleTitle, ringTitle, simpleTitle)
    }

    val currentTypeIndex = remember(type, classicTitle, circleTitle, ringTitle, simpleTitle) {
        when (type) {
            is ColorPickerType.Classic -> 0
            is ColorPickerType.Circle -> 1
            is ColorPickerType.Ring -> 2
            is ColorPickerType.SimpleRing -> 3
        }
    }

    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val savedColorsScrollState = rememberScrollState()
    var swatchMenuIndex by remember { mutableStateOf<Int?>(null) }

    val isCurrentColorSaved = remember(color, savedColors.toList()) {
        savedColors.any { it.toArgb() == color.toArgb() }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.width(ToolkitTheme.dimensions.colorPickerDialogWidth),
            shape = ToolkitTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = ToolkitTheme.dimensions.elevationHighMedium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ToolkitTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                // 1. Header (Title + Close)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.color_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.settingsIconContainerSize)
                            .testTag("color_picker_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.color_picker_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2. Picker Mode Selector - Spans the whole width
                SelectedButtonGroup(
                    buttons = pickerTypeTitles,
                    startingIndex = currentTypeIndex,
                    fillMaxWidth = true,
                    modifier = Modifier.fillMaxWidth(),
                    onButtonSelected = { selected ->
                        type = when (selected) {
                            classicTitle -> ColorPickerType.Classic()
                            circleTitle -> ColorPickerType.Circle()
                            ringTitle -> ColorPickerType.Ring()
                            simpleTitle -> ColorPickerType.SimpleRing()
                            else -> ColorPickerType.Classic()
                        }
                    }
                )

                // 3. Main Interactive Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ToolkitTheme.dimensions.colorPickerCanvasHeight),
                    contentAlignment = Alignment.Center
                ) {
                    ColorPicker(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("color_picker_main_canvas"),
                        type = type,
                        selectedColor = color,
                        hue = currentHue,
                        showSliders = false,
                        onPickedColor = { newColor ->
                            onExternalColorSelected(newColor, currentHue)
                        }
                    )
                }

                // 4. Set from Clipboard & Mode-Specific Sliders Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    IconButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                ColorPickerHelper.parseAnyColor(clip)?.let { parsed ->
                                    onExternalColorSelected(parsed)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.settingsIconContainerSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = ToolkitTheme.opacity.chipTintedBackground))
                            .testTag("color_picker_clipboard_button")
                            .tooltip(Res.string.color_picker_paste_clipboard, delay = 1.seconds)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = stringResource(Res.string.color_picker_paste_clipboard),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                    ) {
                        val showHueSlider = type is ColorPickerType.Classic
                        val activeHue = when (selectedFormat) {
                            FORMAT_HSL -> hslHue
                            FORMAT_HCT -> hctHue
                            else -> currentHue
                        }

                        // Hue Slider (Circle, Ring, Simple replace the hue slider since they are themselves hue selectors)
                        if (showHueSlider) {
                            ColorSlideBar(
                                value = activeHue / 360f,
                                onValueChange = { progress ->
                                    val newHue = progress * 360f
                                    when (selectedFormat) {
                                        FORMAT_HSL -> {
                                            updateHsl(newHue, hslSaturation, hslLightness)
                                        }
                                        FORMAT_HCT -> {
                                            updateHct(newHue, hctChroma, hctTone)
                                        }
                                        else -> {
                                            currentHue = newHue
                                            hslHue = newHue
                                            hctHue = newHue
                                            val newColor = ColorPickerHelper.hsvToColor(
                                                HsvColor(newHue, hsvSaturation, hsvValue, color.alpha)
                                            )
                                            color = newColor
                                            val newHct = ColorEngine.colorToHct(newColor)
                                            hctChroma = newHct.chroma
                                            hctTone = newHct.tone
                                            val newHsl = ColorPickerHelper.colorToHsl(newColor)
                                            hslSaturation = newHsl.saturation
                                            hslLightness = newHsl.lightness
                                        }
                                    }
                                },
                                colors = Colors.gradientColors,
                                modifier = Modifier
                                    .testTag("color_picker_slider_hue")
                                    .tooltip(Res.string.color_picker_tooltip_slider_hue, delay = 1.seconds)
                            )
                        }

                        // Sliders decided by dropdown format
                        when (selectedFormat) {
                            FORMAT_HSL -> {
                                // Saturation Slider
                                ColorSlideBar(
                                    value = hslSaturation,
                                    onValueChange = { progress ->
                                        updateHsl(hslHue, progress, hslLightness)
                                    },
                                    colors = listOf(
                                        ColorPickerHelper.parseHsl(hslHue, 0f, hslLightness * 100f, 100f),
                                        ColorPickerHelper.parseHsl(hslHue, 100f, hslLightness * 100f, 100f)
                                    ),
                                    modifier = Modifier
                                        .testTag("color_picker_slider_hsl_saturation")
                                        .tooltip(Res.string.color_picker_tooltip_slider_saturation, delay = 1.seconds)
                                )

                                // Lightness Slider
                                ColorSlideBar(
                                    value = hslLightness,
                                    onValueChange = { progress ->
                                        updateHsl(hslHue, hslSaturation, progress)
                                    },
                                    colors = listOf(
                                        Color.Black,
                                        ColorPickerHelper.parseHsl(hslHue, hslSaturation * 100f, 50f, 100f),
                                        Color.White
                                    ),
                                    modifier = Modifier
                                        .testTag("color_picker_slider_hsl_lightness")
                                        .tooltip(Res.string.color_picker_tooltip_slider_lightness, delay = 1.seconds)
                                )
                            }

                            FORMAT_HCT -> {
                                // Chroma Slider
                                val currentChromaNorm = (hctChroma / 120f).coerceIn(0f, 1f)
                                ColorSlideBar(
                                    value = currentChromaNorm,
                                    onValueChange = { progress ->
                                        updateHct(hctHue, progress * 120f, hctTone)
                                    },
                                    colors = listOf(
                                        ColorEngine.hctToColor(hctHue, 0f, hctTone),
                                        ColorEngine.hctToColor(hctHue, 120f, hctTone)
                                    ),
                                    modifier = Modifier
                                        .testTag("color_picker_slider_hct_chroma")
                                        .tooltip(Res.string.color_picker_tooltip_slider_chroma, delay = 1.seconds)
                                )

                                // Tone Slider
                                val currentToneNorm = (hctTone / 100f).coerceIn(0f, 1f)
                                ColorSlideBar(
                                    value = currentToneNorm,
                                    onValueChange = { progress ->
                                        updateHct(hctHue, hctChroma, progress * 100f)
                                    },
                                    colors = listOf(
                                        ColorEngine.hctToColor(hctHue, hctChroma, 0f),
                                        ColorEngine.hctToColor(hctHue, hctChroma, 50f),
                                        ColorEngine.hctToColor(hctHue, hctChroma, 100f)
                                    ),
                                    modifier = Modifier
                                        .testTag("color_picker_slider_hct_tone")
                                        .tooltip(Res.string.color_picker_tooltip_slider_tone, delay = 1.seconds)
                                )
                            }
                        }

                        // Alpha Slider (present across all formats, clean gradient without checkerboard)
                        ColorSlideBar(
                            value = color.alpha,
                            onValueChange = { progress ->
                                color = color.copy(alpha = progress)
                            },
                            colors = listOf(color.copy(alpha = 0f), color.copy(alpha = 1f)),
                            modifier = Modifier
                                .testTag("color_picker_slider_alpha")
                                .tooltip(Res.string.color_picker_tooltip_slider_alpha, delay = 1.seconds)
                        )
                    }
                }

                // 5. Format Dropdown & Channel Inputs (Segmented Connected Row)
                FormatInputRow(
                    color = color,
                    format = selectedFormat,
                    onFormatChange = { selectedFormat = it },
                    onColorChange = { onExternalColorSelected(it) },
                    hslHue = hslHue,
                    hslSaturation = hslSaturation,
                    hslLightness = hslLightness,
                    onHslChange = { h, s, l -> updateHsl(h, s, l) },
                    hctHue = hctHue,
                    hctChroma = hctChroma,
                    hctTone = hctTone,
                    onHctChange = { h, c, t -> updateHct(h, c, t) },
                    menuExpanded = formatMenuExpanded,
                    onMenuExpandedChange = { formatMenuExpanded = it }
                )

                // 6. Saved Colors Header and Swatches (with tactile feedback, fast canc delete, fading edges)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.color_picker_saved_colors),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    // Delete current color if it is already in saved colors
                    if (isCurrentColorSaved) {
                        IconButton(
                            onClick = {
                                savedColors.removeAll { it.toArgb() == color.toArgb() }
                            },
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.settingsIconContainerSize)
                                .testTag("color_picker_delete_saved_color_button")
                                .tooltip(Res.string.color_picker_delete_saved_color, delay = 1.seconds)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(Res.string.color_picker_delete_saved_color),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (savedColors.none { it.toArgb() == color.toArgb() }) {
                                savedColors.add(color)
                            }
                        },
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.settingsIconContainerSize)
                            .testTag("color_picker_add_saved_color_button")
                            .tooltip(Res.string.color_picker_add_saved_color, delay = 1.seconds)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(Res.string.color_picker_add_saved_color),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalFadingEdges(
                            scrollState = savedColorsScrollState,
                            leftFadeLength = ToolkitTheme.spacing.medium,
                            rightFadeLength = ToolkitTheme.spacing.medium
                        )
                        .horizontalScroll(savedColorsScrollState),
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    savedColors.forEachIndexed { index, swatch ->
                        SavedColorSwatch(
                            swatch = swatch,
                            isSelected = color.toArgb() == swatch.toArgb(),
                            index = index,
                            onSelect = { onExternalColorSelected(swatch) },
                            onDelete = {
                                if (index in savedColors.indices) {
                                    savedColors.removeAt(index)
                                }
                            }
                        )
                    }
                }

                // 7. Select Confirm Button
                Button(
                    onClick = {
                        onPickedColor(color)
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("color_picker_select_button"),
                    shape = CircleShape
                ) {
                    Text(text = stringResource(Res.string.color_picker_select))
                }
            }
        }
    }
}

@Composable
private fun FormatInputRow(
    color: Color,
    format: String,
    onFormatChange: (String) -> Unit,
    onColorChange: (Color) -> Unit,
    hslHue: Float,
    hslSaturation: Float,
    hslLightness: Float,
    onHslChange: (h: Float, s: Float, l: Float) -> Unit,
    hctHue: Float,
    hctChroma: Float,
    hctTone: Float,
    onHctChange: (h: Float, c: Float, t: Float) -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit
) {
    val formats = remember { listOf(FORMAT_RGB, FORMAT_HEX, FORMAT_HSL, FORMAT_CMYK, FORMAT_HCT) }
    val controlHeight = ToolkitTheme.dimensions.segmentedButtonHeight
    val outerCorner = ToolkitTheme.dimensions.buttonGroupOuterCorner
    val innerCorner = ToolkitTheme.dimensions.buttonGroupInnerCorner

    val totalCount = when (format) {
        FORMAT_HEX -> 3
        FORMAT_CMYK -> 6
        else -> 5
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.dimensions.buttonGroupGap)
    ) {
        // Dropdown format selector with ToolkitDropdownMenu
        Box {
            Surface(
                onClick = { onMenuExpandedChange(true) },
                modifier = Modifier
                    .height(controlHeight)
                    .testTag("color_picker_format_dropdown")
                    .tooltip(Res.string.color_picker_tooltip_format, delay = 1.seconds),
                shape = computeGroupedShape(
                    index = 0,
                    totalCount = totalCount,
                    outerCorner = outerCorner,
                    innerCorner = innerCorner,
                    orientation = GroupOrientation.Horizontal
                ),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = ToolkitTheme.opacity.chipTintedBackground)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = ToolkitTheme.spacing.smallMedium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = format,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(Res.string.color_picker_format_select),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ToolkitDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) }
            ) {
                formats.forEach { f ->
                    ToolkitDropdownMenuItem(
                        text = { Text(f, style = MaterialTheme.typography.bodyMedium) },
                        isSelected = f == format,
                        modifier = Modifier.testTag("color_picker_format_item_$f"),
                        onClick = {
                            onFormatChange(f)
                            onMenuExpandedChange(false)
                        }
                    )
                }
            }
        }

        // Channel numeric input boxes based on format
        when (format) {
            FORMAT_RGB -> {
                val (alphaVal, rVal, gVal, bVal) = color.argb()
                val alphaPercent = (alphaVal / 255f * 100f).roundToInt()

                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_rgb_r",
                    value = rVal.toString(),
                    shape = computeGroupedShape(1, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_red,
                    onValueChanged = { str ->
                        val r = str.toIntOrNull()?.coerceIn(0, 255) ?: rVal
                        onColorChange(ColorPickerHelper.parseRgb(r, gVal, bVal, alphaPercent))
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_rgb_g",
                    value = gVal.toString(),
                    shape = computeGroupedShape(2, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_green,
                    onValueChanged = { str ->
                        val g = str.toIntOrNull()?.coerceIn(0, 255) ?: gVal
                        onColorChange(ColorPickerHelper.parseRgb(rVal, g, bVal, alphaPercent))
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_rgb_b",
                    value = bVal.toString(),
                    shape = computeGroupedShape(3, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_blue,
                    onValueChanged = { str ->
                        val b = str.toIntOrNull()?.coerceIn(0, 255) ?: bVal
                        onColorChange(ColorPickerHelper.parseRgb(rVal, gVal, b, alphaPercent))
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1.2f),
                    testTag = "color_picker_input_rgb_a",
                    value = "$alphaPercent%",
                    shape = computeGroupedShape(4, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_alpha,
                    onValueChanged = { str ->
                        val clean = str.removeSuffix("%").trim()
                        val a = clean.toIntOrNull()?.coerceIn(0, 100) ?: alphaPercent
                        onColorChange(ColorPickerHelper.parseRgb(rVal, gVal, bVal, a))
                    }
                )
            }

            FORMAT_HEX -> {
                val hexCode = color.toHex(hexPrefix = true, includeAlpha = false)
                val alphaPercent = (color.alpha * 100f).roundToInt()

                ChannelInputBox(
                    modifier = Modifier.weight(2.5f),
                    testTag = "color_picker_input_hex_code",
                    isNumeric = false,
                    value = hexCode,
                    shape = computeGroupedShape(1, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_hex,
                    onValueChanged = { str ->
                        ColorPickerHelper.parseHex(str)?.let { parsed ->
                            onColorChange(parsed.copy(alpha = color.alpha))
                        }
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1.2f),
                    testTag = "color_picker_input_hex_alpha",
                    value = "$alphaPercent%",
                    shape = computeGroupedShape(2, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_alpha,
                    onValueChanged = { str ->
                        val clean = str.removeSuffix("%").trim()
                        val a = clean.toIntOrNull()?.coerceIn(0, 100) ?: alphaPercent
                        onColorChange(color.copy(alpha = a / 100f))
                    }
                )
            }

            FORMAT_HSL -> {
                val hueInt = hslHue.roundToInt()
                val satInt = (hslSaturation * 100f).roundToInt()
                val lightInt = (hslLightness * 100f).roundToInt()
                val alphaInt = (color.alpha * 100f).roundToInt()

                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_hsl_hue",
                    value = "$hueInt°",
                    shape = computeGroupedShape(1, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_hue,
                    onValueChanged = { str ->
                        val h = str.removeSuffix("°").trim().toFloatOrNull()?.coerceIn(0f, 360f) ?: hslHue
                        onHslChange(h, hslSaturation, hslLightness)
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_hsl_sat",
                    value = "$satInt%",
                    shape = computeGroupedShape(2, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_saturation,
                    onValueChanged = { str ->
                        val s = (str.removeSuffix("%").trim().toFloatOrNull()?.coerceIn(0f, 100f) ?: (satInt.toFloat())) / 100f
                        onHslChange(hslHue, s, hslLightness)
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_hsl_light",
                    value = "$lightInt%",
                    shape = computeGroupedShape(3, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_lightness,
                    onValueChanged = { str ->
                        val l = (str.removeSuffix("%").trim().toFloatOrNull()?.coerceIn(0f, 100f) ?: (lightInt.toFloat())) / 100f
                        onHslChange(hslHue, hslSaturation, l)
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1.2f),
                    testTag = "color_picker_input_hsl_alpha",
                    value = "$alphaInt%",
                    shape = computeGroupedShape(4, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_alpha,
                    onValueChanged = { str ->
                        val a = (str.removeSuffix("%").trim().toFloatOrNull()?.coerceIn(0f, 100f) ?: (alphaInt.toFloat())) / 100f
                        onColorChange(color.copy(alpha = a))
                    }
                )
            }

            FORMAT_CMYK -> {
                val cmyk = ColorPickerHelper.colorToCmyk(color)
                val cInt = (cmyk.cyan * 100f).roundToInt()
                val mInt = (cmyk.magenta * 100f).roundToInt()
                val yInt = (cmyk.yellow * 100f).roundToInt()
                val kInt = (cmyk.keyBlack * 100f).roundToInt()
                val aInt = (cmyk.alpha * 100f).roundToInt()

                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_cmyk_c",
                    value = "$cInt%",
                    shape = computeGroupedShape(1, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_cyan,
                    onValueChanged = { str ->
                        val c = str.removeSuffix("%").trim().toFloatOrNull()?.coerceIn(0f, 100f) ?: cInt.toFloat()
                        onColorChange(ColorPickerHelper.parseCmyk(c, mInt.toFloat(), yInt.toFloat(), kInt.toFloat(), aInt.toFloat()))
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_cmyk_m",
                    value = "$mInt%",
                    shape = computeGroupedShape(2, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_magenta,
                    onValueChanged = { str ->
                        val m = str.removeSuffix("%").trim().toFloatOrNull()?.coerceIn(0f, 100f) ?: mInt.toFloat()
                        onColorChange(ColorPickerHelper.parseCmyk(cInt.toFloat(), m, yInt.toFloat(), kInt.toFloat(), aInt.toFloat()))
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_cmyk_y",
                    value = "$yInt%",
                    shape = computeGroupedShape(3, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_yellow,
                    onValueChanged = { str ->
                        val y = str.removeSuffix("%").trim().toFloatOrNull()?.coerceIn(0f, 100f) ?: yInt.toFloat()
                        onColorChange(ColorPickerHelper.parseCmyk(cInt.toFloat(), mInt.toFloat(), y, kInt.toFloat(), aInt.toFloat()))
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_cmyk_k",
                    value = "$kInt%",
                    shape = computeGroupedShape(4, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_black,
                    onValueChanged = { str ->
                        val k = str.removeSuffix("%").trim().toFloatOrNull()?.coerceIn(0f, 100f) ?: kInt.toFloat()
                        onColorChange(ColorPickerHelper.parseCmyk(cInt.toFloat(), mInt.toFloat(), yInt.toFloat(), k, aInt.toFloat()))
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1.2f),
                    testTag = "color_picker_input_cmyk_a",
                    value = "$aInt%",
                    shape = computeGroupedShape(5, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_alpha,
                    onValueChanged = { str ->
                        val a = str.removeSuffix("%").trim().toFloatOrNull()?.coerceIn(0f, 100f) ?: aInt.toFloat()
                        onColorChange(ColorPickerHelper.parseCmyk(cInt.toFloat(), mInt.toFloat(), yInt.toFloat(), kInt.toFloat(), a))
                    }
                )
            }

            FORMAT_HCT -> {
                val hueInt = hctHue.roundToInt()
                val chromaInt = hctChroma.roundToInt()
                val toneInt = hctTone.roundToInt()
                val alphaInt = (color.alpha * 100f).roundToInt()

                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_hct_hue",
                    value = "$hueInt°",
                    shape = computeGroupedShape(1, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_hue,
                    onValueChanged = { str ->
                        val h = str.removeSuffix("°").trim().toFloatOrNull()?.coerceIn(0f, 360f) ?: hctHue
                        onHctChange(h, hctChroma, hctTone)
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_hct_chroma",
                    value = chromaInt.toString(),
                    shape = computeGroupedShape(2, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_chroma,
                    onValueChanged = { str ->
                        val c = str.trim().toFloatOrNull()?.coerceAtLeast(0f) ?: hctChroma
                        onHctChange(hctHue, c, hctTone)
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1f),
                    testTag = "color_picker_input_hct_tone",
                    value = "$toneInt%",
                    shape = computeGroupedShape(3, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_tone,
                    onValueChanged = { str ->
                        val t = str.removeSuffix("%").trim().toFloatOrNull()?.coerceIn(0f, 100f) ?: hctTone
                        onHctChange(hctHue, hctChroma, t)
                    }
                )
                ChannelInputBox(
                    modifier = Modifier.weight(1.2f),
                    testTag = "color_picker_input_hct_alpha",
                    value = "$alphaInt%",
                    shape = computeGroupedShape(4, totalCount, outerCorner, innerCorner, GroupOrientation.Horizontal),
                    height = controlHeight,
                    tooltipRes = Res.string.color_picker_tooltip_alpha,
                    onValueChanged = { str ->
                        val a = (str.removeSuffix("%").trim().toFloatOrNull()?.coerceIn(0f, 100f) ?: (alphaInt.toFloat())) / 100f
                        onColorChange(color.copy(alpha = a))
                    }
                )
            }
        }
    }
}

@Composable
private fun ChannelInputBox(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    value: String,
    isNumeric: Boolean = true,
    shape: Shape = ToolkitTheme.shapes.small,
    height: Dp = ToolkitTheme.dimensions.segmentedButtonHeight,
    tooltipRes: StringResource? = null,
    onValueChanged: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }

    LaunchedEffect(value) {
        text = value
    }

    val boxModifier = if (tooltipRes != null) {
        modifier.tooltip(tooltipRes, delay = 1.seconds)
    } else {
        modifier
    }

    Box(
        modifier = boxModifier
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = ToolkitTheme.opacity.chipTintedBackground))
            .padding(horizontal = ToolkitTheme.spacing.extraSmall),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = text,
            onValueChange = { newStr ->
                text = newStr
                onValueChanged(newStr)
            },
            singleLine = true,
            textStyle = ToolkitTheme.codeSmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        )
    }
}

@Composable
private fun SavedColorSwatch(
    swatch: Color,
    isSelected: Boolean,
    index: Int,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.86f
            isHovered -> 1.10f
            isSelected -> 1.05f
            else -> 1.0f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "SwatchScale"
    )

    val swatchSize = ToolkitTheme.dimensions.colorPickerSwatchSize

    Box(
        modifier = modifier
            .size(swatchSize + ToolkitTheme.spacing.extraSmall * 2)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Delete || keyEvent.key == Key.Back)) {
                    onDelete()
                    true
                } else {
                    false
                }
            }
            .testTag("color_picker_swatch_$index"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(swatchSize)
                .clip(CircleShape)
                .background(swatch)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = ToolkitTheme.dimensions.borderSelected,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                    } else if (isHovered) {
                        Modifier.border(
                            width = ToolkitTheme.dimensions.strokeWidthThin,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                    } else {
                        Modifier.border(
                            width = ToolkitTheme.dimensions.strokeWidthThin,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.secondaryText),
                            shape = CircleShape
                        )
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false, radius = swatchSize / 2f + ToolkitTheme.spacing.extraSmall)
                ) {
                    onSelect()
                }
                .tooltip(Res.string.color_picker_tooltip_delete_color, delay = 1.seconds)
        )

        if (isHovered) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(ToolkitTheme.dimensions.iconExtraSmall)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onDelete)
                    .tooltip(Res.string.color_picker_tooltip_delete_color, delay = 1.seconds),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.color_picker_tooltip_delete_color),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMicro)
                )
            }
        }
    }
}
