package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JarvisLanguage
import com.example.data.model.UiState
import com.example.data.model.WhiteboardShape
import com.example.data.model.WhiteboardStroke
import com.example.data.model.WhiteboardTool
import com.example.ui.components.JarvisHudCard
import com.example.ui.components.LanguageSelectorRow
import com.example.ui.theme.JarvisAmberAlert
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisErrorRed
import com.example.ui.theme.JarvisHoloTeal
import com.example.ui.theme.JarvisMidnightNavy
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WhiteboardScreen(
    whiteboardState: UiState<String>,
    selectedLanguage: JarvisLanguage,
    onSelectLanguage: (JarvisLanguage) -> Unit,
    onExplainWhiteboard: (Bitmap, String) -> Unit
) {
    var activeTool by remember { mutableStateOf(WhiteboardTool.PEN) }
    var activeColor by remember { mutableStateOf(JarvisElectricCyan) }
    val strokes = remember { mutableStateListOf<WhiteboardStroke>() }
    val shapes = remember { mutableStateListOf<WhiteboardShape>() }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var currentShapeStart by remember { mutableStateOf<Offset?>(null) }
    var currentShapeEnd by remember { mutableStateOf<Offset?>(null) }
    var pendingTextOffset by remember { mutableStateOf<Offset?>(null) }
    var textLabelInput by remember { mutableStateOf("") }
    var lessonPrompt by remember { mutableStateOf("") }
    var canvasSize by remember { mutableStateOf(IntSize(900, 600)) }
    val textMeasurer = rememberTextMeasurer()

    if (pendingTextOffset != null) {
        AlertDialog(
            onDismissRequest = { pendingTextOffset = null },
            containerColor = JarvisMidnightNavy,
            title = { Text("Add Whiteboard Label") },
            text = {
                OutlinedTextField(
                    value = textLabelInput,
                    onValueChange = { textLabelInput = it },
                    label = { Text("Label or equation") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pos = pendingTextOffset
                        if (pos != null && textLabelInput.isNotBlank()) {
                            shapes += WhiteboardShape(
                                tool = WhiteboardTool.TEXT,
                                start = pos,
                                end = pos,
                                color = activeColor,
                                strokeWidth = 5f,
                                text = textLabelInput.trim()
                            )
                        }
                        textLabelInput = ""
                        pendingTextOffset = null
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextOffset = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AI WHITEBOARD & LESSONS", style = MaterialTheme.typography.titleMedium, color = JarvisElectricCyan)
                    Row {
                        IconButton(
                            onClick = {
                                if (shapes.isNotEmpty()) shapes.removeAt(shapes.lastIndex)
                                else if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(
                            onClick = {
                                strokes.clear()
                                shapes.clear()
                            }
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear board")
                        }
                    }
                }
                LanguageSelectorRow(selected = selectedLanguage, onSelect = onSelectLanguage)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val toolItems = listOf(
                        WhiteboardTool.PEN to Icons.Filled.Brush,
                        WhiteboardTool.RECTANGLE to Icons.Filled.CropSquare,
                        WhiteboardTool.CIRCLE to Icons.Filled.Circle,
                        WhiteboardTool.ARROW to Icons.AutoMirrored.Filled.ArrowForward,
                        WhiteboardTool.TEXT to Icons.Filled.TextFields
                    )
                    toolItems.forEach { (tool, icon) ->
                        FilterChip(
                            selected = activeTool == tool,
                            onClick = { activeTool = tool },
                            label = { Icon(icon, contentDescription = tool.name, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ink:", style = MaterialTheme.typography.labelMedium)
                    listOf(JarvisElectricCyan, JarvisHoloTeal, JarvisAmberAlert, Color.White, JarvisErrorRed).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (activeColor == c) 2.5.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { activeColor = c }
                        )
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(0xFF060D1C))
                    .border(1.dp, JarvisElectricCyan.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
                    .onSizeChanged { canvasSize = it }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeTool, activeColor) {
                            if (activeTool == WhiteboardTool.TEXT) {
                                detectTapGestures { offset ->
                                    pendingTextOffset = offset
                                }
                            } else {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        if (activeTool == WhiteboardTool.PEN) {
                                            currentStrokePoints = listOf(start)
                                        } else {
                                            currentShapeStart = start
                                            currentShapeEnd = start
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        if (activeTool == WhiteboardTool.PEN) {
                                            currentStrokePoints = currentStrokePoints + change.position
                                        } else {
                                            currentShapeEnd = change.position
                                        }
                                    },
                                    onDragEnd = {
                                        if (activeTool == WhiteboardTool.PEN && currentStrokePoints.size > 1) {
                                            strokes += WhiteboardStroke(currentStrokePoints, activeColor, 5f)
                                        } else if (currentShapeStart != null && currentShapeEnd != null) {
                                            shapes += WhiteboardShape(
                                                tool = activeTool,
                                                start = currentShapeStart!!,
                                                end = currentShapeEnd!!,
                                                color = activeColor,
                                                strokeWidth = 5f
                                            )
                                        }
                                        currentStrokePoints = emptyList()
                                        currentShapeStart = null
                                        currentShapeEnd = null
                                    }
                                )
                            }
                        }
                ) {
                    val gridStep = 36.dp.toPx()
                    var gx = 0f
                    while (gx < size.width) {
                        drawLine(Color(0x1400E5FF), Offset(gx, 0f), Offset(gx, size.height), 1f)
                        gx += gridStep
                    }
                    var gy = 0f
                    while (gy < size.height) {
                        drawLine(Color(0x1400E5FF), Offset(0f, gy), Offset(size.width, gy), 1f)
                        gy += gridStep
                    }

                    strokes.forEach { stroke ->
                        drawStrokePath(stroke.points, stroke.color, stroke.strokeWidth)
                    }
                    if (currentStrokePoints.size > 1) {
                        drawStrokePath(currentStrokePoints, activeColor, 5f)
                    }
                    shapes.forEach { shape ->
                        drawShapeItem(shape, textMeasurer)
                    }
                    if (currentShapeStart != null && currentShapeEnd != null) {
                        drawShapeItem(
                            WhiteboardShape(activeTool, currentShapeStart!!, currentShapeEnd!!, activeColor, 5f),
                            textMeasurer
                        )
                    }
                }
            }
        }

        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = lessonPrompt,
                    onValueChange = { lessonPrompt = it },
                    label = { Text("Lesson topic or question about your diagram") },
                    placeholder = { Text("e.g., Explain this flowchart or teach photosynthesis in ${selectedLanguage.displayName}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whiteboard_prompt_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val bmp = renderWhiteboardToBitmap(
                            width = canvasSize.width.coerceAtLeast(300),
                            height = canvasSize.height.coerceAtLeast(240),
                            strokes = strokes,
                            shapes = shapes
                        )
                        onExplainWhiteboard(bmp, lessonPrompt)
                    },
                    enabled = whiteboardState !is UiState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whiteboard_explain_button")
                ) {
                    if (whiteboardState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating ${selectedLanguage.displayName} Lesson...")
                    } else {
                        Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Explain & Lesson (${selectedLanguage.nativeLabel})")
                    }
                }
            }
        }

        when (whiteboardState) {
            is UiState.Error -> {
                item {
                    JarvisHudCard(modifier = Modifier.fillMaxWidth(), borderColor = JarvisErrorRed) {
                        Text(whiteboardState.message, color = JarvisErrorRed)
                    }
                }
            }
            is UiState.Success -> {
                item {
                    JarvisHudCard(modifier = Modifier.fillMaxWidth(), borderColor = JarvisHoloTeal) {
                        Text("AI WHITEBOARD LESSON (${selectedLanguage.nativeLabel})", style = MaterialTheme.typography.labelMedium, color = JarvisHoloTeal)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(whiteboardState.data, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            else -> {}
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float
) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShapeItem(
    shape: WhiteboardShape,
    textMeasurer: TextMeasurer
) {
    when (shape.tool) {
        WhiteboardTool.RECTANGLE -> {
            val left = minOf(shape.start.x, shape.end.x)
            val top = minOf(shape.start.y, shape.end.y)
            val w = kotlin.math.abs(shape.end.x - shape.start.x)
            val h = kotlin.math.abs(shape.end.y - shape.start.y)
            drawRect(shape.color, Offset(left, top), Size(w, h), style = Stroke(width = shape.strokeWidth))
        }
        WhiteboardTool.CIRCLE -> {
            val center = Offset((shape.start.x + shape.end.x) / 2f, (shape.start.y + shape.end.y) / 2f)
            val r = (shape.end - shape.start).getDistance() / 2f
            drawCircle(shape.color, radius = r, center = center, style = Stroke(width = shape.strokeWidth))
        }
        WhiteboardTool.ARROW -> {
            drawLine(shape.color, shape.start, shape.end, strokeWidth = shape.strokeWidth, cap = StrokeCap.Round)
            val angle = atan2((shape.end.y - shape.start.y).toDouble(), (shape.end.x - shape.start.x).toDouble())
            val arrowLen = 22f
            val a1 = angle - Math.toRadians(28.0)
            val a2 = angle + Math.toRadians(28.0)
            drawLine(
                shape.color,
                shape.end,
                Offset((shape.end.x - arrowLen * cos(a1)).toFloat(), (shape.end.y - arrowLen * sin(a1)).toFloat()),
                strokeWidth = shape.strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                shape.color,
                shape.end,
                Offset((shape.end.x - arrowLen * cos(a2)).toFloat(), (shape.end.y - arrowLen * sin(a2)).toFloat()),
                strokeWidth = shape.strokeWidth,
                cap = StrokeCap.Round
            )
        }
        WhiteboardTool.TEXT -> {
            if (shape.text.isNotBlank()) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = shape.text,
                    topLeft = shape.start,
                    style = TextStyle(color = shape.color, fontSize = 16.sp)
                )
            }
        }
        WhiteboardTool.PEN -> {}
    }
}

private fun renderWhiteboardToBitmap(
    width: Int,
    height: Int,
    strokes: List<WhiteboardStroke>,
    shapes: List<WhiteboardShape>
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(android.graphics.Color.rgb(6, 13, 28))
    val paint = AndroidPaint().apply {
        isAntiAlias = true
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    strokes.forEach { stroke ->
        paint.color = stroke.color.toArgb()
        paint.strokeWidth = stroke.strokeWidth
        val p = android.graphics.Path()
        stroke.points.forEachIndexed { idx, pt ->
            if (idx == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
        }
        canvas.drawPath(p, paint)
    }
    shapes.forEach { shape ->
        paint.color = shape.color.toArgb()
        paint.strokeWidth = shape.strokeWidth
        when (shape.tool) {
            WhiteboardTool.RECTANGLE -> {
                paint.style = AndroidPaint.Style.STROKE
                canvas.drawRect(
                    minOf(shape.start.x, shape.end.x),
                    minOf(shape.start.y, shape.end.y),
                    maxOf(shape.start.x, shape.end.x),
                    maxOf(shape.start.y, shape.end.y),
                    paint
                )
            }
            WhiteboardTool.CIRCLE -> {
                paint.style = AndroidPaint.Style.STROKE
                val cx = (shape.start.x + shape.end.x) / 2f
                val cy = (shape.start.y + shape.end.y) / 2f
                val r = (shape.end - shape.start).getDistance() / 2f
                canvas.drawCircle(cx, cy, r, paint)
            }
            WhiteboardTool.ARROW -> {
                paint.style = AndroidPaint.Style.STROKE
                canvas.drawLine(shape.start.x, shape.start.y, shape.end.x, shape.end.y, paint)
            }
            WhiteboardTool.TEXT -> {
                paint.style = AndroidPaint.Style.FILL
                paint.textSize = 36f
                canvas.drawText(shape.text, shape.start.x, shape.start.y, paint)
            }
            WhiteboardTool.PEN -> {}
        }
    }
    return bmp
}
