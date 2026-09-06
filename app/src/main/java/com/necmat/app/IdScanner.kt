package com.necmat.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Citirea actului de identitate: imaginea (poză sau fișier) e redimensionată,
 * rotită după EXIF și trimisă la ML Kit (offline, pe dispozitiv). Rezultatul
 * sunt liniile de text în ordinea de citire + o miniatură pentru confirmare.
 * Nimic nu se salvează și nimic nu pleacă de pe telefon.
 */
object IdScanner {

    private const val MAX_PX = 2000
    private const val THUMB_PX = 720

    data class Scan(val lines: List<String>, val thumbnail: Bitmap?, val result: IdScanResult = IdScanResult())

    /** Fișier temporar pentru camera telefonului (prin FileProvider, în cache/scans). */
    fun newCaptureUri(context: Context): Pair<Uri, File> {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return uri to file
    }

    /** Șterge pozele temporare făcute cu camera. */
    fun cleanup(context: Context) {
        File(context.cacheDir, "scans").listFiles()?.forEach { it.delete() }
    }

    /**
     * Recunoaște textul și îl interpretează. Dacă prima trecere nu găsește
     * niciun câmp, încearcă și imaginea rotită (poze fără orientare EXIF) și
     * păstrează varianta cu cel mai bun rezultat.
     */
    suspend fun scan(context: Context, uri: Uri): Scan = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(context, uri, MAX_PX) ?: return@withContext Scan(emptyList(), null)
        var lines: List<String> = emptyList()
        var result = IdScanResult()
        var bestBmp = bitmap
        val attempts = listOf(0f, 90f, 270f, 180f)
        for (deg in attempts) {
            val bmp = if (deg == 0f) bitmap else rotate(bitmap, deg)
            val l = try {
                recognize(bmp)
            } catch (e: Exception) {
                AppLog.e("Scan", "Recunoașterea textului a eșuat", e)
                emptyList()
            }
            val r = IdCardParser.parse(l)
            AppLog.d("Scan", "OCR la ${deg.toInt()}°: ${l.size} linii, cnp=${r.cnpSure}, nume=${r.surname.isNotBlank()}")
            val better = score(r, l.size) > score(result, lines.size)
            if (better) {
                lines = l
                result = r
                bestBmp = bmp
            }
            if (result.cnpSure && result.surname.isNotBlank()) break
        }
        fun complete() = result.cnpSure && result.surname.isNotBlank() && result.givenNames.isNotBlank()
        // trecere cu contrast mărit, alb-negru (act lucios, lumină slabă)
        if (!complete()) {
            val l = try { recognize(enhance(bestBmp)) } catch (e: Exception) { emptyList() }
            val r = IdCardParser.parse(l)
            AppLog.d("Scan", "OCR contrast: ${l.size} linii, cnp=${r.cnpSure}")
            if (score(r, l.size) > score(result, lines.size)) lines = l
            result = IdCardParser.merge(result, r)
        }
        // banda MRZ (jos, ~36 % din înălțime) mărită de 2× — pe cartea veche dă sigur numele și CNP-ul
        if (!complete()) {
            val l = try { recognize(cropBottom(bestBmp, 0.36f, 2f)) } catch (e: Exception) { emptyList() }
            val r = IdCardParser.parse(l)
            AppLog.d("Scan", "OCR MRZ: ${l.size} linii, cnp=${r.cnpSure}, nume=${r.surname.isNotBlank()}")
            result = IdCardParser.merge(result, r)
            if (l.isNotEmpty()) lines = (lines + l).distinct()
        }
        val thumb = try {
            val scale = THUMB_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
            if (scale < 1f) Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1), true
            ) else bitmap
        } catch (e: Exception) {
            null
        }
        AppLog.i("Scan", "Act scanat: ${lines.size} linii de text recunoscute")
        Scan(lines, thumb, result)
    }

    /**
     * Citirea pe zone a unui act deja decupat și îndreptat (de scanerul de
     * documente): cardul ocupă toată imaginea, deci putem tăia zonele cunoscute
     * ale cărții de identitate — fără fotografie, banda MRZ, blocul de adresă —
     * și le putem citi mărite și preprocesate separat, apoi cumulăm.
     */
    suspend fun scanCard(context: Context, uri: Uri): Scan = withContext(Dispatchers.IO) {
        val decoded = decodeScaled(context, uri, 2400) ?: return@withContext Scan(emptyList(), null)
        // cardul e landscape; scanerul poate întoarce portret
        val card = if (decoded.height > decoded.width) rotate(decoded, 90f) else decoded
        var result = IdScanResult()
        var lines: List<String> = emptyList()
        fun complete() = result.cnpSure && result.surname.isNotBlank() &&
            result.givenNames.isNotBlank() && result.addressSure
        val passes: List<Pair<String, () -> Bitmap>> = listOf(
            "card" to { card },
            "card contrast" to { enhance(card) },
            // zona de text, fără fotografie (stânga ~24 %) și fără MRZ (jos ~22 %), mărită 1,5×
            "zona text" to { cropRegion(card, 0.24f, 0.0f, 1.0f, 0.78f, 1.5f) },
            "MRZ 2×" to { cropBottom(card, 0.30f, 2f) },
            "MRZ binarizat" to { scale(binarize(cropBottom(card, 0.30f, 1f)), 2f) },
            // blocul de adresă (cartea veche): sub mijloc, în dreapta fotografiei
            "adresă 2×" to { cropRegion(card, 0.24f, 0.50f, 1.0f, 0.80f, 2f) },
            "adresă contrast" to { enhance(cropRegion(card, 0.24f, 0.50f, 1.0f, 0.80f, 2f)) }
        )
        for ((name, make) in passes) {
            if (complete()) break
            val l = try {
                recognize(make())
            } catch (e: Exception) {
                AppLog.w("Scan", "Trecerea „$name” a eșuat", e)
                emptyList()
            }
            val r = IdCardParser.parse(l)
            AppLog.d("Scan", "Trecere $name: ${l.size} linii, cnp=${r.cnpSure}, nume=${r.surname.isNotBlank()}, adresă=${r.addressSure}")
            if (l.size > lines.size) lines = l
            result = IdCardParser.merge(result, r)
        }
        // cartea electronică nu are adresa pe față: dacă nu găsim eticheta, nu mai insistăm
        val thumb = try {
            val s = THUMB_PX.toFloat() / maxOf(card.width, card.height)
            if (s < 1f) scale(card, s) else card
        } catch (e: Exception) {
            null
        }
        AppLog.i("Scan", "Act decupat citit: cnp=${result.cnpSure}, nume=${result.surname.isNotBlank()}, adresă=${result.address.isNotBlank()}")
        Scan(lines, thumb, result)
    }

    /** Decupaj în fracțiuni din lățime/înălțime, opțional mărit. */
    fun cropRegion(src: Bitmap, x0: Float, y0: Float, x1: Float, y1: Float, scaleBy: Float = 1f): Bitmap {
        val l = (src.width * x0).toInt().coerceIn(0, src.width - 1)
        val t = (src.height * y0).toInt().coerceIn(0, src.height - 1)
        val w = ((src.width * x1).toInt() - l).coerceIn(1, src.width - l)
        val h = ((src.height * y1).toInt() - t).coerceIn(1, src.height - t)
        val crop = Bitmap.createBitmap(src, l, t, w, h)
        return if (scaleBy == 1f) crop else scale(crop, scaleBy)
    }

    private fun scale(src: Bitmap, factor: Float): Bitmap = Bitmap.createScaledBitmap(
        src, (src.width * factor).toInt().coerceAtLeast(1), (src.height * factor).toInt().coerceAtLeast(1), true
    )

    /** Binarizare Otsu (prag automat): textul MRZ negru pe fond alb, fără ghioșe. */
    fun binarize(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        val hist = IntArray(256)
        for (i in px.indices) {
            val c = px[i]
            val g = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
            gray[i] = g
            hist[g]++
        }
        val total = w * h
        var sum = 0L
        for (i in 0..255) sum += i.toLong() * hist[i]
        var sumB = 0L
        var wB = 0L
        var best = 0.0
        var thr = 128
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0L) continue
            val wF = total - wB
            if (wF == 0L) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val v = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (v > best) {
                best = v
                thr = t
            }
        }
        for (i in px.indices) px[i] = if (gray[i] > thr) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Cât de completă e citirea: CNP validat cântărește cel mai mult, apoi numele, adresa, volumul de text. */
    private fun score(r: IdScanResult, lineCount: Int): Int =
        (if (r.cnpSure) 100 else if (r.cnp.isNotBlank()) 40 else 0) +
            (if (r.surname.isNotBlank()) 30 else 0) + (if (r.givenNames.isNotBlank()) 20 else 0) +
            (if (r.address.isNotBlank()) 10 else 0) + minOf(lineCount, 20)

    private fun rotate(src: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** Alb-negru cu contrast mărit — textul negru iese mai bine de pe fondul ghioșat al actului. */
    fun enhance(src: Bitmap, contrast: Float = 1.6f): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
        val t = (1f - contrast) * 128f
        cm.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        val paint = android.graphics.Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(cm) }
        android.graphics.Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Banda de jos a imaginii (fracțiune din înălțime), mărită — pentru zona MRZ. */
    fun cropBottom(src: Bitmap, fraction: Float, scale: Float): Bitmap {
        val h = (src.height * fraction).toInt().coerceIn(1, src.height)
        val crop = Bitmap.createBitmap(src, 0, src.height - h, src.width, h)
        return if (scale == 1f) crop
        else Bitmap.createScaledBitmap(crop, (crop.width * scale).toInt(), (crop.height * scale).toInt(), true)
    }

    /** Decodează imaginea la cel mult [maxPx] pe latura lungă și o rotește după EXIF. */
    private fun decodeScaled(context: Context, uri: Uri, maxPx: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // ATENȚIE: cu inJustDecodeBounds decodeStream întoarce mereu null — nu e semn de eroare
        val boundsStream = resolver.openInputStream(uri)
        if (boundsStream == null) {
            AppLog.w("Scan", "Nu pot deschide imaginea: $uri")
            return null
        }
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            AppLog.w("Scan", "Imagine fără dimensiuni (format necunoscut?): $uri")
            return null
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bitmap == null) {
            AppLog.w("Scan", "Decodarea imaginii a eșuat (${bounds.outWidth}×${bounds.outHeight}, sample=$sample)")
            return null
        }
        AppLog.d("Scan", "Imagine decodată ${bitmap.width}×${bitmap.height} (original ${bounds.outWidth}×${bounds.outHeight})")

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private class Line(val top: Int, val left: Int, val height: Int, val text: String)

    /** Liniile recunoscute, ordonate de sus în jos și de la stânga la dreapta. */
    private suspend fun recognize(bitmap: Bitmap): List<String> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val lines = text.textBlocks.flatMap { b ->
                b.lines.map { l ->
                    val r = l.boundingBox
                    Line(r?.top ?: 0, r?.left ?: 0, r?.height() ?: 20, l.text)
                }
            }.sortedBy { it.top }
            return orderLines(lines)
        } finally {
            recognizer.close()
        }
    }

    /** Varianta publică pentru analizorul de cadre: (top, left, height) → text. */
    fun orderLinesPublic(items: List<Pair<Triple<Int, Int, Int>, String>>): List<String> =
        orderLines(items.map { (g, t) -> Line(g.first, g.second, g.third, t) }.sortedBy { it.top })

    /** Grupează liniile pe rânduri (top apropiat) și le ordonează după stânga. */
    private fun orderLines(sorted: List<Line>): List<String> {
        val out = mutableListOf<String>()
        var row = mutableListOf<Line>()
        var rowTop = 0
        sorted.forEach { l ->
            if (row.isEmpty()) {
                row += l
                rowTop = l.top
            } else {
                val tol = (row.map { it.height }.average() * 0.5).toInt().coerceAtLeast(4)
                if (l.top - rowTop <= tol) row += l
                else {
                    out += row.sortedBy { it.left }.map { it.text }
                    row = mutableListOf(l)
                    rowTop = l.top
                }
            }
        }
        if (row.isNotEmpty()) out += row.sortedBy { it.left }.map { it.text }
        return out
    }
}
