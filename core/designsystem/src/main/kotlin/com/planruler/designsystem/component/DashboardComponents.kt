package com.planruler.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planruler.designsystem.theme.Space

/** Large, calm entry point used on the redesigned home screen. */
@Composable
fun PlanRulerHeroCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingText: String? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(Space.x5), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(28.dp))
                        }
                    }
                }
                Column(Modifier.padding(start = if (icon == null) 0.dp else Space.x3)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    supportingText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                        )
                    }
                }
            }
            Text(body, style = MaterialTheme.typography.bodyLarge)
            Button(onAction) { Text(actionLabel) }
        }
    }
}

/** Reusable professional tool tile with icon, description and optional visual preview. */
@Composable
fun PlanRulerToolTile(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    preview: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            if (preview != null) {
                Surface(
                    Modifier.fillMaxWidth().heightIn(min = 84.dp),
                    color = accent.copy(alpha = 0.09f),
                    contentColor = accent,
                ) {
                    Box(Modifier.padding(Space.x3), contentAlignment = Alignment.Center) { preview() }
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(Space.x4),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = accent.copy(alpha = 0.13f),
                    contentColor = accent,
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        body,
                        Modifier.padding(top = Space.x1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** One row in the dedicated application menu. */
@Composable
fun PlanRulerMenuRow(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(Space.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(23.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.let { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * Gradient header of the home screen: who/what at a glance and the single most likely
 * next step (continue the last project, or start one).
 */
@Composable
fun PlanRulerGradientHero(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.secondary)))
            .padding(Space.x5),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                if (icon != null) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, Modifier.size(26.dp), tint = Color.White) }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.86f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Square quick action: big touch target, icon first, at most two lines of title. */
@Composable
fun PlanRulerActionTile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 118.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(24.dp), tint = Color.White) }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Compact tool card for two-column grids: small illustration, title and a two-line hint. */
@Composable
fun PlanRulerCompactToolCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    preview: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(76.dp).background(accent.copy(alpha = 0.10f)).padding(Space.x2),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    Box(Modifier.fillMaxSize()) { preview() }
                } else {
                    Icon(icon, null, Modifier.size(36.dp), tint = accent)
                }
            }
            Column(Modifier.padding(Space.x3), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    Icon(icon, null, Modifier.size(18.dp), tint = accent)
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 32.dp),
                )
            }
        }
    }
}
