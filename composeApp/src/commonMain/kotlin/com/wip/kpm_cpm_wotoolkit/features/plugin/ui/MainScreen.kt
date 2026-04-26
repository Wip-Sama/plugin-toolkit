package com.wip.kpm_cpm_wotoolkit.features.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel.PluginViewModel
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: PluginViewModel = viewModel { PluginViewModel() }
) {
    Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        PluginSidebar(viewModel = viewModel)
        PluginContent(viewModel = viewModel, modifier = Modifier.weight(1f))
    }
}

@Preview
@Composable
private fun MainScreenPreview() {
    MaterialTheme {
        MainScreen()
    }
}

