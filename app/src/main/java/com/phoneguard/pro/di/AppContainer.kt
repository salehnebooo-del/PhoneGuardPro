package com.phoneguard.pro.di

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.phoneguard.pro.PhoneGuardApp
import com.phoneguard.pro.data.battery.BatteryAnalyzer
import com.phoneguard.pro.data.benchmark.BenchmarkEngine
import com.phoneguard.pro.data.benchmark.BenchmarkHistoryRepository
import com.phoneguard.pro.data.diagnostics.CpuInfoProvider
import com.phoneguard.pro.data.diagnostics.DeviceInfoProvider
import com.phoneguard.pro.data.diagnostics.DisplayInfoProvider
import com.phoneguard.pro.data.diagnostics.MemoryInfoProvider
import com.phoneguard.pro.data.diagnostics.NetworkInfoProvider
import com.phoneguard.pro.data.diagnostics.SensorInfoProvider
import com.phoneguard.pro.data.diagnostics.StorageInfoProvider
import com.phoneguard.pro.data.diagnostics.ThermalProvider
import com.phoneguard.pro.data.optimization.OptimizationAnalyzer
import com.phoneguard.pro.data.report.HealthReportBuilder
import com.phoneguard.pro.data.report.HealthScorer
import com.phoneguard.pro.data.report.PdfReportGenerator

/**
 * Minimal manual dependency container (no DI framework needed).
 * All providers are stateless or thread-safe and live for the process lifetime.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val cpu = CpuInfoProvider()
    val memory = MemoryInfoProvider(app)
    val storage = StorageInfoProvider(app)
    val display = DisplayInfoProvider(app)
    val device = DeviceInfoProvider()
    val sensors = SensorInfoProvider(app)
    val network = NetworkInfoProvider(app)
    val thermal = ThermalProvider(app)
    val battery = BatteryAnalyzer(app)
    val benchmark = BenchmarkEngine(app, memory, thermal, battery)
    val history = BenchmarkHistoryRepository(app)
    val optimization = OptimizationAnalyzer(app)
    val healthScorer = HealthScorer()
    val pdfGenerator = PdfReportGenerator(app)
    val reportBuilder: HealthReportBuilder by lazy { HealthReportBuilder(this) }
}

val AndroidViewModel.container: AppContainer
    get() = getApplication<PhoneGuardApp>().container
