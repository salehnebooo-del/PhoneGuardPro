package com.phoneguard.pro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.phoneguard.pro.data.model.CoreInfo
import com.phoneguard.pro.data.model.DataSource
import com.phoneguard.pro.data.model.Severity
import com.phoneguard.pro.ui.theme.PgAccentBrush
import com.phoneguard.pro.ui.theme.PgBlue
import com.phoneguard.pro.ui.theme.PgCyan
import com.phoneguard.pro.ui.theme.PgDanger
import com.phoneguard.pro.ui.theme.PgOutline
import com.phoneguard.pro.ui.theme.PgSuccess
import com.phoneguard.pro.ui.theme.PgSurface
import com.phoneguard.pro.ui.theme.PgSurface2
import com.phoneguard.pro.ui.theme.PgText
import com.phoneguard.pro.ui.theme.PgTextDim
import com.phoneguard.pro.ui.theme.PgWarning
import com.phoneguard.pro.ui.theme.scoreColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.verticalGradient(listOf(PgSurface2, PgSurface)))
            .border(1.dp, Brush.linearGradient(listOf(PgBlue.copy(alpha = 0.45f), PgCyan.copy(alpha = 0.06f))), CardShape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun SectionTitle(title: String, icon: ImageVector? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(PgBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = PgCyan, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = PgText, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun SourceBadge(source: DataSource) {
    val c = when (source) {
        DataSource.MEASURED -> PgCyan
        DataSource.REPORTED -> PgBlue
        DataSource.CALCULATED -> PgSuccess
        DataSource.ESTIMATED -> PgWarning
        DataSource.UNAVAILABLE -> PgTextDim
    }
    Text(
        source.label.uppercase(),
        color = c,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun InfoRow(label: String, value: String, source: DataSource? = null, note: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = PgTextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.45f))
            Row(Modifier.weight(0.55f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value, color = PgText, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (source != null) {
                    Spacer(Modifier.width(6.dp))
                    SourceBadge(source)
                }
            }
        }
        if (note != null) {
            Text(note, color = PgTextDim.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun ScoreGauge(
    score: Int?,
    modifier: Modifier = Modifier,
    diameter: Dp = 170.dp,
    strokeWidth: Dp = 14.dp,
    label: String = "HEALTH"
) {
    val target = (score ?: 0) / 100f
    val animated by animateFloatAsState(target, tween(1200, easing = FastOutSlowInEasing), label = "gauge")
    val color = scoreColor(score)
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(PgOutline, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                color.copy(alpha = 0.18f), 135f, 270f * animated, false, topLeft, arcSize,
                style = Stroke(stroke * 2f, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.linearGradient(listOf(PgBlue, color)),
                startAngle = 135f, sweepAngle = 270f * animated, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                score?.let { (animated * 100).toInt().toString() } ?: "--",
                style = MaterialTheme.typography.displaySmall, color = PgText, fontWeight = FontWeight.Bold
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp, color: Color? = null) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(PgOutline)) {
        Box(
            Modifier.fillMaxWidth(anim).fillMaxHeight().clip(RoundedCornerShape(50))
                .background(if (color != null) SolidColor(color) else PgAccentBrush)
        )
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    fraction: Float? = null,
    accent: Color = PgCyan
) {
    GlassCard(modifier, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = PgTextDim)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = PgText, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PgTextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (fraction != null) {
            Spacer(Modifier.height(8.dp))
            UsageBar(fraction, color = if (fraction > 0.9f) PgDanger else if (fraction > 0.75f) PgWarning else null)
        }
    }
}

/** Animated line chart drawn on Canvas with gradient fill and optional Y labels. */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    minY: Float? = null,
    maxY: Float? = null,
    lineColor: Color = PgCyan,
    fillColor: Color = PgBlue,
    gridLines: Int = 4,
    emptyText: String = "Collecting data…",
    yLabel: ((Float) -> String)? = null
) {
    if (values.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(emptyText, color = PgTextDim, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val measurer = rememberTextMeasurer()
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val labelStyle = TextStyle(color = PgTextDim, fontSize = 9.sp)

    Canvas(modifier) {
        val lo = minY ?: values.min()
        val hiRaw = maxY ?: values.max()
        val hi = if (hiRaw - lo < 1e-6f) lo + 1f else hiRaw
        val range = hi - lo
        val leftPad = if (yLabel != null) 34.dp.toPx() else 0f
        val w = size.width - leftPad
        val h = size.height

        for (i in 0..gridLines) {
            val y = h * i / gridLines
            drawLine(PgOutline, Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1f)
            if (yLabel != null) {
                val v = hi - range * i / gridLines
                val tl = measurer.measure(yLabel(v), labelStyle)
                drawText(tl, topLeft = Offset(0f, (y - tl.size.height / 2f).coerceIn(0f, h - tl.size.height)))
            }
        }

        val stepX = w / (values.size - 1)
        val line = Path()
        val area = Path()
        values.forEachIndexed { i, v ->
            val x = leftPad + i * stepX
            val y = h - ((v - lo) / range).coerceIn(0f, 1f) * h
            if (i == 0) {
                line.moveTo(x, y); area.moveTo(x, h); area.lineTo(x, y)
            } else {
                line.lineTo(x, y); area.lineTo(x, y)
            }
        }
        area.lineTo(leftPad + (values.size - 1) * stepX, h)
        area.close()

        clipRect(right = leftPad + w * reveal.value) {
            drawPath(area, Brush.verticalGradient(listOf(fillColor.copy(alpha = 0.35f), Color.Transparent), startY = 0f, endY = h))
            drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val lastY = h - ((values.last() - lo) / range).coerceIn(0f, 1f) * h
            drawCircle(lineColor, 4.dp.toPx(), Offset(leftPad + (values.size - 1) * stepX, lastY))
        }
    }
}

/** Per-core current frequency as a fraction of that core's max frequency. */
@Composable
fun CoreFreqBars(cores: List<CoreInfo>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        cores.forEach { c ->
            key(c.index) {
                val cur = c.curFreqKhz
                val max = c.maxFreqKhz
                val f = if (c.online && cur != null && max != null && max > 0) cur.toFloat() / max else 0f
                val anim by animateFloatAsState(f.coerceIn(0f, 1f), tween(600), label = "core")
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(PgOutline.copy(alpha = 0.6f)))
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(anim.coerceAtLeast(0.02f))
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.verticalGradient(listOf(PgCyan, PgBlue)))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(if (c.online) "C${c.index}" else "off", fontSize = 10.sp, color = PgTextDim)
                }
            }
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

fun severityColor(s: Severity): Color = when (s) {
    Severity.CRITICAL -> PgDanger
    Severity.WARNING -> PgWarning
    Severity.INFO -> PgCyan
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PgBlue, contentColor = Color.White)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PgCyan.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PgCyan)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppIcon(packageName: String, size: Dp = 36.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val b = bitmap
    if (b != null) Image(b, null, Modifier.size(size).clip(RoundedCornerShape(10.dp)))
    else Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(PgSurface2))
}

@Composable
fun NoteText(text: String) {
    Text(text, color = PgTextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun DataSourceLegend() {
    Column {
        Text("Data source labels", style = MaterialTheme.typography.labelLarge, color = PgTextDim)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SourceBadge(DataSource.REPORTED); SourceBadge(DataSource.MEASURED); SourceBadge(DataSource.CALCULATED)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SourceBadge(DataSource.ESTIMATED); SourceBadge(DataSource.UNAVAILABLE)
        }
    }
}
