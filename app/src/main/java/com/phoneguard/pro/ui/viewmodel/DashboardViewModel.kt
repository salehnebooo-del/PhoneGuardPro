package com.phoneguard.pro.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoneguard.pro.data.model.BatterySnapshot
import com.phoneguard.pro.data.model.BenchmarkResult
import com.phoneguard.pro.data.model.CpuInfo
import com.phoneguard.pro.data.model.RamInfo
import com.phoneguard.pro.data.model.StorageInfo
import com.phoneguard.pro.data.model.ThermalInfo
import com.phoneguard.pro.data.report.HealthScorer
import com.phoneguard.pro.di.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class DashboardState(
    val loading: Boolean = true,
    val deviceName: String = "",
    val androidVersion: String = "",
    val ram: RamInfo? = null,
    val storage: StorageInfo? = null,
    val battery: BatterySnapshot? = null,
    val thermal: ThermalInfo? = null,
    val cpu: CpuInfo? = null,
    val ramHistory: List<Float> = emptyList(),
    val cpuFreqHistory: List<Float> = emptyList(),
    val healthScore: Int? = null,
    val healthCondition: String = "",
    val latestBench: BenchmarkResult? = null
)

/**
 * Live dashboard. Polls every 2 s ONLY while the screen is visible
 * (WhileSubscribed stops the loop 3 s after the UI leaves), keeping the app's own energy use low.
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val c = container

    val state: StateFlow<DashboardState> = flow {
        val ramHist = ArrayDeque<Float>()
        val cpuHist = ArrayDeque<Float>()
        val device = c.device.read()
        var storage = c.storage.read()
        var tick = 0
        while (true) {
            if (tick > 0 && tick % 15 == 0) storage = c.storage.read()
            val ram = c.memory.read()
            val cpu = c.cpu.read()
            val bat = c.battery.snapshot()
            val th = c.thermal.read(includeZones = false)
            val bench = c.history.latest()

            ramHist.addLast(ram.usedFraction * 100f)
            if (ramHist.size > 40) ramHist.removeFirst()
            cpu.frequencyLoadPercent?.let {
                cpuHist.addLast(it)
                if (cpuHist.size > 40) cpuHist.removeFirst()
            }
            val scored = c.healthScorer.evaluate(ram, storage, bat, th, bench)
            emit(
                DashboardState(
                    loading = false,
                    deviceName = "${device.manufacturer} ${device.model}",
                    androidVersion = "Android ${device.androidVersion} · API ${device.sdkInt}",
                    ram = ram, storage = storage, battery = bat, thermal = th, cpu = cpu,
                    ramHistory = ramHist.toList(),
                    cpuFreqHistory = cpuHist.toList(),
                    healthScore = scored.overall,
                    healthCondition = HealthScorer.condition(scored.overall),
                    latestBench = bench
                )
            )
            tick++
            delay(2000)
        }
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), DashboardState())
}
