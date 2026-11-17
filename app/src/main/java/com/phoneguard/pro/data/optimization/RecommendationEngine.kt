package com.phoneguard.pro.data.optimization

import android.os.PowerManager
import com.phoneguard.pro.data.model.AppUsageItem
import com.phoneguard.pro.data.model.BatterySnapshot
import com.phoneguard.pro.data.model.BenchmarkResult
import com.phoneguard.pro.data.model.RamInfo
import com.phoneguard.pro.data.model.Recommendation
import com.phoneguard.pro.data.model.RecommendationAction
import com.phoneguard.pro.data.model.Severity
import com.phoneguard.pro.data.model.StorageInfo
import com.phoneguard.pro.data.model.SystemTuning
import com.phoneguard.pro.data.model.ThermalInfo
import com.phoneguard.pro.util.Fmt
import java.text.SimpleDateFormat
import java.util.Locale

data class RecommendationInput(
    val ram: RamInfo,
    val storage: StorageInfo,
    val battery: BatterySnapshot,
    val thermal: ThermalInfo,
    val apps: List<AppUsageItem>,
    val usageAccess: Boolean,
    val uptimeMs: Long,
    val securityPatch: String,
    val tuning: SystemTuning,
    val latestBenchmark: BenchmarkResult?
)

/** Rule-based, explainable recommendations. Each rule is derived from a real reading. */
object RecommendationEngine {

    fun build(i: RecommendationInput): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        val now = System.currentTimeMillis()

        // Storage
        val free = i.storage.freeFraction
        if (free < 0.10f) {
            out += Recommendation(
                "Storage almost full",
                "Only ${Fmt.bytes(i.storage.freeBytes)} (${Fmt.percent(free)}) is free. Android slows down when storage is nearly full because there is little room for caches, updates and flash wear-levelling.",
                Severity.CRITICAL, RecommendationAction.OPEN_STORAGE_MANAGER
            )
        } else if (free < 0.20f) {
            out += Recommendation(
                "Storage running low",
                "${Fmt.bytes(i.storage.freeBytes)} (${Fmt.percent(free)}) free. Keeping at least 20% free helps app installs, updates and overall responsiveness.",
                Severity.WARNING, RecommendationAction.OPEN_STORAGE_MANAGER
            )
        }

        // Memory
        if (i.ram.lowMemory || i.ram.usedFraction > 0.88f) {
            out += Recommendation(
                "High memory pressure",
                "${Fmt.percent(i.ram.usedFraction)} of RAM is in use. Close apps you are not using from the Recents screen. Android manages background memory itself; task killers make things worse, so this app does not kill processes.",
                Severity.WARNING
            )
        }

        // Thermal
        when {
            i.thermal.status >= PowerManager.THERMAL_STATUS_SEVERE -> out += Recommendation(
                "Device is overheating (${i.thermal.statusLabel})",
                "The system is throttling performance. Stop heavy apps/games, remove the case, avoid charging while gaming, and let it cool down.",
                Severity.CRITICAL
            )
            i.thermal.status >= PowerManager.THERMAL_STATUS_MODERATE -> out += Recommendation(
                "Device is warm (${i.thermal.statusLabel})",
                "Performance may be reduced to control temperature. Avoid heavy use while charging.",
                Severity.WARNING
            )
        }
        val t = i.battery.temperatureC.value
        if (t != null && t >= 42f) {
            out += Recommendation(
                "High battery temperature (${Fmt.num(t)} °C)",
                "Sustained temperatures above ~40 °C accelerate battery ageing. Unplug fast chargers during heavy use and keep the phone out of direct sun.",
                if (t >= 45f) Severity.CRITICAL else Severity.WARNING
            )
        }

