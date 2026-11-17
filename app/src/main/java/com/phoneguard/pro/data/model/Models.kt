package com.phoneguard.pro.data.model

import android.net.Uri

/** Every value shown to the user carries where it came from. Nothing is simulated. */
enum class DataSource(val label: String) {
    MEASURED("Measured"),          // measured by this app (benchmarks, latency, touch test)
    REPORTED("System-reported"),   // read directly from Android / hardware APIs
    CALCULATED("Calculated"),      // arithmetic on reported values (e.g. V × I = W)
    ESTIMATED("Estimated"),        // model-based approximation (e.g. battery health)
    UNAVAILABLE("Unavailable")     // not exposed by this device / Android version
}

data class Sourced<T>(val value: T?, val source: DataSource, val note: String? = null) {
    companion object {
        fun <T> unavailable(note: String? = null): Sourced<T> = Sourced(null, DataSource.UNAVAILABLE, note)
    }
}

// ---------------- Diagnostics ----------------

data class CoreInfo(
    val index: Int,
    val curFreqKhz: Long?,
    val minFreqKhz: Long?,
    val maxFreqKhz: Long?,
    val online: Boolean
)

data class CpuInfo(
    val model: String,
    val vendor: String?,
    val abis: List<String>,
    val arch: String,
    val coreCount: Int,
    val cores: List<CoreInfo>,
    val clusterSummary: String,
    /** Average of current/max frequency across online cores. NOT CPU utilisation (see README). */
    val frequencyLoadPercent: Float?,
    val governor: String?
)

data class RamInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val thresholdBytes: Long,
    val lowMemory: Boolean,
    val isLowRamDevice: Boolean
) {
    val usedBytes: Long get() = totalBytes - availableBytes
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class StorageInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = totalBytes - freeBytes
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val freeFraction: Float get() = if (totalBytes > 0) freeBytes.toFloat() / totalBytes else 0f
}

data class DisplayInfo(
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
    val supportedRefreshRates: List<Float>,
    val hdrTypes: List<String>,
    val diagonalInches: Double?
)

data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val hardware: String,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildId: String,
    val kernel: String,
    val uptimeMs: Long,
    val fingerprint: String
)

data class SensorItem(
    val index: Int,
    val name: String,
    val vendor: String,
    val typeName: String,
    val type: Int,
    val powerMa: Float,
    val maxRange: Float,
    val resolution: Float,
    val isWakeUp: Boolean
)

data class WifiDetails(
    val rssiDbm: Int?,
    val signalPercent: Int?,
    val linkSpeedMbps: Int?,
    val txLinkMbps: Int?,
    val rxLinkMbps: Int?,
    val frequencyMhz: Int?,
    val standard: String?,
    val ssid: String?
)

data class NetworkStatus(
    val connected: Boolean,
    val validated: Boolean,
    val transport: String,
    val metered: Boolean,
    val downstreamKbps: Int?,
    val upstreamKbps: Int?,
    val ipAddresses: List<String>,
    val dnsServers: List<String>,
    val wifi: WifiDetails?,
    val totalRxBytes: Long,
    val totalTxBytes: Long
)

data class LatencyResult(val target: String, val medianMs: Double?, val error: String?)

data class ThermalZone(val name: String, val tempC: Float)

data class ThermalInfo(
    val status: Int,
    val statusLabel: String,
    val headroom: Float?,
    val zones: List<ThermalZone>
)

// ---------------- Battery ----------------

data class BatterySnapshot(
    val timestamp: Long,
    val levelPercent: Int?,
    val status: String,
    val isCharging: Boolean,
    val isPlugged: Boolean,
    val plugType: String,
    val healthReported: String,
    val healthReportedGood: Boolean?,
    val technology: String?,
    val temperatureC: Sourced<Float>,
    val voltageMv: Sourced<Int>,
    val currentMa: Sourced<Int>,
    val averageCurrentMa: Sourced<Int>,
    val powerW: Sourced<Float>,
    val chargeCounterMah: Sourced<Int>,
    val energyWh: Sourced<Float>,
    val designCapacityMah: Sourced<Int>,
    val estimatedFullCapacityMah: Sourced<Int>,
    val estimatedHealthPercent: Sourced<Int>,
    val cycleCount: Sourced<Int>,
    val chargingClass: Sourced<String>,
    val chargeTimeRemainingMs: Sourced<Long>
)

data class BatterySample(
    val timestamp: Long,
    val level: Int,
    val currentMa: Int?,
    val tempC: Float?,
    val charging: Boolean
)

data class DischargeAnalysis(
    val drainPercentPerHour: Sourced<Float>,
    val remainingHours: Sourced<Float>,
    val sessionMinutes: Float
)

// ---------------- Benchmark ----------------

data class BenchmarkProgress(
    val stage: String,
    val fraction: Float,
    val tempC: Float?,
    val thermalStatus: String
)

data class BenchmarkResult(
    val timestamp: Long,
    val threads: Int,
    val cpuSingleMops: Double,
    val cpuSingleMflops: Double,
    val cpuMultiMops: Double,
    val cpuMultiMflops: Double,
    val memCopyMBps: Double,
    val memLatencyNs: Double,
    val storageSeqWriteMBps: Double,
    val storageSeqReadMBps: Double,
    val storageRandWriteIops: Double,
    val storageRandReadIops: Double,
    val cpuSingleScore: Int,
    val cpuMultiScore: Int,
    val memoryScore: Int,
    val storageScore: Int,
    val overallScore: Int,
    val tempStartC: Float?,
    val tempEndC: Float?,
    val thermalStart: String,
    val thermalEnd: String,
    val durationMs: Long
)

// ---------------- Optimization ----------------

data class AppUsageItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val firstInstallTime: Long,
    val foregroundMs: Long,
    val foregroundServiceMs: Long,
    val lastUsed: Long,
    val appBytes: Long?,
    val dataBytes: Long?,
    val cacheBytes: Long?
) {
    val totalBytes: Long? get() = if (appBytes == null) null else appBytes + (dataBytes ?: 0) + (cacheBytes ?: 0)
}

data class MediaFileItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val dateModifiedSec: Long
)

data class SystemTuning(
    val animatorScale: Float,
    val transitionScale: Float,
    val windowScale: Float,
    val powerSaveMode: Boolean
)

enum class Severity { INFO, WARNING, CRITICAL }

enum class RecommendationAction(val buttonLabel: String?) {
    NONE(null),
    OPEN_STORAGE_MANAGER("Free up space"),
    OPEN_USAGE_ACCESS("Grant access"),
    OPEN_BATTERY_SAVER("Battery saver"),
    OPEN_DEVELOPER_OPTIONS("Developer options"),
    OPEN_BATTERY_USAGE("Battery usage"),
    OPEN_ABOUT_PHONE("Open settings")
}

data class Recommendation(
    val title: String,
    val detail: String,
    val severity: Severity,
    val action: RecommendationAction = RecommendationAction.NONE
)

// ---------------- Report ----------------

data class ComponentHealth(
    val name: String,
    val score: Int?,
    val condition: String,
    val details: List<String>
)

data class HealthReport(
    val timestamp: Long,
    val overallScore: Int,
    val overallCondition: String,
    val components: List<ComponentHealth>,
    val issues: List<Recommendation>,
    val recommendations: List<Recommendation>,
    val device: DeviceInfo,
    val cpu: CpuInfo,
    val ram: RamInfo,
    val storage: StorageInfo,
    val display: DisplayInfo,
    val battery: BatterySnapshot,
    val thermal: ThermalInfo,
    val latestBenchmark: BenchmarkResult?
)
