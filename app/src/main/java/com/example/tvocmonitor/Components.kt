package com.example.tvocmonitor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------- palette
object Palette {
    val BgTop = Color(0xFF1B3346)
    val BgBottom = Color(0xFF13202E)
    val Glow = Color(0xFF1F8A8A)
    val CardTop = Color(0x1FFFFFFF)
    val CardBottom = Color(0x0DFFFFFF)
    val CardBorder = Color(0x26FFFFFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFC8D5DF)
    val TextMuted = Color(0xFF8FA3B3)
    val Green = Color(0xFF3DD68C)
    val Yellow = Color(0xFFF2C94C)
    val Orange = Color(0xFFF2994A)
    val Red = Color(0xFFE5533D)
    val IconTint = Color(0xFF9FC4D9)
    val BottomBar = Color(0xFF1D2B3B)
}

data class Level(val label: String, val color: Color)

/** Kategori TVOC (ppb) – mengikuti pedoman umum kualitas udara dalam ruang untuk sensor SGP30. */
fun tvocLevel(v: Int?): Level = when {
    v == null -> Level("--", Palette.TextMuted)
    v <= 220 -> Level("Good", Palette.Green)
    v <= 660 -> Level("Moderate", Palette.Yellow)
    v <= 2200 -> Level("Poor", Palette.Orange)
    else -> Level("Unhealthy", Palette.Red)
}

/** Kategori eCO2 (ppm). */
fun eco2Level(v: Int?): Level = when {
    v == null -> Level("--", Palette.TextMuted)
    v < 1000 -> Level("Safe", Palette.Green)
    v < 2000 -> Level("Elevated", Palette.Yellow)
    else -> Level("High", Palette.Red)
}

fun Int?.fmt(): String = this?.let { String.format(Locale.US, "%,d", it) } ?: "--"
fun Double?.fmt2(): String = this?.let { String.format(Locale.US, "%.2f", it) } ?: "--"

// ---------------------------------------------------------------- glass card
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Palette.CardTop, Palette.CardBottom)))
            .border(1.dp, Palette.CardBorder, shape)
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun StatusBadge(level: Level) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(level.color)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(level.label, color = Color(0xFF0F2A1C), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- tick scale
/** Skala garis (hijau -> kuning -> merah). Garis di bawah [fraction] tampil terang. */
@Composable
fun TickScale(fraction: Float, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "tick")
    Canvas(modifier.fillMaxWidth().height(26.dp)) {
        val n = 21
        val step = size.width / (n - 1)
        for (i in 0 until n) {
            val t = i / (n - 1f)
            val color = if (t < 0.5f) lerp(Palette.Green, Palette.Yellow, t * 2f)
            else lerp(Palette.Yellow, Palette.Red, (t - 0.5f) * 2f)
            val major = i % 5 == 0
            val h = if (major) size.height else size.height * 0.55f
            val x = (i * step).coerceIn(2f, size.width - 2f)
            val active = t <= anim + 0.001f
            drawLine(
                color = color.copy(alpha = if (active) 1f else 0.3f),
                start = Offset(x, size.height - h),
                end = Offset(x, size.height),
                strokeWidth = if (major) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

// ---------------------------------------------------------------- eCO2 gauge
@Composable
fun Eco2Gauge(value: Int?, modifier: Modifier = Modifier) {
    val min = 400f
    val max = 2000f
    val frac = (((value ?: 400) - min) / (max - min)).coerceIn(0f, 1f)
    val anim by animateFloatAsState(frac, label = "gauge")
    val color = eco2Level(value).color.takeIf { value != null } ?: Palette.Green
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = 135f, sweepAngle = (270f * anim).coerceAtLeast(1f), useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // titik di ujung jarum
            val angle = Math.toRadians((135f + 270f * anim).toDouble())
            val r = d / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = color,
                radius = stroke * 0.75f,
                center = Offset(c.x + r * cos(angle).toFloat(), c.y + r * sin(angle).toFloat()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.fmt(), color = Palette.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("ppm", color = Palette.TextSecondary, fontSize = 14.sp)
        }
    }
}