        // Battery health
        if (i.battery.healthReportedGood == false) {
            out += Recommendation(
                "Battery reports a fault: ${i.battery.healthReported}",
                "The battery controller itself reports a problem. Have the battery checked by an authorised service centre.",
                Severity.CRITICAL
            )
        }
        val h = i.battery.estimatedHealthPercent.value
        if (h != null && h < 80) {
            out += Recommendation(
                "Battery capacity appears degraded (~$h%, estimated)",
                "Estimated from the fuel-gauge charge counter vs design capacity. Below ~80% most manufacturers consider replacement. Verify with your manufacturer's diagnostics.",
                Severity.WARNING
            )
        }
        val lvl = i.battery.levelPercent
        if (lvl != null && lvl <= 20 && !i.battery.isCharging && !i.tuning.powerSaveMode) {
            out += Recommendation(
                "Low battery",
                "Battery is at $lvl%. Turning on Battery Saver limits background activity and extends runtime.",
                Severity.INFO, RecommendationAction.OPEN_BATTERY_SAVER
            )
        }

        // Background drain
        val drainers = i.apps.filter { it.foregroundServiceMs >= 3 * 3_600_000L }
            .sortedByDescending { it.foregroundServiceMs }
        if (drainers.isNotEmpty()) {
            val names = drainers.take(3).joinToString { "${it.label} (${Fmt.duration(it.foregroundServiceMs)})" }
            out += Recommendation(
                "Apps running long in the background",
                "These apps ran foreground services for hours in the last day: $names. Restrict their background usage in battery settings if you don't need it.",
                Severity.WARNING, RecommendationAction.OPEN_BATTERY_USAGE
            )
        }

        // Unused apps
        val unused = i.apps.filter {
            !it.isSystem && it.firstInstallTime in 1 until (now - 30 * OptimizationAnalyzer.DAY_MS) &&
                it.lastUsed < now - 30 * OptimizationAnalyzer.DAY_MS
        }
        if (unused.size >= 5) {
            out += Recommendation(
                "${unused.size} apps unused for 30+ days",
                "Uninstalling apps you don't use frees storage and removes their background jobs. See the list in the Optimize tab.",
                Severity.INFO
            )
        }

        // Responsiveness (perceived)
        if (i.tuning.animatorScale >= 1f || i.tuning.transitionScale >= 1f) {
            out += Recommendation(
                "Make the UI feel snappier",
                "Setting Window, Transition and Animator scale to 0.5× in Developer options shortens animations. This changes perceived speed only; it does not make the CPU faster.",
                Severity.INFO, RecommendationAction.OPEN_DEVELOPER_OPTIONS
            )
        }

        if (i.uptimeMs > 7 * OptimizationAnalyzer.DAY_MS) {
            out += Recommendation(
                "Restart recommended",
                "The phone has been running for ${Fmt.duration(i.uptimeMs)}. A restart clears leaked memory and stuck services. Apps cannot reboot the device; use the power menu.",
                Severity.INFO
            )
        }

        // Security patch age
        try {
            val patch = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(i.securityPatch)
            if (patch != null && now - patch.time > 365 * OptimizationAnalyzer.DAY_MS) {
                out += Recommendation(
                    "Security patch is over a year old (${i.securityPatch})",
                    "Check for system updates. Outdated firmware can contain known security and stability bugs.",
                    Severity.WARNING, RecommendationAction.OPEN_ABOUT_PHONE
                )
            }
        } catch (e: Exception) { /* unparseable patch string */ }

        if (!i.usageAccess) {
            out += Recommendation(
                "Enable Usage Access for deeper analysis",
                "Lets Phone Guard Pro read per-app screen time, background service time and app sizes. Data stays on the device.",
                Severity.INFO, RecommendationAction.OPEN_USAGE_ACCESS
            )
        }

        if (i.latestBenchmark == null) {
            out += Recommendation(
                "Run a benchmark",
                "A benchmark measures real CPU, memory and storage performance and is included in the CPU condition of the health report.",
                Severity.INFO
            )
        }

        return out.sortedByDescending { it.severity.ordinal }
    }
}
