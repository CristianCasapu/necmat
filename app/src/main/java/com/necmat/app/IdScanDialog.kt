package com.necmat.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Ce se preia din act în formularul de client. */
data class IdScanChoice(val name: String, val cnp: String, val address: String)

/**
 * Confirmarea datelor citite de pe act: miniatura pozei + câmpurile recunoscute,
 * editabile, marcate „✓ verificat” / „? verifică”. Nimic nu se completează
 * în formular până la „Folosește”.
 */
@Composable
fun IdScanConfirmDialog(
    result: IdScanResult,
    thumbnail: Bitmap?,
    showCnp: Boolean,
    onDismiss: () -> Unit,
    onUse: (IdScanChoice) -> Unit,
    rawLines: List<String> = emptyList()
) {
    var showRaw by remember { mutableStateOf(false) }
    var surname by remember { mutableStateOf(result.surname) }
    var given by remember { mutableStateOf(result.givenNames) }
    var cnp by remember { mutableStateOf(result.cnp) }
    var address by remember { mutableStateOf(result.address) }
    val cnpOk = cnp.isBlank() || isValidCnp(cnp)

    @Composable
    fun mark(sure: Boolean, present: Boolean) = Text(
        when {
            !present -> "necitit"
            sure -> "✓ verificat"
            else -> "? verifică"
        },
        style = MaterialTheme.typography.labelSmall,
        color = when {
            !present -> MaterialTheme.colorScheme.error
            sure -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.tertiary
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Date citite de pe act") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                thumbnail?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Actul fotografiat",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                Text(
                    "Verifică și corectează înainte de a folosi. Poza nu se păstrează.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = surname, onValueChange = { surname = it },
                    label = { Text("Nume") }, singleLine = true,
                    supportingText = { mark(result.surnameSure, result.surname.isNotBlank()) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = given, onValueChange = { given = it },
                    label = { Text("Prenume") }, singleLine = true,
                    supportingText = { mark(result.givenSure, result.givenNames.isNotBlank()) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (showCnp) OutlinedTextField(
                    value = cnp,
                    onValueChange = { v -> cnp = v.filter { it.isDigit() }.take(13) },
                    label = { Text("CNP") }, singleLine = true,
                    isError = !cnpOk,
                    supportingText = {
                        if (!cnpOk) Text("CNP invalid") else mark(result.cnpSure, result.cnp.isNotBlank())
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Adresă de domiciliu") },
                    minLines = 1, maxLines = 3,
                    supportingText = {
                        if (result.address.isBlank()) Text(
                            "Cartea electronică nu are adresa pe față — completeaz-o manual sau din locație"
                        ) else mark(result.addressSure, true)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (rawLines.isNotEmpty()) {
                    TextButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "Ascunde textul recunoscut" else "Vezi textul recunoscut (${rawLines.size} linii)")
                    }
                    if (showRaw) Text(
                        rawLines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (result.isEmpty) Text(
                    "Nu am recunoscut niciun câmp. Încearcă o poză mai dreaptă, fără reflexii, " +
                        "cu actul pe un fundal închis și fără blitz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = cnpOk && (surname.isNotBlank() || given.isNotBlank() || cnp.isNotBlank() || address.isNotBlank()),
                onClick = {
                    onUse(
                        IdScanChoice(
                            name = listOf(surname, given).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" "),
                            cnp = if (showCnp) cnp.trim() else "",
                            address = address.trim()
                        )
                    )
                }
            ) { Text("Folosește") }
        },
        dismissButton = {
            Row { TextButton(onClick = onDismiss) { Text("Anulează") } }
        }
    )
}
