package com.phoneguard.pro.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoneguard.pro.data.model.AppUsageItem
import com.phoneguard.pro.data.model.BatterySample
import com.phoneguard.pro.data.model.BatterySnapshot
import com.phoneguard.pro.data.model.DischargeAnalysis
import com.phoneguard.pro.di.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BatteryUiState(
    val snapshot: BatterySnapshot? = null,
    val samples: List<BatterySample> = emptyList(),
    val discharge: DischargeAnalysis? = null,
    val usageAccess: Boolean = false,
    val appUsage: List<AppUsageItem> = emptyList()
)

/** Samples the battery every 5 s while the Battery screen is visible. No background service. */
class BatteryViewModel(app: Application) : AndroidViewModel(app) {
    private val c = container
    private val samples = MutableStateFlow<List<BatterySample>>(emptyList())
    private val usage = MutableStateFlow<Pair<Boolean, List<AppUsageItem>>>(false to emptyList())

    private val snapshots = flow {
        while (true) {
            val s = c.battery.snapshot()
            samples.update {
                (it + BatterySample(s.timestamp, s.levelPercent ?: 0, s.currentMa.value, s.temperatureC.value, s.isCharging))
                    .takeLast(720)
            }
            emit(s)
            delay(5000)
        }
    }.flowOn(Dispatchers.IO)

    val state: StateFlow<BatteryUiState> = combine(snapshots, samples, usage) { snap, smp, u ->
        BatteryUiState(
            snapshot = snap,
            samples = smp,
            discharge = c.battery.analyzeDischarge(smp, snap),
            usageAccess = u.first,
            appUsage = u.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), BatteryUiState())

    fun refreshUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            val has = c.optimization.hasUsageAccess()
            val list = if (has) {
                c.optimization.appUsage(includeStorage = false)
                    .filter { it.foregroundMs > 0 || it.foregroundServiceMs > 0 }
                    .sortedByDescending { it.foregroundMs + it.foregroundServiceMs }
                    .take(10)
            } else emptyList()
            usage.value = has to list
        }
    }
}
