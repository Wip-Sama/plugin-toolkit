package org.wip.plugintoolkit.shared.components.sidebar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.wip.plugintoolkit.core.model.resolve
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import org.wip.plugintoolkit.shared.components.ToolkitTooltipPosition

private const val SIDEBAR_LABEL_REVEAL_THRESHOLD = 0.01f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SidebarItem(
    element: SidebarElement<out T>,
    isSelected: Boolean,
    onClick: () -> Unit,
    isExpanded: Boolean = true,
    position: SidebarItemPosition = SidebarItemPosition.StandAlone,
    expandProgress: Float = if (isExpanded) 1f else 0f
) {
    val motion = MaterialTheme.motionScheme
    val inputModeManager = LocalInputModeManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val showKeyboardFocus = isFocused && inputModeManager.inputMode == InputMode.Keyboard

    val hoverSurface = MaterialTheme.colorScheme.surfaceContainerHigh
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = ToolkitTheme.opacity.buttonBackground)
            isHovered || showKeyboardFocus -> hoverSurface
            else -> hoverSurface.copy(alpha = 0f)
        },
        animationSpec = motion.fastEffectsSpec(),
        label = "sidebarItemBackground"
    )

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.secondaryText)
    }

    val iconBackgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.high)
    }

    val iconTintColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.high)
    }

    val radiusLarge = ToolkitTheme.spacing.medium
    val radiusExtraSmall = ToolkitTheme.spacing.extraSmall
    val targetTop = if (position == SidebarItemPosition.Start || position == SidebarItemPosition.StandAlone) {
        radiusLarge
    } else {
        radiusExtraSmall
    }
    val targetBottom = if (position == SidebarItemPosition.End || position == SidebarItemPosition.StandAlone) {
        radiusLarge
    } else {
        radiusExtraSmall
    }

    val topRadius by animateDpAsState(targetTop, animationSpec = motion.fastSpatialSpec(), label = "sidebarItemTopRadius")
    val bottomRadius by animateDpAsState(targetBottom, animationSpec = motion.fastSpatialSpec(), label = "sidebarItemBottomRadius")

    val animatedShape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius
    )

    val resolvedTitle = element.title.resolve()
    var isTitleTruncated by remember(resolvedTitle) { mutableStateOf(false) }

    ToolkitTooltipBox(
        tooltip = if (!isExpanded || isTitleTruncated) resolvedTitle else null,
        position = if (!isExpanded) ToolkitTooltipPosition.Right else ToolkitTooltipPosition.Above
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ToolkitTheme.spacing.extraExtraSmall)
                .clip(animatedShape)
                .background(backgroundColor)
                .then(
                    if (showKeyboardFocus) {
                        Modifier.border(
                            width = ToolkitTheme.dimensions.progressIndicatorStroke,
                            color = MaterialTheme.colorScheme.primary,
                            shape = animatedShape
                        )
                    } else {
                        Modifier
                    }
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier.size(ToolkitTheme.dimensions.settingsIconContainerSize),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(ToolkitTheme.dimensions.settingsIconContainerSize)
                        .clip(ToolkitTheme.shapes.medium)
                        .background(iconBackgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = element.icon,
                        contentDescription = resolvedTitle,
                        tint = iconTintColor,
                        modifier = Modifier.size(ToolkitTheme.dimensions.sidebarIconSize)
                    )
                }
                if (expandProgress <= SIDEBAR_LABEL_REVEAL_THRESHOLD) {
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        element.trailingContent(false)
                    }
                }
            }

            if (expandProgress > SIDEBAR_LABEL_REVEAL_THRESHOLD) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = expandProgress.coerceIn(0f, 1f) }
                ) {
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resolvedTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result -> isTitleTruncated = result.hasVisualOverflow }
                        )
                        val resolvedDesc = element.description?.resolve()
                        if (!resolvedDesc.isNullOrBlank()) {
                            Text(
                                text = resolvedDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    element.trailingContent(true)
                }
            }
        }
    }
}
