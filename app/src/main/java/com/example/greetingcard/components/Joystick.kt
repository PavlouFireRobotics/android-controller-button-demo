package com.example.greetingcard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun JoystickControl(
    modifier: Modifier = Modifier,
    size: Float = 200f,
    onValueChange: (Offset) -> Unit
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val radius = size / 2

    Box(
        modifier = modifier
            .size(size.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner thumb
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        thumbOffset.x.roundToInt(),
                        thumbOffset.y.roundToInt()
                    )
                }
                .size((size * 0.4f).dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            thumbOffset = Offset.Zero
                            onValueChange(Offset.Zero)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = thumbOffset + dragAmount
                            
                            // Keep within bounds
                            val distanceFromCenter = newOffset.getDistance()
                            if (distanceFromCenter <= radius) {
                                thumbOffset = newOffset
                            } else {
                                thumbOffset = newOffset * (radius / distanceFromCenter)
                            }
                            
                            // Normalize to -1 to 1
                            onValueChange(Offset(thumbOffset.x / radius, thumbOffset.y / radius))
                        }
                    )
                }
        )
    }
}

@Composable
fun JoystickValueDisplay(label: String, value: Float, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(160.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
