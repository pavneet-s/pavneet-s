package com.pavneet.workoutcycle.ui.cable

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.StackLoad
import kotlin.math.min

/** Rep tempo: a short pause at the bottom, about a second to lift, then a squeeze at the top. */
private const val BOTTOM_PAUSE_MILLIS = 150
private const val LIFT_MILLIS = 1100
private const val TOP_SQUEEZE_MILLIS = 350

/**
 * A looping demonstration of [movement] on a cable machine. It is decorative for screen
 * readers; show the movement's name as text nearby.
 *
 * @param load how much of the weight stack to light up and lift, e.g. from the weight entered.
 * @param containerColor the background, also used to outline the arm where it crosses the body.
 */
@Composable
fun CableMachineAnimation(
    movement: CableMovement,
    modifier: Modifier = Modifier,
    load: StackLoad = CableDrawing.PREVIEW_LOAD,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val phase by rememberInfiniteTransition(label = "cableRep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = BOTTOM_PAUSE_MILLIS + LIFT_MILLIS + TOP_SQUEEZE_MILLIS
                0f at BOTTOM_PAUSE_MILLIS using FastOutSlowInEasing
                1f at BOTTOM_PAUSE_MILLIS + LIFT_MILLIS
            },
            repeatMode = RepeatMode.Reverse,
        ),
        label = "repPhase",
    )
    val scheme = MaterialTheme.colorScheme
    Canvas(
        modifier
            .background(containerColor)
            .clearAndSetSemantics {},
    ) {
        // Reading the phase here, in the draw phase, redraws each frame without recomposing.
        drawCableFrame(CablePoses.frame(movement, phase), load) { ink -> scheme.inkColor(ink, containerColor) }
    }
}

private fun ColorScheme.inkColor(ink: Ink, background: Color): Color = when (ink) {
    Ink.BACKGROUND -> background
    Ink.FLOOR, Ink.PLATE -> outlineVariant
    Ink.MACHINE -> outline
    Ink.PLATE_LIFTED, Ink.HANDLE -> primary
    Ink.CABLE -> onSurfaceVariant
    Ink.FIGURE_FAR -> onSurface.copy(alpha = 0.32f)
    Ink.FIGURE -> onSurface
}

/** Maps scene units onto a canvas of [size]: scaled to fit, centred, keeping the proportions. */
class SceneFit(size: Size) {
    val scale = min(size.width / CableScene.VIEW_WIDTH, size.height / CableScene.VIEW_HEIGHT)
    private val originX = (size.width - CableScene.VIEW_WIDTH * scale) / 2f
    private val originY = (size.height - CableScene.VIEW_HEIGHT * scale) / 2f

    fun toOffset(point: ScenePoint) =
        Offset(originX + (point.x - CableScene.VIEW_LEFT) * scale, originY + (point.y - CableScene.VIEW_TOP) * scale)
}

private fun DrawScope.drawCableFrame(frame: CableFrame, load: StackLoad, color: (Ink) -> Color) {
    val fit = SceneFit(size)
    val scale = fit.scale

    fun ScenePoint.toOffset() = fit.toOffset(this)

    for (shape in CableDrawing.shapes(frame, load)) {
        when (shape) {
            is SceneShape.Line -> drawLine(
                color = color(shape.ink),
                start = shape.from.toOffset(),
                end = shape.to.toOffset(),
                strokeWidth = shape.width * scale,
                cap = StrokeCap.Round,
            )
            is SceneShape.Circle -> drawCircle(
                color = color(shape.ink),
                radius = shape.radius * scale,
                center = shape.center.toOffset(),
                style = shape.strokeWidth?.let { Stroke(it * scale) } ?: Fill,
            )
            is SceneShape.Box -> drawRoundRect(
                color = color(shape.ink),
                topLeft = ScenePoint(shape.left, shape.top).toOffset(),
                size = Size((shape.right - shape.left) * scale, (shape.bottom - shape.top) * scale),
                cornerRadius = CornerRadius(shape.corner * scale),
                style = shape.strokeWidth?.let { Stroke(it * scale) } ?: Fill,
            )
        }
    }
}
