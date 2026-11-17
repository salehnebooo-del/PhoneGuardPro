package com.phoneguard.pro.data.report

import com.phoneguard.pro.data.model.HealthReport
import com.phoneguard.pro.data.model.Severity
import com.phoneguard.pro.data.optimization.RecommendationEngine
import com.phoneguard.pro.data.optimization.RecommendationInput
import com.phoneguard.pro.di.AppContainer

/** Collects a full, real snapshot of the device and turns it into a HealthReport. Call off the main thread. */
class HealthReportBuilder(private val c: AppContainer) {
    fun build(): HealthReport {
        val device = c.device.read()
        val cpu = c.cpu.read()
        val ram = c.memory.read()
        val storage = c.storage.read()
        val display = c.display.read()
        val battery = c.battery.snapshot()
        val thermal = c.thermal.read(includeZones = true)
        val bench = c.history.latest()
        val usageAccess = c.optimization.hasUsageAccess()
        val apps = if (usageAccess) c.optimization.appUsage(includeStorage = false) else emptyList()

        val scored = c.healthScorer.evaluate(ram, storage, battery, thermal, bench)
        val recs = RecommendationEngine.build(
            RecommendationInput(
                ram = ram, storage = storage, battery = battery, thermal = thermal, apps = apps,
                usageAccess = usageAccess, uptimeMs = device.uptimeMs, securityPatch = device.securityPatch,
                tuning = c.optimization.systemTuning(), latestBenchmark = bench
            )
        )
        return HealthReport(
            timestamp = System.currentTimeMillis(),
            overallScore = scored.overall,
            overallCondition = HealthScorer.condition(scored.overall),
            components = scored.components,
            issues = recs.filter { it.severity != Severity.INFO },
            recommendations = recs,
            device = device,
            cpu = cpu,
            ram = ram,
            storage = storage,
            display = display,
            battery = battery,
            thermal = thermal,
            latestBenchmark = bench
        )
    }
}
