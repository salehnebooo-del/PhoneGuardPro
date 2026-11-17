package com.phoneguard.pro.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoneguard.pro.data.model.CpuInfo
import com.phoneguard.pro.data.model.DeviceInfo
import com.phoneguard.pro.data.model.DisplayInfo
import com.phoneguard.pro.data.model.LatencyResult
import com.phoneguard.pro.data.model.NetworkStatus
import com.phoneguard.pro.data.model.RamInfo
import com.phoneguard.pro.data.model.SensorItem
import com.phoneguard.pro.data.model.StorageInfo
import com.phoneguard.pro.data.model.ThermalInfo
import com.phoneguard.pro.di.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiagnosticsState(
    val loading: Boolean = true,
    val device: DeviceInfo? = null,
    val cpu: CpuInfo? = null,
    val ram: RamInfo? = null,
    val storage: StorageInfo? = null,
    val display: DisplayInfo? = null,
    val network: NetworkStatus? = null,
    val thermal: ThermalInfo? = null,
    val sensors: List<SensorItem> = emptyList(),
    val latency: List<LatencyResult> = emptyList(),
    val latencyRunning: Boolean = false
)

class DiagnosticsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = container
    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val fresh = withContext(Dispatchers.IO) {
                DiagnosticsState(
                    loading = false,
                    device = c.device.read(),
                    cpu = c.cpu.read(),
                    ram = c.memory.read(),
                    storage = c.storage.read(),
                    display = c.display.read(),
                    network = c.network.read(),
                    thermal = c.thermal.read(includeZones = true),
                    sensors = c.sensors.list()
                )
            }
            _state.update { fresh.copy(latency = it.latency) }
        }
    }

    fun runConnectivityTest() {
        if (_state.value.latencyRunning) return
        viewModelScope.launch {
            _state.update { it.copy(latencyRunning = true) }
            val results = listOf(
                c.network.dnsLookup("www.wikipedia.org"),
                c.network.tcpLatency("Cloudflare (1.1.1.1:443)", "1.1.1.1", 443),
                c.network.tcpLatency("Google DNS (8.8.8.8:53)", "8.8.8.8", 53)
            )
            val net = withContext(Dispatchers.IO) { c.network.read() }
            _state.update { it.copy(latency = results, latencyRunning = false, network = net) }
        }
    }

    fun sensorReadings(index: Int): Flow<FloatArray> = c.sensors.readings(index)
}
