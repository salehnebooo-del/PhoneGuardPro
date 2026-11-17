package com.phoneguard.pro.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneguard.pro.data.model.DataSource
import com.phoneguard.pro.data.model.SensorItem
import com.phoneguard.pro.ui.components.GlassCard
import com.phoneguard.pro.ui.components.InfoRow
import com.phoneguard.pro.ui.components.NoteText
import com.phoneguard.pro.ui.components.PrimaryButton
import com.phoneguard.pro.ui.components.SecondaryButton
import com.phoneguard.pro.ui.components.SectionTitle
import com.phoneguard.pro.ui.components.UsageBar
import com.phoneguard.pro.ui.theme.PgCyan
import com.phoneguard.pro.ui.theme.PgOutline
import com.phoneguard.pro.ui.theme.PgSurface
import com.phoneguard.pro.ui.theme.PgText
import com.phoneguard.pro.ui.theme.PgTextDim
import com.phoneguard.pro.ui.viewmodel.DiagnosticsViewModel
import com.phoneguard.pro.util.Fmt
import java.util.Locale

@Composable
fun DiagnosticsScreen(onOpenTouchTest: () -> Unit, vm: DiagnosticsViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var sensorDialog by remember { mutableStateOf<SensorItem?>(null) }

    if (s.device == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PgCyan) }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Device diagnostics", style = MaterialTheme.typography.headlineSmall, color = PgText, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::refresh) { Icon(Icons.Rounded.Refresh, "Refresh", tint = PgCyan) }
            }
        }

        s.device?.let { d ->
            item {
                GlassCard {
                    SectionTitle("Device", Icons.Rounded.PhoneAndroid)
                    InfoRow("Model", "${d.manufacturer} ${d.model}")
                    InfoRow("Codename", "${d.device} / ${d.product}")
                    InfoRow("Android", "${d.androidVersion} (API ${d.sdkInt})")
                    InfoRow("Security patch", d.securityPatch)
                    InfoRow("Build", d.buildId)
                    InfoRow("Board / hardware", "${d.board} / ${d.hardware}")
                    InfoRow("Kernel", d.kernel)
                    InfoRow("Uptime", Fmt.duration(d.uptimeMs))
                }
            }
        }

        s.cpu?.let { cpu ->
            item {
                GlassCard {
                    SectionTitle("Processor", Icons.Rounded.DeveloperBoard)
                    InfoRow("Model", listOfNotNull(cpu.vendor, cpu.model).joinToString(" "), DataSource.REPORTED)
                    InfoRow("Architecture", cpu.arch)
                    InfoRow("ABIs", cpu.abis.joinToString())
                    InfoRow("Cores", cpu.coreCount.toString())
                    InfoRow("Clusters", cpu.clusterSummary)
                    cpu.governor?.let { InfoRow("Governor", it) }
                    InfoRow(
                        "Frequency load",
                        cpu.frequencyLoadPercent?.let { "${Fmt.num(it, 0)}%" } ?: "Not exposed",
                        if (cpu.frequencyLoadPercent != null) DataSource.REPORTED else DataSource.UNAVAILABLE,
                        "Average current clock ÷ max clock. True CPU usage is blocked for apps since Android 8."
                    )
                    Spacer(Modifier.height(6.dp))
                    cpu.cores.forEach { c ->
                        InfoRow(
                            "Core ${c.index}",
                            if (!c.online) "Offline"
                            else "${Fmt.freq(c.curFreqKhz)} (${Fmt.freq(c.minFreqKhz)}–${Fmt.freq(c.maxFreqKhz)})"
                        )
                    }
                }
            }
        }

        s.ram?.let { r ->
            item {
                GlassCard {
                    SectionTitle("Memory (RAM)", Icons.Rounded.Memory)
                    InfoRow("Total", Fmt.bytes(r.totalBytes), DataSource.REPORTED)
                    InfoRow("Available", Fmt.bytes(r.availableBytes), DataSource.REPORTED)
                    InfoRow("Used", "${Fmt.bytes(r.usedBytes)} (${Fmt.percent(r.usedFraction)})", DataSource.CALCULATED)
                    InfoRow("Low-memory threshold", Fmt.bytes(r.thresholdBytes))
                    InfoRow("Low-memory state", if (r.lowMemory) "Yes" else "No")
                    InfoRow("Low-RAM device class", if (r.isLowRamDevice) "Yes" else "No")
                    Spacer(Modifier.height(8.dp))
                    UsageBar(r.usedFraction)
                    NoteText("Total excludes memory reserved by the kernel/firmware, so it is lower than the marketed size.")
                }
            }
        }

        s.storage?.let { st ->
            item {
                GlassCard {
                    SectionTitle("Internal storage", Icons.Rounded.Storage)
                    InfoRow("Capacity", Fmt.bytes(st.totalBytes), DataSource.REPORTED)
                    InfoRow("Used", "${Fmt.bytes(st.usedBytes)} (${Fmt.percent(st.usedFraction)})", DataSource.CALCULATED)
                    InfoRow("Free", Fmt.bytes(st.freeBytes), DataSource.REPORTED)
                    Spacer(Modifier.height(8.dp))
                    UsageBar(st.usedFraction)
                }
            }
        }

        s.display?.let { d ->
            item {
                GlassCard {
                    SectionTitle("Display", Icons.Rounded.Smartphone)
                    InfoRow("Resolution", "${d.widthPx} × ${d.heightPx} px", DataSource.REPORTED)
                    InfoRow("Density", "${d.densityDpi} dpi")
                    InfoRow("Current refresh rate", "${Fmt.num(d.refreshRateHz, 0)} Hz", DataSource.REPORTED)
                    InfoRow("Supported rates", d.supportedRefreshRates.joinToString { "${Fmt.num(it, 0)} Hz" })
                    InfoRow("HDR", d.hdrTypes.ifEmpty { listOf("None reported") }.joinToString())
                    d.diagonalInches?.let {
                        InfoRow("Diagonal", "${Fmt.num(it)}\"", DataSource.ESTIMATED, "Derived from reported pixel density; OEM values can be imprecise.")
                    }
                    NoteText("Adaptive displays may lower the current rate while idle.")
                }
            }
        }

        s.network?.let { n ->
            item {
                GlassCard {
                    SectionTitle("Network", Icons.Rounded.Wifi)
                    InfoRow("Connection", if (n.connected) n.transport else "Offline")
                    InfoRow("Internet validated", if (n.validated) "Yes" else "No")
                    InfoRow("Metered", if (n.metered) "Yes" else "No")
                    n.downstreamKbps?.let { InfoRow("Link bandwidth (down)", "${it / 1000} Mbps", DataSource.ESTIMATED, "System estimate, not a speed test") }
                    n.wifi?.let { w ->
                        InfoRow("SSID", w.ssid ?: "Hidden (needs location permission)")
                        w.rssiDbm?.let { InfoRow("Signal", "$it dBm (${w.signalPercent}%)", DataSource.REPORTED) }
                        w.linkSpeedMbps?.let { InfoRow("Link speed", "$it Mbps", DataSource.REPORTED) }
                        if (w.txLinkMbps != null || w.rxLinkMbps != null) InfoRow("TX / RX link", "${w.txLinkMbps ?: "—"} / ${w.rxLinkMbps ?: "—"} Mbps")
                        w.frequencyMhz?.let { InfoRow("Band", if (it >= 5900) "6 GHz ($it MHz)" else if (it >= 4900) "5 GHz ($it MHz)" else "2.4 GHz ($it MHz)") }
                        w.standard?.let { InfoRow("Standard", it) }
                    }
                    if (n.ipAddresses.isNotEmpty()) InfoRow("IP", n.ipAddresses.joinToString())
                    if (n.dnsServers.isNotEmpty()) InfoRow("DNS", n.dnsServers.joinToString())
                    InfoRow("Data since boot", "↓ ${Fmt.bytes(n.totalRxBytes)} · ↑ ${Fmt.bytes(n.totalTxBytes)}")
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = PgOutline)
                    Spacer(Modifier.height(10.dp))
                    SectionTitle("Connectivity test", Icons.Rounded.NetworkCheck)
                    if (s.latency.isEmpty() && !s.latencyRunning) {
                        NoteText("Measures DNS resolution and TCP handshake latency to public servers. Nothing is uploaded.")
                    }
                    s.latency.forEach { l ->
                        InfoRow(l.target, l.medianMs?.let { "${Fmt.num(it, 0)} ms" } ?: "Failed (${l.error})", if (l.medianMs != null) DataSource.MEASURED else DataSource.UNAVAILABLE)
                    }
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        if (s.latencyRunning) "Testing…" else "Run connectivity test",
                        vm::runConnectivityTest, Modifier.fillMaxWidth(), Icons.Rounded.NetworkCheck, enabled = !s.latencyRunning
                    )
                }
            }
        }

        s.thermal?.let { t ->
            item {
                GlassCard {
                    SectionTitle("Thermal", Icons.Rounded.Thermostat)
                    InfoRow("System status", t.statusLabel, DataSource.REPORTED)
                    t.headroom?.let { InfoRow("Thermal headroom", "${Fmt.num(it * 100, 0)}% of throttling limit", DataSource.REPORTED, "1.0 (100%) = severe throttling") }
                    if (t.zones.isEmpty()) NoteText("Sensor-level thermal zones are not readable by apps on this device.")
                    t.zones.forEach { z -> InfoRow(z.name, "${Fmt.num(z.tempC)} °C", DataSource.REPORTED) }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("Touchscreen test", Icons.Rounded.TouchApp)
                Text("Draw across the whole screen to find dead zones and check multi-touch.", color = PgTextDim, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Start touch test", onOpenTouchTest, Modifier.fillMaxWidth(), Icons.Rounded.TouchApp)
            }
        }

        item {
            GlassCard {
                SectionTitle("Sensors (${s.sensors.size})", Icons.Rounded.Sensors)
                if (s.sensors.isEmpty()) NoteText("No sensors reported.")
                NoteText("Tap a sensor to read live values.")
            }
        }
        items(s.sensors.size) { i ->
            val sensor = s.sensors[i]
            Row(
                Modifier.fillMaxWidth().clickable { sensorDialog = sensor }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(sensor.typeName, color = PgText, style = MaterialTheme.typography.bodyMedium)
                    Text("${sensor.name} · ${sensor.vendor}", color = PgTextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = PgTextDim, modifier = Modifier.size(20.dp))
            }
        }
    }

    sensorDialog?.let { item ->
        SensorLiveDialog(item, vm) { sensorDialog = null }
    }
}

