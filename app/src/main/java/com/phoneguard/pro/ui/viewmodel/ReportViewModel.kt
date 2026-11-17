package com.phoneguard.pro.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoneguard.pro.data.model.HealthReport
import com.phoneguard.pro.di.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReportState(
    val generating: Boolean = false,
    val exporting: Boolean = false,
    val report: HealthReport? = null,
    val shareFile: File? = null,
    val message: String? = null
)

class ReportViewModel(app: Application) : AndroidViewModel(app) {
    private val c = container
    private val _state = MutableStateFlow(ReportState())
    val state: StateFlow<ReportState> = _state.asStateFlow()

    init { generate() }

    fun generate() {
        viewModelScope.launch {
            _state.update { it.copy(generating = true) }
            val r = withContext(Dispatchers.IO) { c.reportBuilder.build() }
            _state.update { it.copy(generating = false, report = r) }
        }
    }

    fun exportAndShare() {
        val r = _state.value.report ?: return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true) }
            try {
                val f = withContext(Dispatchers.IO) { c.pdfGenerator.generate(r) }
                _state.update { it.copy(exporting = false, shareFile = f) }
            } catch (e: Exception) {
                _state.update { it.copy(exporting = false, message = "PDF export failed: ${e.message}") }
            }
        }
    }

    fun saveToDownloads() {
        val r = _state.value.report ?: return
        viewModelScope.launch {
            _state.update { it.copy(exporting = true) }
            val msg = try {
                withContext(Dispatchers.IO) {
                    val f = c.pdfGenerator.generate(r)
                    if (c.pdfGenerator.saveToDownloads(f) != null) "Saved to Downloads/${f.name}" else "Could not save to Downloads"
                }
            } catch (e: Exception) {
                "Save failed: ${e.message}"
            }
            _state.update { it.copy(exporting = false, message = msg) }
        }
    }

    fun shareIntent(file: File): Intent = c.pdfGenerator.shareIntent(file)
    fun onShareHandled() = _state.update { it.copy(shareFile = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}
