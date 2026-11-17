package com.phoneguard.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneguard.pro.data.model.BenchmarkResult
import com.phoneguard.pro.data.model.DataSource
import com.phoneguard.pro.data.report.HealthScorer
import com.phoneguard.pro.ui.components.GlassCard
import com.phoneguard.pro.ui.components.InfoRow
import com.phoneguard.pro.ui.components.LineChart
import com.phoneguard.pro.ui.components.NoteText
import com.phoneguard.pro.ui.components.PrimaryButton
import com.phoneguard.pro.ui.components.ScoreGauge
import com.phoneguard.pro.ui.components.SecondaryButton
import com.phoneguard.pro.ui.components.SectionTitle
import com.phoneguard.pro.ui.components.UsageBar
import com.phoneguard.pro.ui.theme.PgCyan
import com.phoneguard.pro.ui.theme.PgDanger
import com.phoneguard.pro.ui.theme.PgText
import com.phoneguard.pro.ui.theme.PgTextDim
import com.phoneguard.pro.ui.theme.PgWarning
import com.phoneguard.pro.ui.theme.scoreColor
import com.phoneguard.pro.ui.viewmodel.BenchmarkViewModel
import com.phoneguard.pro.util.Fmt

@Composable
fun BenchmarkScreen(vm: BenchmarkViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val view = LocalView.current

    // Keep the screen on while measuring so the device doesn't sleep mid-test.
    DisposableEffect(s.running) {
        view.keepScreenOn = s.running
        onDispose { view.keepScreenOn = false }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                SectionTitle("Performance benchmark", Icons.Rounded.Speed)
                if (s.running) {
                    val p = s.progress
                    Text(p?.stage ?: "Starting…", color = PgText, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { p?.fraction ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = PgCyan,
                        trackColor = PgCyan.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Thermostat, null, tint = PgWarning)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Battery ${p?.tempC?.let { "${Fmt.num(it)} °C" } ?: "—"} · Thermal: ${p?.thermalStatus ?: "—"}",
                            color = PgTextDim, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (s.tempTrace.size >= 2) {
                        Spacer(Modifier.height(8.dp))
                        LineChart(s.tempTrace, Modifier.fillMaxWidth().height(80.dp), lineColor = PgWarning, yLabel = { "${Fmt.num(it)}°" })
                    }
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Cancel", vm::cancel, Modifier.fillMaxWidth(), Icons.Rounded.Stop)
                } else {
                    Text(
                        "Runs real CPU (single & multi-core), memory and storage tests on this device. Takes about 15–40 seconds. " +
                            "Stops automatically if the device reaches a severe thermal state.",
                        color = PgTextDim, style = MaterialTheme.typography.bodyMedium
                    )
                    s.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = PgDanger, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Start benchmark", vm::start, Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow)
                }
            }
        }

        s.result?.let { r ->
            if (!s.running) item { ResultCard(r) }
        }

        item {
            GlassCard {
                SectionTitle("Performance history", Icons.Rounded.History, trailing = {
                    if (history.isNotEmpty()) IconButton(onClick = vm::clearHistory) {
                        Icon(Icons.Rounded.DeleteSweep, "Clear history", tint = PgTextDim)
                    }
                })
                if (history.size < 2) {
                    NoteText("Run at least two benchmarks to see a trend. A falling trend can reveal thermal or storage degradation.")
                } else {
                    LineChart(
                        history.map { it.overallScore.toFloat() }, Modifier.fillMaxWidth().height(140.dp),
                        minY = 0f, maxY = 100f, yLabel = { "${it.toInt()}" }
                    )
                }
                Spacer(Modifier.height(8.dp))
                history.takeLast(8).reversed().forEach { h ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.dateTime(h.timestamp), color = PgTextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            "CPU ${(h.cpuSingleScore + h.cpuMultiScore) / 2} · MEM ${h.memoryScore} · STO ${h.storageScore}",
                            color = PgTextDim, style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${h.overallScore}", color = scoreColor(h.overallScore), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("How scoring works", Icons.Rounded.Speed)
                NoteText(
                    "Each test is timed with a high-resolution clock around real work. Scores use a logarithmic scale relative to a typical 2022 mid-range phone: " +
                        "equal = 50, 2× faster = 75, 4× = 100, half = 25. Raw measurements are shown so results can be compared directly. " +
                        "Storage read speed may include the OS file cache, which apps cannot bypass. Scores are not comparable with Geekbench or AnTuTu."
                )
            }
        }
    }
}

@Composable
private fun ResultCard(r: BenchmarkResult) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreGauge(r.overallScore, diameter = 130.dp, label = "SCORE")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(HealthScorer.condition(r.overallScore), color = scoreColor(r.overallScore), style = MaterialTheme.typography.titleLarge)
                Text(Fmt.dateTime(r.timestamp), color = PgTextDim, style = MaterialTheme.typography.bodySmall)
                Text("Duration ${Fmt.num(r.durationMs / 1000.0)} s · ${r.threads} threads", color = PgTextDim, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Temp ${r.tempStartC?.let { Fmt.num(it) } ?: "—"} → ${r.tempEndC?.let { Fmt.num(it) } ?: "—"} °C",
                    color = PgTextDim, style = MaterialTheme.typography.bodySmall
                )
                Text("Thermal ${r.thermalStart} → ${r.thermalEnd}", color = PgTextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(14.dp))
        ScoreLine("CPU single-core", r.cpuSingleScore, Icons.Rounded.DeveloperBoard)
        InfoRow("Integer (sieve)", "${Fmt.num(r.cpuSingleMops)} M numbers/s", DataSource.MEASURED)
        InfoRow("Floating point (matrix)", "${Fmt.num(r.cpuSingleMflops, 0)} MFLOPS", DataSource.MEASURED)
        Spacer(Modifier.height(8.dp))
        ScoreLine("CPU multi-core", r.cpuMultiScore, Icons.Rounded.DeveloperBoard)
        InfoRow("Integer (sieve)", "${Fmt.num(r.cpuMultiMops)} M numbers/s", DataSource.MEASURED)
        InfoRow("Floating point (matrix)", "${Fmt.num(r.cpuMultiMflops, 0)} MFLOPS", DataSource.MEASURED)
        Spacer(Modifier.height(8.dp))
        ScoreLine("Memory", r.memoryScore, Icons.Rounded.Memory)
        InfoRow("Copy bandwidth", "${Fmt.num(r.memCopyMBps, 0)} MB/s", DataSource.MEASURED)
        InfoRow("Random access latency", "${Fmt.num(r.memLatencyNs)} ns", DataSource.MEASURED)
        Spacer(Modifier.height(8.dp))
        ScoreLine("Storage", r.storageScore, Icons.Rounded.Storage)
        InfoRow("Sequential write", "${Fmt.num(r.storageSeqWriteMBps, 0)} MB/s", DataSource.MEASURED)
        InfoRow("Sequential read", "${Fmt.num(r.storageSeqReadMBps, 0)} MB/s", DataSource.MEASURED, "May include OS cache")
        InfoRow("Random 4K write (fsync)", "${Fmt.num(r.storageRandWriteIops, 0)} IOPS", DataSource.MEASURED)
        InfoRow("Random 4K read", "${Fmt.num(r.storageRandReadIops, 0)} IOPS", DataSource.MEASURED)
    }
}

@Composable
private fun ScoreLine(label: String, score: Int, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PgCyan, modifier = Modifier.padding(end = 6.dp))
            Text(label, color = PgText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$score", color = scoreColor(score), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        UsageBar(score / 100f, color = scoreColor(score))
    }
}
