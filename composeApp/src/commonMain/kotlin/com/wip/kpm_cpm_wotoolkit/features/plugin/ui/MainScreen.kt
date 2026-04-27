package com.wip.kpm_cpm_wotoolkit.features.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel.PluginViewModel
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: PluginViewModel = koinViewModel()
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

