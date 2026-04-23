package com.wip.cmp_desktop_test.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectedButtonGroup(
    buttons: List<String>,
    modifier: Modifier = Modifier,
    startingIndex: Int = 0,
    onButtonSelected: (button: String) -> Unit = {},
) {
    var selectedIndex by remember { mutableIntStateOf(startingIndex) }
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEachIndexed { index, cinema ->
            ToggleButton(
                checked = selectedIndex == index,
                onCheckedChange = {
                    selectedIndex = index
                    onButtonSelected(cinema)
                },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        buttons.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                modifier = Modifier.semantics { role = Role.RadioButton },
            ) {
                Text(cinema)
            }
        }
    }
}