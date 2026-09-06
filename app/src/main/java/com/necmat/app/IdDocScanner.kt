package com.necmat.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Detectarea conturului actului și îndreptarea lui (perspectivă), cu scanerul
 * de documente ML Kit livrat prin serviciile Google Play: ghidaj live pe
 * margini, captură automată, decupare și corecție. Rezultatul e imaginea
 * cardului „la plan”, pe care rulăm apoi citirea pe zone ([IdScanner.scanCard]).
 * Nu cere permisiuni; pe telefoane fără servicii Google rămâne camera live proprie.
 */
object IdDocScanner {

    fun isAvailable(context: Context): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    } catch (_: Throwable) {
        false
    }

    private fun options(): GmsDocumentScannerOptions = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setPageLimit(1)
        .setGalleryImportAllowed(true)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .build()

    /** Pornește scanerul; la eșec (modul nedescărcat, fără Play) apelează [onError]. */
    fun start(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onError: (Exception) -> Unit
    ) {
        try {
            GmsDocumentScanning.getClient(options())
                .getStartScanIntent(activity)
                .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { e ->
                    AppLog.w("Scan", "Scanerul de documente nu a pornit", e)
                    onError(e)
                }
        } catch (e: Exception) {
            AppLog.w("Scan", "Scanerul de documente indisponibil", e)
            onError(e)
        }
    }

    /** Imaginea decupată și îndreptată a actului, sau null. */
    fun resultUri(data: Intent?): Uri? = try {
        GmsDocumentScanningResult.fromActivityResultIntent(data)?.pages?.firstOrNull()?.imageUri
    } catch (_: Exception) {
        null
    }
}

/** Activitatea din spatele unui context Compose (necesară scanerului de documente). */
fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
