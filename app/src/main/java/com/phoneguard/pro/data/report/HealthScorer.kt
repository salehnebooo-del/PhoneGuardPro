package com.phoneguard.pro.data.report

import android.os.PowerManager
import com.phoneguard.pro.data.model.BatterySnapshot
import com.phoneguard.pro.data.model.BenchmarkResult
import com.phoneguard.pro.data.model.ComponentHealth
import com.phoneguard.pro.data.model.RamInfo
import com.phoneguard.pro.data.model.StorageInfo
import com.phoneguard.pro.data.model.ThermalInfo
import com.phoneguard.pro.util.Fmt
import kotlin.math.roundToInt

/**
 * Transparent scoring. Each component score is derived from real readings using the rules below;
 * components without data are excluded from the weighted average instead of being guessed.
 */
class HealthScorer {

    data class Result(val overall: Int, val components: List<ComponentHealth>)

    fun evaluate(
        ram: RamInfo,
        storage: StorageInfo,
        battery: BatterySnapshot,
        thermal: ThermalInfo,
        bench: BenchmarkResult?
    ): Result {
        val comps = listOf(
            cpu(bench, thermal),
            ram(ram),
            storage(storage),
            battery(battery),
            temperature(battery, thermal)
        )
        val weights = mapOf("CPU" to 0.20, "RAM" to 0.20, "Storage" to 0.20, "Battery" to 0.25, "Temperature" to 0.15)
        var sum = 0.0
        var wsum = 0.0
        comps.forEach { c ->
            val s = c.score ?: return@forEach
            val w = weights[c.name] ?: 0.0
            sum += s * w
            wsum += w
        }
        val overall = if (wsum > 0) (sum / wsum).roundToInt().coerceIn(0, 100) else 0
        return Result(overall, comps)
    }

    private fun cpu(bench: BenchmarkResult?, thermal: ThermalInfo): ComponentHealth {
        val details = mutableListOf<String>()
        if (bench == null) {
            details += "No benchmark yet — run one to rate CPU performance."
            details += "Thermal status: ${thermal.statusLabel}"
            return ComponentHealth("CPU", null, "Not measured", details)
        }
        var s = (bench.cpuSingleScore + bench.cpuMultiScore) / 2
        details += "Single-core score ${bench.cpuSingleScore}, multi-core ${bench.cpuMultiScore} (benchmark ${Fmt.dateTime(bench.timestamp)})"
        if (thermal.status >= PowerManager.THERMAL_STATUS_SEVERE) {
            s -= 30; details += "Severe thermal throttling active now"
        } else if (thermal.status >= PowerManager.THERMAL_STATUS_MODERATE) {
            s -= 15; details += "Moderate thermal throttling active now"
        }
        s = s.coerceIn(0, 100)
        return ComponentHealth("CPU", s, condition(s), details)
    }

    private fun ram(r: RamInfo): ComponentHealth {
        val avail = 1f - r.usedFraction
        val s = when {
            r.lowMemory -> 35
            avail >= 0.35f -> 100
            else -> (40 + avail / 0.35f * 60).roundToInt()
        }
        return ComponentHealth(
            "RAM", s, condition(s),
            listOf(
                "${Fmt.bytes(r.availableBytes)} available of ${Fmt.bytes(r.totalBytes)} (${Fmt.percent(r.usedFraction)} used)",
                if (r.lowMemory) "System reports low-memory state" else "No low-memory state reported"
            )
        )
    }

    private fun storage(st: StorageInfo): ComponentHealth {
        val f = st.freeFraction
        val s = when {
            f >= 0.25f -> 100
            f >= 0.10f -> (50 + (f - 0.10f) / 0.15f * 50).roundToInt()
            else -> (f / 0.10f * 50).roundToInt()
        }.coerceIn(0, 100)
        return ComponentHealth(
            "Storage", s, condition(s),
            listOf("${Fmt.bytes(st.freeBytes)} free of ${Fmt.bytes(st.totalBytes)} (${Fmt.percent(f)} free)")
        )
    }

    private fun battery(b: BatterySnapshot): ComponentHealth {
        val details = mutableListOf("Reported health flag: ${b.healthReported}")
        var s: Int? = when (b.healthReportedGood) {
            true -> 100
            false -> 25
            null -> null
        }
        val est = b.estimatedHealthPercent.value
        if (est != null) {
            val fromCapacity = when {
                est >= 90 -> 100
                est >= 80 -> 60 + (est - 80) * 4
                else -> (est * 0.7).roundToInt()
            }
            s = if (s == null) fromCapacity else minOf(s, fromCapacity)
            details += "Estimated capacity health ~$est% (estimated)"
        } else {
            details += "Capacity health not estimable on this device"
        }
        b.cycleCount.value?.let { details += "Charge cycles: $it" }
        return ComponentHealth("Battery", s, s?.let { condition(it) } ?: "Unknown", details)
    }

    private fun temperature(b: BatterySnapshot, thermal: ThermalInfo): ComponentHealth {
        val t = b.temperatureC.value
        var s: Int? = when {
            t == null -> null
            t <= 35f -> 100
            t <= 40f -> 85
            t <= 45f -> 60
            else -> 30
        }
        if (thermal.status >= PowerManager.THERMAL_STATUS_SEVERE) s = minOf(s ?: 100, 25)
        else if (thermal.status >= PowerManager.THERMAL_STATUS_MODERATE) s = minOf(s ?: 100, 60)
        val details = mutableListOf("System thermal status: ${thermal.statusLabel}")
        if (t != null) details += "Battery temperature ${Fmt.num(t)} °C"
        return ComponentHealth("Temperature", s, s?.let { condition(it) } ?: "Unknown", details)
    }

    companion object {
        fun condition(score: Int?): String = when {
            score == null -> "Unknown"
            score >= 85 -> "Excellent"
            score >= 70 -> "Good"
            score >= 50 -> "Fair"
            else -> "Poor"
        }
    }
}
