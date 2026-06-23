package com.sabahhub.meetai.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

private val MeetAiColors = darkColorScheme(
    primary = Teal,
    onPrimary = NavyDeep,
    secondary = Mint,
    onSecondary = NavyDeep,
    tertiary = DarkTeal,
    background = Navy,
    onBackground = TextPrimary,
    surface = Navy,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    error = DangerRed,
    onError = Color.White,
)

@Composable
fun MeetAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MeetAiColors, content = content)
}

/**
 * Full-screen gradient background with soft, blurred color blobs. The blur gives
 * the glass surfaces layered on top something to refract, selling the frosted
 * "glassmorphism" look.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        // Gradient + blurred blobs. Marked as the Haze source so glass surfaces
        // layered on top can genuinely blur it.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to NavyDeep,
                        0.55f to Navy,
                        1.0f to Color(0xFF0C3A52), // navy easing toward teal at the bottom
                    )
                )
                .then(if (hazeState != null) Modifier.haze(hazeState) else Modifier)
        ) {
            Box(Modifier.fillMaxSize().blur(90.dp)) { Blobs() }
        }
        content()
    }
}

@Composable
private fun Blobs() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Teal.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.22f),
                    radius = size.minDimension * 0.5f,
                ),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width * 0.15f, size.height * 0.22f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Mint.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.75f),
                    radius = size.minDimension * 0.6f,
                ),
                radius = size.minDimension * 0.6f,
                center = Offset(size.width * 0.9f, size.height * 0.75f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(DarkTeal.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.1f),
                    radius = size.minDimension * 0.4f,
                ),
                radius = size.minDimension * 0.4f,
                center = Offset(size.width * 0.8f, size.height * 0.1f),
            )
        }
    }
}
