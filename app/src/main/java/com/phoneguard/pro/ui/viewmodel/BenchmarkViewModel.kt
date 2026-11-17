package com.phoneguard.pro.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoneguard.pro.data.benchmark.ThermalAbortException
import com.phoneguard.pro.data.model.BenchmarkProgress
import com.phoneguard.pro.data.model.BenchmarkResult
import com.phoneguard.pro.di.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BenchmarkUiState(
    val running: Boolean = false,
    val progress: BenchmarkProgress? = null,
    val result: BenchmarkResult? = null,
    val error: String? = null,
    val tempTrace: List<Float> = emptyList()
)

class BenchmarkViewModel(app: Application) : AndroidViewModel(app) {
    private val c = container
    private val _state = MutableStateFlow(BenchmarkUiState(result = c.history.latest()))
    val state: StateFlow<BenchmarkUiState> = _state.asStateFlow()
    val history: StateFlow<List<BenchmarkResult>> = c.history.history
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        _state.value = BenchmarkUiState(running = true, result = _state.value.result)
        job = viewModelScope.launch {
            try {
                val r = c.benchmark.run { p ->
                    _state.update { s ->
                        s.copy(progress = p, tempTrace = p.tempC?.let { t -> s.tempTrace + t } ?: s.tempTrace)
                    }
                }
                c.history.add(r)
                _state.update { it.copy(running = false, progress = null, result = r, error = null) }
            } catch (e: CancellationException) {
                _state.update { it.copy(running = false, progress = null, error = "Benchmark cancelled.") }
                throw e
            } catch (e: ThermalAbortException) {
                _state.update { it.copy(running = false, progress = null, error = e.message) }
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(running = false, progress = null, error = "Not enough free memory to run the memory test. Close some apps and retry.") }
            } catch (e: Exception) {
                _state.update { it.copy(running = false, progress = null, error = "Benchmark failed: ${e.message}") }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun clearHistory() = c.history.clear()
}
