package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.wip.plugintoolkit.core.theme.ToolkitTheme

/**
 * Material 3 Expressive morphing polygon loading indicator with optional container fill.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolkitLoadingIndicator(
    modifier: Modifier = Modifier,
    contained: Boolean = false,
    progress: Float? = null,
    color: Color = if (contained) {
        LoadingIndicatorDefaults.containedIndicatorColor
    } else {
        LoadingIndicatorDefaults.indicatorColor
    },
    containerColor: Color = LoadingIndicatorDefaults.containedContainerColor
) {
    val bounded = progress?.coerceIn(0f, 1f)
    if (contained) {
        if (bounded != null) {
            ContainedLoadingIndicator(
                progress = { bounded },
                modifier = modifier,
                indicatorColor = color,
                containerColor = containerColor
            )
        } else {
            ContainedLoadingIndicator(
                modifier = modifier,
                indicatorColor = color,
                containerColor = containerColor
            )
        }
    } else {
        if (bounded != null) {
            LoadingIndicator(
                progress = { bounded },
                modifier = modifier,
                color = color
            )
        } else {
            LoadingIndicator(
                modifier = modifier,
                color = color
            )
        }
    }
}

/**
 * Linear progress bar supporting both M3 Expressive wavy tracks and crisp flat bars.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolkitLinearProgressIndicator(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    wavy: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = ToolkitTheme.opacity.borderLow)
) {
    if (wavy) {
        if (progress != null) {
            val animatedProgress by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "wavyLinearProgress"
            )
            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = modifier.fillMaxWidth(),
                color = color,
                trackColor = trackColor
            )
        } else {
            LinearWavyProgressIndicator(
                modifier = modifier.fillMaxWidth(),
                color = color,
                trackColor = trackColor
            )
        }
    } else {
        val barModifier = modifier.clip(MaterialTheme.shapes.extraSmall)
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = barModifier,
                color = color,
                trackColor = trackColor
            )
        } else {
            LinearProgressIndicator(
                modifier = barModifier,
                color = color,
                trackColor = trackColor
            )
        }
    }
}

/**
 * Circular progress indicator supporting M3 Expressive wavy arcs and crisp ring tracks.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolkitCircularProgressIndicator(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    wavy: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = ToolkitTheme.opacity.borderLow)
) {
    if (wavy) {
        if (progress != null) {
            val animatedProgress by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
                label = "wavyCircularProgress"
            )
            CircularWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = modifier,
                color = color,
                trackColor = trackColor
            )
        } else {
            CircularWavyProgressIndicator(
                modifier = modifier,
                color = color,
                trackColor = trackColor
            )
        }
    } else {
        val sizedModifier = Modifier.size(ToolkitTheme.dimensions.wavyCircularSmallSize).then(modifier)
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = sizedModifier,
                color = color,
                trackColor = trackColor,
                strokeWidth = ToolkitTheme.dimensions.progressIndicatorStroke
            )
        } else {
            CircularProgressIndicator(
                modifier = sizedModifier,
                color = color,
                strokeWidth = ToolkitTheme.dimensions.progressIndicatorStroke
            )
        }
    }
}
