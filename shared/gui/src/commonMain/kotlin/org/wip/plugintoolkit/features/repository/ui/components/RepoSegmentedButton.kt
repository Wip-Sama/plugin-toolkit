package org.wip.plugintoolkit.features.repository.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.features.repository.viewmodel.RepoTypeTab
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.repo_tab_all
import plugintoolkit.composeapp.generated.resources.repo_tab_local
import plugintoolkit.composeapp.generated.resources.repo_tab_remote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoSegmentedButton(
    selectedTab: RepoTypeTab,
    totalCount: Int,
    remoteCount: Int,
    localCount: Int,
    onTabSelected: (RepoTypeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        RepoTypeTab.All to stringResource(Res.string.repo_tab_all, totalCount),
        RepoTypeTab.Remote to stringResource(Res.string.repo_tab_remote, remoteCount),
        RepoTypeTab.Local to stringResource(Res.string.repo_tab_local, localCount)
    )

    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth()
    ) {
        tabs.forEachIndexed { index, (tab, label) ->
            SegmentedButton(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }
    }
}
