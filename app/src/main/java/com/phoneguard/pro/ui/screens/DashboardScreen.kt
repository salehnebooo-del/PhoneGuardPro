package com.phoneguard.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneguard.pro.ui.components.CoreFreqBars
import com.phoneguard.pro.ui.components.GlassCard
import com.phoneguard.pro.ui.components.LineChart
import com.phoneguard.pro.ui.components.MetricTile
import com.phoneguard.pro.ui.components.NoteText
import com.phoneguard.pro.ui.components.PrimaryButton
import com.phoneguard.pro.ui.components.ScoreGauge
import com.phoneguard.pro.ui.components.SecondaryButton
import com.phoneguard.pro.ui.components.SectionTitle
import com.phoneguard.pro.ui.components.StatusChip
import com.phoneguard.pro.ui.theme.PgCyan
import com.phoneguard.pro.ui.theme.PgDanger
import com.phoneguard.pro.ui.theme.PgSuccess
import com.phoneguard.pro.ui.theme.PgText
import com.phoneguard.pro.ui.theme.PgTextDim
import com.phoneguard.pro.ui.theme.PgWarning
import com.phoneguard.pro.ui.theme.scoreColor
import com.phoneguard.pro.ui.viewmodel.DashboardViewModel
import com.phoneguard.pro.util.Fmt

@Composable
fun DashboardScreen(
    onOpenReport: () -> Unit,
    onOpenBenchmark: () -> Unit,
    vm: DashboardViewModel = viewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()

    if (s.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PgCyan) }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreGauge(s.healthScore, diameter = 150.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.deviceName, style = MaterialTheme.typography.titleMedium, color = PgText)
                        Text(s.androidVersion, style = MaterialTheme.typography.bodySmall, color = PgTextDim)
                        Spacer(Modifier.height(10.dp))
                        StatusChip(s.healthCondition, scoreColor(s.healthScore))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Live health score from RAM, storage, battery, temperature" +
                                if (s.latestBench != null) " and your last benchmark." else ". Run a benchmark to include CPU.",
                            style = MaterialTheme.typography.bodySmall, color = PgTextDim
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Full health report", onOpenReport, Modifier.fillMaxWidth(), Icons.Rounded.Assessment)
            }
        }

        item {
            val ram = s.ram
            val st = s.storage
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    "RAM", ram?.let { Fmt.percent(it.usedFraction) } ?: "—",
                    ram?.let { "${Fmt.bytes(it.usedBytes)} / ${Fmt.bytes(it.totalBytes)}" } ?: "",
                    Icons.Rounded.Memory, Modifier.weight(1f), fraction = ram?.usedFraction
                )
                MetricTile(
                    "Storage", st?.let { Fmt.percent(it.usedFraction) } ?: "—",
                    st?.let { "${Fmt.bytes(it.freeBytes)} free" } ?: "",
                    Icons.Rounded.Storage, Modifier.weight(1f), fraction = st?.usedFraction
                )
            }
        }

        item {
            val b = s.battery
            val th = s.thermal
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    "Battery", b?.levelPercent?.let { "$it%" } ?: "—",
                    b?.let { bat -> bat.status + (bat.temperatureC.value?.let { " · ${Fmt.num(it)} °C" } ?: "") } ?: "",
                    Icons.Rounded.BatteryStd, Modifier.weight(1f),
                    fraction = b?.levelPercent?.let { it / 100f },
                    accent = if ((b?.levelPercent ?: 100) <= 20) PgDanger else PgSuccess
                )
                val thColor = when {
                    th == null -> PgTextDim
                    th.status >= 3 -> PgDanger
                    th.status >= 2 -> PgWarning
                    else -> PgSuccess
                }
                MetricTile(
                    "Thermal", th?.statusLabel ?: "—",
                    th?.headroom?.let { "Headroom ${Fmt.num(it * 100, 0)}% of limit" } ?: "System thermal status",
                    Icons.Rounded.Thermostat, Modifier.weight(1f), accent = thColor
                )
            }
        }

        item {
            GlassCard {
                SectionTitle("Memory usage · live", Icons.Rounded.Memory)
                LineChart(
                    s.ramHistory, Modifier.fillMaxWidth().height(140.dp),
                    minY = 0f, maxY = 100f, yLabel = { "${it.toInt()}%" }
                )
                NoteText("System-reported RAM in use, sampled every 2 s while this screen is open.")
            }
        }

        item {
            val cpu = s.cpu
            GlassCard {
                SectionTitle("CPU cores · live frequency", Icons.Rounded.DeveloperBoard)
                if (cpu != null) {
                    Text(listOfNotNull(cpu.vendor, cpu.model).joinToString(" "), color = PgText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${cpu.coreCount} cores · ${cpu.clusterSummary}", color = PgTextDim, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    if (cpu.cores.any { it.maxFreqKhz != null }) {
                        CoreFreqBars(cpu.cores)
                        Spacer(Modifier.height(10.dp))
                        LineChart(
                            s.cpuFreqHistory, Modifier.fillMaxWidth().height(90.dp),
                            minY = 0f, maxY = 100f, yLabel = { "${it.toInt()}%" }
                        )
                        NoteText("Bars show each core's current clock as a share of its maximum. Android 8+ blocks apps from reading true CPU utilisation, so this is a frequency-scaling indicator, not a usage percentage.")
                    } else {
                        NoteText("This device does not expose per-core frequencies to apps.")
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("Performance", Icons.Rounded.Speed)
                val bench = s.latestBench
                if (bench == null) {
                    Text("No benchmark yet.", color = PgTextDim, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${bench.overallScore}", style = MaterialTheme.typography.displaySmall, color = scoreColor(bench.overallScore), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Last benchmark score / 100", color = PgText, style = MaterialTheme.typography.bodyMedium)
                            Text(Fmt.dateTime(bench.timestamp), color = PgTextDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(if (bench == null) "Run benchmark" else "Run again", onOpenBenchmark, Modifier.fillMaxWidth(), Icons.Rounded.Speed)
            }
        }
    }
}
