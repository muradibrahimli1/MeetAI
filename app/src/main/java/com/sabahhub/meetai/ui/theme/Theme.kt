package com.sabahhub.meetai.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
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

private val DarkColors = darkColorScheme(
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

private val LightColors = lightColorScheme(
    primary = DarkTeal,
    onPrimary = Color.White,
    secondary = DarkTeal,
    onSecondary = Color.White,
    tertiary = Teal,
    background = Color(0xFFEAF3F4),
    onBackground = Color(0xFF0A1E50),
    surface = Color(0xFFEAF3F4),
    onSurface = Color(0xFF0A1E50),
    onSurfaceVariant = Color(0xFF4A5A68),
    error = DangerRed,
    onError = Color.White,
)

@Composable
fun MeetAiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
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
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val gradient = if (darkTheme) {
        Brush.verticalGradient(
            0.0f to NavyDeep,
            0.55f to Navy,
            1.0f to Color(0xFF0C3A52),
        )
    } else {
        Brush.verticalGradient(
            0.0f to Color(0xFFEFF7F7),
            0.55f to Color(0xFFDDEDED),
            1.0f to Color(0xFFC7E6E4),
        )
    }
    Box(modifier.fillMaxSize()) {
        // Gradient + blurred blobs. Marked as the Haze source so glass surfaces
        // layered on top can genuinely blur it.
        Box(
            Modifier
                .fillMaxSize()
                .background(gradient)
                .then(if (hazeState != null) Modifier.haze(hazeState) else Modifier)
        ) {
            Box(Modifier.fillMaxSize().blur(90.dp)) { Blobs(darkTheme) }
        }
        content()
    }
}

@Composable
private fun Blobs(darkTheme: Boolean) {
    val strong = if (darkTheme) 0.55f else 0.40f
    val mid = if (darkTheme) 0.30f else 0.25f
    val soft = if (darkTheme) 0.45f else 0.30f
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Teal.copy(alpha = strong), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.22f),
                    radius = size.minDimension * 0.5f,
                ),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width * 0.15f, size.height * 0.22f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Mint.copy(alpha = mid), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.75f),
                    radius = size.minDimension * 0.6f,
                ),
                radius = size.minDimension * 0.6f,
                center = Offset(size.width * 0.9f, size.height * 0.75f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(DarkTeal.copy(alpha = soft), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.1f),
                    radius = size.minDimension * 0.4f,
                ),
                radius = size.minDimension * 0.4f,
                center = Offset(size.width * 0.8f, size.height * 0.1f),
            )
        }
    }
}
