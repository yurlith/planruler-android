package com.planruler.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planruler.designsystem.theme.LocalPlanRulerDimens
import com.planruler.designsystem.theme.Space

/**
 * Tool selection is never signalled by colour alone: the selected button also grows
 * its icon, switches the label to a bolder style and shows a marker bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    badge: String? = null,
    showLabel: Boolean = true,
    selectedState: String? = null,
) {
    val dimens = LocalPlanRulerDimens.current
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .widthIn(min = 64.dp)
            .height(dimens.toolButtonHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            )
            .padding(horizontal = Space.x2)
            .semantics {
                contentDescription = label
                if (selectedState != null && selected) stateDescription = selectedState
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(icon, null, Modifier.size(if (selected) 26.dp else 24.dp), tint = content)
            if (badge != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopEnd).padding(start = 14.dp),
                ) {
                    Text(
                        badge,
                        Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        if (showLabel) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (selected) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(width = 18.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

enum class IndicatorStatus { OK, WARNING, ERROR, NEUTRAL }

/** Status is carried by colour *and* by fill/outline/icon so it survives greyscale. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IndicatorChip(
    icon: ImageVector,
    text: String,
    status: IndicatorStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    showText: Boolean = true,
    contentDescription: String? = null,
) {
    val dimens = LocalPlanRulerDimens.current
    val scheme = MaterialTheme.colorScheme
    val container = when (status) {
        IndicatorStatus.OK -> scheme.secondaryContainer
        IndicatorStatus.WARNING -> scheme.tertiaryContainer
        IndicatorStatus.ERROR -> scheme.errorContainer
        IndicatorStatus.NEUTRAL -> Color.Transparent
    }
    val onContainer = when (status) {
        IndicatorStatus.OK -> scheme.onSecondaryContainer
        IndicatorStatus.WARNING -> scheme.onTertiaryContainer
        IndicatorStatus.ERROR -> scheme.onErrorContainer
        IndicatorStatus.NEUTRAL -> scheme.onSurfaceVariant
    }
    val borderWidth = when (status) {
        IndicatorStatus.ERROR -> 2.dp
        IndicatorStatus.NEUTRAL -> 1.dp
        else -> 0.dp
    }
    Row(
        modifier = modifier
            .height(dimens.indicatorChipHeight)
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, onContainer.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            )
            .padding(horizontal = Space.x3)
            .semantics { contentDescription?.let { this.contentDescription = it } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = onContainer)
        if (showText) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun PlanRulerIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val dimens = LocalPlanRulerDimens.current
    Box(
        modifier = modifier
            .size(dimens.minTouchTarget)
            .clip(CircleShape)
            .combinedClickableCompat(enabled, onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            Modifier.size(24.dp),
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(enabled: Boolean, onClick: () -> Unit): Modifier =
    this.combinedClickable(enabled = enabled, onClick = onClick)

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.padding(Space.x6).widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(74.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            title,
            Modifier.padding(top = Space.x5),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            Modifier.padding(top = Space.x2),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(Modifier.padding(top = Space.x5)) { action() }
        }
    }
}

/** Contextual teaching: shown the first time a feature is actually needed. */
@Composable
fun CoachTip(
    text: String,
    onUnderstood: () -> Unit,
    onNeverShow: () -> Unit,
    understoodLabel: String,
    neverLabel: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(Space.x4)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.inverseOnSurface) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.x1),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onNeverShow) { Text(neverLabel, color = MaterialTheme.colorScheme.inversePrimary) }
                    TextButton(onUnderstood) { Text(understoodLabel, color = MaterialTheme.colorScheme.inversePrimary) }
                }
            }
        }
    }
}

@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.x5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions?.invoke()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier.fillMaxWidth().padding(start = Space.x4, end = Space.x4, top = Space.x5, bottom = Space.x1),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
