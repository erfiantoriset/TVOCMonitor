package com.example.tvocmonitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- history
@Composable
fun HistoryScreen(rows: List<HistoryRow>, onBack: () -> Unit, onClear: () -> Unit) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Column(Modifier.fillMaxSize()) {
        SubScreenHeader("History", onBack) {
            IconButton(onClick = onClear) {
                Icon(Icons.Outlined.DeleteSweep, "Hapus riwayat", tint = Palette.TextSecondary)
            }
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada data.\nMenunggu pesan dari M5Stack…", color = Palette.TextMuted)
            }
        } else {
            Column(Modifier.padding(horizontal = 20.dp)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("Tren (${rows.size} data)", color = Palette.TextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        LegendRow()
                        Spacer(Modifier.height(6.dp))
                        TrendChart(rows.takeLast(120))
                    }
                }
                Spacer(Modifier.height(12.dp))
                HistoryHeaderRow()
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                items(rows.asReversed()) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Cell(fmt.format(Date(r.time)), 1.3f)
                        Cell(r.data.temperature.fmt2(), 1f)
                        Cell(r.data.tvoc.fmt(), 1f)
                        Cell(r.data.eco2.fmt(), 1f)
                        Cell(r.data.h2.fmt(), 1.1f)
                        Cell(r.data.ethanol.fmt(), 1.1f)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeaderRow() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        listOf("Waktu" to 1.3f, "°C" to 1f, "TVOC" to 1f, "eCO2" to 1f, "H2" to 1.1f, "Ethanol" to 1.1f).forEach { (t, w) ->
            Text(t, color = Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(w))
        }
    }
}

@Composable
private fun RowScope.Cell(text: String, w: Float) {
    Text(text, color = Palette.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(w), maxLines = 1)
}

@Composable
private fun LegendRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Legend("TVOC (ppb)", Palette.Green)
        Legend("eCO2 (ppm)", Palette.Yellow)
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(14.dp).height(4.dp)) { drawRect(color) }
        Spacer(Modifier.width(6.dp))
        Text(label, color = Palette.TextSecondary, fontSize = 12.sp)
    }
}

/** Grafik garis sederhana TVOC & eCO2, masing-masing dinormalisasi ke tinggi grafik. */
@Composable
private fun TrendChart(rows: List<HistoryRow>) {
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        fun series(values: List<Int?>, color: Color) {
            val v = values.map { it?.toFloat() }
            val nonNull = v.filterNotNull()
            if (nonNull.size < 2) return
            val min = nonNull.min()
            val max = nonNull.max().let { if (it == min) min + 1f else it }
            val step = size.width / (v.size - 1).coerceAtLeast(1)
            val path = Path()
            var started = false
            v.forEachIndexed { i, y ->
                if (y == null) return@forEachIndexed
                val p = Offset(i * step, size.height - (y - min) / (max - min) * size.height)
                if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx()))
        }
        // garis bantu
        for (i in 0..3) {
            val y = size.height * i / 3f
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y))
        }
        series(rows.map { it.data.tvoc }, Palette.Green)
        series(rows.map { it.data.eco2 }, Palette.Yellow)
    }
}

// ---------------------------------------------------------------- settings
@Composable
fun SettingsScreen(
    config: MqttConfig,
    status: ConnStatus,
    error: String?,
    onBack: () -> Unit,
    onSave: (MqttConfig) -> Unit,
    onReconnect: () -> Unit,
) {
    var host by remember(config) { mutableStateOf(config.host) }
    var port by remember(config) { mutableStateOf(config.port.toString()) }
    var topic by remember(config) { mutableStateOf(config.baseTopic) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Palette.TextPrimary,
        unfocusedTextColor = Palette.TextPrimary,
        focusedBorderColor = Palette.Green,
        unfocusedBorderColor = Palette.CardBorder,
        focusedLabelColor = Palette.Green,
        unfocusedLabelColor = Palette.TextSecondary,
        cursorColor = Palette.Green,
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubScreenHeader("Settings", onBack)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MQTT Broker", color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(host, { host = it }, label = { Text("Host") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(topic, { topic = it }, label = { Text("Base topic") }, singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Subscribe ke: ${topic.trim().trimEnd('/')}/#\nTanpa enkripsi & tanpa autentikasi (TCP).",
                        color = Palette.TextMuted, fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                onSave(MqttConfig(host.ifBlank { MqttConfig.DEFAULT_HOST }, port.toIntOrNull() ?: MqttConfig.DEFAULT_PORT, topic.ifBlank { MqttConfig.DEFAULT_TOPIC }))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Green, contentColor = Color(0xFF0F2A1C)),
                        ) { Text("Simpan & Connect") }
                        OutlinedButton(onClick = {
                            host = MqttConfig.DEFAULT_HOST
                            port = MqttConfig.DEFAULT_PORT.toString()
                            topic = MqttConfig.DEFAULT_TOPIC
                        }) { Text("Default", color = Palette.TextSecondary) }
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Status koneksi", color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(status.label, color = if (status == ConnStatus.CONNECTED) Palette.Green else Palette.Yellow)
                    if (!error.isNullOrBlank()) Text(error, color = Palette.Red, fontSize = 13.sp)
                    OutlinedButton(onClick = onReconnect) { Text("Reconnect", color = Palette.TextSecondary) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
