package org.wip.plugintoolkit.features.job.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// import org.koin.compose.viewmodel.koinViewModel
import org.wip.plugintoolkit.features.job.viewmodel.JobViewModel
import org.wip.plugintoolkit.core.theme.ToolkitTheme

@Composable
fun JobBadge(
    isExpanded: Boolean,
    viewModel: JobViewModel = org.koin.compose.koinInject()
) {
    val runningJobs by viewModel.runningJobs.collectAsState()
    val queuedJobs by viewModel.queuedJobs.collectAsState()

    val totalActive = runningJobs.size + queuedJobs.size

    if (totalActive > 0) {
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = ToolkitTheme.spacing.badgeHorizontal, vertical = ToolkitTheme.spacing.badgeVertical),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = totalActive.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(ToolkitTheme.dimensions.iconExtraSmall)
                    .offset(x = ToolkitTheme.spacing.extraSmall, y = (-4).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = totalActive.toString(),
                    color = ToolkitTheme.colors.white,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
