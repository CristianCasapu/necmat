package com.necmat.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as MlText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/** Nivelul ghidajului: roșu = nimic util, galben = ajustează, verde = bun / citit. */
private enum class GuideLevel { NONE, ADJUST, GOOD }

/** Rezultatul analizei unui cadru, calculat pur (testabil) din liniile și casetele recunoscute. */
data class FrameAssessment(
    val status: String,
    val level: Int,               // 0 = roșu, 1 = galben, 2 = verde
    val result: IdScanResult,
    val looksLikeId: Boolean
)

/** Dreptunghi simplu (fără Android), ca evaluarea cadrului să fie testabilă pe JVM. */
data class FrameBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun union(o: FrameBox) = FrameBox(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))
}

/**
 * Evaluează un cadru: e act românesc? e prea departe / prea aproape? e citit?
 * [textUnion] = dreptunghiul care cuprinde tot textul, în coordonatele imaginii;
 * [guide] = chenarul-ghid, în aceleași coordonate.
 */
fun assessFrame(lines: List<String>, textUnion: FrameBox?, guide: FrameBox, result: IdScanResult): FrameAssessment {
    val norm = lines.joinToString(" ") { normalizeName(it) }
    val looksLikeId = listOf("roman", "identit", "cnp", "idrou", "carte de").any { norm.contains(it) }
    if (lines.size < 3 || textUnion == null) {
        return FrameAssessment("Îndreaptă camera spre act, în chenar", 0, result, false)
    }
    val widthRatio = textUnion.width / guide.width
    val outside = textUnion.left < guide.left - guide.width * 0.06f ||
        textUnion.right > guide.right + guide.width * 0.06f ||
        textUnion.top < guide.top - guide.height * 0.1f ||
        textUnion.bottom > guide.bottom + guide.height * 0.1f
    if (result.cnpSure && result.surname.isNotBlank()) {
        return FrameAssessment("Citit ✓ — ține nemișcat", 2, result, true)
    }
    if (!looksLikeId) {
        return FrameAssessment("Nu pare un act de identitate românesc", 0, result, false)
    }
    if (widthRatio < 0.55f) return FrameAssessment("Apropie camera de act", 1, result, true)
    if (outside) return FrameAssessment("Depărtează puțin, actul iese din chenar", 1, result, true)
    if (result.cnpSure && result.surname.isBlank()) {
        return FrameAssessment("Am CNP-ul ✓ — caut numele, ține actul drept", 1, result, true)
    }
    if (result.surname.isNotBlank() && result.cnp.isBlank()) {
        return FrameAssessment("Am numele ✓ — caut CNP-ul, evită reflexiile", 1, result, true)
    }
    if (result.cnp.isNotBlank() && !result.cnpSure) {
        return FrameAssessment("CNP citit greșit — evită reflexiile, mai multă lumină", 1, result, true)
    }
    return FrameAssessment("Ține nemișcat… citesc datele", 1, result, true)
}

