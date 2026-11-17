package com.phoneguard.pro.ui.screens

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.phoneguard.pro.ui.theme.PgBg
import com.phoneguard.pro.ui.theme.PgBlue
import com.phoneguard.pro.ui.theme.PgCyan
import com.phoneguard.pro.ui.theme.PgDanger
import com.phoneguard.pro.ui.theme.PgOutline
import com.phoneguard.pro.ui.theme.PgSuccess
import com.phoneguard.pro.ui.theme.PgSurface
import com.phoneguard.pro.ui.theme.PgText
import com.phoneguard.pro.ui.theme.PgTextDim

private const val COLS = 9
private const val ROWS = 16

/**
 * Real touchscreen diagnostic: the screen is split into a grid; every cell the digitizer reports a
 * touch in is marked. Untouched cells after "Finish" are potential dead zones. Also records the
 * maximum number of simultaneous touch points the panel reported.
 */
@Composable
fun TouchTestScreen(onExit: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val touched = remember { mutableStateListOf<Boolean>().apply { repeat(COLS * ROWS) { add(false) } } }
    var maxPointers by remember { mutableIntStateOf(0) }
    var pointers by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var finished by remember { mutableStateOf(false) }
    val coverage by remember { derivedStateOf { touched.count { it } / (COLS * ROWS).toFloat() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(PgBg)
            .systemGestureExclusion()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size > maxPointers) maxPointers = pressed.size
                        pointers = pressed.map { it.position }
                        val cw = size.width / COLS.toFloat()
                        val ch = size.height / ROWS.toFloat()
                        pressed.forEach { p ->
                            val col = (p.position.x / cw).toInt().coerceIn(0, COLS - 1)
                            val row = (p.position.y / ch).toInt().coerceIn(0, ROWS - 1)
                            val idx = row * COLS + col
                            if (!touched[idx]) touched[idx] = true
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cw = size.width / COLS
            val ch = size.height / ROWS
            for (r in 0 until ROWS) for (c in 0 until COLS) {
                val idx = r * COLS + c
                val tl = Offset(c * cw, r * ch)
                val color = when {
                    touched[idx] -> PgCyan.copy(alpha = 0.35f)
                    finished -> PgDanger.copy(alpha = 0.45f)
                    else -> null
                }
                if (color != null) drawRect(color, tl, Size(cw, ch))
                drawRect(PgOutline, tl, Size(cw, ch), style = Stroke(1f))
            }
            pointers.forEachIndexed { i, p ->
                drawCircle(PgBlue.copy(alpha = 0.35f), 44.dp.toPx(), p)
                drawCircle(PgCyan, 44.dp.toPx(), p, style = Stroke(3.dp.toPx()))
                if (i == 0) drawCircle(PgCyan, 4.dp.toPx(), p)
            }
        }

        Column(
            Modifier.align(Alignment.Center).clip(RoundedCornerShape(18.dp))
                .background(PgSurface.copy(alpha = 0.88f)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TOUCH TEST", color = PgCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Coverage ${(coverage * 100).toInt()}%", color = PgText, style = MaterialTheme.typography.titleLarge)
            Text("Max simultaneous touches: $maxPointers", color = PgTextDim, style = MaterialTheme.typography.bodySmall)
            if (finished) {
                val dead = touched.count { !it }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (dead == 0) "No dead zones found ✓" else "$dead cells never registered a touch (red). Retest them carefully; persistent red cells indicate a digitizer fault.",
                    color = if (dead == 0) PgSuccess else PgDanger,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text("Swipe over every cell. Use several fingers to test multi-touch.", color = PgTextDim, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    for (i in touched.indices) touched[i] = false
                    maxPointers = 0
                    finished = false
                }) { Text("Reset", color = PgTextDim) }
                TextButton(onClick = { finished = true }) { Text("Finish", color = PgCyan) }
                TextButton(onClick = onExit) { Text("Exit", color = PgText) }
            }
        }
    }
}
