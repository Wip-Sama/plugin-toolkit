package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.*

@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.semantics { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ToolkitTheme.dimensions.settingsIconSize)
        )
        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Preview
@Composable
private fun SectionHeaderPreview() {
    AppTheme(appearance = AppearanceSettings()) {
        SectionHeader(
            title = stringResource(Res.string.preview_section),
            icon = Icons.Default.Star,
            modifier = Modifier.padding(ToolkitTheme.spacing.medium)
        )
    }
}

