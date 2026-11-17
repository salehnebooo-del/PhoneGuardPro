package com.phoneguard.pro.data.benchmark

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import com.phoneguard.pro.data.battery.BatteryAnalyzer
import com.phoneguard.pro.data.diagnostics.MemoryInfoProvider
import com.phoneguard.pro.data.diagnostics.ThermalProvider
import com.phoneguard.pro.data.model.BenchmarkProgress
import com.phoneguard.pro.data.model.BenchmarkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Random
import kotlin.math.log2
import kotlin.math.roundToInt

class ThermalAbortException(message: String) : Exception(message)

/**
 * Real, on-device benchmark. Every number is measured with System.nanoTime() around real work.
 *
 * Scores (0–100) use a logarithmic scale relative to mid-range reference values in
 * [BenchmarkReferences]: equal to reference = 50, 2× = 75, 4× = 100, ½× = 25.
 * Raw measurements are always shown next to the score.
 */
class BenchmarkEngine(
    private val context: Context,
    private val memory: MemoryInfoProvider,
    private val thermal: ThermalProvider,
    private val battery: BatteryAnalyzer
) {
    @Volatile private var sink: Long = 0L
    @Volatile private var sinkD: Double = 0.0

    suspend fun run(onProgress: (BenchmarkProgress) -> Unit): BenchmarkResult = withContext(Dispatchers.Default) {
        val startedAt = System.currentTimeMillis()
        val t0 = SystemClock.elapsedRealtime()
        val tempStart = battery.snapshot().temperatureC.value
        val thermalStart = thermal.read(includeZones = false).statusLabel
        val threads = Runtime.getRuntime().availableProcessors()

        fun report(stage: String, fraction: Float) {
            val th = thermal.read(includeZones = false)
            onProgress(BenchmarkProgress(stage, fraction, battery.snapshot().temperatureC.value, th.statusLabel))
            if (th.status >= PowerManager.THERMAL_STATUS_SEVERE) {
                throw ThermalAbortException(
                    "Device reached '${th.statusLabel}' thermal state. Benchmark stopped to protect the device. Let it cool down and try again."
                )
            }
        }

        report("Warming up (JIT)", 0.02f)
        sink += sieve(SIEVE_N).toLong()
        sinkD += matmul(MAT_N)
        ensureActive()

        report("CPU single-core · integer", 0.08f)
        val intTime = timeNs { repeat(INT_ROUNDS) { sink += sieve(SIEVE_N).toLong() } }
        val singleMops = SIEVE_N.toDouble() * INT_ROUNDS / (intTime / 1e9) / 1e6
        ensureActive()

        report("CPU single-core · floating point", 0.18f)
        val fpTime = timeNs { repeat(FP_ROUNDS) { sinkD += matmul(MAT_N) } }
        val flopsPerRound = 2.0 * MAT_N * MAT_N * MAT_N
        val singleMflops = flopsPerRound * FP_ROUNDS / (fpTime / 1e9) / 1e6
        ensureActive()

        report("CPU multi-core · integer ($threads threads)", 0.28f)
        val mIntTime = parallel(threads) { repeat(INT_ROUNDS) { sink += sieve(SIEVE_N).toLong() } }
        val multiMops = SIEVE_N.toDouble() * INT_ROUNDS * threads / (mIntTime / 1e9) / 1e6
        ensureActive()

        report("CPU multi-core · floating point ($threads threads)", 0.40f)
        val mFpTime = parallel(threads) { repeat(FP_ROUNDS) { sinkD += matmul(MAT_N) } }
        val multiMflops = flopsPerRound * FP_ROUNDS * threads / (mFpTime / 1e9) / 1e6
        ensureActive()

        report("Memory · copy bandwidth", 0.52f)
        val copy = memCopyMBps()
        ensureActive()

        report("Memory · random-access latency", 0.60f)
        val latency = memLatencyNs()
        ensureActive()

        val st = storageBench { stage, f -> report(stage, f) }
        ensureActive()

        report("Calculating scores", 0.98f)
        val r = BenchmarkReferences
        val cpuSingleScore = avg(r.logScore(singleMops, r.CPU_INT_MOPS), r.logScore(singleMflops, r.CPU_FP_MFLOPS))
        val cpuMultiScore = avg(
            r.logScore(multiMops, r.CPU_INT_MOPS * r.MULTI_FACTOR),
            r.logScore(multiMflops, r.CPU_FP_MFLOPS * r.MULTI_FACTOR)
        )
        val memScore = avg(r.logScore(copy, r.MEM_COPY_MBPS), r.logScore(latency, r.MEM_LATENCY_NS, higherIsBetter = false))
        val storageScore = avg(
            r.logScore(st.seqWrite, r.SEQ_WRITE_MBPS),
            r.logScore(st.seqRead, r.SEQ_READ_MBPS),
            r.logScore(st.randWrite, r.RAND_WRITE_IOPS),
            r.logScore(st.randRead, r.RAND_READ_IOPS)
        )
        val overall = (cpuSingleScore * 0.30 + cpuMultiScore * 0.25 + memScore * 0.20 + storageScore * 0.25)
            .roundToInt().coerceIn(0, 100)

        BenchmarkResult(
            timestamp = startedAt,
            threads = threads,
            cpuSingleMops = singleMops,
            cpuSingleMflops = singleMflops,
            cpuMultiMops = multiMops,
            cpuMultiMflops = multiMflops,
            memCopyMBps = copy,
            memLatencyNs = latency,
            storageSeqWriteMBps = st.seqWrite,
            storageSeqReadMBps = st.seqRead,
            storageRandWriteIops = st.randWrite,
            storageRandReadIops = st.randRead,
            cpuSingleScore = cpuSingleScore,
            cpuMultiScore = cpuMultiScore,
            memoryScore = memScore,
            storageScore = storageScore,
            overallScore = overall,
            tempStartC = tempStart,
            tempEndC = battery.snapshot().temperatureC.value,
            thermalStart = thermalStart,
            thermalEnd = thermal.read(includeZones = false).statusLabel,
            durationMs = SystemClock.elapsedRealtime() - t0
        )
    }

    // ---------------- CPU workloads ----------------

    /** Sieve of Eratosthenes: integer + branch + memory-access heavy. Returns prime count. */
    private fun sieve(n: Int): Int {
        val composite = BooleanArray(n + 1)
        var count = 0
        var i = 2
        while (i <= n) {
            if (!composite[i]) {
                count++
                var j = i.toLong() * i
                while (j <= n) {
                    composite[j.toInt()] = true
                    j += i
                }
            }
            i++
        }
        return count
    }

    /** Dense double-precision matrix multiply (i-k-j order). */
    private fun matmul(n: Int): Double {
        val a = DoubleArray(n * n) { (it % 13) * 0.5 + 1.0 }
        val b = DoubleArray(n * n) { (it % 7) * 0.25 + 0.5 }
        val c = DoubleArray(n * n)
        for (i in 0 until n) {
            val rowC = i * n
            for (k in 0 until n) {
                val aik = a[i * n + k]
                val rowB = k * n
                for (j in 0 until n) {
                    c[rowC + j] += aik * b[rowB + j]
                }
            }
        }
        return c[n * n / 2]
    }

    private suspend fun parallel(threads: Int, block: () -> Unit): Long = coroutineScope {
        val start = System.nanoTime()
        List(threads) { async(Dispatchers.Default) { block() } }.awaitAll()
        System.nanoTime() - start
    }

    // ---------------- Memory ----------------

    private fun memCopyMBps(): Double {
        val avail = memory.read().availableBytes
        val heap = Runtime.getRuntime().maxMemory()
        val elems = if (avail > 1_000_000_000L && heap >= 256L * 1024 * 1024) 4 * 1024 * 1024 else 1024 * 1024
        val src = LongArray(elems) { it.toLong() }
        val dst = LongArray(elems)
        System.arraycopy(src, 0, dst, 0, elems)
        val reps = 20
        val t = timeNs { repeat(reps) { System.arraycopy(src, 0, dst, 0, elems) } }
        sink += dst[elems - 1]
        return elems * 8.0 * reps / (t / 1e9) / 1e6
    }

    /** Pointer chasing over a random single-cycle permutation (Sattolo) defeats prefetching. */
    private fun memLatencyNs(): Double {
        val heap = Runtime.getRuntime().maxMemory()
        val n = if (heap >= 256L * 1024 * 1024) 8 * 1024 * 1024 else 2 * 1024 * 1024
        val next = IntArray(n) { it }
        val rnd = Random(42)
        for (i in n - 1 downTo 1) {
            val j = rnd.nextInt(i)
            val tmp = next[i]
            next[i] = next[j]
            next[j] = tmp
        }
        var idx = 0
        val steps = 4_000_000
        val t = timeNs { repeat(steps) { idx = next[idx] } }
        sink += idx.toLong()
        return t.toDouble() / steps
    }

    // ---------------- Storage ----------------

    private class StorageNumbers(val seqWrite: Double, val seqRead: Double, val randWrite: Double, val randRead: Double)

    /**
     * Writes a temporary file in app-private storage, then deletes it.
     * Limitation: apps cannot drop the OS page cache, so read results may partly reflect RAM cache.
     */
    private suspend fun storageBench(report: (String, Float) -> Unit): StorageNumbers = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "bench").apply { mkdirs() }
        val file = File(dir, "bench.bin")
        val free = dir.usableSpace
        val sizeMb = when {
            free > 2L * 1024 * 1024 * 1024 -> 128
            free > 512L * 1024 * 1024 -> 64
            else -> 16
        }
        val buf = ByteArray(1024 * 1024).also { Random(7).nextBytes(it) }
        try {
            report("Storage · sequential write ($sizeMb MB)", 0.68f)
            val wt = timeNs {
                FileOutputStream(file).use { out ->
                    repeat(sizeMb) { out.write(buf) }
                    out.fd.sync()
                }
            }
            val seqWrite = sizeMb / (wt / 1e9)
            ensureActive()

            report("Storage · sequential read", 0.78f)
            val rt = timeNs {
                FileInputStream(file).use { inp ->
                    while (inp.read(buf) > 0) { /* read through */ }
                }
            }
            val seqRead = sizeMb / (rt / 1e9)
            ensureActive()

            val block = ByteArray(4096).also { Random(9).nextBytes(it) }
            val blocks = sizeMb * 256
            val rnd = Random(11)

            report("Storage · random 4K write (synced)", 0.86f)
            val writeOps = 200
            val rwt = timeNs {
                RandomAccessFile(file, "rw").use { raf ->
                    repeat(writeOps) {
                        raf.seek(rnd.nextInt(blocks).toLong() * 4096)
                        raf.write(block)
                        raf.fd.sync()
                    }
                }
            }
            val randWrite = writeOps / (rwt / 1e9)
            ensureActive()

            report("Storage · random 4K read", 0.93f)
            val readOps = 4000
            val rrt = timeNs {
                RandomAccessFile(file, "r").use { raf ->
                    repeat(readOps) {
                        raf.seek(rnd.nextInt(blocks).toLong() * 4096)
                        raf.readFully(block)
                    }
                }
            }
            val randRead = readOps / (rrt / 1e9)
            StorageNumbers(seqWrite, seqRead, randWrite, randRead)
        } finally {
            file.delete()
        }
    }

    private inline fun timeNs(block: () -> Unit): Long {
        val s = System.nanoTime()
        block()
        return System.nanoTime() - s
    }

    private fun avg(vararg v: Int): Int = v.average().roundToInt()

    companion object {
        const val SIEVE_N = 2_000_000
        const val INT_ROUNDS = 20
        const val MAT_N = 160
        const val FP_ROUNDS = 20
    }
}

/**
 * Reference values ≈ a typical 2022 mid-range phone. They define the score scale only;
 * raw measurements are always displayed. Tune here if you calibrate against your own device fleet.
 */
object BenchmarkReferences {
    const val CPU_INT_MOPS = 60.0          // million numbers sieved / s (single core)
    const val CPU_FP_MFLOPS = 400.0        // MFLOPS, single core
    const val MULTI_FACTOR = 4.0           // effective multi-core scaling of reference device
    const val MEM_COPY_MBPS = 4000.0
    const val MEM_LATENCY_NS = 120.0
    const val SEQ_WRITE_MBPS = 250.0
    const val SEQ_READ_MBPS = 900.0
    const val RAND_WRITE_IOPS = 300.0      // with fsync after every write
    const val RAND_READ_IOPS = 8000.0

    fun logScore(value: Double, ref: Double, higherIsBetter: Boolean = true): Int {
        if (value <= 0 || value.isNaN() || value.isInfinite()) return 0
        val ratio = if (higherIsBetter) value / ref else ref / value
        return (50 + 25 * log2(ratio)).roundToInt().coerceIn(0, 100)
    }
}
