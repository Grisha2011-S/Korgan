package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Квадратная «плашка» со схематичной (outlined) иконкой. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/** Плоская карточка без тени: иконка + заголовок, справа — необязательное действие. */
@Composable
fun SectionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconBadge(icon = icon, tint = tint, size = 36.dp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                action?.invoke()
            }
            content()
        }
    }
}

/** Строка настройки: иконка, одна строка текста и переключатель. Вся строка кликабельна. */
@Composable
fun ToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Плитка для сетки на главном экране: иконка, крупное значение, короткая подпись. */
@Composable
fun InfoTile(
    icon: ImageVector,
    tint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.then(
            if (onClick != null) Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick) else Modifier
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(icon = icon, tint = tint, size = 36.dp)
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Схематичное изображение датчика-кулона: корпус, ремешок и «волны» сигнала.
 * При [active] волны расходятся по кругу, при [searching] — мигают.
 */
@Composable
fun DeviceIllustration(
    color: Color,
    active: Boolean,
    searching: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "device")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)

    Canvas(modifier = modifier.size(160.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val unit = size.minDimension / 10f
        val stroke = unit * 0.32f

        // Фоновый круг-«ореол»
        drawCircle(color = color.copy(alpha = 0.10f), radius = unit * 4.6f, center = c)

        // Расходящиеся волны
        if (active || searching) {
            for (i in 0 until 2) {
                val t = (phase + i * 0.5f) % 1f
                val alpha = if (active) (1f - t) * 0.55f else (if (t < 0.5f) 0.45f else 0.15f)
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = unit * (2.6f + t * 2.2f),
                    center = c,
                    style = Stroke(width = stroke * 0.7f)
                )
            }
        }

        // Ремешок (сверху и снизу)
        val strapW = unit * 1.6f
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(c.x - strapW / 2, c.y - unit * 3.4f),
            size = Size(strapW, unit * 1.3f),
            cornerRadius = CornerRadius(unit * 0.3f),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(c.x - strapW / 2, c.y + unit * 2.1f),
            size = Size(strapW, unit * 1.3f),
            cornerRadius = CornerRadius(unit * 0.3f),
            style = Stroke(width = stroke)
        )

        // Корпус
        val bodyW = unit * 3.6f
        val bodyH = unit * 4.2f
        drawRoundRect(
            color = color.copy(alpha = 0.18f),
            topLeft = Offset(c.x - bodyW / 2, c.y - bodyH / 2),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(unit * 1.1f)
        )
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(c.x - bodyW / 2, c.y - bodyH / 2),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(unit * 1.1f),
            style = Stroke(width = stroke)
        )

        // Индикатор / «пульс» на экране
        val y = c.y
        val x0 = c.x - unit * 1.1f
        val points = listOf(
            Offset(x0, y),
            Offset(x0 + unit * 0.55f, y),
            Offset(x0 + unit * 0.85f, y - unit * 0.8f),
            Offset(x0 + unit * 1.25f, y + unit * 0.8f),
            Offset(x0 + unit * 1.6f, y),
            Offset(x0 + unit * 2.2f, y)
        )
        val pulseColor = if (active) color else lineColor.copy(alpha = 0.4f)
        points.zipWithNext().forEach { (a, b) ->
            drawLine(pulseColor, a, b, strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}
