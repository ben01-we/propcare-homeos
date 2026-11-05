package app.propcare.homeos.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import app.propcare.homeos.data.JobEntity
import app.propcare.homeos.data.PropertySummary
import app.propcare.homeos.data.refLabel
import app.propcare.homeos.data.statusEnum
import app.propcare.homeos.domain.CheckState
import app.propcare.homeos.domain.Company
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Maintenance passport: one A4 PDF per property with its health score, safety checks,
 * job history and photo thumbnails. Written to cache/shared and shared through the FileProvider.
 * Print colours are fixed (a PDF has no light/dark theme).
 */
object PassportPdf {
    private const val W = 595 // A4 in PostScript points
    private const val H = 842
    private const val M = 48f
    private val navy = Color.rgb(0x1A, 0x27, 0x44)
    private val grey = Color.rgb(0x5A, 0x63, 0x73)
    private val red = Color.rgb(0xB3, 0x26, 0x1E)
    private val amber = Color.rgb(0x8A, 0x5A, 0x00)
    private val green = Color.rgb(0x1E, 0x6B, 0x3A)

    suspend fun create(context: Context, s: PropertySummary, jobs: List<JobEntity>): Uri = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val w = Writer(doc)
        val p = s.property

        w.text("Maintenance passport", 22f, navy, bold = true)
        w.text(p.name, 15f, navy, bold = true)
        w.text(listOf(p.addressLine, p.town, p.postcode).filter { it.isNotBlank() }.joinToString(", "), 11f, grey)
        w.text("${s.type.label}. Created ${Format.date(LocalDate.now())}.", 11f, grey)
        w.gap(10f)

        w.heading("Home Health Score: ${s.health.score}/100 (${s.health.band})")
        if (s.health.tips.isEmpty()) w.text("Nothing to sort right now.", 11f, grey)
        s.health.tips.take(5).forEach { w.text("• ${it.text} (+${it.points})", 11f, Color.BLACK) }
        w.gap(8f)

        w.heading("Safety checks")
        s.checks.forEach { c ->
            val colour = when (c.state) { CheckState.ok -> green; CheckState.due_soon -> amber; else -> red }
            val last = c.lastDone?.let { "last done ${Format.date(it)}" } ?: "no date recorded"
            w.text("${c.rule.name}: ${Format.checkState(c)} ($last)", 11f, colour)
        }
        w.gap(8f)

        w.heading("Job history")
        if (jobs.isEmpty()) w.text("No jobs yet.", 11f, grey)
        jobs.forEach { j ->
            w.text("${j.refLabel}  ${j.title}", 11f, Color.BLACK, bold = true)
            val quote = j.quotePence?.let { ", quote ${Format.pounds(it)}" } ?: ""
            w.text("${j.statusEnum.label}. Reported ${Format.date(j.createdAt)}$quote.", 10f, grey)
            listOfNotNull(j.photoPath, j.afterPhotoPath).forEach { path -> Images.loadThumb(path, 240)?.let { w.image(it) } }
            w.gap(4f)
        }

        w.gap(12f)
        w.text("${Company.conceptLabel} Generated on this phone; no data was uploaded.", 9f, grey)
        w.finish()

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "passport-${p.id}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Flows text and images down A4 pages, starting a new page when one fills. */
    private class Writer(private val doc: PdfDocument) {
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init { newPage() }

        private fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()).also { canvas = it.canvas }
            y = M
        }

        private fun ensure(h: Float) { if (y + h > H - M) newPage() }

        fun gap(h: Float) { y += h }

        fun heading(s: String) { gap(4f); text(s, 13f, navy, bold = true); gap(2f) }

        fun text(s: String, size: Float, colour: Int, bold: Boolean = false) {
            paint.textSize = size
            paint.color = colour
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val lineH = size * 1.4f
            wrap(s, W - 2 * M).forEach { line ->
                ensure(lineH)
                y += size
                canvas.drawText(line, M, y, paint)
                y += lineH - size
            }
        }

        fun image(b: android.graphics.Bitmap) {
            val h = 110f
            val w = h * b.width / b.height.coerceAtLeast(1)
            ensure(h + 6f)
            canvas.drawBitmap(b, Rect(0, 0, b.width, b.height), RectF(M, y + 3f, M + w.coerceAtMost(W - 2 * M), y + 3f + h), null)
            y += h + 6f
        }

        private fun wrap(s: String, maxW: Float): List<String> {
            val out = mutableListOf<String>()
            var line = ""
            for (word in s.split(' ')) {
                val trial = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(trial) <= maxW || line.isEmpty()) line = trial else { out += line; line = word }
            }
            if (line.isNotEmpty()) out += line
            return out
        }

        fun finish() { page?.let { doc.finishPage(it) }; page = null }
    }
}
