package org.wip.plugintoolkit.shared.components.plugin.inputs

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class EnumDropdownInputTest {

    @Test
    fun testEnumDropdownInput_lockedOptionDisabledWhenLockFalse() = runDesktopComposeUiTest {
        val enumType = DataType.Enum(
            className = "TestEnum",
            options = listOf("ONLINE", "EXPERIMENTAL"),
            optionLockRequirements = mapOf("EXPERIMENTAL" to listOf("feature_unlocked"))
        )
        val metadata = ParameterMetadata(
            description = "Test Mode",
            type = enumType
        )

        setContent {
            EnumDropdownInput(
                name = "mode",
                metadata = metadata,
                value = "ONLINE",
                onValueChange = {},
                providedLocks = mapOf("feature_unlocked" to false)
            )
        }

        onNodeWithText("ONLINE").performClick()
        onNodeWithText("EXPERIMENTAL").assertIsNotEnabled()
    }

    @Test
    fun testEnumDropdownInput_lockedOptionEnabledWhenLockTrue() = runDesktopComposeUiTest {
        val enumType = DataType.Enum(
            className = "TestEnum",
            options = listOf("ONLINE", "EXPERIMENTAL"),
            optionLockRequirements = mapOf("EXPERIMENTAL" to listOf("feature_unlocked"))
        )
        val metadata = ParameterMetadata(
            description = "Test Mode",
            type = enumType
        )

        setContent {
            EnumDropdownInput(
                name = "mode",
                metadata = metadata,
                value = "ONLINE",
                onValueChange = {},
                providedLocks = mapOf("feature_unlocked" to true)
            )
        }

        onNodeWithText("ONLINE").performClick()
        onNodeWithText("EXPERIMENTAL").assertIsEnabled()
    }
}

