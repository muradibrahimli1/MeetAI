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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import com.sabahhub.meetai.ui.theme.Navy

/**
 * Frosted-glass surface styling. When [hazeState] is supplied, the surface uses
 * Haze to genuinely blur the content behind it (the gradient background);
 * otherwise it falls back to a translucent fill that reads as glass over the
 * blurred background blobs. Either way a soft highlight border is drawn.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    fillAlpha: Float = 0.10f,
    borderAlpha: Float = 0.28f,
    hazeState: HazeState? = null,
): Modifier {
    val base = this.clip(shape)
    val filled = if (hazeState != null) {
        base.hazeChild(
            state = hazeState,
            shape = shape,
            style = HazeStyle(
                backgroundColor = Navy,
                tint = HazeTint(Color.White.copy(alpha = fillAlpha)),
                blurRadius = 24.dp,
            ),
        )
    } else {
        base.background(Color.White.copy(alpha = fillAlpha))
    }
    return filled.border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = borderAlpha), Color.White.copy(alpha = borderAlpha * 0.25f))
        ),
        shape = shape,
    )
}

/** A glass panel container. Pass [hazeState] for real backdrop blur. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    fillAlpha: Float = 0.10f,
    hazeState: HazeState? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier.glass(RoundedCornerShape(cornerRadius), fillAlpha = fillAlpha, hazeState = hazeState)) {
        content()
    }
}
