package com.phoneguard.pro.data.benchmark

import android.content.Context
import com.phoneguard.pro.data.model.BenchmarkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Stores the last 30 benchmark results locally (never uploaded). */
class BenchmarkHistoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bench_history", Context.MODE_PRIVATE)
    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<BenchmarkResult>> = _history.asStateFlow()

    fun latest(): BenchmarkResult? = _history.value.lastOrNull()

    @Synchronized
    fun add(r: BenchmarkResult) {
        val list = (_history.value + r).takeLast(MAX)
        save(list)
        _history.value = list
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY).apply()
        _history.value = emptyList()
    }

    private fun save(list: List<BenchmarkResult>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<BenchmarkResult> = try {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun toJson(r: BenchmarkResult) = JSONObject().apply {
        put("timestamp", r.timestamp)
        put("threads", r.threads)
        put("cpuSingleMops", r.cpuSingleMops)
        put("cpuSingleMflops", r.cpuSingleMflops)
        put("cpuMultiMops", r.cpuMultiMops)
        put("cpuMultiMflops", r.cpuMultiMflops)
        put("memCopyMBps", r.memCopyMBps)
        put("memLatencyNs", r.memLatencyNs)
        put("seqW", r.storageSeqWriteMBps)
        put("seqR", r.storageSeqReadMBps)
        put("randW", r.storageRandWriteIops)
        put("randR", r.storageRandReadIops)
        put("sCpu1", r.cpuSingleScore)
        put("sCpuN", r.cpuMultiScore)
        put("sMem", r.memoryScore)
        put("sSto", r.storageScore)
        put("sAll", r.overallScore)
        put("tStart", r.tempStartC?.toDouble() ?: JSONObject.NULL)
        put("tEnd", r.tempEndC?.toDouble() ?: JSONObject.NULL)
        put("thStart", r.thermalStart)
        put("thEnd", r.thermalEnd)
        put("dur", r.durationMs)
    }

    private fun fromJson(o: JSONObject) = BenchmarkResult(
        timestamp = o.getLong("timestamp"),
        threads = o.getInt("threads"),
        cpuSingleMops = o.getDouble("cpuSingleMops"),
        cpuSingleMflops = o.getDouble("cpuSingleMflops"),
        cpuMultiMops = o.getDouble("cpuMultiMops"),
        cpuMultiMflops = o.getDouble("cpuMultiMflops"),
        memCopyMBps = o.getDouble("memCopyMBps"),
        memLatencyNs = o.getDouble("memLatencyNs"),
        storageSeqWriteMBps = o.getDouble("seqW"),
        storageSeqReadMBps = o.getDouble("seqR"),
        storageRandWriteIops = o.getDouble("randW"),
        storageRandReadIops = o.getDouble("randR"),
        cpuSingleScore = o.getInt("sCpu1"),
        cpuMultiScore = o.getInt("sCpuN"),
        memoryScore = o.getInt("sMem"),
        storageScore = o.getInt("sSto"),
        overallScore = o.getInt("sAll"),
        tempStartC = if (o.isNull("tStart")) null else o.getDouble("tStart").toFloat(),
        tempEndC = if (o.isNull("tEnd")) null else o.getDouble("tEnd").toFloat(),
        thermalStart = o.optString("thStart", "Unknown"),
        thermalEnd = o.optString("thEnd", "Unknown"),
        durationMs = o.optLong("dur", 0L)
    )

    private companion object {
        const val KEY = "results"
        const val MAX = 30
    }
}
