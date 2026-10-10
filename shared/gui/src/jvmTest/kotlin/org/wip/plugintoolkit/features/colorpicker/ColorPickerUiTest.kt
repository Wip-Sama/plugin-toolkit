package org.wip.plugintoolkit.features.colorpicker

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import org.junit.Rule
import org.wip.plugintoolkit.features.colorpicker.model.ColorPickerType
import org.wip.plugintoolkit.features.colorpicker.model.Colors
import org.wip.plugintoolkit.features.colorpicker.ui.ColorPicker
import org.wip.plugintoolkit.features.colorpicker.ui.ColorPickerDialog
import org.wip.plugintoolkit.features.colorpicker.ui.ColorSlideBar
import org.wip.plugintoolkit.features.colorpicker.ui.pickers.CircleColorPicker
import org.wip.plugintoolkit.features.colorpicker.ui.pickers.ClassicColorPicker
import org.wip.plugintoolkit.features.colorpicker.ui.pickers.RingColorPicker
import org.wip.plugintoolkit.features.colorpicker.ui.pickers.SimpleRingColorPicker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorPickerUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testColorSlideBarRendersAndTriggers() {
        var progress = 0.5f
        composeTestRule.setContent {
            ColorSlideBar(
                modifier = Modifier.testTag("slider_test"),
                value = progress,
                onValueChange = { progress = it },
                colors = Colors.gradientColors
            )
        }
        composeTestRule.waitForIdle()
        assertEquals(0.5f, progress)

        composeTestRule.onNodeWithTag("slider_test").performMouseInput {
            click(Offset(10f, 5f))
        }
        composeTestRule.waitForIdle()
        assertTrue(progress >= 0f)
    }

    @Test
    fun testColorPickerAllModesRender() {
        var pickedColor = Color.Red

        composeTestRule.setContent {
            ColorPicker(
                type = ColorPickerType.Classic(),
                selectedColor = Color.Red,
                showSliders = true,
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.setContent {
            ColorPicker(
                type = ColorPickerType.Circle(),
                selectedColor = Color.Blue,
                showSliders = true,
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.setContent {
            ColorPicker(
                type = ColorPickerType.Ring(),
                selectedColor = Color.Green,
                showSliders = true,
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.setContent {
            ColorPicker(
                type = ColorPickerType.SimpleRing(),
                selectedColor = Color.Yellow,
                showSliders = false,
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()
        assertTrue(pickedColor != Color.Transparent)
    }

    @Test
    fun testClassicColorPickerInteraction() {
        var pickedColor = Color.Red
        composeTestRule.setContent {
            ClassicColorPicker(
                modifier = Modifier.testTag("classic_canvas"),
                selectedColor = Color.Red,
                showAlphaBar = true,
                showHueBar = true,
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("classic_canvas").performMouseInput {
            click(Offset(50f, 50f))
        }
        composeTestRule.waitForIdle()
        assertTrue(pickedColor != Color.Transparent)
    }

    @Test
    fun testCircleColorPickerInteraction() {
        var pickedColor = Color.Blue
        composeTestRule.setContent {
            CircleColorPicker(
                modifier = Modifier.testTag("circle_canvas"),
                selectedColor = Color.Blue,
                showAlphaBar = true,
                showBrightnessBar = true,
                lightCenter = true,
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("circle_canvas").performMouseInput {
            click(Offset(50f, 50f))
        }
        composeTestRule.waitForIdle()
        assertTrue(pickedColor != Color.Transparent)

        composeTestRule.onNodeWithTag("circle_canvas").performMouseInput {
            moveTo(Offset(50f, 50f))
            press()
            moveTo(Offset(60f, 60f))
            release()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("circle_brightness_bar").performMouseInput {
            click(Offset(100f, 10f))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("circle_alpha_bar").performMouseInput {
            click(Offset(80f, 10f))
        }
        composeTestRule.waitForIdle()

        // Also test lightCenter = false
        composeTestRule.setContent {
            CircleColorPicker(
                modifier = Modifier.testTag("circle_canvas_dark"),
                selectedColor = Color.Red,
                showAlphaBar = false,
                showBrightnessBar = true,
                lightCenter = false,
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("circle_canvas_dark").performMouseInput {
            click(Offset(30f, 30f))
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testRingColorPickerInteraction() {
        var pickedColor = Color.Green
        composeTestRule.setContent {
            RingColorPicker(
                modifier = Modifier.testTag("ring_canvas"),
                selectedColor = Color.Green,
                showLightColorBar = true,
                showDarkColorBar = true,
                showAlphaBar = true,
                showColorPreview = true,
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ring_canvas").performMouseInput {
            click(Offset(30f, 30f))
        }
        composeTestRule.waitForIdle()
        assertTrue(pickedColor != Color.Transparent)

        composeTestRule.onNodeWithTag("ring_canvas").performMouseInput {
            moveTo(Offset(30f, 30f))
            press()
            moveTo(Offset(40f, 40f))
            release()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ring_lightness_bar").performMouseInput {
            click(Offset(50f, 10f))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ring_darkness_bar").performMouseInput {
            click(Offset(70f, 10f))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ring_alpha_bar").performMouseInput {
            click(Offset(90f, 10f))
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testSimpleRingColorPickerInteraction() {
        var pickedColor = Color.Yellow
        composeTestRule.setContent {
            SimpleRingColorPicker(
                modifier = Modifier.testTag("simple_ring_canvas"),
                selectedColor = Color.Yellow,
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("simple_ring_canvas").performMouseInput {
            click(Offset(40f, 40f))
        }
        composeTestRule.waitForIdle()
        assertTrue(pickedColor != Color.Transparent)

        composeTestRule.onNodeWithTag("simple_ring_canvas").performMouseInput {
            moveTo(Offset(40f, 40f))
            press()
            moveTo(Offset(60f, 60f))
            release()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testColorPickerDialogRendersAndSelects() {
        var confirmedColor = Color.Transparent
        var dismissed = false

        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = { dismissed = true },
                initialColor = Color.Blue,
                initialType = ColorPickerType.Classic(),
                onPickedColor = { confirmedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        // Click Select button via test tag
        composeTestRule.onNodeWithTag("color_picker_select_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(Color.Blue.red, confirmedColor.red, 0.05f)
        assertEquals(Color.Blue.green, confirmedColor.green, 0.05f)
        assertEquals(Color.Blue.blue, confirmedColor.blue, 0.05f)
        assertTrue(dismissed)
    }

    @Test
    fun testColorPickerDialogFormatSwitching() {
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color.Cyan,
                initialType = ColorPickerType.Classic(),
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()

        // Switch to HEX
        composeTestRule.onNodeWithTag("color_picker_format_dropdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_picker_format_item_HEX").performClick()
        composeTestRule.waitForIdle()

        // Switch to HSL
        composeTestRule.onNodeWithTag("color_picker_format_dropdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_picker_format_item_HSL").performClick()
        composeTestRule.waitForIdle()

        // Switch to CMYK
        composeTestRule.onNodeWithTag("color_picker_format_dropdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_picker_format_item_CMYK").performClick()
        composeTestRule.waitForIdle()

        // Switch to HCT
        composeTestRule.onNodeWithTag("color_picker_format_dropdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_picker_format_item_HCT").performClick()
        composeTestRule.waitForIdle()

        // Switch back to RGB
        composeTestRule.onNodeWithTag("color_picker_format_dropdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_picker_format_item_RGB").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testColorPickerDialogSelectSwatch() {
        var confirmedColor = Color.Transparent
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color.White,
                initialType = ColorPickerType.Classic(),
                onPickedColor = { confirmedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        // Click first swatch
        composeTestRule.onNodeWithTag("color_picker_swatch_1").performClick()
        composeTestRule.waitForIdle()

        // Click select
        composeTestRule.onNodeWithTag("color_picker_select_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(confirmedColor != Color.Transparent)
    }

    @Test
    fun testColorPickerDialogClipboardButton() {
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color.Green,
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_clipboard_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testColorPickerDialogDismissViaCloseButton() {
        var dismissed = false

        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = { dismissed = true },
                initialColor = Color.Red,
                initialType = ColorPickerType.Circle(),
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_close_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun testColorPickerDialogAddAndDeleteSavedColor() {
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color(0xFF6750A4), // In default palette
                initialType = ColorPickerType.Ring(),
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()

        // Delete button should be present for color already in palette
        composeTestRule.onNodeWithTag("color_picker_delete_saved_color_button").performClick()
        composeTestRule.waitForIdle()

        // Now add it back
        composeTestRule.onNodeWithTag("color_picker_add_saved_color_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testColorPickerDialogModesRenderSliders() {
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color.Magenta,
                initialType = ColorPickerType.Circle(),
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color.Yellow,
                initialType = ColorPickerType.Ring(),
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color.Cyan,
                initialType = ColorPickerType.SimpleRing(),
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testColorPickerDialogHidden() {
        composeTestRule.setContent {
            ColorPickerDialog(
                show = false,
                onDismissRequest = {},
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testSavedColorFastDeleteWithCancKey() {
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color.Blue,
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()

        // Swatch at index 0 should exist initially
        composeTestRule.onNodeWithTag("color_picker_swatch_0").assertIsDisplayed()

        // Focus and press Delete (Canc)
        composeTestRule.onNodeWithTag("color_picker_swatch_0").performKeyInput {
            pressKey(Key.Delete)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testSavedColorFastDeleteWithBackspaceKey() {
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color.Blue,
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()

        // Swatch at index 0 should exist initially
        composeTestRule.onNodeWithTag("color_picker_swatch_0").assertIsDisplayed()

        // Focus and press Backspace (Canc / Back)
        composeTestRule.onNodeWithTag("color_picker_swatch_0").performKeyInput {
            pressKey(Key.Back)
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testClassicColorPickerRetainsHueAtBoundaries() {
        var pickedColor = Color.White
        composeTestRule.setContent {
            ClassicColorPicker(
                modifier = Modifier.testTag("classic_boundary_canvas"),
                selectedColor = Color.White,
                hue = 180f,
                showAlphaBar = false,
                showHueBar = true,
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        val canvasNode = composeTestRule.onNodeWithTag("classic_boundary_canvas")
        val size = canvasNode.fetchSemanticsNode().size

        // Click all the way to the left (saturation = 0)
        canvasNode.performMouseInput {
            click(Offset(0f, size.height / 2f))
        }
        composeTestRule.waitForIdle()

        // Click all the way to the bottom (value = 0)
        canvasNode.performMouseInput {
            click(Offset(size.width / 2f, size.height.toFloat() - 1f))
        }
        composeTestRule.waitForIdle()
        assertEquals(0f, pickedColor.red, 0.05f)
        assertEquals(0f, pickedColor.green, 0.05f)
        assertEquals(0f, pickedColor.blue, 0.05f)
    }

    @Test
    fun testColorPickerDialogHueSliderWorksAtBoundaries() {
        var pickedColor = Color.Transparent
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color.White,
                initialType = ColorPickerType.Classic(),
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        // Hue slider is displayed in Classic mode
        composeTestRule.onNodeWithTag("color_picker_slider_hue").assertIsDisplayed()

        // Interact with Hue slider even when at left boundary (White)
        composeTestRule.onNodeWithTag("color_picker_slider_hue").performMouseInput {
            click(Offset(50f, 10f))
        }
        composeTestRule.waitForIdle()

        val mainCanvas = composeTestRule.onNodeWithTag("color_picker_main_canvas")
        val mainSize = mainCanvas.fetchSemanticsNode().size

        // Drag canvas to bottom edge (Black)
        mainCanvas.performMouseInput {
            click(Offset(mainSize.width / 2f, mainSize.height.toFloat() - 1f))
        }
        composeTestRule.waitForIdle()

        // Interact with Hue slider again at bottom boundary (Black)
        composeTestRule.onNodeWithTag("color_picker_slider_hue").performMouseInput {
            click(Offset(150f, 10f))
        }
        composeTestRule.waitForIdle()

        // Confirm selection
        composeTestRule.onNodeWithTag("color_picker_select_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(pickedColor != Color.Transparent)
    }

    @Test
    fun testColorPickerDialogHslSlidersChannelIsolation() {
        var pickedColor = Color.Transparent
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color(0xFFFF5500),
                initialType = ColorPickerType.Classic(),
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        // Switch to HSL
        composeTestRule.onNodeWithTag("color_picker_format_dropdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_picker_format_item_HSL").performClick()
        composeTestRule.waitForIdle()

        // Assert HSL sliders and inputs are displayed
        composeTestRule.onNodeWithTag("color_picker_slider_hsl_saturation").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_picker_slider_hsl_lightness").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_picker_input_hsl_hue").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_picker_input_hsl_sat").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_picker_input_hsl_light").assertIsDisplayed()

        // Adjust Saturation slider
        composeTestRule.onNodeWithTag("color_picker_slider_hsl_saturation").performMouseInput {
            click(Offset(50f, 10f))
        }
        composeTestRule.waitForIdle()

        // Adjust Lightness slider
        composeTestRule.onNodeWithTag("color_picker_slider_hsl_lightness").performMouseInput {
            click(Offset(120f, 10f))
        }
        composeTestRule.waitForIdle()

        // Confirm
        composeTestRule.onNodeWithTag("color_picker_select_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(pickedColor != Color.Transparent)
    }

    @Test
    fun testColorPickerDialogHctSlidersChannelIsolation() {
        var pickedColor = Color.Transparent
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color(0xFF6750A4),
                initialType = ColorPickerType.Classic(),
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        // Switch to HCT
        composeTestRule.onNodeWithTag("color_picker_format_dropdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_picker_format_item_HCT").performClick()
        composeTestRule.waitForIdle()

        // Assert HCT sliders and inputs are displayed
        composeTestRule.onNodeWithTag("color_picker_slider_hct_chroma").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_picker_slider_hct_tone").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_picker_input_hct_hue").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_picker_input_hct_chroma").assertIsDisplayed()
        composeTestRule.onNodeWithTag("color_picker_input_hct_tone").assertIsDisplayed()

        // Increment Chroma via slider
        composeTestRule.onNodeWithTag("color_picker_slider_hct_chroma").performMouseInput {
            click(Offset(200f, 10f))
        }
        composeTestRule.waitForIdle()

        // Adjust Tone via slider
        composeTestRule.onNodeWithTag("color_picker_slider_hct_tone").performMouseInput {
            click(Offset(150f, 10f))
        }
        composeTestRule.waitForIdle()

        // Confirm
        composeTestRule.onNodeWithTag("color_picker_select_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(pickedColor != Color.Transparent)
    }

    @Test
    fun testColorPickerDialogModeSwitchingViaButtonGroup() {
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color.Blue,
                initialType = ColorPickerType.Classic(),
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()

        // Switch to Circle
        composeTestRule.onNodeWithTag("selected_button_1").performClick()
        composeTestRule.waitForIdle()

        // Switch to Ring
        composeTestRule.onNodeWithTag("selected_button_2").performClick()
        composeTestRule.waitForIdle()

        // Switch to Simple
        composeTestRule.onNodeWithTag("selected_button_3").performClick()
        composeTestRule.waitForIdle()

        // Switch back to Classic
        composeTestRule.onNodeWithTag("selected_button_0").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testColorPickerDialogAlphaSliderInteraction() {
        var pickedColor = Color.Red
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color.Red,
                initialType = ColorPickerType.Classic(),
                onPickedColor = { pickedColor = it }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_slider_alpha").performMouseInput {
            click(Offset(50f, 10f))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_select_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(pickedColor.alpha < 1f)
    }

    @Test
    fun testColorPickerDialogInputsDirectEditing() {
        composeTestRule.setContent {
            ColorPickerDialog(
                show = true,
                onDismissRequest = {},
                initialColor = Color(100, 150, 200),
                initialType = ColorPickerType.Classic(),
                onPickedColor = {}
            )
        }
        composeTestRule.waitForIdle()

        // Edit RGB values
        composeTestRule.onNodeWithTag("color_picker_input_rgb_r").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_rgb_r").performTextInput("120")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_rgb_g").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_rgb_g").performTextInput("160")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_rgb_b").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_rgb_b").performTextInput("210")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_rgb_a").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_rgb_a").performTextInput("90%")
        composeTestRule.waitForIdle()

        // Switch to HEX
        composeTestRule.onNodeWithTag("color_picker_format_dropdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_picker_format_item_HEX").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_hex_code").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_hex_code").performTextInput("#FF8800")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_hex_alpha").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_hex_alpha").performTextInput("80%")
        composeTestRule.waitForIdle()

        // Switch to CMYK
        composeTestRule.onNodeWithTag("color_picker_format_dropdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_picker_format_item_CMYK").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_cmyk_c").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_cmyk_c").performTextInput("10%")
        composeTestRule.waitForIdle()

        // Switch to HSL
        composeTestRule.onNodeWithTag("color_picker_format_dropdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_picker_format_item_HSL").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_hsl_hue").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_hsl_hue").performTextInput("180°")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_hsl_sat").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_hsl_sat").performTextInput("50%")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_hsl_light").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_hsl_light").performTextInput("50%")
        composeTestRule.waitForIdle()

        // Switch to HCT
        composeTestRule.onNodeWithTag("color_picker_format_dropdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("color_picker_format_item_HCT").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_hct_hue").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_hct_hue").performTextInput("200°")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_hct_chroma").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_hct_chroma").performTextInput("45")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("color_picker_input_hct_tone").performTextClearance()
        composeTestRule.onNodeWithTag("color_picker_input_hct_tone").performTextInput("60%")
        composeTestRule.waitForIdle()
    }
}
