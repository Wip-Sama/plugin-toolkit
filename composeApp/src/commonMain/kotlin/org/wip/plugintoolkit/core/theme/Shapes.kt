package org.wip.plugintoolkit.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private val outerCornerRadius = 16.dp
private val innerCornerRadius = 4.dp

val StartActionRowShape = RoundedCornerShape(
    topStart = outerCornerRadius,
    topEnd = outerCornerRadius,
    bottomStart = innerCornerRadius,
    bottomEnd = innerCornerRadius
)

val MiddleActionRowShape = RoundedCornerShape(innerCornerRadius)

val EndActionRowShape = RoundedCornerShape(
    topStart = innerCornerRadius,
    topEnd = innerCornerRadius,
    bottomStart = outerCornerRadius,
    bottomEnd = outerCornerRadius
)

val StandAloneActionRowShape = RoundedCornerShape(outerCornerRadius)
