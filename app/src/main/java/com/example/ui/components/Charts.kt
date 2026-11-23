package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategoryBreakdown
import com.example.data.model.StorageCategory
import com.example.ui.theme.LocalStorageColors
import com.example.ui.theme.MonoNumericStyle
import com.example.util.Formatters
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun StorageRingGauge(
    usedBytes: Long,
    totalBytes: Long,
    freeBytes: Long,
    modifier: Modifier = Modifier
) {
    val targetFraction = if (totalBytes > 0L) {
        (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "storageRingFraction"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val warningColor = LocalStorageColors.current.warning
    val errorColor = MaterialTheme.colorScheme.error

    val ringColors = when {
        targetFraction >= 0.9f -> listOf(warningColor, errorColor)
        targetFraction >= 0.75f -> listOf(primaryColor, warningColor)
        else -> listOf(primaryColor, secondaryColor)
    }

    val usedText = Formatters.formatBytes(usedBytes)
    val totalText = Formatters.formatBytes(totalBytes)
    val freeText = Formatters.formatBytes(freeBytes)
    val percentText = Formatters.formatPercent(usedBytes, totalBytes)

    Box(
        modifier = modifier
            .size(196.dp)
            .semantics {
                contentDescription = "$usedText used of $totalText, $freeText free"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Background track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active sweep arc
            if (animatedFraction > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(ringColors.first(), ringColors.last(), ringColors.first())
                    ),
                    startAngle = -90f,
                    sweepAngle = (animatedFraction * 360f).coerceAtLeast(4f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = percentText,
                style = MonoNumericStyle.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = usedText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "of $totalText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class DonutSlice(
    val category: StorageCategory,
    val bytes: Long,
    val startAngle: Float,
    val sweepAngle: Float,
    val color: Color
)

@Composable
fun CategoryDonutChart(
    categories: List<CategoryBreakdown>,
    onCategoryTap: (StorageCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val storageColors = LocalStorageColors.current
    val nonEmpty = remember(categories) { categories.filter { it.bytes > 0L } }
    val totalMeasured = remember(nonEmpty) { nonEmpty.sumOf { it.bytes } }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    val slices = remember(nonEmpty, totalMeasured, storageColors) {
        if (totalMeasured <= 0L) {
            emptyList()
        } else {
            var currentAngle = -90f
            val gap = if (nonEmpty.size > 1) 2.2f else 0f
            nonEmpty.map { item ->
                val rawSweep = (item.bytes.toFloat() / totalMeasured.toFloat()) * 360f
                val sweep = (rawSweep - gap).coerceAtLeast(1.5f)
                val slice = DonutSlice(
                    category = item.category,
                    bytes = item.bytes,
                    startAngle = currentAngle,
                    sweepAngle = sweep,
                    color = storageColors.colorFor(item.category)
                )
                currentAngle += rawSweep
                slice
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(204.dp)
                .pointerInput(slices) {
                    detectTapGestures { tapOffset ->
                        if (slices.isEmpty()) return@detectTapGestures
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = tapOffset.x - cx
                        val dy = tapOffset.y - cy
                        val distance = hypot(dx, dy)
                        val outerRadius = minOf(cx, cy)
                        val innerRadius = outerRadius * 0.45f
                        if (distance in innerRadius..outerRadius) {
                            var angle = (atan2(dy, dx) * (180.0 / PI)).toFloat()
                            if (angle < -90f) angle += 360f
                            val tapped = slices.firstOrNull { slice ->
                                angle >= slice.startAngle && angle <= (slice.startAngle + slice.sweepAngle + 2.5f)
                            }
                            tapped?.let { onCategoryTap(it.category) }
                        }
                    }
                }
        ) {
            val strokeWidth = 38.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            if (slices.isEmpty()) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
            } else {
                slices.forEach { slice ->
                    drawArc(
                        color = slice.color,
                        startAngle = slice.startAngle,
                        sweepAngle = slice.sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                }
            }

            // Inner hole surface polish
            drawCircle(
                color = surfaceColor,
                radius = (size.minDimension / 2f) - strokeWidth - 2.dp.toPx()
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (totalMeasured > 0L) Formatters.formatBytes(totalMeasured) else "No Data",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (totalMeasured > 0L) "Tap slice to browse" else "Tap Rescan",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class TreemapTile(
    val category: StorageCategory,
    val bytes: Long,
    val rect: Rect,
    val color: Color
)

@Composable
fun CategoryTreemapChart(
    categories: List<CategoryBreakdown>,
    onCategoryTap: (StorageCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val storageColors = LocalStorageColors.current
    val textMeasurer = rememberTextMeasurer()
    val nonEmpty = remember(categories) {
        categories.filter { it.bytes > 0L }.sortedByDescending { it.bytes }
    }
    val totalBytes = remember(nonEmpty) { nonEmpty.sumOf { it.bytes } }
    val emptySurfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val emptyTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (nonEmpty.isEmpty() || totalBytes <= 0L) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = emptySurfaceColor,
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
            }
            Text(
                text = "Run a scan to visualize storage blocks",
                style = MaterialTheme.typography.bodyMedium,
                color = emptyTextColor
            )
        }
        return
    }

    val tileHolder = remember(nonEmpty, storageColors) {
        object {
            var tiles: List<TreemapTile> = emptyList()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(nonEmpty, storageColors) {
                detectTapGestures { offset ->
                    val hit = tileHolder.tiles.firstOrNull { it.rect.contains(offset) }
                    hit?.let { onCategoryTap(it.category) }
                }
            }
    ) {
        val gap = 4.dp.toPx()
        val rootRect = Rect(0f, 0f, size.width, size.height)
        val tiles = buildTreemapTiles(
            items = nonEmpty,
            bounds = rootRect,
            colorFor = { storageColors.colorFor(it) }
        )
        tileHolder.tiles = tiles

        tiles.forEach { tile ->
            val insetRect = Rect(
                left = tile.rect.left + gap / 2f,
                top = tile.rect.top + gap / 2f,
                right = (tile.rect.right - gap / 2f).coerceAtLeast(tile.rect.left + 2f),
                bottom = (tile.rect.bottom - gap / 2f).coerceAtLeast(tile.rect.top + 2f)
            )
            val corner = 12.dp.toPx()
            drawRoundRect(
                color = tile.color,
                topLeft = Offset(insetRect.left, insetRect.top),
                size = Size(insetRect.width, insetRect.height),
                cornerRadius = CornerRadius(corner, corner)
            )

            // Render text labels if tile is spacious enough
            if (insetRect.width >= 68.dp.toPx() && insetRect.height >= 44.dp.toPx()) {
                val label = tile.category.displayName
                val value = Formatters.formatBytes(tile.bytes)
                val titleResult = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                val pad = 8.dp.toPx()
                if (titleResult.size.width + pad * 2 <= insetRect.width) {
                    drawText(
                        textLayoutResult = titleResult,
                        topLeft = Offset(insetRect.left + pad, insetRect.top + pad)
                    )
                    if (insetRect.height >= 64.dp.toPx()) {
                        val valueResult = textMeasurer.measure(
                            text = value,
                            style = MonoNumericStyle.copy(
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp
                            )
                        )
                        if (valueResult.size.width + pad * 2 <= insetRect.width) {
                            drawText(
                                textLayoutResult = valueResult,
                                topLeft = Offset(
                                    insetRect.left + pad,
                                    insetRect.top + pad + titleResult.size.height + 2.dp.toPx()
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildTreemapTiles(
    items: List<CategoryBreakdown>,
    bounds: Rect,
    colorFor: (StorageCategory) -> Color
): List<TreemapTile> {
    if (items.isEmpty()) return emptyList()
    if (items.size == 1) {
        val single = items.first()
        return listOf(
            TreemapTile(
                category = single.category,
                bytes = single.bytes,
                rect = bounds,
                color = colorFor(single.category)
            )
        )
    }

    val total = items.sumOf { it.bytes }.coerceAtLeast(1L)
    var running = 0L
    var splitIndex = 1
    for (i in 0 until items.lastIndex) {
        running += items[i].bytes
        splitIndex = i + 1
        if (running >= total / 2L) break
    }

    val firstHalf = items.subList(0, splitIndex)
    val secondHalf = items.subList(splitIndex, items.size)
    val ratio = (firstHalf.sumOf { it.bytes }.toFloat() / total.toFloat()).coerceIn(0.15f, 0.85f)

    val (firstBounds, secondBounds) = if (bounds.width >= bounds.height) {
        val splitX = bounds.left + bounds.width * ratio
        Rect(bounds.left, bounds.top, splitX, bounds.bottom) to
            Rect(splitX, bounds.top, bounds.right, bounds.bottom)
    } else {
        val splitY = bounds.top + bounds.height * ratio
        Rect(bounds.left, bounds.top, bounds.right, splitY) to
            Rect(bounds.left, splitY, bounds.right, bounds.bottom)
    }

    return buildTreemapTiles(firstHalf, firstBounds, colorFor) +
        buildTreemapTiles(secondHalf, secondBounds, colorFor)
}
