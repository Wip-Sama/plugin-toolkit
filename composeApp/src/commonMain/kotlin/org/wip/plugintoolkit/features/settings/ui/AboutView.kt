package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
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
import plugintoolkit.composeapp.generated.resources.about_icon_credits
import plugintoolkit.composeapp.generated.resources.about_icon_author
import plugintoolkit.composeapp.generated.resources.about_icon_site
import plugintoolkit.composeapp.generated.resources.app_logo
import plugintoolkit.composeapp.generated.resources.app_name
import plugintoolkit.composeapp.generated.resources.plugin_changelog

private data class Library(val name: String, val url: String)

private val libraries = listOf(
    Library("Compose Multiplatform", "https://www.jetbrains.com/lp/compose-multiplatform/"),
    Library("Kotlin Multiplatform", "https://kotlinlang.org/docs/multiplatform.html"),
    Library("Koin", "https://insert-koin.io/"),
    Library("Ktor", "https://ktor.io/"),
    Library("FileKit", "https://github.com/vinceglb/FileKit"),
    Library("Kermit", "https://github.com/touchlab/Kermit"),
    Library("JNA", "https://github.com/java-native-access/jna"),
    Library("kotlinx.serialization", "https://github.com/Kotlin/kotlinx.serialization"),
    Library("kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines"),
    Library("kotlinx.datetime", "https://github.com/Kotlin/kotlinx-datetime"),
    Library("kotlinx.io", "https://github.com/Kotlin/kotlinx-io")
)

@OptIn(ExperimentalResourceApi::class, ExperimentalLayoutApi::class)
@Composable
fun AboutView(
    dialogService: DialogService = koinInject()
) {
    val uriHandler = LocalUriHandler.current
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
            modifier = Modifier.padding(top = ToolkitTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            Image(
                painter = painterResource(Res.drawable.app_logo),
                contentDescription = stringResource(Res.string.app_name),
                modifier = Modifier.size(128.dp)
            )
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                ) {
                    libraries.forEach { library ->
                        AssistChip(
                            onClick = { uriHandler.openUri(library.url) },
                            label = { Text(library.name) }
                        )
                    }
                }
            }
        }

        // Icon Credits Section
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
                    text = stringResource(Res.string.about_icon_credits),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                ) {
                    AssistChip(
                        onClick = { uriHandler.openUri("https://www.svgrepo.com/author/muh_zakaria/") },
                        label = { Text(stringResource(Res.string.about_icon_author)) }
                    )
                    AssistChip(
                        onClick = { uriHandler.openUri("https://www.svgrepo.com/") },
                        label = { Text(stringResource(Res.string.about_icon_site)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))
    }
}
