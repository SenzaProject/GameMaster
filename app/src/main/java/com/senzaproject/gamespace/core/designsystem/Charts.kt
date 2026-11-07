package com.senzaproject.gamespace.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Line graph of real samples, oldest on the left. Draws nothing until there are at least two values.
 * [minValue] and [maxValue] fix the vertical range (for example 0..100 for percentages); when null the
 * range follows the data.
 */
@Composable
fun GmSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    minValue: Float? = null,
    maxValue: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val guide = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        // Baseline so the graph has a visible frame even while it is still filling up.
        drawLine(guide, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        if (values.size < 2) return@Canvas
        val low = minValue ?: values.min()
        val high = maxValue ?: values.max()
        val range = (high - low).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - low) / range).coerceIn(0f, 1f) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Horizontal meter for a value between 0 and 100. */
@Composable
fun GmMeter(percent: Int, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { percent.coerceIn(0, 100) / 100f },
        modifier = modifier.fillMaxWidth(),
    )
}
