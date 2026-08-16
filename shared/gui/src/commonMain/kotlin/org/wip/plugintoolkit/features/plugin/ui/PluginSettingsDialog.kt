package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginSettingsViewModel

@Composable
fun PluginSettingsDialog(
    pkg: String,
    scrollToSetting: String? = null,
    onDismiss: () -> Unit,
    viewModel: PluginSettingsViewModel = koinInject(parameters = { parametersOf(pkg) })
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f)
                .widthIn(max = ToolkitTheme.dimensions.dialogMaxWidth)
                .heightIn(max = ToolkitTheme.dimensions.dialogMaxHeight)
        ) {
            PluginSettingsContent(
                pkg = pkg,
                scrollToSetting = scrollToSetting,
                onDismiss = onDismiss,
                viewModel = viewModel
            )
        }
    }
}
