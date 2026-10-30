package app.umgangstagebuch.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.umgangstagebuch.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class ReportOptions(
    val from: Long,
    val to: Long,
    val includeVisits: Boolean = true,
    val includeEntries: Boolean = true,
    val includePhotos: Boolean = true,
    val includeChecksums: Boolean = true,
    val categories: Set<EntryCategory> = EntryCategory.entries.toSet(),
)

/**
 * Erstellt einen sachlichen PDF-Bericht (A4) für Jugendamt, Anwalt oder Gericht.
 * Verwendet immer den echten Namen des Vaters, nie Spitznamen.
 */
class PdfReport(private val context: Context, private val repo: Repository) {

    private val pageW = 595
    private val pageH = 842
    private val margin = 48f
    private val contentW get() = pageW - 2 * margin

    private val accent = Color.rgb(0x6B, 0x3F, 0x8F)
    private val muted = Color.rgb(0x66, 0x66, 0x6E)

    private val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = Color.BLACK }
    private val h2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = accent }
    private val h3 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = Color.BLACK }
    private val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.SANS_SERIF; color = Color.rgb(0x20, 0x20, 0x24) }
    private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; typeface = Typeface.SANS_SERIF; color = muted }
    private val mono = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7f; typeface = Typeface.MONOSPACE; color = muted }
    private val line = Paint().apply { color = Color.rgb(0xDD, 0xDA, 0xE3); strokeWidth = 0.8f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xF4, 0xEF, 0xF8) }

    private lateinit var doc: PdfDocument
    private var page: PdfDocument.Page? = null
    private var pageNo = 0
    private var y = 0f
    private val canvas: Canvas get() = page!!.canvas

    suspend fun write(target: Uri, o: ReportOptions) = withContext(Dispatchers.IO) {
        val data = repo.data.value
        val photos = repo.photos.value
        val p = data.profile
        doc = PdfDocument()
        pageNo = 0
        newPage()

        // Kopf
        text("Umgangsdokumentation", title)
        y += 4
        text("Kind: ${p.childName.ifBlank { "–" }}" + (p.childBirthday?.let { ", geb. ${Fmt.dateShort(Fmt.pickerToDate(it))}" } ?: ""), body)
        text("Kindsvater: ${p.fatherOfficial}", body)
        text("Dokumentiert von: ${p.motherName.ifBlank { "–" }} (Mutter)", body)
        text("Zeitraum: ${Fmt.dateShort(o.from)} bis ${Fmt.dateShort(o.to)}", body)
        text("Bericht erstellt: ${Fmt.stamp(System.currentTimeMillis())}", body)
        y += 6
        box(
            "Hinweis: Alle Einträge wurden zeitnah in der App „Umgangstagebuch“ erfasst. Der Erfassungszeitpunkt wird " +
                "automatisch gesetzt und ist nicht änderbar. Nachträgliche Änderungen werden mit Zeitpunkt protokolliert. " +
                "Jeder Eintrag trägt eine SHA-256-Prüfsumme über seinen Inhalt.",
        )

        val visits = data.visits.filter { it.start in o.from..o.to }.sortedBy { it.start }
        val entries = data.entries.filter { it.eventDate in o.from..o.to && it.category in o.categories }.sortedBy { it.eventDate }

        // Zusammenfassung
        heading("Zusammenfassung")
        val s = visits.stats(now = System.currentTimeMillis())
        val rows = listOf(
            "Umgangstermine im Zeitraum" to s.total.toString(),
            "Stattgefunden" to s.done.toString(),
            "Verkürzt / vorzeitig beendet" to s.shortened.toString(),
            "Abgesagt durch den Vater" to s.cancelledByFather.toString(),
            "  davon kurzfristig (< 24 Std.)" to s.shortNotice.toString(),
            "Vater nicht erschienen" to s.noShow.toString(),
            "Abgesagt durch die Mutter" to s.cancelledByMother.toString(),
            "Abgesagt wegen Krankheit des Kindes" to s.cancelledChildIll.toString(),
            "Sonstige Absagen" to s.cancelledOther.toString(),
            "Noch geplant / ohne Status" to s.planned.toString(),
            "Dokumentierte Einträge" to entries.size.toString(),
        )
        rows.forEach { (k, v) -> keyValue(k, v) }

        if (o.includeVisits && visits.isNotEmpty()) {
            heading("Umgangstermine")
            visits.forEach { v -> visitBlock(v, data) }
        }

        if (o.includeEntries && entries.isNotEmpty()) {
            heading("Einträge")
            entries.forEach { e -> entryBlock(e, data, photos, o) }
        }

        y += 16
        ensure(40f)
        canvas.drawLine(margin, y, pageW - margin, y, line)
        y += 12
        text("Ende des Berichts. Erstellt mit der App „Umgangstagebuch“ – Daten werden ausschließlich lokal gespeichert.", small)

        finishPage()
        try {
            context.contentResolver.openOutputStream(target, "wt")?.use { doc.writeTo(it) }
                ?: throw IOException("PDF konnte nicht gespeichert werden")
        } finally {
            doc.close()
        }
    }

    private fun visitBlock(v: Visit, data: AppData) {
        val status = when (v.status) {
            VisitStatus.CANCELLED -> "Abgesagt (${v.cancellation?.by?.let { if (it == CancelledBy.FATHER) "durch Vater" else it.label } ?: "–"})"
            else -> v.status.label
        }
        ensure(36f)
        text("${Fmt.range(v.start, v.end)}  ·  ${v.type.label}", h3)
        val lines = buildList {
            add("Status: $status")
            if (v.location.isNotBlank()) add("Ort / Übergabe: ${v.location}")
            v.cancellation?.takeIf { v.status == VisitStatus.CANCELLED }?.let { c ->
                c.notifiedAt?.let { n ->
                    val h = c.noticeHours(v.start) ?: 0
                    add("Absage mitgeteilt: ${Fmt.dateTime(n)} Uhr über ${c.channel.label} (${if (h >= 0) "$h Std. vorher" else "nachträglich"})")
                }
                if (c.reason.isNotBlank()) add("Begründung: ${c.reason}")
                if (c.replacementOffered) add("Ersatztermin wurde angeboten.")
            }
            if (v.note.isNotBlank()) add("Notiz: ${v.note}")
            val reports = data.entries.count { it.visitId == v.id }
            if (reports > 0) add("Zugehörige Einträge: $reports")
        }
        lines.forEach { text(it, body, indent = 10f) }
        y += 6
    }

    private fun entryBlock(e: Entry, data: AppData, photos: Map<String, Uri>, o: ReportOptions) {
        val childName = data.profile.childName.ifBlank { "Kind" }
        ensure(70f)
        y += 4
        canvas.drawLine(margin, y, pageW - margin, y, line)
        y += 10
        text("${Fmt.dateTime(e.eventDate)} Uhr  ·  ${e.category.label}", h3)
        if (e.title.isNotBlank()) text(e.title, body.withBold())
        text(
            "Erfasst am ${Fmt.stamp(e.createdAt)}" +
                (if (e.isEdited) "  ·  zuletzt geändert am ${Fmt.stamp(e.updatedAt)} (${e.revisions.size}× bearbeitet)" else ""),
            small,
        )
        data.visits.firstOrNull { it.id == e.visitId }?.let { text("Bezug: Umgang ${Fmt.range(it.start, it.end)}", small) }
        y += 4

        e.pickup?.let { handover("Abholung / Übergabe an den Vater", it) }
        e.dropoff?.let { handover("Rückgabe an die Mutter", it) }
        if (e.moodBefore != null || e.tagsBefore.isNotEmpty()) {
            labeled("Befinden vorher", listOfNotNull(e.moodBefore?.let { Moods.labelOf(it) }, e.tagsBefore.joinToString(", ").ifBlank { null }).joinToString(" – "))
        }
        if (e.moodAfter != null || e.tags.isNotEmpty()) {
            val label = if (e.category == EntryCategory.VISIT_REPORT) "Befinden nachher" else "Befinden / Beobachtungen"
            labeled(label, listOfNotNull(e.moodAfter?.let { Moods.labelOf(it) }, e.tags.joinToString(", ").ifBlank { null }).joinToString(" – "))
        }
        if (e.text.isNotBlank()) labeled("Beschreibung", e.text)
        if (e.childQuotes.isNotBlank()) labeled("Äußerungen von $childName (wörtlich)", e.childQuotes)
        if (e.health.isNotBlank()) labeled("Gesundheit / Auffälligkeiten", e.health)
        if (e.witnesses.isNotBlank()) labeled("Zeugen / Beteiligte", e.witnesses)

        if (o.includePhotos && e.photos.isNotEmpty()) {
            y += 4
            text("Fotos (${e.photos.size})", body.withBold())
            e.photos.forEach { ph ->
                val uri = photos[ph.fileName]
                val bmp = uri?.let { repo.storage.loadBitmap(it, 900) }
                if (bmp != null) {
                    val maxW = contentW * 0.6f
                    val maxH = 260f
                    val scale = minOf(maxW / bmp.width, maxH / bmp.height)
                    val w = bmp.width * scale; val h = bmp.height * scale
                    ensure(h + 24f)
                    canvas.drawBitmap(bmp, null, RectF(margin + 10f, y, margin + 10f + w, y + h), null)
                    bmp.recycle()
                    y += h + 3f
                }
                text("${ph.fileName} · hinzugefügt ${Fmt.stamp(ph.addedAt)}" + (if (o.includeChecksums && ph.sha256.isNotBlank()) " · SHA-256 ${ph.sha256}" else ""), mono, indent = 10f)
                y += 4
            }
        }
        if (o.includeChecksums && e.hash.isNotBlank()) {
            y += 2
            text("Prüfsumme (SHA-256): ${e.hash}", mono)
        }
        y += 8
    }

    private fun handover(label: String, h: Handover) {
        val parts = listOfNotNull(
            h.punctuality?.let { p -> p.label + (h.delayMinutes?.takeIf { p == Punctuality.LATE || p == Punctuality.EARLY }?.let { " ($it Min.)" } ?: "") },
            h.note.ifBlank { null },
        )
        if (parts.isNotEmpty()) labeled(label, parts.joinToString(" – "))
    }

    private fun TextPaint.withBold() = TextPaint(this).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }

    private fun labeled(label: String, value: String) {
        if (value.isBlank()) return
        text(label, small.let { TextPaint(it).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) } })
        text(value, body, indent = 0f)
        y += 3
    }

    private fun heading(t: String) {
        y += 14
        ensure(40f)
        text(t, h2)
        canvas.drawLine(margin, y, pageW - margin, y, Paint().apply { color = accent; strokeWidth = 1.2f })
        y += 8
    }

    private fun keyValue(k: String, v: String) {
        ensure(16f)
        canvas.drawText(k, margin + 4, y + 10, body)
        val w = body.measureText(v)
        canvas.drawText(v, pageW - margin - 4 - w, y + 10, body.withBold())
        y += 14f
        canvas.drawLine(margin, y, pageW - margin, y, line)
        y += 2f
    }

    private fun box(t: String) {
        val layout = layout(t, small, contentW - 20)
        ensure(layout.height + 16f)
        canvas.drawRoundRect(RectF(margin, y, pageW - margin, y + layout.height + 16f), 8f, 8f, fill)
        canvas.save(); canvas.translate(margin + 10, y + 8); layout.draw(canvas); canvas.restore()
        y += layout.height + 16f
    }

    private fun layout(t: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, paint, width.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.5f, 1f).build()

    /** Text mit automatischem Seitenumbruch zwischen den Zeilen. */
    private fun text(t: String, paint: TextPaint, indent: Float = 0f) {
        if (t.isEmpty()) return
        val l = layout(t, paint, contentW - indent)
        for (i in 0 until l.lineCount) {
            val top = l.getLineTop(i); val bottom = l.getLineBottom(i)
            val h = (bottom - top).toFloat()
            ensure(h)
            canvas.save()
            canvas.translate(margin + indent, y - top)
            canvas.clipRect(0f, top.toFloat(), contentW - indent, bottom.toFloat())
            l.draw(canvas)
            canvas.restore()
            y += h
        }
        y += 2f
    }

    private fun ensure(h: Float) {
        if (y + h > pageH - margin - 16) {
            finishPage()
            newPage()
        }
    }

    private fun newPage() {
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        y = margin
    }

    private fun finishPage() {
        page?.let {
            val footer = "Umgangsdokumentation · Seite $pageNo"
            it.canvas.drawText(footer, pageW - margin - small.measureText(footer), pageH - margin / 2, small)
            doc.finishPage(it)
        }
        page = null
    }
}
