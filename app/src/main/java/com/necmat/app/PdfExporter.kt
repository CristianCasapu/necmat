package com.necmat.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generează PDF-urile aplicației: necesarul de materiale (merge la client și la
 * furnizor) și oferta de manoperă (merge la client). Ambele au aceeași structură:
 * bandă de antet cu titlu / dată / referință, două carduri cu părțile implicate,
 * titlul lucrării, tabelul, blocul de total și subsol cu numărul paginii.
 */
object PdfExporter {

    private const val PAGE_W = 595f   // A4 @ 72dpi
    private const val PAGE_H = 842f
    private const val MARGIN = 40f
    private const val BAND_H = 74f
    private const val CONT_BAND_H = 30f
    private const val BOTTOM = PAGE_H - 60f
    private const val ROW_H = 20f
    private const val LINE_H = 12.5f
    private const val HEADER_H = 22f

    private val ACCENT = Color.rgb(24, 62, 110)        // albastru închis
    private val ACCENT_DARK = Color.rgb(14, 40, 76)
    private val ACCENT_LIGHT = Color.rgb(226, 234, 245)
    private val BAND_MUTED = Color.rgb(196, 211, 232)
    private val CARD_BG = Color.rgb(246, 248, 251)
    private val ROW_ALT = Color.rgb(247, 249, 252)
    private val GRID = Color.rgb(200, 208, 219)
    private val RULE = Color.rgb(226, 231, 238)
    private val TEXT = Color.rgb(25, 28, 33)
    private val TEXT_MUTED = Color.rgb(110, 117, 128)
    private val OWNED_BG = Color.rgb(255, 247, 226)
    private val OWNED_BORDER = Color.rgb(214, 166, 60)

    data class Result(val shareUri: Uri, val savedToDownloads: Boolean, val fileName: String)

    // ------------------------------------------------------------------ utilitare

    private fun paint(
        size: Float,
        col: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = col
        textAlign = align
        typeface = Typeface.create(
            Typeface.DEFAULT,
            when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
        )
    }

    /** Toate stilurile de text folosite, create o singură dată per document. */
    private class Paints {
        val band = paint(19f, Color.WHITE, bold = true).apply { letterSpacing = 0.06f }
        val bandSub = paint(9.5f, BAND_MUTED)
        val bandRight = paint(9.5f, BAND_MUTED, align = Paint.Align.RIGHT)
        val bandRightBold = paint(11f, Color.WHITE, bold = true, align = Paint.Align.RIGHT)
        val contBand = paint(10.5f, Color.WHITE, bold = true)
        val contBandRight = paint(9f, BAND_MUTED, align = Paint.Align.RIGHT)
        val cardTitle = paint(8.5f, ACCENT, bold = true).apply { letterSpacing = 0.08f }
        val cardText = paint(10f, TEXT)
        val cardBold = paint(11f, TEXT, bold = true)
        val cardMuted = paint(9.5f, TEXT_MUTED, italic = true)
        val title = paint(14f, TEXT, bold = true)
        val meta = paint(9.5f, TEXT_MUTED)
        val th = paint(9.5f, Color.WHITE, bold = true)
        val thCenter = paint(9.5f, Color.WHITE, bold = true, align = Paint.Align.CENTER)
        val thRight = paint(9.5f, Color.WHITE, bold = true, align = Paint.Align.RIGHT)
        val cat = paint(10f, ACCENT, bold = true)
        val catBrand = paint(9f, TEXT_MUTED, italic = true, align = Paint.Align.RIGHT)
        val item = paint(10f, TEXT)
        val itemBold = paint(10f, TEXT, bold = true)
        val nr = paint(9f, TEXT_MUTED, align = Paint.Align.CENTER)
        val center = paint(10f, TEXT, align = Paint.Align.CENTER)
        val centerBold = paint(10f, TEXT, bold = true, align = Paint.Align.CENTER)
        val right = paint(10f, TEXT, align = Paint.Align.RIGHT)
        val rightBold = paint(10f, TEXT, bold = true, align = Paint.Align.RIGHT)
        val total = paint(11f, Color.WHITE, bold = true)
        val totalRight = paint(12f, Color.WHITE, bold = true, align = Paint.Align.RIGHT)
        val sumLabel = paint(10f, TEXT_MUTED, align = Paint.Align.RIGHT)
        val sumValue = paint(10f, TEXT, bold = true, align = Paint.Align.RIGHT)
        val ownedTitle = paint(9f, Color.rgb(120, 86, 10), bold = true).apply { letterSpacing = 0.06f }
        val ownedNote = paint(8.5f, Color.rgb(120, 86, 10), italic = true, align = Paint.Align.RIGHT)
        val note = paint(8.5f, TEXT_MUTED, italic = true)
        val sign = paint(9.5f, TEXT)
        val signMuted = paint(8f, TEXT_MUTED)
        val footer = paint(8f, TEXT_MUTED)
        val footerRight = paint(8f, TEXT_MUTED, align = Paint.Align.RIGHT)
        val fill = Paint()
        val grid = Paint().apply { color = GRID; strokeWidth = 0.6f; style = Paint.Style.STROKE }
        val rule = Paint().apply { color = RULE; strokeWidth = 0.6f }
        val ruleDark = Paint().apply { color = GRID; strokeWidth = 0.8f }
    }

