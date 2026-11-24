package com.example.tvocmonitor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TvocApp() }
    }
}

enum class Screen { DASHBOARD, HISTORY, SETTINGS }

@Composable
fun TvocApp(vm: DashboardViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.DASHBOARD) }
    val data by vm.data.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(enabled = screen != Screen.DASHBOARD) { screen = Screen.DASHBOARD }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Green,
            background = Palette.BgBottom,
            surface = Palette.BgTop,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Palette.BgTop, Palette.BgBottom)))
                .drawBehind {
                    // cahaya teal di tengah seperti mockup
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Palette.Glow.copy(alpha = 0.45f), Color.Transparent),
                            center = Offset(size.width * 0.3f, size.height * 0.45f),
                            radius = size.width * 0.8f,
                        ),
                        radius = size.width * 0.8f,
                        center = Offset(size.width * 0.3f, size.height * 0.45f),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Palette.Green.copy(alpha = 0.18f), Color.Transparent),
                            center = Offset(size.width * 0.8f, size.height * 0.7f),
                            radius = size.width * 0.6f,
                        ),
                        radius = size.width * 0.6f,
                        center = Offset(size.width * 0.8f, size.height * 0.7f),
                    )
                },
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).statusBarsPadding()) {
                    when (screen) {
                        Screen.DASHBOARD -> DashboardScreen(data, status)
                        Screen.HISTORY -> HistoryScreen(history, onBack = { screen = Screen.DASHBOARD }, onClear = vm::clearHistory)
                        Screen.SETTINGS -> SettingsScreen(
                            config = config, status = status, error = error,
                            onBack = { screen = Screen.DASHBOARD },
                            onSave = { vm.saveConfig(it) },
                            onReconnect = { vm.reconnect() },
                        )
                    }
                }
                BottomBar(
                    current = screen,
                    onHistory = { screen = if (screen == Screen.HISTORY) Screen.DASHBOARD else Screen.HISTORY },
                    onSettings = { screen = if (screen == Screen.SETTINGS) Screen.DASHBOARD else Screen.SETTINGS },
                    onExport = { exportCsv(context, history) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- dashboard
@Composable
fun DashboardScreen(d: SensorData, status: ConnStatus) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Air Quality Monitor", color = Palette.TextSecondary, fontSize = 20.sp)
        Text(
            "TVOC/eCO2 Dashboard", color = Palette.TextPrimary, fontSize = 28.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        StatusLine(status, d.updatedAt)
        Spacer(Modifier.height(18.dp))

        // Temperature
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x22000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Thermostat, null, tint = Palette.IconTint, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Temperature", color = Palette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.Thermostat, null, tint = Palette.Red, modifier = Modifier.size(22.dp))
                    }
                    Text("${d.temperature.fmt2()} °C", color = Palette.TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // TVOC + eCO2
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard(Modifier.weight(1f).height(330.dp)) {
                Column(Modifier.fillMaxSize()) {
                    CardHeader("TVOC", Icons.Outlined.BubbleChart)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TVOC Now", color = Palette.TextSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        StatusBadge(tvocLevel(d.tvoc))
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(d.tvoc.fmt(), color = Palette.TextPrimary, fontSize = if ((d.tvoc ?: 0) >= 1000) 36.sp else 52.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                        Text(" ppb", color = Palette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    TickScale((d.tvoc ?: 0) / 2200f)
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Palette.CardBorder)
                    Spacer(Modifier.weight(1f))
                    Text("TVOC Baseline", color = Palette.TextSecondary, fontSize = 15.sp)
                    Text(d.tvocBaseline.fmt(), color = Palette.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
            GlassCard(Modifier.weight(1f).height(330.dp)) {
                Column(Modifier.fillMaxSize()) {
                    CardHeader("eCO2", Icons.Outlined.Cloud)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("eCO2 Now", color = Palette.TextSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        StatusBadge(eco2Level(d.eco2))
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Eco2Gauge(d.eco2, Modifier.fillMaxWidth().widthIn(max = 150.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("eCO2 Baseline", color = Palette.TextSecondary, fontSize = 15.sp)
                    Text(d.eco2Baseline.fmt(), color = Palette.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        Text(
            "Gas Lainnya", color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GasCard("H2 Now", d.h2, Icons.Outlined.Hub, Palette.IconTint, Modifier.weight(1f))
            GasCard("Ethanol Now", d.ethanol, Icons.Outlined.LocalBar, Palette.Green, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatusLine(status: ConnStatus, updatedAt: Long) {
    val dot = when (status) {
        ConnStatus.CONNECTED -> Palette.Green
        ConnStatus.CONNECTING -> Palette.Yellow
        else -> Palette.Red
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text("Status: ${status.label}", color = Palette.TextSecondary, fontSize = 16.sp)
    }
    if (updatedAt > 0) {
        Text(
            "Update: " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(updatedAt)),
            color = Palette.TextMuted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun CardHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Palette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = Palette.IconTint, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun GasCard(title: String, value: Int?, icon: ImageVector, tint: Color, modifier: Modifier) {
    GlassCard(modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Palette.TextSecondary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Text(value.fmt(), color = Palette.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            // sinyal mentah SGP30 umumnya ~10.000–25.000
            TickScale(((value ?: 10_000) - 10_000) / 15_000f)
        }
    }
}

// ---------------------------------------------------------------- bottom bar
@Composable
fun BottomBar(current: Screen, onHistory: () -> Unit, onSettings: () -> Unit, onExport: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Palette.BottomBar)
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BottomItem("History", Icons.Outlined.History, current == Screen.HISTORY, onHistory)
        BottomItem("Settings", Icons.Outlined.Settings, current == Screen.SETTINGS, onSettings)
        BottomItem("Export Data", Icons.Outlined.IosShare, false, onExport)
    }
}

@Composable
private fun BottomItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Palette.Green else Palette.TextSecondary
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 15.sp)
    }
}

@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Palette.TextPrimary)
        }
        Text(title, color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

// ---------------------------------------------------------------- export
fun exportCsv(context: Context, rows: List<HistoryRow>) {
    if (rows.isEmpty()) {
        Toast.makeText(context, "Belum ada data untuk diekspor", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "tvoc_$stamp.csv")
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        file.bufferedWriter().use { w ->
            w.appendLine("time,temperature,tvoc,tvoc_baseline,eco2,eco2_baseline,h2,ethanol")
            rows.forEach { r ->
                val d = r.data
                w.appendLine(
                    listOf(
                        ts.format(Date(r.time)), d.temperature, d.tvoc, d.tvocBaseline,
                        d.eco2, d.eco2Baseline, d.h2, d.ethanol,
                    ).joinToString(",") { it?.toString() ?: "" },
                )
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TVOC data $stamp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export Data"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export gagal: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
