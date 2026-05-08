package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.AppConfig
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.api.utils.ChangelogParser
import org.wip.plugintoolkit.api.Release
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.about_built_by
import plugintoolkit.composeapp.generated.resources.about_libraries
import plugintoolkit.composeapp.generated.resources.about_libraries_placeholder
import plugintoolkit.composeapp.generated.resources.app_name
import plugintoolkit.composeapp.generated.resources.plugin_changelog

@OptIn(ExperimentalResourceApi::class)
@Composable
fun AboutView(
    dialogService: DialogService = koinInject()
) {
    var changelogVersions by remember { mutableStateOf<List<Release>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val content = Res.readBytes("files/CHANGELOG.md").decodeToString()
            changelogVersions = ChangelogParser.parse(content).releases

        } catch (e: Exception) {
            // Log or handle error
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.large)
    ) {
        // App Identity
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = ToolkitTheme.spacing.large)
        ) {
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Version ${AppConfig.VERSION}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Built by
        Text(
            text = stringResource(Res.string.about_built_by),
            style = MaterialTheme.typography.bodyLarge
        )

        val appName = stringResource(Res.string.app_name)
        // Changelog Button
        Button(
            onClick = {
                dialogService.showChangelog(appName, changelogVersions) //TODO: need replacement with App_name
            },
            modifier = Modifier.fillMaxWidth(0.5f)
        ) {
            Text(stringResource(Res.string.plugin_changelog))
        }

        // Libraries Section
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolkitTheme.spacing.medium)
        ) {
            Column(
                modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
            ) {
                Text(
                    text = stringResource(Res.string.about_libraries),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(Res.string.about_libraries_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))
    }
}
