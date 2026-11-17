package com.phoneguard.pro.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun bytes(b: Long?): String {
        if (b == null || b < 0) return "—"
        if (b < 1024) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = b / 1024.0
        var i = 0
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024
            i++
        }
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun freq(khz: Long?): String = when {
        khz == null || khz <= 0 -> "—"
        khz >= 1_000_000 -> String.format(Locale.US, "%.2f GHz", khz / 1_000_000.0)
        else -> "${khz / 1000} MHz"
    }

    fun duration(ms: Long): String {
        if (ms <= 0) return "0m"
        val totalMin = ms / 60_000
        val d = totalMin / 1440
        val h = (totalMin % 1440) / 60
        val m = totalMin % 60
        return buildString {
            if (d > 0) append("${d}d ")
            if (h > 0 || d > 0) append("${h}h ")
            append("${m}m")
        }.trim()
    }

    fun hours(h: Float): String = duration((h * 3_600_000f).toLong())

    fun num(v: Double, decimals: Int = 1): String = String.format(Locale.US, "%.${decimals}f", v)
    fun num(v: Float, decimals: Int = 1): String = num(v.toDouble(), decimals)

    fun percent(fraction: Float): String = "${(fraction * 100).toInt()}%"

    fun dateTime(ts: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts))
    fun fileStamp(ts: Long): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(ts))
}

/** Safe reads from sysfs/procfs. Many nodes are blocked by SELinux on modern Android; failures return null. */
object SysFs {
    fun readText(path: String): String? = try {
        File(path).readText().trim().ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    fun readLong(path: String): Long? = readText(path)?.toLongOrNull()
}

fun dirSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}
