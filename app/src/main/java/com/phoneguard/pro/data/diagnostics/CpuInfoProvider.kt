package com.phoneguard.pro.data.diagnostics

import android.os.Build
import com.phoneguard.pro.data.model.CoreInfo
import com.phoneguard.pro.data.model.CpuInfo
import com.phoneguard.pro.util.Fmt
import com.phoneguard.pro.util.SysFs
import java.io.File

/**
 * Reads CPU information from Build, /proc/cpuinfo and /sys/devices/system/cpu.
 *
 * Android restriction: since Android 8, /proc/stat is blocked for apps by SELinux, so
 * system-wide CPU *utilisation* cannot be measured by a normal app. We expose per-core
 * current frequency relative to maximum instead and label it clearly as such.
 */
class CpuInfoProvider {

    private val cpuDirRegex = Regex("cpu\\d+")

    fun read(): CpuInfo {
        val runtimeCores = Runtime.getRuntime().availableProcessors()
        val sysCores = File("/sys/devices/system/cpu").listFiles()?.count { it.name.matches(cpuDirRegex) } ?: 0
        val count = maxOf(runtimeCores, sysCores)

        val cores = (0 until count).map { i ->
            val base = "/sys/devices/system/cpu/cpu$i"
            CoreInfo(
                index = i,
                curFreqKhz = SysFs.readLong("$base/cpufreq/scaling_cur_freq"),
                minFreqKhz = SysFs.readLong("$base/cpufreq/cpuinfo_min_freq"),
                maxFreqKhz = SysFs.readLong("$base/cpufreq/cpuinfo_max_freq"),
                online = SysFs.readText("$base/online")?.let { it == "1" } ?: true
            )
        }

        val cpuinfo = parseCpuInfo()
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        } else null
        val socVendor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        } else null

        val model = socModel
            ?: cpuinfo["Hardware"]
            ?: cpuinfo["model name"]
            ?: Build.HARDWARE

        val loads = cores.mapNotNull { c ->
            val cur = c.curFreqKhz
            val max = c.maxFreqKhz
            if (c.online && cur != null && max != null && max > 0) cur.toFloat() / max else null
        }
        val freqLoad = if (loads.isEmpty()) null else (loads.average() * 100).toFloat()

        val clusters = cores.mapNotNull { it.maxFreqKhz }
            .groupingBy { it }.eachCount()
            .toSortedMap()
            .map { (f, n) -> "$n × ${Fmt.freq(f)}" }
            .joinToString(" + ")
            .ifEmpty { "Frequency data not exposed" }

        return CpuInfo(
            model = model,
            vendor = socVendor,
            abis = Build.SUPPORTED_ABIS.toList(),
            arch = System.getProperty("os.arch") ?: "unknown",
            coreCount = count,
            cores = cores,
            clusterSummary = clusters,
            frequencyLoadPercent = freqLoad,
            governor = SysFs.readText("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        )
    }

    private fun parseCpuInfo(): Map<String, String> = try {
        File("/proc/cpuinfo").readLines().mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
        }.filter { it.second.isNotEmpty() }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }
}
