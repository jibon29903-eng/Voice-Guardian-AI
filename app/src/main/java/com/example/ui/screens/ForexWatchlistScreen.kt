package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DataFreshness
import com.example.data.model.ForexQuote
import com.example.data.model.ForexWatchItem
import com.example.ui.components.JarvisHudCard
import com.example.ui.theme.JarvisAmberAlert
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisElectricCyan
import com.example.ui.theme.JarvisErrorRed
import com.example.ui.theme.JarvisHoloTeal
import com.example.ui.theme.JarvisMidnightNavy
import java.util.Locale

@Composable
fun ForexWatchlistScreen(
    watchlist: List<ForexWatchItem>,
    quotes: List<ForexQuote>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onAddOrUpdatePair: (base: String, quote: String, alertRate: Double, direction: String) -> Unit,
    onRemovePair: (String) -> Unit
) {
    var selectedPair by remember { mutableStateOf<String?>("EUR/USD") }
    var showAddDialog by remember { mutableStateOf(false) }
    var baseInput by remember { mutableStateOf("USD") }
    var quoteInput by remember { mutableStateOf("BDT") }
    var alertRateInput by remember { mutableStateOf("") }
    var alertDirection by remember { mutableStateOf("none") }

    val activeQuote = quotes.firstOrNull { it.pair == selectedPair } ?: quotes.firstOrNull()

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = JarvisMidnightNavy,
            title = { Text("Add Pair / Set Alert") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = baseInput,
                            onValueChange = { baseInput = it.uppercase().take(5) },
                            label = { Text("Base (e.g. EUR)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = quoteInput,
                            onValueChange = { quoteInput = it.uppercase().take(5) },
                            label = { Text("Quote (e.g. USD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = alertRateInput,
                        onValueChange = { alertRateInput = it },
                        label = { Text("Alert Target Rate (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("none", "above", "below").forEach { dir ->
                            FilterChip(
                                selected = alertDirection == dir,
                                onClick = { alertDirection = dir },
                                label = { Text(dir.uppercase()) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rate = alertRateInput.toDoubleOrNull() ?: 0.0
                        val dir = if (rate > 0.0 && alertDirection != "none") alertDirection else "none"
                        onAddOrUpdatePair(baseInput.ifBlank { "EUR" }, quoteInput.ifBlank { "USD" }, rate, dir)
                        showAddDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            JarvisHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LIVE FOREX WATCHLIST", style = MaterialTheme.typography.titleMedium, color = JarvisElectricCyan)
                        Text(
                            text = "Real market rates via Frankfurter (ECB) & Open ER-API. No synthetic ticks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.testTag("forex_refresh_button")) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh forex rates", tint = JarvisElectricCyan)
                            }
                        }
                        IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("forex_add_button")) {
                            Icon(Icons.Filled.Add, contentDescription = "Add currency pair", tint = JarvisHoloTeal)
                        }
                    }
                }
            }
        }

        if (activeQuote != null) {
            item {
                ForexInteractiveChartCard(quote = activeQuote)
            }
        }

        items(watchlist, key = { it.id.ifBlank { it.pairLabel } }) { watch ->
            val quote = quotes.firstOrNull { it.pair == watch.pairLabel }
            val freshness = quote?.freshness ?: DataFreshness.UNAVAILABLE
            val badgeColor = when (freshness) {
                DataFreshness.REALTIME_API -> JarvisHoloTeal
                DataFreshness.CACHED_DELAYED -> JarvisAmberAlert
                DataFreshness.UNAVAILABLE -> JarvisErrorRed
            }
            val alertTriggered = quote?.rate != null && watch.targetAlertRate > 0.0 && (
                (watch.alertDirection == "above" && quote.rate >= watch.targetAlertRate) ||
                    (watch.alertDirection == "below" && quote.rate <= watch.targetAlertRate)
                )

            JarvisHudCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedPair = watch.pairLabel },
                borderColor = if (alertTriggered) JarvisAmberAlert else if (selectedPair == watch.pairLabel) JarvisElectricCyan else badgeColor.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(watch.pairLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = badgeColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = when (freshness) {
                                        DataFreshness.REALTIME_API -> "LIVE API"
                                        DataFreshness.CACHED_DELAYED -> "CACHED / DELAYED"
                                        DataFreshness.UNAVAILABLE -> "UNAVAILABLE"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${quote?.providerName ?: "Pending"} • ${quote?.timestampLabel ?: "Fetching..."}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (watch.targetAlertRate > 0.0 && watch.alertDirection != "none") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (alertTriggered) JarvisAmberAlert else JarvisElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (alertTriggered) {
                                        "ALERT TRIGGERED (${watch.alertDirection} ${watch.targetAlertRate})"
                                    } else {
                                        "Alert: ${watch.alertDirection} ${watch.targetAlertRate}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (alertTriggered) JarvisAmberAlert else JarvisElectricCyan
                                )
                            }
                        }
                        if (quote?.errorNote != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(quote.errorNote, style = MaterialTheme.typography.labelSmall, color = badgeColor)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = quote?.rate?.let { String.format(Locale.US, "%.4f", it) } ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                            color = JarvisElectricCyan
                        )
                        val pct = quote?.deltaPercent
                        if (pct != null) {
                            Text(
                                text = String.format(Locale.US, "%+.2f%%", pct),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (pct >= 0) JarvisHoloTeal else JarvisErrorRed
                            )
                        }
                        IconButton(
                            onClick = { onRemovePair(watch.id.ifBlank { "${watch.baseCurrency}_${watch.quoteCurrency}" }) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove pair", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForexInteractiveChartCard(quote: ForexQuote) {
    var inspectedIndex by remember(quote.pair) { mutableStateOf<Int?>(null) }
    val points = quote.history

    JarvisHudCard(modifier = Modifier.fillMaxWidth(), borderColor = JarvisElectricCyan.copy(alpha = 0.6f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${quote.pair} • 21-Day Trend", style = MaterialTheme.typography.titleMedium, color = JarvisElectricCyan)
            val inspected = inspectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
            if (inspected != null) {
                Text(
                    text = "${inspected.dateLabel}: ${String.format(Locale.US, "%.4f", inspected.rate)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisHoloTeal
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (points.size < 2) {
            Text(
                text = "Historical chart unavailable for ${quote.pair} from free ECB endpoint (spot quote only).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val idx = ((offset.x / size.width) * (points.size - 1))
                                .toInt()
                                .coerceIn(0, points.lastIndex)
                            inspectedIndex = idx
                        }
                    }
            ) {
                val minVal = points.minOf { it.rate }
                val maxVal = points.maxOf { it.rate }
                val span = (maxVal - minVal).takeIf { it > 0.0 } ?: 0.0001
                val path = Path()
                val stepX = size.width / (points.size - 1).coerceAtLeast(1)

                points.forEachIndexed { idx, pt ->
                    val x = idx * stepX
                    val normY = ((pt.rate - minVal) / span).toFloat()
                    val y = size.height - (normY * (size.height - 16.dp.toPx()) + 8.dp.toPx())
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = JarvisElectricCyan,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                val highlightIdx = inspectedIndex ?: points.lastIndex
                val hiPt = points[highlightIdx]
                val hx = highlightIdx * stepX
                val hy = size.height - (((hiPt.rate - minVal) / span).toFloat() * (size.height - 16.dp.toPx()) + 8.dp.toPx())
                drawCircle(color = JarvisHoloTeal, radius = 5.dp.toPx(), center = Offset(hx, hy))
            }
            Text(
                text = "Tap anywhere on the chart to inspect historical daily ECB rate.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
