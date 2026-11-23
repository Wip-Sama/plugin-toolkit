package org.wip.plugintoolkit.core.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * Scales a normalized `0..1` Material 3 [Morph] polygon path to fill layout bounds
 * centered at `size.center`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class NormalizedMorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress = progress.coerceIn(0f, 1f))
        path.transform(Matrix().apply { scale(x = size.width, y = size.height) })
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path)
    }
}

/**
 * Remembers a [NormalizedMorphShape] smoothly interpolating between two `MaterialShapes` polygons.
 */
@Composable
fun rememberMorphShape(
    from: RoundedPolygon,
    to: RoundedPolygon,
    progress: Float
): Shape {
    val morph = remember(from, to) { Morph(from, to) }
    return remember(morph, progress) { NormalizedMorphShape(morph, progress) }
}
