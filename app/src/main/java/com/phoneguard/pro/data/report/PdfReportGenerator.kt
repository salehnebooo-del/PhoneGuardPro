package com.phoneguard.pro.data.report

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.phoneguard.pro.data.model.HealthReport
import com.phoneguard.pro.data.model.Sourced
import com.phoneguard.pro.util.Fmt
import java.io.File
import java.io.FileOutputStream

/** Generates an A4 PDF with android.graphics.pdf.PdfDocument (no third-party libraries). */
class PdfReportGenerator(private val context: Context) {

    fun generate(r: HealthReport): File {
        val doc = PdfDocument()
        val w = PdfWriter(doc)
        w.header("Device Health Report", "${r.device.manufacturer} ${r.device.model} · ${Fmt.dateTime(r.timestamp)}")

        w.heading("Overall health")
        w.scoreLine("Overall device health", r.overallScore, r.overallCondition)
        r.components.forEach { c ->
            w.scoreLine(c.name, c.score, c.condition)
            c.details.forEach { w.bullet(it, small = true) }
        }

        w.heading("Detected issues")
        if (r.issues.isEmpty()) w.paragraph("No significant issues detected.")
        else r.issues.forEach { w.bullet("[${it.severity}] ${it.title} — ${it.detail}") }

        w.heading("Recommended actions")
        if (r.recommendations.isEmpty()) w.paragraph("No actions needed.")
        else r.recommendations.forEach { w.bullet("${it.title}: ${it.detail}") }

        w.heading("Device")
        w.kv("Model", "${r.device.manufacturer} ${r.device.model} (${r.device.device})")
        w.kv("Android", "${r.device.androidVersion} (API ${r.device.sdkInt}) · patch ${r.device.securityPatch}")
        w.kv("Build", r.device.buildId)
        w.kv("Kernel", r.device.kernel)
        w.kv("Uptime", Fmt.duration(r.device.uptimeMs))

        w.heading("CPU")
        w.kv("Model", listOfNotNull(r.cpu.vendor, r.cpu.model).joinToString(" "))
        w.kv("Cores / clusters", "${r.cpu.coreCount} · ${r.cpu.clusterSummary}")
        w.kv("Architecture", "${r.cpu.arch} (${r.cpu.abis.joinToString()})")
        w.kv("Thermal status", r.thermal.statusLabel)

        w.heading("Memory & storage")
        w.kv("RAM", "${Fmt.bytes(r.ram.usedBytes)} used / ${Fmt.bytes(r.ram.totalBytes)} (${Fmt.percent(r.ram.usedFraction)})")
        w.kv("Storage", "${Fmt.bytes(r.storage.usedBytes)} used / ${Fmt.bytes(r.storage.totalBytes)} (${Fmt.percent(r.storage.usedFraction)})")
        w.kv("Display", "${r.display.widthPx}×${r.display.heightPx} · ${r.display.densityDpi} dpi · ${Fmt.num(r.display.refreshRateHz, 0)} Hz")

        w.heading("Battery")
        val b = r.battery
        w.kv("Level / status", "${b.levelPercent ?: "—"}% · ${b.status} · ${b.plugType}")
        w.kv("Reported health", b.healthReported)
        w.kv("Technology", b.technology ?: "—")
        w.kv("Temperature", src(b.temperatureC) { "${Fmt.num(it)} °C" })
        w.kv("Voltage", src(b.voltageMv) { "$it mV" })
        w.kv("Current", src(b.currentMa) { "$it mA" })
        w.kv("Design capacity", src(b.designCapacityMah) { "$it mAh" })
        w.kv("Est. full capacity", src(b.estimatedFullCapacityMah) { "$it mAh" })
        w.kv("Est. health", src(b.estimatedHealthPercent) { "$it%" })
        w.kv("Cycle count", src(b.cycleCount) { "$it" })

        r.latestBenchmark?.let { bm ->
            w.heading("Latest benchmark (${Fmt.dateTime(bm.timestamp)})")
            w.scoreLine("Overall", bm.overallScore, HealthScorer.condition(bm.overallScore))
            w.kv("CPU single-core", "${bm.cpuSingleScore} · ${Fmt.num(bm.cpuSingleMops)} Mops/s int, ${Fmt.num(bm.cpuSingleMflops, 0)} MFLOPS")
            w.kv("CPU multi-core", "${bm.cpuMultiScore} · ${Fmt.num(bm.cpuMultiMops)} Mops/s int, ${Fmt.num(bm.cpuMultiMflops, 0)} MFLOPS")
            w.kv("Memory", "${bm.memoryScore} · ${Fmt.num(bm.memCopyMBps, 0)} MB/s copy, ${Fmt.num(bm.memLatencyNs)} ns latency")
            w.kv("Storage", "${bm.storageScore} · W ${Fmt.num(bm.storageSeqWriteMBps, 0)} MB/s, R ${Fmt.num(bm.storageSeqReadMBps, 0)} MB/s")
        }

        w.heading("Data sources & limitations")
        w.paragraph(
            "Values are labelled by source: System-reported (read from Android APIs), Measured (tests run by this app), " +
                "Calculated (arithmetic on reported values) and Estimated (approximations such as battery health). " +
                "Android does not let normal apps read system-wide CPU utilisation, per-app energy use, the fast-charging protocol " +
                "or the official battery state-of-health. Where data is unavailable it is reported as such, never simulated."
        )

        w.finish()

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "PhoneGuardPro_Report_${Fmt.fileStamp(r.timestamp)}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun shareIntent(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Phone Guard Pro – Device Health Report")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Saves a copy to Downloads via MediaStore (no storage permission needed on Android 10+). */
    fun saveToDownloads(file: File): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun <T> src(s: Sourced<T>, fmt: (T) -> String): String {
        val v = s.value ?: return "Unavailable" + (s.note?.let { " ($it)" } ?: "")
        return "${fmt(v)} [${s.source.label}]"
    }
}

