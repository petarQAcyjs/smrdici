package com.petar.smrdici.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min

object PieChartData {
    data class Slice(
        val value: Float,
        val color: Color,
        val label: String
    )
}

@Composable
fun PieChart(
    data: List<PieChartData.Slice>,
    modifier: Modifier = Modifier,
    centerText: String? = null
) {
    if (data.isEmpty()) return
    
    val textMeasurer = rememberTextMeasurer()
    val animatedProgress = remember { Animatable(0f) }
    var selectedSlice by remember { mutableStateOf<PieChartData.Slice?>(null) }
    
    // Calculate total value for percentages
    val total = data.sumOf { it.value.toDouble() }.toFloat()
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    
    LaunchedEffect(data) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        // Calculate which slice was tapped
                        val canvasSize = min(size.width, size.height)
                        val radius = canvasSize / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        
                        // Calculate angle from center to touch point
                        val touchAngle = atan2(
                            y = offset.y - center.y,
                            x = offset.x - center.x
                        ) * (180f / PI.toFloat())
                        
                        // Convert to positive angle (0-360)
                        val normalizedAngle = (touchAngle + 360f) % 360f
                        
                        // Find which slice contains this angle
                        var startAngle = -90f // Start from top (12 o'clock position)
                        for (slice in data) {
                            val sweepAngle = (slice.value / total) * 360f
                            if (normalizedAngle >= startAngle && 
                                normalizedAngle <= startAngle + sweepAngle) {
                                selectedSlice = slice
                                break
                            }
                            startAngle += sweepAngle
                        }
                    }
                }
        ) {
            val canvasSize = min(size.width, size.height)
            val thickness = canvasSize * 0.15f
            val radius = (canvasSize / 2f) * 0.8f // Leave some padding
            val center = Offset(size.width / 2f, size.height / 2f)
            
            // Draw background circle
            drawCircle(
                color = backgroundColor,
                radius = radius,
                center = center,
                style = Stroke(width = thickness)
            )
            
            var startAngle = -90f // Start from top (12 o'clock position)
            
            // Draw slices as arcs (not filled)
            data.forEach { slice ->
                val sweepAngle = (slice.value / total) * 360f * animatedProgress.value
                
                // Draw arc segment
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = thickness, cap = StrokeCap.Butt)
                )
                
                startAngle += sweepAngle
            }
        }
        
        // Center text showing the total
        centerText?.let {
            Text(
                text = centerText,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
        }
    }
} 