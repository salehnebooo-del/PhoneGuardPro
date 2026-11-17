package com.phoneguard.pro.data.battery

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.phoneguard.pro.data.model.BatterySample
import com.phoneguard.pro.data.model.BatterySnapshot
import com.phoneguard.pro.data.model.DataSource
import com.phoneguard.pro.data.model.DischargeAnalysis
import com.phoneguard.pro.data.model.Sourced
import kotlin.math.abs

/**
 * Battery analysis built only on public Android APIs (plus one clearly-labelled unofficial source).
 *
 * What Android exposes to normal apps:
 *  - ACTION_BATTERY_CHANGED: level, status, plug type, health flag, temperature, voltage, chemistry,
 *    and (Android 14+) cycle count on devices whose HAL reports it.
 *  - BatteryManager properties: charge counter (µAh), instantaneous/average current, energy counter.
 *    Support varies per device; unsupported values are shown as "Unavailable".
 *
 * What it does NOT expose: true state-of-health % (system-only on Android 14), fast-charge protocol,
 * and per-app energy usage (BATTERY_STATS is a system permission).
 */
class BatteryAnalyzer(private val context: Context) {

    private val bm: BatteryManager = context.getSystemService(BatteryManager::class.java)
    private val designCapacity: Int? by lazy { readDesignCapacity() }

    fun snapshot(): BatterySnapshot {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct: Int? = if (level >= 0 && scale > 0) level * 100 / scale
        else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val status = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val charging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val plugType = when (plugged) {
            0 -> "Not plugged in"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            8 -> "Dock"
            else -> "Other"
        }

        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val (healthLabel, healthGood) = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good" to true
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat" to false
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead" to false
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage" to false
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure" to false
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold" to false
            else -> "Unknown" to null
        }

        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temperature: Sourced<Float> = if (tempRaw != Int.MIN_VALUE && tempRaw > -400) {
            Sourced(tempRaw / 10f, DataSource.REPORTED)
        } else Sourced.unavailable("Battery temperature not reported")

