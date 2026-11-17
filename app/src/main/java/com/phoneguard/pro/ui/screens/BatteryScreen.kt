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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneguard.pro.data.model.DataSource
import com.phoneguard.pro.data.model.Sourced
import com.phoneguard.pro.ui.components.AppIcon
import com.phoneguard.pro.ui.components.DataSourceLegend
import com.phoneguard.pro.ui.components.GlassCard
import com.phoneguard.pro.ui.components.InfoRow
import com.phoneguard.pro.ui.components.LineChart
import com.phoneguard.pro.ui.components.NoteText
import com.phoneguard.pro.ui.components.PrimaryButton
import com.phoneguard.pro.ui.components.ScoreGauge
import com.phoneguard.pro.ui.components.SecondaryButton
import com.phoneguard.pro.ui.components.SectionTitle
import com.phoneguard.pro.ui.components.StatusChip
import com.phoneguard.pro.ui.navigation.SystemIntents
import com.phoneguard.pro.ui.theme.PgCyan
import com.phoneguard.pro.ui.theme.PgDanger
import com.phoneguard.pro.ui.theme.PgSuccess
import com.phoneguard.pro.ui.theme.PgText
import com.phoneguard.pro.ui.theme.PgTextDim
import com.phoneguard.pro.ui.theme.PgWarning
import com.phoneguard.pro.ui.viewmodel.BatteryViewModel
import com.phoneguard.pro.util.Fmt

@Composable
fun BatteryScreen(vm: BatteryViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshUsage() }

    val b = s.snapshot
    if (b == null) {
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
                    ScoreGauge(b.levelPercent, diameter = 140.dp, label = "CHARGE")
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        StatusChip(b.status, if (b.isCharging) PgSuccess else PgCyan)
                        Spacer(Modifier.height(8.dp))
                        Text(b.plugType, color = PgText, style = MaterialTheme.typography.bodyMedium)
                        b.technology?.let { Text("Chemistry: $it", color = PgTextDim, style = MaterialTheme.typography.bodySmall) }
                        b.temperatureC.value?.let {
                            Text("${Fmt.num(it)} °C", color = tempColor(it), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("Battery health", Icons.Rounded.Favorite)
                InfoRow("Health flag", b.healthReported, DataSource.REPORTED, "Binary status from the battery controller (Good / Overheat / Dead…)")
                sourcedRow("Estimated health", b.estimatedHealthPercent) { "$it%" }
                sourcedRow("Design capacity", b.designCapacityMah) { "$it mAh" }
                sourcedRow("Est. full-charge capacity", b.estimatedFullCapacityMah) { "$it mAh" }
                sourcedRow("Remaining charge", b.chargeCounterMah) { "$it mAh" }
                sourcedRow("Charge cycles", b.cycleCount) { "$it" }
                NoteText("Android doesn't give normal apps the official state-of-health value. The estimate compares the fuel-gauge charge counter with design capacity and is most reliable at 50–100% charge.")
            }
        }

        item {
            GlassCard {
                SectionTitle("Electrical", Icons.Rounded.Bolt)
                sourcedRow("Voltage", b.voltageMv) { "${Fmt.num(it / 1000f, 3)} V" }
                sourcedRow(if (b.isCharging) "Charge current" else "Discharge current", b.currentMa) { "$it mA" }
                sourcedRow("Average current", b.averageCurrentMa) { "$it mA" }
                sourcedRow("Power", b.powerW) { "${Fmt.num(it, 2)} W" }
                sourcedRow("Energy remaining", b.energyWh) { "${Fmt.num(it, 2)} Wh" }
                NoteText("Some vendors report current in mA instead of µA or with an inverted sign; values are normalised and shown as absolute.")
            }
        }

        item {
            GlassCard {
                SectionTitle("Charging", Icons.Rounded.BatteryChargingFull)
                InfoRow("Status", b.status, DataSource.REPORTED)
                InfoRow("Source", b.plugType, DataSource.REPORTED)
                sourcedRow("Charging class", b.chargingClass) { it }
                sourcedRow("Time to full", b.chargeTimeRemainingMs) { Fmt.duration(it) }
            }
        }

        item {
            GlassCard {
                SectionTitle("Temperature", Icons.Rounded.Thermostat)
                sourcedRow("Battery temperature", b.temperatureC) { "${Fmt.num(it)} °C" }
                Spacer(Modifier.height(6.dp))
                LineChart(
                    s.samples.mapNotNull { it.tempC }, Modifier.fillMaxWidth().height(110.dp),
                    lineColor = PgWarning, yLabel = { "${Fmt.num(it)}°" }
                )
                NoteText("Ideal: 20–35 °C. Above 40 °C accelerates ageing; above 45 °C is a warning.")
            }
        }

        item {
            val d = s.discharge
            GlassCard {
                SectionTitle("Discharge analysis", Icons.Rounded.Timeline)
                LineChart(
                    s.samples.map { it.level.toFloat() }, Modifier.fillMaxWidth().height(120.dp),
                    yLabel = { "${it.toInt()}%" }
                )
                Spacer(Modifier.height(8.dp))
                if (d != null) {
                    sourcedRow("Drain rate", d.drainPercentPerHour) { "${Fmt.num(it)} %/h" }
                    sourcedRow("Estimated time left", d.remainingHours) { Fmt.hours(it) }
                    InfoRow("Session length", "${Fmt.num(d.sessionMinutes, 0)} min")
                }
                Spacer(Modifier.height(6.dp))
                LineChart(
                    s.samples.mapNotNull { it.currentMa?.toFloat() }, Modifier.fillMaxWidth().height(90.dp),
                    lineColor = PgCyan, yLabel = { "${it.toInt()}" }, emptyText = "Current readings not available"
                )
                NoteText("Sampled every 5 s while this screen is open (current in mA above). No background monitoring is used, to avoid draining the battery.")
            }
        }

        item {
            GlassCard {
                SectionTitle("App usage (last ~24 h)", Icons.Rounded.Apps)
                if (!s.usageAccess) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Rounded.Lock, null, tint = PgWarning)
                        Spacer(Modifier.width(8.dp))
                        Text("Usage Access needed to rank apps by activity.", color = PgTextDim, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Grant Usage Access", { SystemIntents.openUsageAccess(context) }, Modifier.fillMaxWidth())
                } else if (s.appUsage.isEmpty()) {
                    NoteText("No app activity recorded in this period.")
                } else {
                    s.appUsage.forEach { a ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(a.packageName, 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.label, color = PgText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    "Screen ${Fmt.duration(a.foregroundMs)} · Background service ${Fmt.duration(a.foregroundServiceMs)}",
                                    color = PgTextDim, style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                NoteText("Per-app energy (mAh) is restricted to system apps by Android (BATTERY_STATS). Activity time is shown as a proxy; open the system screen for exact battery usage.")
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Open system battery usage", { SystemIntents.openBatteryUsage(context) }, Modifier.fillMaxWidth(), Icons.Rounded.BatteryAlert)
            }
        }

        item { GlassCard { DataSourceLegend() } }
    }
}

@Composable
private fun <T> sourcedRow(label: String, s: Sourced<T>, fmt: (T) -> String) {
    val v = s.value
    if (v != null) InfoRow(label, fmt(v), s.source, s.note)
    else InfoRow(label, "Unavailable", DataSource.UNAVAILABLE, s.note)
}

private fun tempColor(t: Float) = when {
    t >= 45f -> PgDanger
    t >= 40f -> PgWarning
    else -> PgSuccess
}
