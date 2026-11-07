package com.senzaproject.gamespace.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** A row of choice chips for an enum or any short list. Scrolls sideways instead of wrapping. */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

/**
 * A slider that shows its value while dragging and saves only when the finger lifts,
 * so a drag does not write to storage on every pixel.
 */
@Composable
fun CommitSlider(
    title: String,
    value: Int,
    range: IntRange,
    valueText: (Int) -> String,
    onCommit: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var dragging by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(valueText(dragging.roundToInt()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onCommit(dragging.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}

/**
 * A column whose rows can be reordered by dragging a handle. Rows have a fixed [rowHeight] so the drop position can
 * be computed exactly. Used for at most a handful of items, so it is a plain Column, not a lazy list.
 * [onMove] is called each time the dragged row passes its neighbour; [onDragEnd] when the finger lifts.
 */
@Composable
fun <T : Any> ReorderableColumn(
    items: List<T>,
    itemKey: (T) -> Any,
    rowHeight: Dp,
    onMove: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    row: @Composable (item: T, dragHandle: Modifier) -> Unit,
) {
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnEnd by rememberUpdatedState(onDragEnd)
    val currentKey by rememberUpdatedState(itemKey)

    Column(modifier) {
        items.forEach { item ->
            val k = itemKey(item)
            key(k) {
                val dragging = draggingKey == k
                val handle = Modifier.pointerInput(k) {
                    detectDragGestures(
                        onDragStart = { draggingKey = k; dragOffset = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val index = currentItems.indexOfFirst { currentKey(it) == k }
                            if (dragOffset > rowPx / 2 && index in 0 until currentItems.lastIndex) {
                                currentOnMove(index, index + 1)
                                dragOffset -= rowPx
                            } else if (dragOffset < -rowPx / 2 && index > 0) {
                                currentOnMove(index, index - 1)
                                dragOffset += rowPx
                            }
                        },
                        onDragEnd = { draggingKey = null; dragOffset = 0f; currentOnEnd() },
                        onDragCancel = { draggingKey = null; dragOffset = 0f; currentOnEnd() },
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .zIndex(if (dragging) 1f else 0f)
                        .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) },
                    contentAlignment = Alignment.CenterStart,
                ) { row(item, handle) }
            }
        }
    }
}

/** Simple bar chart. Bars share one scale; a zero value still draws a thin stub so the day is visible. */
@Composable
fun GmBarChart(values: List<Long>, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(96.dp)) {
        drawLine(guide, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        if (values.isEmpty()) return@Canvas
        val max = (values.max()).coerceAtLeast(1L).toFloat()
        val slot = size.width / values.size
        val barWidth = slot * 0.62f
        values.forEachIndexed { index, value ->
            val h = (value / max * (size.height - 2.dp.toPx())).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = if (value > 0) bar else guide,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}