@Composable
private fun SensorLiveDialog(item: SensorItem, vm: DiagnosticsViewModel, onDismiss: () -> Unit) {
    val flow = remember(item.index) { vm.sensorReadings(item.index) }
    val values by flow.collectAsStateWithLifecycle(initialValue = null)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PgSurface,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = PgCyan) } },
        title = { Text(item.typeName, color = PgText) },
        text = {
            Column {
                InfoRow("Name", item.name)
                InfoRow("Vendor", item.vendor)
                InfoRow("Max range", Fmt.num(item.maxRange, 2))
                InfoRow("Resolution", String.format(Locale.US, "%.4f", item.resolution))
                InfoRow("Power", "${Fmt.num(item.powerMa, 2)} mA")
                InfoRow("Wake-up sensor", if (item.isWakeUp) "Yes" else "No")
                Spacer(Modifier.height(8.dp))
                Text("Live readings", color = PgCyan, style = MaterialTheme.typography.labelLarge)
                val v = values
                if (v == null) {
                    NoteText("Waiting for events… Some sensors only report on change or are one-shot.")
                } else {
                    v.forEachIndexed { i, value ->
                        InfoRow(axisLabel(i), String.format(Locale.US, "%.4f", value), DataSource.MEASURED)
                    }
                }
            }
        }
    )
}

private fun axisLabel(i: Int) = when (i) {
    0 -> "X / value[0]"
    1 -> "Y / value[1]"
    2 -> "Z / value[2]"
    else -> "value[$i]"
}
