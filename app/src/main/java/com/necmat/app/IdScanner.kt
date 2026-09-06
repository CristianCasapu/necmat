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
            }
            if (result.cnpSure && result.surname.isNotBlank()) break
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

    /** Cât de completă e citirea: CNP validat cântărește cel mai mult, apoi numele, adresa, volumul de text. */
    private fun score(r: IdScanResult, lineCount: Int): Int =
        (if (r.cnpSure) 100 else if (r.cnp.isNotBlank()) 40 else 0) +
            (if (r.surname.isNotBlank()) 30 else 0) + (if (r.givenNames.isNotBlank()) 20 else 0) +
            (if (r.address.isNotBlank()) 10 else 0) + minOf(lineCount, 20)

    private fun rotate(src: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** Decodează imaginea la cel mult [maxPx] pe latura lungă și o rotește după EXIF. */
    private fun decodeScaled(context: Context, uri: Uri, maxPx: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

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
