package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectedButtonGroup(
    buttons: List<String>,
    modifier: Modifier = Modifier,
    startingIndex: Int = 0,
    fillMaxWidth: Boolean = false,
    onButtonSelected: (button: String) -> Unit = {},
) {
    var selectedIndex by remember(startingIndex) { mutableIntStateOf(startingIndex) }
    androidx.compose.runtime.LaunchedEffect(startingIndex) {
        selectedIndex = startingIndex
    }
    val scrollState = rememberScrollState()

    Row(
        modifier = if (fillMaxWidth) {
            modifier
        } else {
            modifier.horizontalScroll(scrollState)
        },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEachIndexed { index, buttonText ->
            val buttonModifier = if (fillMaxWidth) {
                Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton }
            } else {
                Modifier.semantics { role = Role.RadioButton }
            }

            ToggleButton(
                checked = selectedIndex == index,
                onCheckedChange = {
                    selectedIndex = index
                    onButtonSelected(buttonText)
                },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        buttons.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                modifier = buttonModifier.testTag("selected_button_$index"),
            ) {
                Text(buttonText)
            }
        }
    }
}

@Preview
@Composable
private fun SelectedButtonGroupPreview() {
    AppTheme(appearance = AppearanceSettings()) {
        SelectedButtonGroup(
            buttons = listOf("Option A", "Option B", "Option C"),
            startingIndex = 1
        )
    }
}

