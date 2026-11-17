package com.phoneguard.pro.ui.viewmodel

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoneguard.pro.data.model.AppUsageItem
import com.phoneguard.pro.data.model.MediaFileItem
import com.phoneguard.pro.data.model.Recommendation
import com.phoneguard.pro.data.model.StorageInfo
import com.phoneguard.pro.data.optimization.OptimizationAnalyzer
import com.phoneguard.pro.data.optimization.RecommendationEngine
import com.phoneguard.pro.data.optimization.RecommendationInput
import com.phoneguard.pro.di.container
import com.phoneguard.pro.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OptimizationState(
    val loading: Boolean = true,
    val usageAccess: Boolean = false,
    val mediaPermission: Boolean = false,
    val storage: StorageInfo? = null,
    val ownCacheBytes: Long = 0,
    val heavyApps: List<AppUsageItem> = emptyList(),
    val drainApps: List<AppUsageItem> = emptyList(),
    val largestApps: List<AppUsageItem> = emptyList(),
    val cacheApps: List<AppUsageItem> = emptyList(),
    val unusedApps: List<AppUsageItem> = emptyList(),
    val largeFiles: List<MediaFileItem> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val message: String? = null
)

class OptimizationViewModel(app: Application) : AndroidViewModel(app) {
    private val c = container
    private val _state = MutableStateFlow(OptimizationState())
    val state: StateFlow<OptimizationState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val result = withContext(Dispatchers.IO) {
                val opt = c.optimization
                val now = System.currentTimeMillis()
                val usageAccess = opt.hasUsageAccess()
                val apps = if (usageAccess) opt.appUsage(includeStorage = true) else emptyList()
                val storage = c.storage.read()
                val device = c.device.read()
                val recs = RecommendationEngine.build(
                    RecommendationInput(
                        ram = c.memory.read(), storage = storage, battery = c.battery.snapshot(),
                        thermal = c.thermal.read(includeZones = false), apps = apps, usageAccess = usageAccess,
                        uptimeMs = device.uptimeMs, securityPatch = device.securityPatch,
                        tuning = opt.systemTuning(), latestBenchmark = c.history.latest()
                    )
                )
                val cutoff = now - 30 * OptimizationAnalyzer.DAY_MS
                OptimizationState(
                    loading = false,
                    usageAccess = usageAccess,
                    mediaPermission = opt.hasMediaPermission(),
                    storage = storage,
                    ownCacheBytes = opt.ownCacheBytes(),
                    heavyApps = apps.filter { it.foregroundMs > 0 }.sortedByDescending { it.foregroundMs }.take(8),
                    drainApps = apps.filter { it.foregroundServiceMs > 0 }.sortedByDescending { it.foregroundServiceMs }.take(8),
                    largestApps = apps.filter { (it.totalBytes ?: 0) > 0 }.sortedByDescending { it.totalBytes ?: 0 }.take(8),
                    cacheApps = apps.filter { (it.cacheBytes ?: 0) > 10L * 1024 * 1024 }.sortedByDescending { it.cacheBytes ?: 0 }.take(8),
                    unusedApps = apps.filter { !it.isSystem && it.firstInstallTime in 1 until cutoff && it.lastUsed < cutoff }
                        .sortedByDescending { it.totalBytes ?: 0 }.take(15),
                    largeFiles = opt.largeMediaFiles(),
                    recommendations = recs
                )
            }
            _state.update { result.copy(message = it.message) }
        }
    }

    fun clearOwnCache() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) { c.optimization.clearOwnCache() }
            _state.update { it.copy(ownCacheBytes = c.optimization.ownCacheBytes(), message = "Freed ${Fmt.bytes(freed)} from Phone Guard Pro's cache") }
        }
    }

    /** Returns a system confirmation IntentSender (Android 11+) or null if deletion completed directly. */
    fun createDeleteRequest(uris: List<Uri>): IntentSender? = c.optimization.deleteMedia(uris)

    fun onDeleteFinished(confirmed: Boolean) {
        _state.update { it.copy(message = if (confirmed) "Selected files deleted" else "Deletion cancelled") }
        refreshJob = null
        refresh()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
