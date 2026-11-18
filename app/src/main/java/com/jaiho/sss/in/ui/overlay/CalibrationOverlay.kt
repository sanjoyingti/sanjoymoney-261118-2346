package com.jaiho.sss.`in`.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jaiho.sss.`in`.R
import com.jaiho.sss.`in`.ui.components.GoldButton
import com.jaiho.sss.`in`.ui.components.SectionTitle
import com.jaiho.sss.`in`.ui.theme.CardBg
import com.jaiho.sss.`in`.ui.theme.CardLine
import com.jaiho.sss.`in`.ui.theme.Gold
import com.jaiho.sss.`in`.ui.theme.Green
import com.jaiho.sss.`in`.ui.theme.TextMuted
import kotlin.math.roundToInt

@Composable
fun CalibrationOverlay(
    initialSpinX: Float,
    initialSpinY: Float,
    initialCollectX: Float,
    initialCollectY: Float,
    onSave: (Float, Float, Float, Float) -> Unit,
    onCancel: () -> Unit
) {
    val density = LocalDensity.current
    val handle = with(density) { 74.dp.toPx() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var spinPos by remember { mutableStateOf(Offset.Zero) }
    var collectPos by remember { mutableStateOf(Offset.Zero) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(size) {
        if (!ready && size.width > 0 && size.height > 0) {
            spinPos = clampOffset(size, handle, initialSpinX, initialSpinY)
            collectPos = clampOffset(size, handle, initialCollectX, initialCollectY)
            ready = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x59000000))
            .onSizeChanged { size = it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .border(1.dp, CardLine, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle(stringResource(R.string.calibrate_title))
            Text(
                text = stringResource(R.string.calibrate_drag_hint),
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            if (ready && size.width > 0 && size.height > 0) {
                Text(
                    text = stringResource(
                        R.string.calibrate_hint_pct,
                        (fraction(spinPos.x, handle, size.width) * 100).roundToInt(),
                        (fraction(spinPos.y, handle, size.height) * 100).roundToInt(),
                        (fraction(collectPos.x, handle, size.width) * 100).roundToInt(),
                        (fraction(collectPos.y, handle, size.height) * 100).roundToInt()
                    ),
                    color = Gold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel), color = TextMuted)
                }
                Box(modifier = Modifier.weight(1f)) {
                    GoldButton(
                        text = stringResource(R.string.calibrate_save),
                        onClick = {
                            if (size.width > 0 && size.height > 0) {
                                onSave(
                                    fraction(spinPos.x, handle, size.width),
                                    fraction(spinPos.y, handle, size.height),
                                    fraction(collectPos.x, handle, size.width),
                                    fraction(collectPos.y, handle, size.height)
                                )
                            }
                        }
                    )
                }
            }
        }

        if (ready && size.width > 0 && size.height > 0) {
            val maxX = (size.width - handle).coerceAtLeast(0f)
            val maxY = (size.height - handle).coerceAtLeast(0f)
            Crosshair(
                label = stringResource(R.string.calibrate_spin),
                tint = Gold,
                modifier = Modifier
                    .offset { IntOffset(spinPos.x.roundToInt(), spinPos.y.roundToInt()) }
                    .size(74.dp)
                    .pointerInput(size) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            spinPos = Offset(
                                (spinPos.x + drag.x).coerceIn(0f, maxX),
                                (spinPos.y + drag.y).coerceIn(0f, maxY)
                            )
                        }
                    }
            )
            Crosshair(
                label = stringResource(R.string.calibrate_collect),
                tint = Green,
                modifier = Modifier
                    .offset { IntOffset(collectPos.x.roundToInt(), collectPos.y.roundToInt()) }
                    .size(74.dp)
                    .pointerInput(size) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            collectPos = Offset(
                                (collectPos.x + drag.x).coerceIn(0f, maxX),
                                (collectPos.y + drag.y).coerceIn(0f, maxY)
                            )
                        }
                    }
            )
        }
    }
}

@Composable
private fun Crosshair(label: String, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC0A0A0C))
            .border(2.dp, tint, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.CenterFocusStrong,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = label,
                color = tint,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun clampOffset(size: IntSize, handle: Float, fractionX: Float, fractionY: Float): Offset {
    val maxX = (size.width - handle).coerceAtLeast(0f)
    val maxY = (size.height - handle).coerceAtLeast(0f)
    val x = (size.width * fractionX.coerceIn(0f, 1f) - handle / 2f).coerceIn(0f, maxX)
    val y = (size.height * fractionY.coerceIn(0f, 1f) - handle / 2f).coerceIn(0f, maxY)
    return Offset(x, y)
}

private fun fraction(position: Float, handle: Float, total: Int): Float {
    if (total <= 0) return 0.5f
    return ((position + handle / 2f) / total).coerceIn(0f, 1f)
}