/** Simple flowing layout writer with automatic page breaks. */
private class PdfWriter(private val doc: PdfDocument) {
    private val pageW = 595
    private val pageH = 842
    private val margin = 40f
    private val contentW = pageW - margin * 2
    private var pageNum = 0
    private var page: PdfDocument.Page? = null
    private var y = margin

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B2533.toInt(); textSize = 10.5f }
    private val small = Paint(text).apply { textSize = 9f; color = 0xFF55657A.toInt() }
    private val bold = Paint(text).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A5BD8.toInt(); textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun canvas(): Canvas {
        if (page == null) newPage()
        return page!!.canvas
    }

    private fun newPage() {
        page?.let { footer(it); doc.finishPage(it) }
        pageNum++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
        y = margin
    }

    private fun ensure(space: Float) {
        if (page == null || y + space > pageH - margin - 16) newPage()
    }

    private fun footer(p: PdfDocument.Page) {
        p.canvas.drawText("Phone Guard Pro · page $pageNum", margin, pageH - 20f, small)
    }

    fun finish() {
        page?.let { footer(it); doc.finishPage(it) }
        page = null
    }

    fun header(title: String, subtitle: String) {
        val c = canvas()
        fill.color = 0xFF05070A.toInt()
        c.drawRect(0f, 0f, pageW.toFloat(), 96f, fill)
        fill.color = 0xFF00E5FF.toInt()
        c.drawRect(0f, 96f, pageW.toFloat(), 99f, fill)
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E5FF.toInt(); textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText("PHONE GUARD PRO", margin, 44f, brand)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 12f }
        c.drawText(title, margin, 64f, sub)
        val sub2 = Paint(sub).apply { color = 0xFF8A9BB0.toInt(); textSize = 10f }
        c.drawText(subtitle, margin, 82f, sub2)
        y = 124f
    }

    fun heading(t: String) {
        ensure(34f)
        y += 10f
        canvas().drawText(t, margin, y, headingPaint)
        y += 6f
        fill.color = 0xFFD6E4F5.toInt()
        canvas().drawRect(margin, y, margin + contentW, y + 1f, fill)
        y += 14f
    }

    fun paragraph(t: String) {
        wrap(t, text, contentW).forEach { line ->
            ensure(14f)
            canvas().drawText(line, margin, y, text)
            y += 14f
        }
        y += 2f
    }

    fun bullet(t: String, small: Boolean = false) {
        val p = if (small) this.small else text
        val lh = if (small) 12f else 14f
        val indent = if (small) 24f else 12f
        wrap(t, p, contentW - indent - 10f).forEachIndexed { i, line ->
            ensure(lh)
            if (i == 0) canvas().drawText("•", margin + indent - 10f, y, p)
            canvas().drawText(line, margin + indent, y, p)
            y += lh
        }
    }

    fun kv(k: String, v: String) {
        val labelW = 130f
        val lines = wrap(v, text, contentW - labelW)
        lines.forEachIndexed { i, line ->
            ensure(14f)
            if (i == 0) canvas().drawText(k, margin, y, bold)
            canvas().drawText(line, margin + labelW, y, text)
            y += 14f
        }
    }

    fun scoreLine(label: String, score: Int?, condition: String) {
        ensure(22f)
        val c = canvas()
        c.drawText(label, margin, y, bold)
        val barX = margin + 150f
        val barW = contentW - 150f - 110f
        fill.color = 0xFFE3E9F1.toInt()
        c.drawRoundRect(RectF(barX, y - 9f, barX + barW, y - 1f), 4f, 4f, fill)
        if (score != null) {
            fill.color = when {
                score >= 85 -> 0xFF1DB57A.toInt()
                score >= 70 -> 0xFF0A9FC2.toInt()
                score >= 50 -> 0xFFE09A12.toInt()
                else -> 0xFFE0405C.toInt()
            }
            c.drawRoundRect(RectF(barX, y - 9f, barX + barW * score / 100f, y - 1f), 4f, 4f, fill)
        }
        c.drawText("${score ?: "—"}/100 · $condition", barX + barW + 10f, y, text)
        y += 20f
    }

    private fun wrap(s: String, paint: Paint, maxW: Float): List<String> {
        val lines = mutableListOf<String>()
        var cur = ""
        for (word in s.split(" ")) {
            val candidate = if (cur.isEmpty()) word else "$cur $word"
            if (paint.measureText(candidate) <= maxW) cur = candidate
            else {
                if (cur.isNotEmpty()) lines += cur
                cur = word
            }
        }
        if (cur.isNotEmpty()) lines += cur
        return lines
    }
}
