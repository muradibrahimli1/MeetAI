package com.sabahhub.meetai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.sabahhub.meetai.ui.theme.Mint
import com.sabahhub.meetai.ui.theme.Teal
import kotlin.math.max

/**
 * Scrolling bar waveform driven by recent mic amplitudes (0f..1f). The newest
 * sample is on the right; older samples scroll left. When [amplitudes] is empty
 * a flat idle line is drawn.
 */
@Composable
fun Waveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    barCount: Int = 56,
) {
    val brush = Brush.verticalGradient(listOf(Mint, Teal))
    Canvas(modifier) {
        val barWidth = size.width / (barCount * 2f)
        val midY = size.height / 2f
        val recent = amplitudes.takeLast(barCount)

        for (i in 0 until barCount) {
            // Map bar index to a sample, right-aligned (newest on the right).
            val sampleIndex = i - (barCount - recent.size)
            val amp = recent.getOrNull(sampleIndex) ?: 0f
            val barHeight = max(barWidth, amp * size.height * 0.9f)
            val x = i * (size.width / barCount) + barWidth / 2f
            drawLine(
                brush = brush,
                start = Offset(x, midY - barHeight / 2f),
                end = Offset(x, midY + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
