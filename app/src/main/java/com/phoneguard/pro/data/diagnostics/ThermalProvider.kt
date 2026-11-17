package com.phoneguard.pro.data.diagnostics

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.phoneguard.pro.data.model.ThermalInfo
import com.phoneguard.pro.data.model.ThermalZone
import com.phoneguard.pro.util.SysFs
import java.io.File

/**
 * Thermal state from PowerManager (official API) plus thermal zones where sysfs is readable.
 * Many devices block /sys/class/thermal for apps; in that case only the official status is shown.
 */
class ThermalProvider(context: Context) {
    private val pm = context.getSystemService(PowerManager::class.java)
    private var lastHeadroomAt = 0L
    private var lastHeadroom: Float? = null

    fun read(includeZones: Boolean = true): ThermalInfo {
        val status = pm.currentThermalStatus
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) headroom() else null
        return ThermalInfo(
            status = status,
            statusLabel = statusLabel(status),
            headroom = headroom,
            zones = if (includeZones) readZones() else emptyList()
        )
    }

    /** getThermalHeadroom must not be called more than about once per second. */
    @Synchronized
    private fun headroom(): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val now = SystemClock.elapsedRealtime()
        if (now - lastHeadroomAt < 1500) return lastHeadroom
        lastHeadroomAt = now
        val h = pm.getThermalHeadroom(10)
        lastHeadroom = if (h.isNaN()) null else h
        return lastHeadroom
    }

    private fun readZones(): List<ThermalZone> {
        val dirs = File("/sys/class/thermal").listFiles()?.filter { it.name.startsWith("thermal_zone") } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val type = SysFs.readText("${dir.path}/type") ?: return@mapNotNull null
            val raw = SysFs.readLong("${dir.path}/temp") ?: return@mapNotNull null
            val c = if (raw > 1000 || raw < -1000) raw / 1000f else raw.toFloat()
            if (c in 5f..125f) ThermalZone(type, c) else null
        }.sortedByDescending { it.tempC }.take(12)
    }

    companion object {
        fun statusLabel(s: Int) = when (s) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unknown"
        }
    }
}
