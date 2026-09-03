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

object PdfExporter {

    private const val PAGE_W = 595f   // A4 @ 72dpi
    private const val PAGE_H = 842f
    private const val MARGIN = 46f
    private const val BOTTOM = PAGE_H - 60f

    // coloane tabel
    private const val COL_NR = 34f
    private const val COL_QTY = 62f
    private const val COL_UM = 44f
    private const val ROW_H = 20f
    private const val HEADER_H = 22f

    private val ACCENT = Color.rgb(24, 62, 110)        // albastru închis
    private val ACCENT_LIGHT = Color.rgb(222, 232, 245)
    private val ROW_ALT = Color.rgb(245, 247, 250)
    private val GRID = Color.rgb(190, 198, 210)
    private val TEXT = Color.rgb(25, 28, 33)
    private val TEXT_MUTED = Color.rgb(115, 120, 130)

    data class Result(val shareUri: Uri, val savedToDownloads: Boolean, val fileName: String)

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

    fun export(
        context: Context,
        work: Work,
        installerName: String = "",
        installerPhone: String = "",
        installerCompany: String = ""
    ): Result {
        val bandText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f; color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17f; color = TEXT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f; color = TEXT_MUTED
        }
        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.5f; color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val catPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f; color = ACCENT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.5f; color = TEXT
        }
        val itemBold = Paint(itemPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val centerBold = Paint(itemBold).apply { textAlign = Paint.Align.CENTER }
        val centerText = Paint(itemPaint).apply { textAlign = Paint.Align.CENTER }
        val gridPaint = Paint().apply {
            color = GRID; strokeWidth = 0.7f; style = Paint.Style.STROKE
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f; color = TEXT_MUTED; textAlign = Paint.Align.CENTER
        }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f; color = TEXT_MUTED
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }

        val hasPrices = work.hasPrices
        val colPrice = 54f
        val colValue = 62f
        val tableL = MARGIN
        val tableR = PAGE_W - MARGIN
        val colNameL = tableL + COL_NR
        val colValL = if (hasPrices) tableR - colValue else tableR
        val colPriceL = if (hasPrices) colValL - colPrice else tableR
        val colUmL = colPriceL - COL_UM
        val colQtyL = colUmL - COL_QTY
        val nameWidth = colQtyL - colNameL - 12f

        fun money(v: Double) = String.format(Locale.US, "%.2f", v)

        val df = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        fun wrap(text: String, paint: Paint, width: Float): List<String> {
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

        val hasInstaller = installerName.isNotBlank() || installerPhone.isNotBlank() ||
            installerCompany.isNotBlank()

        /**
         * Desenează tot documentul. Cu totalPages = 0 doar numără paginile
         * (prima trecere); cu totalul cunoscut scrie subsolul "Pagina X / N".
         */
        fun render(totalPages: Int): Pair<PdfDocument, Int> {
            val doc = PdfDocument()
            val fillPaint = Paint()
            var pageNo = 0
            var page: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var y = 0f

            fun drawTableHeader() {
                val c = canvas!!
                fillPaint.color = ACCENT
                c.drawRect(tableL, y, tableR, y + HEADER_H, fillPaint)
                c.drawText("Nr.", tableL + 8f, y + 15f, thPaint)
                c.drawText("Denumire material", colNameL + 6f, y + 15f, thPaint)
                c.drawText("Cant.", colQtyL + 14f, y + 15f, thPaint)
                c.drawText("UM", colUmL + 12f, y + 15f, thPaint)
                if (hasPrices) {
                    c.drawText("P.U. lei", colPriceL + 6f, y + 15f, thPaint)
                    c.drawText("Val. lei", colValL + 8f, y + 15f, thPaint)
                }
                y += HEADER_H
            }

            fun finishPage() {
                page?.let {
                    val total = if (totalPages > 0) "$totalPages" else "?"
                    canvas?.drawText(
                        "Pagina $pageNo / $total   •   NecMat   •   ${df.format(Date(work.date))}",
                        PAGE_W / 2f, PAGE_H - 30f, footerPaint
                    )
                    doc.finishPage(it)
                }
            }

            fun newPage(withTableHeader: Boolean = true) {
                finishPage()
                pageNo++
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNo).create()
                )
                canvas = page!!.canvas
                y = MARGIN
                if (pageNo > 1 && withTableHeader) drawTableHeader()
            }

            // ---------- prima pagină: antet document ----------
            newPage(withTableHeader = false)
            val c0 = canvas!!

            fillPaint.color = ACCENT
            c0.drawRect(0f, 0f, PAGE_W, 54f, fillPaint)
            c0.drawText("NECESAR DE MATERIALE", MARGIN, 34f, bandText)

            // chenar cu datele instalatorului, în dreapta
            var boxBottom = 60f
            var titleWidth = tableR - tableL
            if (hasInstaller) {
                val boxW = 215f
                val boxL = tableR - boxW
                val pad = 9f
                val innerW = boxW - 2 * pad

                val infoLines = mutableListOf<Pair<String, Paint>>()
                if (installerCompany.isNotBlank())
                    wrap(installerCompany, itemBold, innerW).forEach { infoLines += it to itemBold }
                if (installerName.isNotBlank()) {
                    val p = if (installerCompany.isBlank()) itemBold else itemPaint
                    wrap("Instalator: $installerName", p, innerW).forEach { infoLines += it to p }
                }
                if (installerPhone.isNotBlank())
                    infoLines += "Telefon: $installerPhone" to itemPaint
                val noteLines = wrap(
                    "Pentru întrebări sau nelămuriri, nu ezitați să contactați instalatorul.",
                    notePaint, innerW
                )

                val boxT = 64f
                val boxH = pad + infoLines.size * 13f + 5f + noteLines.size * 11f + pad
                fillPaint.color = ACCENT_LIGHT
                c0.drawRect(boxL, boxT, tableR, boxT + boxH, fillPaint)
                val borderPaint = Paint().apply {
                    color = ACCENT; strokeWidth = 1.2f; style = Paint.Style.STROKE
                }
                c0.drawRect(boxL, boxT, tableR, boxT + boxH, borderPaint)

                var by = boxT + pad + 9f
                infoLines.forEach { (line, p) ->
                    c0.drawText(line, boxL + pad, by, p)
                    by += 13f
                }
                by += 5f
                noteLines.forEach { line ->
                    c0.drawText(line, boxL + pad, by, notePaint)
                    by += 11f
                }
                boxBottom = boxT + boxH
                titleWidth = boxL - MARGIN - 14f
            }

            y = 78f
            wrap(work.name, titlePaint, titleWidth).forEach { line ->
                c0.drawText(line, MARGIN, y, titlePaint)
                y += 21f
            }
            wrap(
                "Data: ${df.format(Date(work.date))}    •    " +
                    "${work.totalTypes} tipuri de materiale    •    ${work.totalPieces} bucăți",
                subPaint, titleWidth
            ).forEach { line ->
                c0.drawText(line, MARGIN, y + 2f, subPaint)
                y += 14f
            }
            val clientLine = buildList {
                if (work.client.isNotBlank()) add("Client: ${work.client}")
                if (work.address.isNotBlank()) add("Adresă: ${work.address}")
                if (work.phone.isNotBlank()) add("Telefon: ${work.phone}")
            }.joinToString("    •    ")
            if (clientLine.isNotEmpty()) {
                wrap(clientLine, subPaint, titleWidth).forEach { line ->
                    c0.drawText(line, MARGIN, y + 2f, subPaint)
                    y += 13f
                }
            }
            y += 4f
            y = maxOf(y, boxBottom + 10f)

            drawTableHeader()

            // ---------- tabel ----------
            var nr = 0
            work.categories.forEach { cat ->
                if (cat.materials.isEmpty()) return@forEach
                val um = if (cat.name.contains("(m)")) "m" else "buc"

                // rând categorie
                if (y + ROW_H + ROW_H > BOTTOM) newPage()
                var c = canvas!!
                fillPaint.color = ACCENT_LIGHT
                c.drawRect(tableL, y, tableR, y + ROW_H, fillPaint)
                c.drawRect(tableL, y, tableR, y + ROW_H, gridPaint)
                val catLabel = buildString {
                    append(cat.name.uppercase(Locale.getDefault()))
                    if (cat.name.trim().equals("module", ignoreCase = true))
                        append(" (aparataj modular)")
                    if (cat.brandLabel.isNotEmpty()) append("   —   ").append(cat.brandLabel)
                }
                c.drawText(catLabel, colNameL + 6f, y + 14f, catPaint)
                y += ROW_H

                cat.materials.forEachIndexed { i, m ->
                    nr++
                    val lines = wrap(m.name, itemPaint, nameWidth)
                    val rowH = maxOf(ROW_H, lines.size * 13f + 8f)
                    if (y + rowH > BOTTOM) {
                        newPage()
                    }
                    c = canvas!!
                    if (i % 2 == 1) {
                        fillPaint.color = ROW_ALT
                        c.drawRect(tableL, y, tableR, y + rowH, fillPaint)
                    }
                    // grid celule
                    c.drawRect(tableL, y, tableR, y + rowH, gridPaint)
                    c.drawLine(colNameL, y, colNameL, y + rowH, gridPaint)
                    c.drawLine(colQtyL, y, colQtyL, y + rowH, gridPaint)
                    c.drawLine(colUmL, y, colUmL, y + rowH, gridPaint)
                    if (hasPrices) {
                        c.drawLine(colPriceL, y, colPriceL, y + rowH, gridPaint)
                        c.drawLine(colValL, y, colValL, y + rowH, gridPaint)
                    }

                    c.drawText("$nr", tableL + 8f, y + 14f, itemPaint)
                    lines.forEachIndexed { li, line ->
                        c.drawText(line, colNameL + 6f, y + 14f + li * 13f, itemPaint)
                    }
                    c.drawText("${m.qty}", colQtyL + COL_QTY / 2f, y + 14f, centerBold)
                    c.drawText(um, colUmL + COL_UM / 2f, y + 14f, centerText)
                    if (hasPrices) {
                        if (m.price > 0.0) {
                            c.drawText(money(m.price), colPriceL + colPrice / 2f, y + 14f, centerText)
                            c.drawText(money(m.qty * m.price), colValL + colValue / 2f, y + 14f, centerBold)
                        } else {
                            c.drawText("-", colPriceL + colPrice / 2f, y + 14f, centerText)
                            c.drawText("-", colValL + colValue / 2f, y + 14f, centerText)
                        }
                    }
                    y += rowH
                }
            }

            // ---------- total ----------
            if (y + ROW_H + 6f > BOTTOM) newPage()
            val cEnd = canvas!!
            y += 14f
            cEnd.drawLine(tableL, y, tableR, y, gridPaint)
            y += 6f
            fillPaint.color = ACCENT
            cEnd.drawRect(tableL, y, tableR, y + ROW_H, fillPaint)
            val totalText = buildString {
                append("TOTAL: ${work.totalTypes} tipuri  —  ${work.totalPieces} bucăți")
                if (hasPrices) append("  —  ${money(work.totalValue)} lei")
            }
            cEnd.drawText(totalText, colNameL + 6f, y + 14f, thPaint)
            y += ROW_H
            if (hasPrices) {
                y += 10f
                cEnd.drawText(
                    "Notă: valorile sunt calculate doar pentru materialele cu preț completat.",
                    tableL, y + 10f, subPaint
                )
                y += 14f
            }

            finishPage()
            return doc to pageNo
        }

        // prima trecere: numărăm paginile; a doua: scriem "Pagina X / N"
        val (probe, pages) = render(0)
        probe.close()
        val (doc, _) = render(pages)

        // ---------- scriere fișier ----------
        // numele conține titlul, clientul, adresa și data creării lucrării
        val fileName = pdfFileName(work)
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

    /** Ofertă de manoperă: montaj doze + echipare tablou + deplasare/mâncare/consumabile. */
    fun exportLaborQuote(
        context: Context,
        work: Work,
        quote: LaborQuote,
        installerName: String = "",
        installerPhone: String = "",
        installerCompany: String = "",
        detailExpenses: Boolean = false
    ): Result {
        val doc = PdfDocument()
        val bandText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f; color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17f; color = TEXT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = TEXT_MUTED }
        val thPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.5f; color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val catPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f; color = ACCENT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = TEXT }
        val itemBold = Paint(itemPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val centerBold = Paint(itemBold).apply { textAlign = Paint.Align.CENTER }
        val centerText = Paint(itemPaint).apply { textAlign = Paint.Align.CENTER }
        val gridPaint = Paint().apply { color = GRID; strokeWidth = 0.7f; style = Paint.Style.STROKE }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f; color = TEXT_MUTED; textAlign = Paint.Align.CENTER
        }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f; color = TEXT_MUTED
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val fillPaint = Paint()

        val tableL = MARGIN
        val tableR = PAGE_W - MARGIN
        val colVal = 80f
        val colPU = 70f
        val colQty = 55f
        val colValL = tableR - colVal
        val colPUL = colValL - colPU
        val colQtyL = colPUL - colQty
        val nameWidth = colQtyL - tableL - 14f
        fun money(v: Double) = String.format(Locale.US, "%.2f", v)
        val df = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        fun wrap(text: String, paint: Paint, width: Float): List<String> {
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

        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create())
        val c = page.canvas
        var y: Float

        fillPaint.color = ACCENT
        c.drawRect(0f, 0f, PAGE_W, 54f, fillPaint)
        c.drawText("OFERTĂ MANOPERĂ", MARGIN, 34f, bandText)

        val hasInstaller = installerName.isNotBlank() || installerPhone.isNotBlank() ||
            installerCompany.isNotBlank()
        var boxBottom = 60f
        var titleWidth = tableR - tableL
        if (hasInstaller) {
            val boxW = 215f
            val boxL = tableR - boxW
            val pad = 9f
            val innerW = boxW - 2 * pad
            val infoLines = mutableListOf<Pair<String, Paint>>()
            if (installerCompany.isNotBlank())
                wrap(installerCompany, itemBold, innerW).forEach { infoLines += it to itemBold }
            if (installerName.isNotBlank()) {
                val p = if (installerCompany.isBlank()) itemBold else itemPaint
                wrap("Instalator: $installerName", p, innerW).forEach { infoLines += it to p }
            }
            if (installerPhone.isNotBlank()) infoLines += "Telefon: $installerPhone" to itemPaint
            val noteLines = wrap(
                "Pentru întrebări sau nelămuriri, nu ezitați să contactați instalatorul.",
                notePaint, innerW
            )
            val boxT = 64f
            val boxH = pad + infoLines.size * 13f + 5f + noteLines.size * 11f + pad
            fillPaint.color = ACCENT_LIGHT
            c.drawRect(boxL, boxT, tableR, boxT + boxH, fillPaint)
            c.drawRect(boxL, boxT, tableR, boxT + boxH,
                Paint().apply { color = ACCENT; strokeWidth = 1.2f; style = Paint.Style.STROKE })
            var by = boxT + pad + 9f
            infoLines.forEach { (line, p) -> c.drawText(line, boxL + pad, by, p); by += 13f }
            by += 5f
            noteLines.forEach { line -> c.drawText(line, boxL + pad, by, notePaint); by += 11f }
            boxBottom = boxT + boxH
            titleWidth = boxL - MARGIN - 14f
        }

        y = 78f
        wrap(work.name, titlePaint, titleWidth).forEach { line ->
            c.drawText(line, MARGIN, y, titlePaint); y += 21f
        }
        val clientLine = buildList {
            add("Data: ${df.format(Date())}")
            if (work.client.isNotBlank()) add("Client: ${work.client}")
            if (work.address.isNotBlank()) add("Adresă: ${work.address}")
            if (quote.days > 0) add("Durată estimată: ${quote.days} zile")
        }.joinToString("    •    ")
        wrap(clientLine, subPaint, titleWidth).forEach { line ->
            c.drawText(line, MARGIN, y + 2f, subPaint); y += 13f
        }
        y += 5f
        y = maxOf(y, boxBottom + 10f)

        fun tableHeader() {
            fillPaint.color = ACCENT
            c.drawRect(tableL, y, tableR, y + HEADER_H, fillPaint)
            c.drawText("Denumire", tableL + 8f, y + 15f, thPaint)
            c.drawText("Cant.", colQtyL + 10f, y + 15f, thPaint)
            c.drawText("P.U. lei", colPUL + 10f, y + 15f, thPaint)
            c.drawText("Valoare lei", colValL + 8f, y + 15f, thPaint)
            y += HEADER_H
        }

        fun row(name: String, qtyText: String, pu: String, value: String, alt: Boolean) {
            val lines = wrap(name, itemPaint, nameWidth)
            val rowH = maxOf(ROW_H, lines.size * 13f + 8f)
            if (alt) {
                fillPaint.color = ROW_ALT
                c.drawRect(tableL, y, tableR, y + rowH, fillPaint)
            }
            c.drawRect(tableL, y, tableR, y + rowH, gridPaint)
            c.drawLine(colQtyL, y, colQtyL, y + rowH, gridPaint)
            c.drawLine(colPUL, y, colPUL, y + rowH, gridPaint)
            c.drawLine(colValL, y, colValL, y + rowH, gridPaint)
            lines.forEachIndexed { li, line ->
                c.drawText(line, tableL + 7f, y + 14f + li * 13f, itemPaint)
            }
            c.drawText(qtyText, colQtyL + colQty / 2f, y + 14f, centerText)
            c.drawText(pu, colPUL + colPU / 2f, y + 14f, centerText)
            c.drawText(value, colValL + colVal / 2f, y + 14f, centerBold)
            y += rowH
        }

        fun sectionRow(title: String) {
            fillPaint.color = ACCENT_LIGHT
            c.drawRect(tableL, y, tableR, y + ROW_H, fillPaint)
            c.drawRect(tableL, y, tableR, y + ROW_H, gridPaint)
            c.drawText(title, tableL + 7f, y + 14f, catPaint)
            y += ROW_H
        }

        tableHeader()
        if (quote.lines.isNotEmpty()) {
            sectionRow("MANOPERĂ")
            quote.lines.forEachIndexed { i, l ->
                row(
                    l.name, "${l.qty}",
                    if (l.unitPrice > 0) money(l.unitPrice) else "-",
                    money(l.value), i % 2 == 1
                )
            }
        }
        val hasExtras = quote.helperCost > 0 || quote.travel > 0 ||
            quote.food > 0 || quote.consumables > 0
        if (detailExpenses && hasExtras) {
            val extras = buildList {
                if (quote.helperCost > 0) add(
                    Triple("Ajutor electrician (${quote.days} zile)", quote.days, quote.helperPerDay)
                )
                if (quote.travel > 0) add(Triple("Deplasare", 1, quote.travel))
                if (quote.food > 0) add(Triple("Mâncare", 1, quote.food))
                if (quote.consumables > 0) add(Triple("Consumabile", 1, quote.consumables))
            }
            sectionRow("CHELTUIELI")
            extras.forEachIndexed { i, (name, qty, pu) ->
                row(name, "$qty", money(pu), money(qty * pu), i % 2 == 1)
            }
        }
        y += 6f
        fillPaint.color = ACCENT
        c.drawRect(tableL, y, tableR, y + ROW_H, fillPaint)
        c.drawText("TOTAL OFERTĂ:  ${money(quote.total)} lei", tableL + 7f, y + 14f, thPaint)
        y += ROW_H + 12f
        if (!detailExpenses && hasExtras) {
            c.drawText(
                "Prețul include deplasarea, hrana zilnică, consumabilele și ajutorul de electrician.",
                tableL, y + 4f, notePaint
            )
            y += 12f
        }
        c.drawText(
            "Oferta acoperă manopera; materialele se facturează separat.",
            tableL, y + 4f, notePaint
        )
        c.drawText("Pagina 1 / 1   •   NecMat   •   ${df.format(Date())}",
            PAGE_W / 2f, PAGE_H - 30f, footerPaint)
        doc.finishPage(page)

        val fileName = pdfFileName(work, prefix = "Ofertă manoperă")
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
}
