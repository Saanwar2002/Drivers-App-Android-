package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoubleArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Exact native mirror of web `SwipeButton.tsx` from DriverTerminal.tsx:
 * Horizontal slide-to-confirm track preventing accidental taps while driving.
 * Triggers `onConfirm` once dragged past 76% of the track with tactile haptic feedback,
 * and springs smoothly back if released early.
 */
@Composable
fun SwipeConfirmButton(
    label: String,
    trackColor: Color,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    resetKey: Any? = label,
    testTag: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val thumbWidthDp = 76.dp
    val trackPaddingDp = 4.dp
    val thumbWidthPx = with(density) { thumbWidthDp.toPx() }
    val trackPaddingPx = with(density) { trackPaddingDp.toPx() }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val maxOffsetPx = (trackWidthPx - thumbWidthPx - trackPaddingPx * 2f).coerceAtLeast(1f)
    val offsetX = remember { Animatable(0f) }
    var triggered by remember { mutableStateOf(false) }

    // Reset slider whenever action label / ride state / enabled resets
    LaunchedEffect(resetKey, enabled) {
        if (!enabled || triggered) {
            triggered = false
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    val progress = (offsetX.value / maxOffsetPx).coerceIn(0f, 1f)
    val effectiveTrackBg = if (enabled) trackColor.copy(alpha = 0.22f) else Color(0xFF27272A)
    val effectiveBorder = if (enabled) trackColor.copy(alpha = 0.75f) else Color(0xFF3F3F46)
    val effectiveFill = if (enabled) trackColor.copy(alpha = 0.35f + 0.55f * progress) else Color(0xFF3F3F46)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(effectiveTrackBg)
            .border(1.5.dp, effectiveBorder, RoundedCornerShape(16.dp))
            .onSizeChanged { size -> trackWidthPx = size.width.toFloat() }
            .semantics {
                role = Role.Button
                contentDescription = "Slide to $label"
                if (enabled) {
                    onClick(label = label) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm()
                        true
                    }
                }
            }
            .testTag(testTag)
    ) {
        // Progressive trailing fill
        if (trackWidthPx > 0f && progress > 0.01f) {
            val fillWidthDp = with(density) { (offsetX.value + thumbWidthPx + trackPaddingPx).toDp() }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(fillWidthDp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(effectiveFill)
            )
        }

        // Centered action label (shifts subtly right of thumb and fades cleanly as thumb passes)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = thumbWidthDp + 8.dp, end = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) contentColor else Color(0xFF9CA3AF),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha((1f - progress * 0.7f).coerceIn(0.25f, 1f))
            )
        }

        // Draggable SLIDE thumb
        Box(
            modifier = Modifier
                .padding(trackPaddingDp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .width(thumbWidthDp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) trackColor else Color(0xFF52525B))
                .pointerInput(enabled, maxOffsetPx) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (!triggered) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (triggered) return@detectHorizontalDragGestures
                            scope.launch {
                                val nextVal = (offsetX.value + dragAmount).coerceIn(0f, maxOffsetPx)
                                offsetX.snapTo(nextVal)
                            }
                        },
                        onDragEnd = {
                            if (triggered) return@detectHorizontalDragGestures
                            val ratio = offsetX.value / maxOffsetPx
                            if (ratio >= 0.76f) {
                                triggered = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = maxOffsetPx,
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                    )
                                    onConfirm()
                                }
                            } else {
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            if (!triggered) {
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SLIDE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) contentColor else Color(0xFFD4D4D8),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Rounded.DoubleArrow,
                    contentDescription = null,
                    tint = if (enabled) contentColor else Color(0xFFD4D4D8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