/**
 * Scanare cu camera din aplicație, în timp real: previzualizare, chenar-ghid,
 * casetele textului recunoscut, instrucțiuni (apropie / depărtează / act greșit)
 * și captură automată când CNP-ul e validat în două cadre consecutive.
 * Cadrele se procesează în memorie și nu se salvează.
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun IdCameraScanDialog(
    onDismiss: () -> Unit,
    onScanned: (IdScanResult, List<String>, Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasPermission = ok
        if (!ok) {
            Toast.makeText(context, "Fără permisiunea de cameră nu pot scana actul", Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }
    LaunchedEffect(Unit) { if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA) }

    var status by remember { mutableStateOf("Pornesc camera…") }
    var level by remember { mutableStateOf(0) }
    var boxes by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var viewSize by remember { mutableStateOf(Size.Zero) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var lastResult by remember { mutableStateOf(IdScanResult()) }
    var lastLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastFrame by remember { mutableStateOf<Bitmap?>(null) }
    var captured by remember { mutableStateOf(false) }
    var stableCnp by remember { mutableStateOf("") }
    var stableCount by remember { mutableStateOf(0) }
    var accumulated by remember { mutableStateOf(IdScanResult()) }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var providerRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try { providerRef?.unbindAll() } catch (_: Exception) {}
            recognizer.close()
            executor.shutdown()
        }
    }

    /** Chenarul-ghid în coordonatele vederii: 90% lățime, proporția cărții de identitate (85,6 × 54 mm). */
    fun guideRect(w: Float, h: Float): Rect {
        val gw = w * 0.9f
        val gh = gw * 54f / 85.6f
        val left = (w - gw) / 2f
        val top = (h - gh) / 2f - h * 0.06f
        return Rect(left, top, left + gw, top + gh)
    }

    fun finish(result: IdScanResult, lines: List<String>, frame: Bitmap?) {
        if (captured) return
        captured = true
        onScanned(result, lines, frame)
    }

    if (hasPermission) Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { viewSize = Size(it.width.toFloat(), it.height.toFloat()) }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val provider = future.get()
                        providerRef = provider
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(
                                            android.util.Size(1920, 1440),
                                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                        )
                                    )
                                    .build()
                            )
                            .build()
                        var lastTs = 0L
                        analysis.setAnalyzer(executor) { proxy ->
                            val now = System.currentTimeMillis()
                            val media = proxy.image
                            if (captured || media == null || now - lastTs < 350) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            lastTs = now
                            val rot = proxy.imageInfo.rotationDegrees
                            val imgW = if (rot == 90 || rot == 270) proxy.height else proxy.width
                            val imgH = if (rot == 90 || rot == 270) proxy.width else proxy.height
                            // decupăm cadrul la chenarul-ghid (cu margine): mai puțin text de fundal,
                            // rezoluție efectivă mai mare pe act
                            val vs0 = viewSize
                            var cropL = 0
                            var cropT = 0
                            val frameBmp: Bitmap? = try { rotateBitmap(proxy.toBitmap(), rot) } catch (_: Exception) { null }
                            val input = if (frameBmp != null && vs0.width > 0 && vs0.height > 0) {
                                val sc = maxOf(vs0.width / imgW, vs0.height / imgH)
                                val ddx = (vs0.width - imgW * sc) / 2f
                                val ddy = (vs0.height - imgH * sc) / 2f
                                val g = guideRect(vs0.width, vs0.height)
                                val mx = g.width * 0.08f
                                val my = g.height * 0.15f
                                cropL = ((g.left - mx - ddx) / sc).toInt().coerceIn(0, imgW - 2)
                                cropT = ((g.top - my - ddy) / sc).toInt().coerceIn(0, imgH - 2)
                                val cropR = ((g.right + mx - ddx) / sc).toInt().coerceIn(cropL + 1, imgW)
                                val cropB = ((g.bottom + my - ddy) / sc).toInt().coerceIn(cropT + 1, imgH)
                                InputImage.fromBitmap(Bitmap.createBitmap(frameBmp, cropL, cropT, cropR - cropL, cropB - cropT), 0)
                            } else InputImage.fromMediaImage(media, rot)
                            recognizer.process(input)
                                .addOnSuccessListener { text ->
                                    val (lines, rects0) = IdScanner.linesAndBoxes(text)
                                    // casetele înapoi în coordonatele cadrului întreg
                                    val rects = rects0.map { RectF(it.left + cropL, it.top + cropT, it.right + cropL, it.bottom + cropT) }
                                    val frameResult = IdCardParser.parse(lines)
                                    // câmpurile se cumulează între cadre: CNP dintr-unul, numele din altul
                                    accumulated = IdCardParser.merge(accumulated, frameResult)
                                    val result = accumulated
                                    val vs = viewSize
                                    if (vs.width > 0 && vs.height > 0) {
                                        // FILL_CENTER: scalare uniformă + centrare
                                        val scale = maxOf(vs.width / imgW, vs.height / imgH)
                                        val dx = (vs.width - imgW * scale) / 2f
                                        val dy = (vs.height - imgH * scale) / 2f
                                        boxes = rects.map {
                                            Rect(it.left * scale + dx, it.top * scale + dy, it.right * scale + dx, it.bottom * scale + dy)
                                        }
                                        val guideView = guideRect(vs.width, vs.height)
                                        // ghidajul se evaluează în coordonatele imaginii
                                        val guideImg = FrameBox(
                                            (guideView.left - dx) / scale, (guideView.top - dy) / scale,
                                            (guideView.right - dx) / scale, (guideView.bottom - dy) / scale
                                        )
                                        val union = rects.fold<RectF, FrameBox?>(null) { acc, r ->
                                            val b = FrameBox(r.left, r.top, r.right, r.bottom)
                                            acc?.union(b) ?: b
                                        }
                                        val a = assessFrame(lines, union, guideImg, result)
                                        status = a.status
                                        level = a.level
                                    }
                                    lastResult = result
                                    if (lines.size >= lastLines.size) lastLines = lines
                                    if (frameResult.cnpSure) {
                                        if (frameResult.cnp == stableCnp) stableCount++ else { stableCnp = frameResult.cnp; stableCount = 1 }
                                    }
                                    if (result.cnpSure && result.surname.isNotBlank() && result.givenNames.isNotBlank()) {
                                        if (stableCount >= 2) {
                                            lastFrame = frameBmp
                                            finish(result, lines, frameBmp)
                                        }
                                    }
                                }
                                .addOnFailureListener { e -> AppLog.w("Scan", "Cadru nerecunoscut", e) }
                                .addOnCompleteListener { proxy.close() }
                        }
                        try {
                            provider.unbindAll()
                            camera = provider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                            )
                            status = "Încadrează actul în chenar"
                        } catch (e: Exception) {
                            AppLog.e("Scan", "Camera nu a putut porni", e)
                            status = "Camera nu a putut porni"
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            // overlay: întunecare în afara chenarului, chenar colorat, casetele textului
            Canvas(Modifier.fillMaxSize()) {
                val g = guideRect(size.width, size.height)
                val dim = Color.Black.copy(alpha = 0.45f)
                drawRect(dim, Offset.Zero, Size(size.width, g.top))
                drawRect(dim, Offset(0f, g.bottom), Size(size.width, size.height - g.bottom))
                drawRect(dim, Offset(0f, g.top), Size(g.left, g.height))
                drawRect(dim, Offset(g.right, g.top), Size(size.width - g.right, g.height))
                val guideColor = when (level) {
                    2 -> Color(0xFF43A047)
                    1 -> Color(0xFFFFB300)
                    else -> Color(0xFFE53935)
                }
                drawRoundRect(
                    guideColor, topLeft = Offset(g.left, g.top), size = Size(g.width, g.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
                    style = Stroke(width = 6f)
                )
                boxes.forEach { r ->
                    drawRect(
                        Color(0xFF64B5F6).copy(alpha = 0.9f), topLeft = Offset(r.left, r.top),
                        size = Size(r.width, r.height), style = Stroke(width = 2.5f)
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        status,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Act pe fundal închis, fără blitz și reflexii. Se capturează singur când CNP-ul e validat.",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Anulează", color = Color.White) }
                OutlinedButton(onClick = {
                    torch = !torch
                    camera?.cameraControl?.enableTorch(torch)
                }) { Text(if (torch) "💡 Lanterna: pornită" else "💡 Lanternă") }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = lastLines.isNotEmpty(),
                    onClick = { finish(lastResult, lastLines, lastFrame) }
                ) { Text("Folosește ce s-a citit") }
            }
        }
    }
}

private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return src
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** Extensie utilă pentru analizorul de cadre: liniile ordonate + casetele lor. */
fun IdScanner.linesAndBoxes(text: MlText): Pair<List<String>, List<RectF>> {
    val items = text.textBlocks.flatMap { b -> b.lines.map { l -> l to (l.boundingBox) } }
    val rects = items.mapNotNull { (_, r) -> r?.let { RectF(it) } }
    val ordered = orderLinesPublic(items.map { (l, r) ->
        Triple(r?.top ?: 0, r?.left ?: 0, r?.height() ?: 20) to l.text
    })
    return ordered to rects
}