        val voltRaw = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val voltage: Sourced<Int> = when {
            voltRaw <= 0 -> Sourced.unavailable("Voltage not reported")
            voltRaw < 100 -> Sourced(voltRaw * 1000, DataSource.REPORTED, "Device reports volts; converted to mV")
            else -> Sourced(voltRaw, DataSource.REPORTED)
        }

        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() }

        // Android 14+: android.os.extra.CYCLE_COUNT (BatteryManager.EXTRA_CYCLE_COUNT)
        val cycleRaw = intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
        val cycles: Sourced<Int> = if (cycleRaw > 0) Sourced(cycleRaw, DataSource.REPORTED)
        else Sourced.unavailable("Cycle count is exposed only on Android 14+ devices whose battery HAL reports it")

        val current = normalizeCurrentMa(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
        val currentAvg = normalizeCurrentMa(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE))
        val currentS: Sourced<Int> = current?.let { Sourced(it, DataSource.REPORTED, "Instantaneous fuel-gauge reading") }
            ?: Sourced.unavailable("Current sensor not exposed by this device")
        val currentAvgS: Sourced<Int> = currentAvg?.let { Sourced(it, DataSource.REPORTED) }
            ?: Sourced.unavailable("Average current not exposed")

        val voltageMv = voltage.value
        val power: Sourced<Float> = if (current != null && voltageMv != null) {
            Sourced(voltageMv.toFloat() * current / 1_000_000f, DataSource.CALCULATED, "Voltage × current")
        } else Sourced.unavailable("Requires voltage and current readings")

        val chargeCounterRaw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val chargeCounterMah: Int? = when {
            chargeCounterRaw == Int.MIN_VALUE || chargeCounterRaw <= 0 -> null
            chargeCounterRaw < 30_000 -> chargeCounterRaw              // device reports mAh
            else -> chargeCounterRaw / 1000                             // µAh → mAh
        }
        val chargeCounterS: Sourced<Int> = chargeCounterMah?.let { Sourced(it, DataSource.REPORTED, "Remaining charge from fuel gauge") }
            ?: Sourced.unavailable("Charge counter not exposed by this device")

        val energyRaw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        val energy: Sourced<Float> = if (energyRaw != Long.MIN_VALUE && energyRaw > 0) {
            Sourced(energyRaw / 1e9f, DataSource.REPORTED)
        } else Sourced.unavailable("Energy counter not exposed")

        val design: Sourced<Int> = designCapacity?.let {
            Sourced(it, DataSource.REPORTED, "From the system power profile (unofficial source, may be generic)")
        } ?: Sourced.unavailable("Design capacity not accessible on this device")

        val estFull: Int? = if (chargeCounterMah != null && pct != null && pct >= 15) {
            (chargeCounterMah * 100 / pct).takeIf { it in 500..30_000 }
        } else null
        val estFullS: Sourced<Int> = estFull?.let {
            Sourced(it, DataSource.ESTIMATED, "Charge counter ÷ level. Most accurate between 50–100%")
        } ?: Sourced.unavailable(if (chargeCounterMah == null) "Needs charge counter support" else "Needs battery level ≥ 15%")

        val designMah = designCapacity
        val estHealth: Sourced<Int> = if (estFull != null && designMah != null) {
            Sourced(
                (estFull * 100 / designMah).coerceIn(1, 130),
                DataSource.ESTIMATED,
                "Estimated full capacity ÷ design capacity. Accuracy depends on fuel-gauge calibration"
            )
        } else Sourced.unavailable("Requires both charge counter and design capacity")

        val powerW = power.value
        val chargingClass: Sourced<String> = if (charging && powerW != null) {
            val w = powerW
            val cls = when {
                w <= 7.5f -> "Standard charging (≤ 7.5 W)"
                w <= 18f -> "Fast charging (7.5–18 W)"
                else -> "High-power fast charging (> 18 W)"
            }
            Sourced(cls, DataSource.ESTIMATED, "Classified from measured power. Charging protocol (USB-PD, QC, VOOC…) is not exposed by Android")
        } else Sourced.unavailable(if (!charging) "Not charging" else "Power reading unavailable")


        val ttf = bm.computeChargeTimeRemaining()
        val ttfS: Sourced<Long> = if (charging && ttf > 0) Sourced(ttf, DataSource.REPORTED, "System estimate")
        else Sourced.unavailable(if (charging) "System has no estimate yet" else "Not charging")

        return BatterySnapshot(
            timestamp = System.currentTimeMillis(),
            levelPercent = pct,
            status = status,
            isCharging = charging,
            isPlugged = plugged != 0,
            plugType = plugType,
            healthReported = healthLabel,
            healthReportedGood = healthGood,
            technology = technology,
            temperatureC = temperature,
            voltageMv = voltage,
            currentMa = currentS,
            averageCurrentMa = currentAvgS,
            powerW = power,
            chargeCounterMah = chargeCounterS,
            energyWh = energy,
            designCapacityMah = design,
            estimatedFullCapacityMah = estFullS,
            estimatedHealthPercent = estHealth,
            cycleCount = cycles,
            chargingClass = chargingClass,
            chargeTimeRemainingMs = ttfS
        )
    }

    /**
     * Discharge analysis from samples collected while the app is open (no background polling).
     * Prefers the real level slope (MEASURED over time); falls back to current-based estimate.
     */
    fun analyzeDischarge(samples: List<BatterySample>, snap: BatterySnapshot): DischargeAnalysis {
        val discharging = samples.takeLastWhile { !it.charging }
        val spanMs = if (discharging.size >= 2) discharging.last().timestamp - discharging.first().timestamp else 0L
        val minutes = spanMs / 60_000f

        var rate: Sourced<Float> = Sourced.unavailable(
            if (snap.isCharging) "Device is charging" else "Keep this screen open for a few minutes to measure"
        )
        if (!snap.isCharging) {
            val levelChanged = discharging.size >= 2 && discharging.first().level != discharging.last().level
            if (minutes >= 5f && levelChanged) {
                val slope = regressionSlopePerHour(discharging)
                if (slope < 0) {
                    rate = Sourced(-slope, DataSource.MEASURED, "Level trend over ${minutes.toInt()} min (includes this app's own usage)")
                }
            } else {
                val cur = snap.currentMa.value
                val full = snap.estimatedFullCapacityMah.value ?: snap.designCapacityMah.value
                if (cur != null && full != null && full > 0) {
                    rate = Sourced(cur * 100f / full, DataSource.ESTIMATED, "From instantaneous current; fluctuates with load")
                }
            }
        }
        val remaining: Sourced<Float> = run {
            val r = rate.value
            val lvl = snap.levelPercent
            if (r != null && r > 0.05f && lvl != null) {
                Sourced(lvl / r, DataSource.ESTIMATED, "At the current usage pattern")
            } else Sourced.unavailable(rate.note)
        }
        return DischargeAnalysis(rate, remaining, minutes)
    }

    private fun regressionSlopePerHour(s: List<BatterySample>): Float {
        val t0 = s.first().timestamp
        val xs = s.map { (it.timestamp - t0) / 3_600_000.0 }
        val ys = s.map { it.level.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        return if (den == 0.0) 0f else (num / den).toFloat()
    }

    /**
     * Android documents CURRENT_NOW in µA, but some vendors report mA and the sign convention
     * varies. We return the absolute value in mA; direction is derived from the charging status.
     */
    private fun normalizeCurrentMa(raw: Int): Int? {
        if (raw == Int.MIN_VALUE || raw == 0) return null
        val a = abs(raw)
        return if (a < 20_000) a else a / 1000
    }

    /** Unofficial: framework PowerProfile via reflection. May be blocked or return a generic value. */
    @SuppressLint("PrivateApi")
    private fun readDesignCapacity(): Int? = try {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val profile = cls.getConstructor(Context::class.java).newInstance(context)
        val cap = cls.getMethod("getBatteryCapacity").invoke(profile) as Double
        cap.toInt().takeIf { it in 1001..30_000 }
    } catch (t: Throwable) {
        null
    }
}
