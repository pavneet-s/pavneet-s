package com.pavneet.workoutcycle.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.using
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.pavneet.workoutcycle.R
import com.pavneet.workoutcycle.domain.CableMovement
import com.pavneet.workoutcycle.domain.StackLoad
import com.pavneet.workoutcycle.ui.Haptic
import com.pavneet.workoutcycle.ui.cable.CableMachineAnimation
import com.pavneet.workoutcycle.ui.cable.CablePoses
import com.pavneet.workoutcycle.ui.cable.SceneFit
import com.pavneet.workoutcycle.ui.cable.labelRes
import com.pavneet.workoutcycle.ui.cable.tipsRes
import com.pavneet.workoutcycle.ui.rememberHaptics
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** How long each form tip stays up before the next one. */
private const val TIP_MILLIS = 6_000L

private val BUBBLE_MARGIN = 10.dp
private val BUBBLE_PADDING_H = 12.dp
private val BUBBLE_PADDING_V = 8.dp
private val BUBBLE_RADIUS = 16.dp
private val TAIL_WIDTH = 14.dp
private val TAIL_HEIGHT = 10.dp

/**
 * A muscle group's cable exercises, one page each: swipe to pick the one you're doing. Each
 * page loops its demo with the weight stack loaded by [loadFor], while the figure talks you
 * through common mistakes. Below are page dots, the exercise's name and pulley height, and
 * [setup] if one is saved.
 *
 * @param onSelect called when you settle on a different exercise.
 */
@Composable
fun CableExercisePager(
    choices: List<CableMovement>,
    selected: CableMovement,
    loadFor: (CableMovement) -> StackLoad,
    onSelect: (CableMovement) -> Unit,
    setup: String?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val pagerState = rememberPagerState(initialPage = choices.indexOf(selected).coerceAtLeast(0)) { choices.size }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    // A swipe that lands on another exercise picks it.
    LaunchedEffect(pagerState, choices) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val movement = choices.getOrNull(page) ?: return@collect
            if (movement != currentSelected) {
                haptics.perform(Haptic.SWIPE)
                currentOnSelect(movement)
            }
        }
    }
    // Follow a choice made elsewhere, such as the picker on the manage screen.
    LaunchedEffect(selected, choices) {
        val page = choices.indexOf(selected)
        if (page >= 0 && page != pagerState.settledPage) pagerState.animateScrollToPage(page)
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            key = { choices[it].name },
            pageSpacing = 12.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            TalkingCableDemo(
                movement = choices[page],
                load = loadFor(choices[page]),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge),
            )
        }
        val shown = choices[pagerState.currentPage.coerceIn(choices.indices)]
        if (choices.size > 1) {
            Spacer(Modifier.height(8.dp))
            PageDots(count = choices.size, current = pagerState.currentPage)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                R.string.cable_caption,
                stringResource(shown.labelRes),
                stringResource(shown.pulley.labelRes),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (setup != null) SetupLine(setup)
    }
}

/**
 * The looping demo with a speech bubble above the figure's head, cycling through [movement]'s
 * common mistakes. Tap the bubble for the next one.
 */
@Composable
private fun TalkingCableDemo(movement: CableMovement, load: StackLoad, modifier: Modifier = Modifier) {
    val tips = stringArrayResource(movement.tipsRes)
    // Start somewhere different each time, so the same muscle group doesn't always open on one tip.
    var tipIndex by rememberSaveable(movement) { mutableIntStateOf(Random.nextInt(tips.size)) }
    LaunchedEffect(tipIndex) {
        delay(TIP_MILLIS)
        tipIndex = (tipIndex + 1) % tips.size
    }
    val haptics = rememberHaptics()
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val textStyle = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val head = remember(movement) { CablePoses.frame(movement, 0f).head }
    var tailX by remember { mutableFloatStateOf(0f) }

    Layout(
        content = {
            SpeechBubble(
                text = tips[tipIndex],
                style = textStyle,
                tailX = { tailX },
                onClick = {
                    haptics.perform(Haptic.SELECT)
                    tipIndex = (tipIndex + 1) % tips.size
                },
            )
            CableMachineAnimation(movement = movement, load = load, containerColor = containerColor)
        },
        modifier = modifier.background(containerColor),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val margin = BUBBLE_MARGIN.roundToPx()
        val tail = TAIL_HEIGHT.roundToPx()
        val bubbleMaxWidth = width - 2 * margin
        // Room for the longest tip, so the figure doesn't change size as the tips rotate.
        val textConstraints = Constraints(maxWidth = (bubbleMaxWidth - 2 * BUBBLE_PADDING_H.roundToPx()).coerceAtLeast(1))
        val band = tips.maxOf { measurer.measure(it, textStyle, constraints = textConstraints).size.height } +
            2 * BUBBLE_PADDING_V.roundToPx()
        val bubble = measurables[0].measure(Constraints(maxWidth = bubbleMaxWidth))
        val sceneTop = margin + band + tail
        val scene = measurables[1].measure(Constraints.fixed(width, (height - sceneTop).coerceAtLeast(1)))
        // Centre the bubble over the head where it fits, with its tail pointing down at it.
        val headX = SceneFit(Size(width.toFloat(), scene.height.toFloat())).toOffset(head).x
        val bubbleX = (headX - bubble.width / 2f).roundToInt().coerceIn(margin, max(margin, width - margin - bubble.width))
        layout(width, height) {
            tailX = headX - bubbleX
            bubble.place(bubbleX, sceneTop - tail - bubble.height)
            scene.place(0, sceneTop)
        }
    }
}

/** [text] in a rounded bubble whose tail, [tailX] from its left edge, points down at the speaker. */
@Composable
private fun SpeechBubble(text: String, style: TextStyle, tailX: () -> Float, onClick: () -> Unit) {
    val fill = MaterialTheme.colorScheme.surfaceBright
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .drawBehind {
                val path = bubblePath(size, tailX(), BUBBLE_RADIUS.toPx(), TAIL_WIDTH.toPx(), TAIL_HEIGHT.toPx())
                drawPath(path, fill)
                drawPath(path, outline, style = Stroke(1.dp.toPx()))
            }
            .clip(RoundedCornerShape(BUBBLE_RADIUS))
            .clickable(onClickLabel = stringResource(R.string.next_tip), onClick = onClick)
            .testTag("form_tip")
            .padding(horizontal = BUBBLE_PADDING_H, vertical = BUBBLE_PADDING_V),
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                fadeIn(tween(durationMillis = 220, delayMillis = 90)) togetherWith fadeOut(tween(90)) using
                    SizeTransform(clip = false)
            },
            label = "formTip",
        ) { tip ->
            Text(tip, style = style, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun bubblePath(size: Size, tailX: Float, radius: Float, tailWidth: Float, tailHeight: Float): Path {
    val body = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
    }
    val x = tailX.coerceIn(radius + tailWidth / 2f, max(radius + tailWidth / 2f, size.width - radius - tailWidth / 2f))
    // Overlaps the body by a pixel so the two merge without a seam.
    val tail = Path().apply {
        moveTo(x - tailWidth / 2f, size.height - 1f)
        lineTo(x + tailWidth / 2f, size.height - 1f)
        lineTo(x, size.height + tailHeight)
        close()
    }
    return Path.combine(PathOperation.Union, body, tail)
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.exercise_page, current + 1, count)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "pageDot",
            )
            Box(
                Modifier
                    .size(if (selected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
