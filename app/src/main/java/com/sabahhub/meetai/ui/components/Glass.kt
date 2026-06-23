package com.sabahhub.meetai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Frosted-glass surface styling: a translucent white fill with a soft top-down
 * highlight border. Layered over [com.sabahhub.meetai.ui.theme.AppBackground]'s
 * blurred blobs, this reads as glassmorphism without needing real backdrop blur
 * (which isn't reliably available across API levels).
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    fillAlpha: Float = 0.10f,
    borderAlpha: Float = 0.28f,
): Modifier = this
    .clip(shape)
    .background(Color.White.copy(alpha = fillAlpha))
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = borderAlpha),
                Color.White.copy(alpha = borderAlpha * 0.25f),
            )
        ),
        shape = shape,
    )

/** A glass panel container. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    fillAlpha: Float = 0.10f,
    content: @Composable () -> Unit,
) {
    Box(modifier.glass(RoundedCornerShape(cornerRadius), fillAlpha = fillAlpha)) {
        content()
    }
}