    /** O parte implicată (solicitant / beneficiar): titlu + linii (text, îngroșat). */
    private class Party(val title: String, val lines: List<Pair<String, Boolean>>)

    /**
     * Gestionarea paginilor: pagină nouă când nu mai e loc, subsol pe fiecare
     * pagină, bandă de continuare (desenată de apelant) pe paginile 2+.
     */
    private class Pager(
        val pt: Paints,
        val totalPages: Int,
        val footerLeft: String,
        val onContinuation: (Pager) -> Unit
    ) {
        val doc = PdfDocument()
        var pageNo = 0
            private set
        private var page: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        var y = 0f

        fun newPage(first: Boolean = false) {
            finishPage()
            pageNo++
            page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNo).create()
            )
            canvas = page!!.canvas
            y = MARGIN
            if (!first) onContinuation(this)
        }

        /** Asigură `height` puncte libere pe pagina curentă. */
        fun ensure(height: Float) {
            if (y + height > BOTTOM) newPage()
        }

        fun finishPage() {
            val p = page ?: return
            canvas.drawLine(MARGIN, PAGE_H - 42f, PAGE_W - MARGIN, PAGE_H - 42f, pt.rule)
            canvas.drawText(footerLeft, MARGIN, PAGE_H - 29f, pt.footer)
            val total = if (totalPages > 0) "$totalPages" else "?"
            canvas.drawText("Pagina $pageNo / $total", PAGE_W - MARGIN, PAGE_H - 29f, pt.footerRight)
            doc.finishPage(p)
            page = null
        }
    }

    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val lines = mutableListOf<String>()
        var rest = text.trim()
        while (rest.isNotEmpty()) {
            val n = paint.breakText(rest, true, width, null)
            if (n <= 0) break
            var cut = n
            if (n < rest.length) {
                val lastSpace = rest.substring(0, n).lastIndexOf(' ')
                if (lastSpace > 0) cut = lastSpace
            }
            lines.add(rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        return lines.ifEmpty { listOf("") }
    }

    /** Prima linie care încape în lățime, cu „…” dacă textul e mai lung. */
    private fun fit(text: String, paint: Paint, width: Float): String {
        val n = paint.breakText(text, true, width, null)
        return if (n >= text.length) text
        else text.substring(0, (n - 1).coerceAtLeast(0)).trimEnd() + "…"
    }

    private fun money(v: Double) = String.format(Locale.US, "%.2f", v)

    /** Referință scurtă a documentului, derivată din data lucrării (ex. NM-20260906-1432). */
    private fun docRef(work: Work, prefix: String): String =
        prefix + "-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(work.date))

    /** Banda de antet a primei pagini: titlu, subtitlu, data și referința. */
    private fun drawBand(
        c: Canvas, pt: Paints, title: String, subtitle: String, ref: String, dateText: String
    ) {
        pt.fill.color = ACCENT
        c.drawRect(0f, 0f, PAGE_W, BAND_H, pt.fill)
        pt.fill.color = ACCENT_DARK
        c.drawRect(0f, BAND_H - 4f, PAGE_W, BAND_H, pt.fill)
        c.drawText(title, MARGIN, 41f, pt.band)
        c.drawText(subtitle, MARGIN, 58f, pt.bandSub)
        c.drawText("Data: $dateText", PAGE_W - MARGIN, 39f, pt.bandRightBold)
        c.drawText("Ref. $ref", PAGE_W - MARGIN, 56f, pt.bandRight)
    }

    /** Banda subțire de pe paginile 2+ (titlu · lucrare, în dreapta „continuare”). */
    private fun drawContinuationBand(pg: Pager, title: String, right: String) {
        val c = pg.canvas
        pg.pt.fill.color = ACCENT
        c.drawRect(0f, 0f, PAGE_W, CONT_BAND_H, pg.pt.fill)
        val rightW = pg.pt.contBandRight.measureText(right)
        c.drawText(fit(title, pg.pt.contBand, PAGE_W - 2 * MARGIN - rightW - 16f), MARGIN, 19f, pg.pt.contBand)
        c.drawText(right, PAGE_W - MARGIN, 19f, pg.pt.contBandRight)
        pg.y = CONT_BAND_H + 18f
    }

    /** Două carduri alăturate (stânga / dreapta); întoarce y-ul de sub ele. */
    private fun drawCards(c: Canvas, pt: Paints, top: Float, left: Party, right: Party): Float {
        val gap = 12f
        val w = (PAGE_W - 2 * MARGIN - gap) / 2f
        val pad = 10f
        fun linesOf(p: Party) = p.lines.flatMap { (t, b) ->
            wrap(t, if (b) pt.cardBold else pt.cardText, w - 2 * pad - 4f).map { it to b }
        }
        val ll = linesOf(left)
        val rl = linesOf(right)
        val n = maxOf(ll.size, rl.size, 1)
        val h = pad + 8f + 16f + n * 13f + pad - 6f
        listOf(MARGIN to (left to ll), MARGIN + w + gap to (right to rl)).forEach { (x, pair) ->
            val (party, lines) = pair
            pt.fill.color = CARD_BG
            c.drawRect(x, top, x + w, top + h, pt.fill)
            c.drawRect(x, top, x + w, top + h, pt.grid)
            pt.fill.color = ACCENT
            c.drawRect(x, top, x + 3f, top + h, pt.fill)
            c.drawText(party.title.uppercase(Locale.getDefault()), x + pad + 2f, top + pad + 7f, pt.cardTitle)
            var ly = top + pad + 7f + 16f
            if (lines.isEmpty()) {
                c.drawText("nespecificat", x + pad + 2f, ly, pt.cardMuted)
            }
            lines.forEach { (t, b) ->
                c.drawText(t, x + pad + 2f, ly, if (b) pt.cardBold else pt.cardText)
                ly += 13f
            }
        }
        return top + h
    }

    private fun installerParty(title: String, company: String, name: String, phone: String) =
        Party(title, buildList {
            if (company.isNotBlank()) add(company to true)
            if (name.isNotBlank()) {
                if (company.isBlank()) add(name to true) else add("Instalator: $name" to false)
            }
            if (phone.isNotBlank()) add("Tel.: $phone" to false)
        })

    private fun clientParty(title: String, work: Work, addressLabel: String = "") =
        Party(title, buildList {
            if (work.client.isNotBlank()) add(work.client to true)
            if (work.address.isNotBlank()) add((addressLabel + work.address) to false)
            if (work.phone.isNotBlank()) add("Tel.: ${work.phone}" to false)
        })

    /** Scrie documentul în cache, îl copiază în Descărcări și întoarce URI-ul de partajare. */
    private fun writeOut(context: Context, doc: PdfDocument, fileName: String): Result {
        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        val savedToDownloads = saveToDownloads(context, file, fileName)
        val shareUri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        return Result(shareUri, savedToDownloads, fileName)
    }

    /**
     * Salvează PDF-ul în Descărcări/NecMat. Dacă există deja un fișier cu
     * același nume, îl suprascrie — nu se creează dubluri „(1).pdf".
     */
    private fun saveToDownloads(context: Context, file: File, fileName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val resolver = context.contentResolver
            val relPath = Environment.DIRECTORY_DOWNLOADS + "/NecMat/"
            var uri: Uri? = null
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
                arrayOf(fileName, relPath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0)
                    )
                }
            }
            if (uri == null) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, relPath)
                }
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }
            val target = uri ?: return false
            resolver.openOutputStream(target, "wt")?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return false
            true
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ necesar materiale

    /**
     * Necesarul de materiale — merge la furnizor (cine solicită, cine primește,
     * ce anume) și la client. `work` trebuie să fie deja trecut prin
     * [AppViewModel.preparePdfWork] (accesorii, materiale existente, filtrare).
     */
    fun export(
        context: Context,
        work: Work,
        installerName: String = "",
        installerPhone: String = "",
        installerCompany: String = ""
    ): Result {
        val pt = Paints()
        val df = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dateText = df.format(Date(work.date))
        val ref = docRef(work, "NM")
        val hasPrices = work.hasPrices

        // coloane: Nr | Denumire | Cant. | UM | [P.U. | Valoare]
        val tableL = MARGIN
        val tableR = PAGE_W - MARGIN
        val colNr = 30f
        val colQty = 52f
        val colUm = 40f
        val colPrice = 64f
        val colVal = 74f
        val colNameL = tableL + colNr
        val colValL = if (hasPrices) tableR - colVal else tableR
        val colPriceL = if (hasPrices) colValL - colPrice else tableR
        val colUmL = colPriceL - colUm
        val colQtyL = colUmL - colQty
        val nameW = colQtyL - colNameL - 12f

        val requester = installerParty(
            "Solicitant (instalator)", installerCompany, installerName, installerPhone
        )
        val beneficiary = clientParty("Beneficiar / livrare (client)", work)
        val footerLeft = "Necesar de materiale  ·  Ref. $ref  ·  generat cu NecMat"

        fun render(totalPages: Int): Pager {
            lateinit var pg: Pager

            fun tableHeader() {
                val c = pg.canvas
                val y = pg.y
                pt.fill.color = ACCENT
                c.drawRect(tableL, y, tableR, y + HEADER_H, pt.fill)
                c.drawText("Nr.", tableL + colNr / 2f, y + 15f, pt.thCenter)
                c.drawText("Denumire material", colNameL + 6f, y + 15f, pt.th)
                c.drawText("Cant.", colQtyL + colQty / 2f, y + 15f, pt.thCenter)
                c.drawText("UM", colUmL + colUm / 2f, y + 15f, pt.thCenter)
                if (hasPrices) {
                    c.drawText("P.U. (lei)", colPriceL + colPrice - 6f, y + 15f, pt.thRight)
                    c.drawText("Valoare (lei)", colValL + colVal - 6f, y + 15f, pt.thRight)
                }
                pg.y += HEADER_H
            }

            pg = Pager(pt, totalPages, footerLeft) { p ->
                drawContinuationBand(p, "NECESAR DE MATERIALE  ·  ${work.name}", "continuare  ·  $dateText")
                tableHeader()
            }
            pg.newPage(first = true)

            // ---- antet ----
            val c0 = pg.canvas
            drawBand(c0, pt, "NECESAR DE MATERIALE", "Listă de achiziție pentru lucrarea de mai jos", ref, dateText)
            var y = drawCards(c0, pt, BAND_H + 16f, requester, beneficiary)
            y += 24f
            wrap("Lucrare: ${work.name}", pt.title, tableR - tableL).forEach { line ->
                c0.drawText(line, MARGIN, y, pt.title)
                y += 17f
            }
            val metaText = buildString {
                append("${work.totalTypes} tipuri de materiale  ·  ${work.totalPieces} bucăți de achiziționat")
                if (work.owned.isNotEmpty())
                    append("  ·  ${work.ownedPieces} buc existente la client (nu sunt în listă)")
            }
            wrap(metaText, pt.meta, tableR - tableL).forEach { line ->
                c0.drawText(line, MARGIN, y, pt.meta)
                y += 12f
            }
            pg.y = y + 4f
            tableHeader()

            // ---- tabel ----
            var nr = 0
            work.categories.forEach { cat ->
                if (cat.materials.isEmpty()) return@forEach
                val um = if (cat.name.contains("(m)")) "m" else "buc"

                pg.ensure(ROW_H * 2 + 4f)
                var c = pg.canvas
                pt.fill.color = ACCENT_LIGHT
                c.drawRect(tableL, pg.y, tableR, pg.y + ROW_H, pt.fill)
                c.drawRect(tableL, pg.y, tableR, pg.y + ROW_H, pt.grid)
                val brandText = if (cat.brandLabel.isNotEmpty()) "Marcă: ${cat.brandLabel}" else ""
                val brandW = if (brandText.isEmpty()) 0f else pt.catBrand.measureText(brandText) + 14f
                val label = buildString {
                    append(cat.name.uppercase(Locale.getDefault()))
                    if (cat.name.trim().equals("module", ignoreCase = true)) append("  (APARATAJ MODULAR)")
                }
                c.drawText(fit(label, pt.cat, tableR - colNameL - 12f - brandW), colNameL + 6f, pg.y + 14f, pt.cat)
                if (brandText.isNotEmpty()) c.drawText(brandText, tableR - 6f, pg.y + 14f, pt.catBrand)
                pg.y += ROW_H

                cat.materials.forEachIndexed { i, m ->
                    nr++
                    val lines = wrap(m.name, pt.item, nameW)
                    val rowH = maxOf(ROW_H, lines.size * LINE_H + 8f)
                    pg.ensure(rowH)
                    c = pg.canvas
                    val y0 = pg.y
                    if (i % 2 == 1) {
                        pt.fill.color = ROW_ALT
                        c.drawRect(tableL, y0, tableR, y0 + rowH, pt.fill)
                    }
                    // margini exterioare + separatoare fine de coloane, linie sub rând
                    c.drawLine(tableL, y0, tableL, y0 + rowH, pt.grid)
                    c.drawLine(tableR, y0, tableR, y0 + rowH, pt.grid)
                    c.drawLine(colNameL, y0, colNameL, y0 + rowH, pt.rule)
                    c.drawLine(colQtyL, y0, colQtyL, y0 + rowH, pt.rule)
                    c.drawLine(colUmL, y0, colUmL, y0 + rowH, pt.rule)
                    if (hasPrices) {
                        c.drawLine(colPriceL, y0, colPriceL, y0 + rowH, pt.rule)
                        c.drawLine(colValL, y0, colValL, y0 + rowH, pt.rule)
                    }
                    c.drawLine(tableL, y0 + rowH, tableR, y0 + rowH, pt.rule)

                    c.drawText("$nr", tableL + colNr / 2f, y0 + 14f, pt.nr)
                    lines.forEachIndexed { li, line ->
                        c.drawText(line, colNameL + 6f, y0 + 14f + li * LINE_H, pt.item)
                    }
                    c.drawText("${m.qty}", colQtyL + colQty / 2f, y0 + 14f, pt.centerBold)
                    c.drawText(um, colUmL + colUm / 2f, y0 + 14f, pt.center)
                    if (hasPrices) {
                        if (m.price > 0.0) {
                            c.drawText(money(m.price), colPriceL + colPrice - 6f, y0 + 14f, pt.right)
                            c.drawText(money(m.qty * m.price), colValL + colVal - 6f, y0 + 14f, pt.rightBold)
                        } else {
                            c.drawText("–", colPriceL + colPrice - 6f, y0 + 14f, pt.right)
                            c.drawText("–", colValL + colVal - 6f, y0 + 14f, pt.right)
                        }
                    }
                    pg.y += rowH
                }
            }

            // ---- total ----
            pg.ensure(ROW_H + 16f)
            var c = pg.canvas
            c.drawLine(tableL, pg.y, tableR, pg.y, pt.ruleDark)
            pg.y += 6f
            pt.fill.color = ACCENT
            c.drawRect(tableL, pg.y, tableR, pg.y + ROW_H + 2f, pt.fill)
            c.drawText("TOTAL DE ACHIZIȚIONAT", colNameL + 6f, pg.y + 15f, pt.total)
            c.drawText(
                buildString {
                    append("${work.totalTypes} tipuri  ·  ${work.totalPieces} bucăți")
                    if (hasPrices) append("  ·  ${money(work.totalValue)} lei")
                },
                tableR - 6f, pg.y + 15f, pt.totalRight
            )
            pg.y += ROW_H + 2f
            if (hasPrices) {
                pg.y += 12f
                c.drawText(
                    "Valorile sunt calculate doar pentru materialele cu preț completat.",
                    tableL, pg.y, pt.note
                )
            }

            // ---- materiale puse la dispoziție de client ----
            if (work.owned.isNotEmpty()) {
                pg.ensure(ROW_H * 3)
                c = pg.canvas
                pg.y += 18f
                pt.fill.color = OWNED_BG
                c.drawRect(tableL, pg.y, tableR, pg.y + ROW_H, pt.fill)
                pt.fill.color = OWNED_BORDER
                c.drawRect(tableL, pg.y, tableL + 3f, pg.y + ROW_H, pt.fill)
                c.drawText("MATERIALE PUSE LA DISPOZIȚIE DE CLIENT", tableL + 10f, pg.y + 14f, pt.ownedTitle)
                c.drawText("deja existente — nu sunt incluse în lista de mai sus", tableR - 6f, pg.y + 14f, pt.ownedNote)
                pg.y += ROW_H
                work.owned.forEach { o ->
                    val um = if (o.category.contains("(m)")) "m" else "buc"
                    val lines = wrap(o.name, pt.item, tableR - tableL - 110f)
                    val rowH = maxOf(ROW_H, lines.size * LINE_H + 8f)
                    pg.ensure(rowH)
                    c = pg.canvas
                    val y0 = pg.y
                    lines.forEachIndexed { li, line ->
                        c.drawText(line, tableL + 10f, y0 + 14f + li * LINE_H, pt.item)
                    }
                    c.drawText("${o.qty} $um", tableR - 6f, y0 + 14f, pt.rightBold)
                    c.drawLine(tableL, y0 + rowH, tableR, y0 + rowH, pt.rule)
                    pg.y += rowH
                }
            }

            // ---- încheiere ----
            pg.ensure(64f)
            c = pg.canvas
            pg.y += 24f
            c.drawLine(tableL, pg.y, tableR, pg.y, pt.rule)
            pg.y += 15f
            val byWho = listOf(
                installerCompany, installerName,
                if (installerPhone.isNotBlank()) "tel. $installerPhone" else ""
            ).filter { it.isNotBlank() }.joinToString("  ·  ")
            c.drawText("Întocmit de: ${byWho.ifBlank { "—" }}", tableL, pg.y, pt.sign)
            pg.y += 14f
            wrap(
                "Cantitățile sunt calculate pentru lucrarea menționată. Pentru echivalențe sau " +
                    "înlocuiri de produse, vă rugăm să contactați instalatorul înainte de livrare.",
                pt.note, tableR - tableL
            ).forEach { line ->
                c.drawText(line, tableL, pg.y, pt.note)
                pg.y += 11f
            }

            pg.finishPage()
            return pg
        }

        // prima trecere numără paginile; a doua scrie „Pagina X / N”
        val probe = render(0)
        val pages = probe.pageNo
        probe.doc.close()
        val pg = render(pages)
        return writeOut(context, pg.doc, pdfFileName(work))
    }

    // ------------------------------------------------------------------ ofertă manoperă

    /** Oferta de manoperă — merge la client: prestator, beneficiar, desfășurător, total, semnături. */
    fun exportLaborQuote(
        context: Context,
        work: Work,
        quote: LaborQuote,
        installerName: String = "",
        installerPhone: String = "",
        installerCompany: String = "",
        detailExpenses: Boolean = false
    ): Result {
        val pt = Paints()
        val df = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dateText = df.format(Date(work.date))
        val ref = docRef(work, "OF")

        // coloane: Descriere | Cant. | P.U. | Valoare
        val tableL = MARGIN
        val tableR = PAGE_W - MARGIN
        val colVal = 84f
        val colPU = 72f
        val colQty = 50f
        val colValL = tableR - colVal
        val colPUL = colValL - colPU
        val colQtyL = colPUL - colQty
        val nameW = colQtyL - tableL - 14f

        val provider = installerParty("Prestator (instalator)", installerCompany, installerName, installerPhone)
        val beneficiary = clientParty("Beneficiar (client)", work, addressLabel = "Adresa lucrării: ")
        val footerLeft = "Ofertă manoperă  ·  Ref. $ref  ·  generat cu NecMat"

        val hasExtras = quote.helperCost > 0 || quote.travel > 0 ||
            quote.food > 0 || quote.consumables > 0
        val extras = buildList {
            if (quote.helperCost > 0) add(
                Triple("Ajutor electrician (${quote.days} zile)", quote.days, quote.helperPerDay)
            )
            if (quote.travel > 0) add(Triple("Deplasare", 1, quote.travel))
            if (quote.food > 0) add(Triple("Mâncare", 1, quote.food))
            if (quote.consumables > 0) add(Triple("Consumabile", 1, quote.consumables))
        }
        val extrasTotal = quote.total - quote.laborTotal

        fun render(totalPages: Int): Pager {
            lateinit var pg: Pager

            fun tableHeader() {
                val c = pg.canvas
                val y = pg.y
                pt.fill.color = ACCENT
                c.drawRect(tableL, y, tableR, y + HEADER_H, pt.fill)
                c.drawText("Descriere", tableL + 8f, y + 15f, pt.th)
                c.drawText("Cant.", colQtyL + colQty / 2f, y + 15f, pt.thCenter)
                c.drawText("P.U. (lei)", colPUL + colPU - 6f, y + 15f, pt.thRight)
                c.drawText("Valoare (lei)", colValL + colVal - 6f, y + 15f, pt.thRight)
                pg.y += HEADER_H
            }

            fun sectionRow(title: String) {
                pg.ensure(ROW_H * 2 + 4f)
                val c = pg.canvas
                pt.fill.color = ACCENT_LIGHT
                c.drawRect(tableL, pg.y, tableR, pg.y + ROW_H, pt.fill)
                c.drawRect(tableL, pg.y, tableR, pg.y + ROW_H, pt.grid)
                c.drawText(title, tableL + 8f, pg.y + 14f, pt.cat)
                pg.y += ROW_H
            }

            fun row(name: String, qtyText: String, pu: String, value: String, alt: Boolean) {
                val lines = wrap(name, pt.item, nameW)
                val rowH = maxOf(ROW_H, lines.size * LINE_H + 8f)
                pg.ensure(rowH)
                val c = pg.canvas
                val y0 = pg.y
                if (alt) {
                    pt.fill.color = ROW_ALT
                    c.drawRect(tableL, y0, tableR, y0 + rowH, pt.fill)
                }
                c.drawLine(tableL, y0, tableL, y0 + rowH, pt.grid)
                c.drawLine(tableR, y0, tableR, y0 + rowH, pt.grid)
                c.drawLine(colQtyL, y0, colQtyL, y0 + rowH, pt.rule)
                c.drawLine(colPUL, y0, colPUL, y0 + rowH, pt.rule)
                c.drawLine(colValL, y0, colValL, y0 + rowH, pt.rule)
                c.drawLine(tableL, y0 + rowH, tableR, y0 + rowH, pt.rule)
                lines.forEachIndexed { li, line ->
                    c.drawText(line, tableL + 8f, y0 + 14f + li * LINE_H, pt.item)
                }
                c.drawText(qtyText, colQtyL + colQty / 2f, y0 + 14f, pt.center)
                c.drawText(pu, colPUL + colPU - 6f, y0 + 14f, pt.right)
                c.drawText(value, colValL + colVal - 6f, y0 + 14f, pt.rightBold)
                pg.y += rowH
            }

            pg = Pager(pt, totalPages, footerLeft) { p ->
                drawContinuationBand(p, "OFERTĂ MANOPERĂ  ·  ${work.name}", "continuare  ·  $dateText")
                tableHeader()
            }
            pg.newPage(first = true)

            // ---- antet ----
            val c0 = pg.canvas
            drawBand(c0, pt, "OFERTĂ DE PREȚ — MANOPERĂ", "Execuție instalație electrică pentru lucrarea de mai jos", ref, dateText)
            var y = drawCards(c0, pt, BAND_H + 16f, provider, beneficiary)
            y += 24f
            wrap("Lucrare: ${work.name}", pt.title, tableR - tableL).forEach { line ->
                c0.drawText(line, MARGIN, y, pt.title)
                y += 17f
            }
            val meta = buildString {
                append("Data ofertei: $dateText")
                if (quote.days > 0) append("  ·  Durată estimată: ${quote.days} ${if (quote.days == 1) "zi" else "zile"}")
            }
            c0.drawText(meta, MARGIN, y, pt.meta)
            pg.y = y + 16f
            tableHeader()

            // ---- desfășurător ----
            if (quote.lines.isNotEmpty()) {
                sectionRow("MANOPERĂ")
                quote.lines.forEachIndexed { i, l ->
                    row(
                        l.name, "${l.qty}",
                        if (l.unitPrice > 0) money(l.unitPrice) else "–",
                        money(l.value), i % 2 == 1
                    )
                }
            }
            if (detailExpenses && hasExtras) {
                sectionRow("CHELTUIELI")
                extras.forEachIndexed { i, (name, qty, pu) ->
                    row(name, "$qty", money(pu), money(qty * pu), i % 2 == 1)
                }
            }

            // ---- sumar și total ----
            val sumRows = if (detailExpenses && hasExtras)
                listOf("Total manoperă" to quote.laborTotal, "Total cheltuieli" to extrasTotal)
            else emptyList()
            pg.ensure(ROW_H + 8f + sumRows.size * 15f + 16f)
            var c = pg.canvas
            c.drawLine(tableL, pg.y, tableR, pg.y, pt.ruleDark)
            pg.y += 6f
            sumRows.forEach { (label, v) ->
                pg.y += 12f
                c.drawText(label, colValL - 10f, pg.y, pt.sumLabel)
                c.drawText("${money(v)} lei", tableR - 6f, pg.y, pt.sumValue)
            }
            pg.y += if (sumRows.isEmpty()) 0f else 8f
            pt.fill.color = ACCENT
            c.drawRect(tableL, pg.y, tableR, pg.y + ROW_H + 4f, pt.fill)
            c.drawText("TOTAL OFERTĂ", tableL + 8f, pg.y + 16f, pt.total)
            c.drawText("${money(quote.total)} lei", tableR - 6f, pg.y + 16f, pt.totalRight)
            pg.y += ROW_H + 4f

            // ---- note ----
            val notes = buildList {
                if (!detailExpenses && hasExtras)
                    add("Prețul include deplasarea, hrana zilnică, consumabilele și ajutorul de electrician.")
                add("Oferta acoperă exclusiv manopera; materialele se achiziționează și se facturează separat.")
            }
            pg.ensure(14f * notes.size + 10f)
            c = pg.canvas
            pg.y += 10f
            notes.forEach { n ->
                wrap(n, pt.note, tableR - tableL).forEach { line ->
                    pg.y += 11f
                    c.drawText(line, tableL, pg.y, pt.note)
                }
            }

            // ---- semnături ----
            pg.ensure(80f)
            c = pg.canvas
            pg.y += 40f
            val lineW = 190f
            val rightX = PAGE_W - MARGIN - lineW
            c.drawText("Prestator", tableL, pg.y, pt.sign)
            c.drawText("Beneficiar", rightX, pg.y, pt.sign)
            pg.y += 26f
            c.drawLine(tableL, pg.y, tableL + lineW, pg.y, pt.ruleDark)
            c.drawLine(rightX, pg.y, rightX + lineW, pg.y, pt.ruleDark)
            pg.y += 11f
            c.drawText("semnătura și data", tableL, pg.y, pt.signMuted)
            c.drawText("semnătura și data", rightX, pg.y, pt.signMuted)

            pg.finishPage()
            return pg
        }

        val probe = render(0)
        val pages = probe.pageNo
        probe.doc.close()
        val pg = render(pages)
        return writeOut(context, pg.doc, pdfFileName(work, prefix = "Ofertă manoperă"))
    }

    // ------------------------------------------------------------------ program săptămânal

    /** PDF cu programul unei săptămâni (pentru instalator / echipă): o secțiune per zi. */
    fun exportWeekSchedule(
        context: Context,
        days: List<java.time.LocalDate>,
        appointments: List<Appointment>,
        installerName: String = "",
        installerCompany: String = ""
    ): Result {
        val pt = Paints()
        val today = toLocalDate(System.currentTimeMillis())
        val stem = weekFileStem(days)
        val ref = "PS-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val dateText = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        val footerLeft = "Program săptămânal  ·  Ref. $ref  ·  generat cu NecMat"
        val tableL = MARGIN
        val tableR = PAGE_W - MARGIN
        val colTime = 78f
        val colType = 70f
        val colPhone = 84f
        val colTimeL = tableL
        val colTypeL = colTimeL + colTime
        val colNameL = colTypeL + colType
        val colPhoneL = tableR - colPhone
        val nameW = colPhoneL - colNameL - 12f

        fun render(totalPages: Int): Pager {
            lateinit var pg: Pager
            fun tableHeader() {
                val c = pg.canvas
                val y = pg.y
                pt.fill.color = ACCENT
                c.drawRect(tableL, y, tableR, y + HEADER_H, pt.fill)
                c.drawText("Ora", colTimeL + 6f, y + 15f, pt.th)
                c.drawText("Tip", colTypeL + 6f, y + 15f, pt.th)
                c.drawText("Client · adresă · detalii", colNameL + 6f, y + 15f, pt.th)
                c.drawText("Telefon", colPhoneL + 6f, y + 15f, pt.th)
                pg.y += HEADER_H
            }
            pg = Pager(pt, totalPages, footerLeft) { p ->
                drawContinuationBand(p, "PROGRAM SĂPTĂMÂNAL  ·  $stem", "continuare")
                tableHeader()
            }
            pg.newPage(first = true)
            val c0 = pg.canvas
            val a = days.first()
            val b = days.last()
            drawBand(
                c0, pt, "PROGRAM SĂPTĂMÂNAL",
                "${a.dayOfMonth} ${monthLabel(a.monthValue)} – ${b.dayOfMonth} ${monthLabel(b.monthValue)} ${b.year}",
                ref, dateText
            )
            var y = BAND_H + 22f
            val who = listOf(installerCompany, installerName).filter { it.isNotBlank() }.joinToString("  ·  ")
            if (who.isNotBlank()) {
                c0.drawText(who, MARGIN, y, pt.title)
                y += 18f
            }
            val active = appointments.filter { it.isActive }
            val total = days.sumOf { d -> appointmentsOn(active, d).size }
            c0.drawText("$total programări active în această săptămână", MARGIN, y, pt.meta)
            pg.y = y + 14f
            tableHeader()

            days.forEach { day ->
                val list = appointmentsOn(active, day)
                pg.ensure(ROW_H * 2 + 4f)
                var c = pg.canvas
                pt.fill.color = if (day == today) ACCENT_LIGHT else CARD_BG
                c.drawRect(tableL, pg.y, tableR, pg.y + ROW_H, pt.fill)
                c.drawRect(tableL, pg.y, tableR, pg.y + ROW_H, pt.grid)
                c.drawText(formatDayLong(day, today).uppercase(Locale.getDefault()), tableL + 6f, pg.y + 14f, pt.cat)
                c.drawText(if (list.isEmpty()) "liber" else "${list.size} programări", tableR - 6f, pg.y + 14f, pt.catBrand)
                pg.y += ROW_H
                list.forEachIndexed { i, ap ->
                    val details = listOf(ap.clientName, ap.address, ap.title, ap.notes).filter { it.isNotBlank() }
                    val lines = wrap(details.joinToString("  ·  "), pt.item, nameW).ifEmpty { listOf("") }
                    val rowH = maxOf(ROW_H, lines.size * LINE_H + 8f)
                    pg.ensure(rowH)
                    c = pg.canvas
                    val y0 = pg.y
                    if (i % 2 == 1) {
                        pt.fill.color = ROW_ALT
                        c.drawRect(tableL, y0, tableR, y0 + rowH, pt.fill)
                    }
                    c.drawLine(tableL, y0, tableL, y0 + rowH, pt.grid)
                    c.drawLine(tableR, y0, tableR, y0 + rowH, pt.grid)
                    c.drawLine(colTypeL, y0, colTypeL, y0 + rowH, pt.rule)
                    c.drawLine(colNameL, y0, colNameL, y0 + rowH, pt.rule)
                    c.drawLine(colPhoneL, y0, colPhoneL, y0 + rowH, pt.rule)
                    c.drawLine(tableL, y0 + rowH, tableR, y0 + rowH, pt.rule)
                    c.drawText(ap.timeLabel(), colTimeL + 6f, y0 + 14f, pt.itemBold)
                    c.drawText(ap.type.label, colTypeL + 6f, y0 + 14f, pt.item)
                    lines.forEachIndexed { li, line -> c.drawText(line, colNameL + 6f, y0 + 14f + li * LINE_H, pt.item) }
                    c.drawText(ap.phone, colPhoneL + 6f, y0 + 14f, pt.item)
                    pg.y += rowH
                }
            }
            pg.ensure(30f)
            pg.y += 16f
            pg.canvas.drawText(
                "Programările confirmate de client sunt marcate în aplicație; sună înainte de plecare.",
                tableL, pg.y, pt.note
            )
            pg.finishPage()
            return pg
        }

        val probe = render(0)
        val pages = probe.pageNo
        probe.doc.close()
        val pg = render(pages)
        return writeOut(context, pg.doc, "$stem.pdf")
    }
}
