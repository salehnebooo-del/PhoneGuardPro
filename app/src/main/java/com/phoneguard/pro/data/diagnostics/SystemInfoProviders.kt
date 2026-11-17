package com.phoneguard.pro.data.diagnostics

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.util.DisplayMetrics
import android.view.Display
import com.phoneguard.pro.data.model.DeviceInfo
import com.phoneguard.pro.data.model.DisplayInfo
import com.phoneguard.pro.data.model.RamInfo
import com.phoneguard.pro.data.model.StorageInfo
import kotlin.math.pow
import kotlin.math.sqrt

class MemoryInfoProvider(context: Context) {
    private val am = context.getSystemService(ActivityManager::class.java)

    fun read(): RamInfo {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return RamInfo(
            totalBytes = mi.totalMem,
            availableBytes = mi.availMem,
            thresholdBytes = mi.threshold,
            lowMemory = mi.lowMemory,
            isLowRamDevice = am.isLowRamDevice
        )
    }
}

class StorageInfoProvider(private val context: Context) {
    /** Uses StorageStatsManager (includes the system partition, matches Settings). Falls back to StatFs. */
    fun read(): StorageInfo = try {
        val ssm = context.getSystemService(StorageStatsManager::class.java)
        StorageInfo(
            totalBytes = ssm.getTotalBytes(StorageManager.UUID_DEFAULT),
            freeBytes = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
        )
    } catch (e: Exception) {
        val st = StatFs(Environment.getDataDirectory().path)
        StorageInfo(st.totalBytes, st.availableBytes)
    }
}

class DisplayInfoProvider(private val context: Context) {
    @Suppress("DEPRECATION")
    fun read(): DisplayInfo {
        val dm = context.getSystemService(DisplayManager::class.java)
        val d: Display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val real = DisplayMetrics()
        d.getRealMetrics(real)
        val mode = d.mode
        val rates = d.supportedModes.map { it.refreshRate }.distinct().sorted()
        val hdr = d.hdrCapabilities?.supportedHdrTypes?.map { hdrName(it) } ?: emptyList()
        val diag = if (real.xdpi > 1f && real.ydpi > 1f) {
            sqrt((real.widthPixels / real.xdpi).pow(2) + (real.heightPixels / real.ydpi).pow(2)).toDouble()
        } else null
        return DisplayInfo(
            name = d.name ?: "Built-in display",
            widthPx = mode.physicalWidth,
            heightPx = mode.physicalHeight,
            densityDpi = real.densityDpi,
            refreshRateHz = d.refreshRate,
            supportedRefreshRates = rates,
            hdrTypes = hdr,
            diagonalInches = diag
        )
    }

    private fun hdrName(t: Int) = when (t) {
        1 -> "Dolby Vision"
        2 -> "HDR10"
        3 -> "HLG"
        4 -> "HDR10+"
        else -> "Type $t"
    }
}

class DeviceInfoProvider {
    fun read(): DeviceInfo = DeviceInfo(
        manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
        brand = Build.BRAND,
        model = Build.MODEL,
        device = Build.DEVICE,
        product = Build.PRODUCT,
        board = Build.BOARD,
        hardware = Build.HARDWARE,
        androidVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH,
        buildId = Build.DISPLAY,
        kernel = System.getProperty("os.version") ?: "unknown",
        uptimeMs = SystemClock.elapsedRealtime(),
        fingerprint = Build.FINGERPRINT
    )
}
