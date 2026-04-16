package com.wip.cmp_desktop_test.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

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
