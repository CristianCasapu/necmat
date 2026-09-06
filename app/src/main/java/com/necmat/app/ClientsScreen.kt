package com.necmat.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ formular refolosibil

/**
 * Starea formularului de client — aceeași în pagina Clienți și în formularul
 * de lucrare (salvare / PDF / ofertă).
 */
@Stable
class ClientFormState(initial: Client?) {
    var clientId by mutableStateOf(initial?.id)
    var name by mutableStateOf(initial?.name ?: "")
    var phone by mutableStateOf(initial?.phone ?: "")
    var address by mutableStateOf(initial?.address ?: "")
    var email by mutableStateOf(initial?.email ?: "")
    var cnp by mutableStateOf(initial?.cnp ?: "")
    var notes by mutableStateOf(initial?.notes ?: "")

    fun setFromClient(c: Client) {
        clientId = c.id
        name = c.name
        phone = c.phone
        address = c.address
        email = c.email
        cnp = c.cnp
        notes = c.notes
    }

    /** Din lucrare vin doar câmpurile pe care le are lucrarea. */
    fun setFromWork(w: Work) {
        clientId = w.clientId
        name = w.client
        phone = w.phone
        address = w.address
    }

    fun unlink() {
        clientId = null
    }

    fun toClient(): Client = Client(
        clientId ?: 0L, name.trim(), phone.trim(), address.trim(),
        email.trim(), cnp.trim(), notes.trim()
    )

    private fun Client.essentials() = copy(id = 0L, createdAt = 0L, updatedAt = 0L)

    fun differsFrom(initial: Client?): Boolean =
        toClient().essentials() != (initial ?: Client(0L, "")).essentials()

    val emailError get() = !isValidEmail(email)
    val cnpError get() = cnp.isNotBlank() && !isValidCnp(cnp)
}

@Composable
fun rememberClientFormState(initial: Client? = null): ClientFormState =
    remember { ClientFormState(initial) }

/**
 * Câmpurile clientului: nume (cu sugestii din clienții existenți), telefon (din
 * agendă), adresă (din locație) și, cu [showExtra], e-mail / CNP / notițe.
 */
@Composable
fun ClientFields(
    form: ClientFormState,
    clients: List<Client>,
    storeCnp: Boolean,
    showExtra: Boolean,
    nameLabel: String = "Client — opțional"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locBusy by remember { mutableStateOf(false) }

    fun fetchAddress() {
        if (locBusy) return
        locBusy = true
        scope.launch {
            val loc = LocationHelper.currentLocation(context)
            val addr = loc?.let { LocationHelper.reverseGeocode(it.latitude, it.longitude) }
            locBusy = false
            when {
                addr != null -> form.address = addr
                loc == null -> Toast.makeText(
                    context, "Locația nu a putut fi determinată — pornește GPS-ul", Toast.LENGTH_LONG
                ).show()
                else -> Toast.makeText(
                    context, "Nu am găsit o adresă pentru locația curentă", Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val locPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.any { it }) fetchAddress()
        else Toast.makeText(
            context, "Fără permisiunea de locație nu pot detecta adresa", Toast.LENGTH_LONG
        ).show()
    }

    val contactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { form.phone = it }
                    val displayName = c.getString(1)
                    if (form.name.isBlank() && !displayName.isNullOrBlank()) form.name = displayName
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Nu am putut citi contactul", Toast.LENGTH_LONG).show()
        }
    }

    // ---- scanarea actului de identitate (ML Kit, offline, pe dispozitiv) ----
    var scanBusy by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<IdScanResult?>(null) }
    var scanThumb by remember { mutableStateOf<Bitmap?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    fun runScan(uri: Uri) {
        if (scanBusy) return
        scanBusy = true
        scope.launch {
            val scan = try {
                IdScanner.scan(context, uri)
            } catch (e: Exception) {
                AppLog.e("Scan", "Eroare la scanarea actului", e)
                IdScanner.Scan(emptyList(), null)
            }
            IdScanner.cleanup(context)
            scanBusy = false
            if (scan.lines.isEmpty()) {
                Toast.makeText(
                    context, "Nu am putut citi imaginea — încearcă o poză mai clară", Toast.LENGTH_LONG
                ).show()
            } else {
                scanResult = IdCardParser.parse(scan.lines)
                scanThumb = scan.thumbnail
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val u = captureUri
        if (ok && u != null) runScan(u) else IdScanner.cleanup(context)
    }
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) runScan(uri) }

    scanResult?.let { r ->
        IdScanConfirmDialog(
            result = r,
            thumbnail = scanThumb,
            showCnp = storeCnp,
            onDismiss = { scanResult = null; scanThumb = null },
            onUse = { ch ->
                if (ch.name.isNotBlank()) form.name = ch.name
                if (ch.address.isNotBlank()) form.address = ch.address
                if (ch.cnp.isNotBlank()) form.cnp = ch.cnp
                scanResult = null
                scanThumb = null
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    try {
                        val (u, _) = IdScanner.newCaptureUri(context)
                        captureUri = u
                        cameraLauncher.launch(u)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Camera nu e disponibilă", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !scanBusy,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1f)
            ) { Text("📷 Scanează buletinul", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            OutlinedButton(
                onClick = {
                    imageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !scanBusy,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1f)
            ) { Text("🖼 Din imagine", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (scanBusy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Se citește actul… (pe telefon, fără internet)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = form.name, onValueChange = { form.name = it },
            label = { Text(nameLabel) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        val linked = form.clientId?.let { id -> clients.firstOrNull { it.id == id } }
        if (linked != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Check, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Client existent: ${linked.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { form.unlink() }) { Text("Dezleagă") }
            }
        } else if (clients.isNotEmpty()) {
            val suggestions = suggestClients(form.name, clients)
            if (suggestions.isNotEmpty()) Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "Clienți existenți — apasă pentru a completa:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                    suggestions.forEach { c ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { form.setFromClient(c) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(c.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            val sub = listOf(c.phone, c.address).filter { it.isNotBlank() }.joinToString("  ·  ")
                            if (sub.isNotEmpty()) Text(
                                sub, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = form.phone, onValueChange = { form.phone = it },
            label = { Text("Telefon — opțional") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            trailingIcon = {
                IconButton(onClick = {
                    try {
                        contactLauncher.launch(
                            Intent(Intent.ACTION_PICK).apply {
                                type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
                            }
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "Agenda de contacte nu e disponibilă", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Icon(
                        Icons.Default.Person, contentDescription = "Alege din contacte",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.address, onValueChange = { form.address = it },
            label = { Text("Adresă — opțional") },
            singleLine = true,
            trailingIcon = {
                if (locBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else IconButton(onClick = {
                    if (LocationHelper.hasPermission(context)) fetchAddress()
                    else locPermLauncher.launch(LocationHelper.PERMISSIONS)
                }) {
                    Icon(
                        Icons.Default.LocationOn, contentDescription = "Detectează adresa din locație",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (showExtra) {
            OutlinedTextField(
                value = form.email, onValueChange = { form.email = it },
                label = { Text("E-mail — opțional") },
                singleLine = true,
                isError = form.emailError,
                supportingText = if (form.emailError) ({ Text("Adresă de e-mail invalidă") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            if (storeCnp) OutlinedTextField(
                value = form.cnp,
                onValueChange = { v -> form.cnp = v.filter { it.isDigit() }.take(13) },
                label = { Text("CNP — opțional") },
                singleLine = true,
                isError = form.cnpError,
                supportingText = {
                    Text(
                        if (form.cnpError) "CNP invalid (13 cifre, cifră de control)"
                        else "Rămâne doar pe telefon; nu apare în PDF"
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.notes, onValueChange = { form.notes = it },
                label = { Text("Notițe — opțional") },
                minLines = 2, maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Dialog de adăugare / editare client, cu avertisment la duplicat și protecție la ieșire. */
@Composable
fun ClientFormDialog(
    vm: AppViewModel,
    initial: Client?,
    onDismiss: () -> Unit,
    onSaved: (Client) -> Unit,
    onOpenExisting: (Client) -> Unit = onSaved
) {
    val form = rememberClientFormState(initial)
    var confirmExit by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf<Client?>(null) }
    val dirty = form.differsFrom(initial)
    val requestDismiss = { if (dirty) confirmExit = true else onDismiss() }
    val canSave = form.name.isNotBlank() && !form.emailError && !form.cnpError

    fun candidate(): Client = form.toClient().copy(
        id = form.clientId ?: initial?.id ?: 0L,
        createdAt = initial?.createdAt ?: 0L
    )

    fun save(force: Boolean) {
        val c = candidate()
        if (!force) {
            val dup = findDuplicateClient(c, vm.clients)
            if (dup != null) {
                duplicate = dup
                return
            }
        }
        onSaved(vm.upsertClient(c))
    }

    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Ieși din formular?") },
        text = { Text("Datele completate se vor pierde.") },
        confirmButton = {
            TextButton(
                onClick = { confirmExit = false; onDismiss() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Ies fără salvare") }
        },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Rămân în formular") } }
    )

    duplicate?.let { dup ->
        AlertDialog(
            onDismissRequest = { duplicate = null },
            title = { Text("Client existent") },
            text = {
                Text(
                    "Există deja „${dup.name}” cu același telefon sau aceeași adresă" +
                        (if (dup.phone.isNotBlank()) " (${dup.phone})" else "") + ".\n\n" +
                        "Îl deschizi pe cel existent sau salvezi oricum un client nou?"
                )
            },
            confirmButton = {
                TextButton(onClick = { duplicate = null; onOpenExisting(dup) }) { Text("Deschide-l") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { duplicate = null }) { Text("Anulează") }
                    TextButton(onClick = { duplicate = null; save(force = true) }) { Text("Salvează oricum") }
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = { requestDismiss() },
        title = { Text(if (initial == null) "Client nou" else "Editează clientul") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ClientFields(
                    form = form,
                    // la editare nu oferim sugestii (ar lega fișa de altă fișă)
                    clients = if (initial == null) vm.clients else emptyList(),
                    storeCnp = vm.settings.storeCnp,
                    showExtra = true,
                    nameLabel = "Nume și prenume"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { save(force = false) }, enabled = canSave) { Text("Salvează") }
        },
        dismissButton = { TextButton(onClick = { requestDismiss() }) { Text("Anulează") } }
    )
}

// ------------------------------------------------------------------ acțiuni rapide (intenții)

private fun openSafely(context: Context, intent: Intent, error: String) {
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }
}

fun dialClient(context: Context, phone: String) =
    openSafely(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")), "Nu pot deschide telefonul")

fun smsClient(context: Context, phone: String) =
    openSafely(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.trim()}")), "Nu pot deschide mesajele")

fun whatsappClient(context: Context, phone: String) = openSafely(
    context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${phoneForWhatsApp(phone)}")),
    "WhatsApp nu e disponibil"
)

fun emailClient(context: Context, email: String) =
    openSafely(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${email.trim()}")), "Nu pot deschide e-mailul")

fun navigateToClient(context: Context, address: String) = openSafely(
    context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}")),
    "Nu am găsit o aplicație de hărți"
)

/**
 * Deschide aplicația Contacte cu un contact nou pre-completat (nume, telefon,
 * e-mail, adresă, notă). Nu cere permisiunea de contacte; CNP-ul nu se trimite.
 */
fun saveClientToContacts(context: Context, c: Client) {
    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        putExtra(ContactsContract.Intents.Insert.NAME, c.name)
        if (c.phone.isNotBlank()) {
            putExtra(ContactsContract.Intents.Insert.PHONE, c.phone)
            putExtra(
                ContactsContract.Intents.Insert.PHONE_TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            )
        }
        if (c.email.isNotBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, c.email)
        if (c.address.isNotBlank()) putExtra(ContactsContract.Intents.Insert.POSTAL, c.address)
        putExtra(
            ContactsContract.Intents.Insert.NOTES,
            listOf("Client NecMat", c.notes).filter { it.isNotBlank() }.joinToString("\n")
        )
    }
    AppLog.i("Clienti", "Contact trimis către agenda telefonului: ${c.name}")
    openSafely(context, intent, "Agenda de contacte nu e disponibilă")
}

// ------------------------------------------------------------------ pagina Clienți

@Composable
fun ClientsScreen(vm: AppViewModel, onNewWork: () -> Unit, onDeleted: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Client?>(null) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    val q = query.trim()
    val list = vm.clients
        .filter { c ->
            q.isEmpty() || normalizeName(c.name).contains(normalizeName(q)) ||
                normalizePhone(c.phone).contains(q.filter { it.isDigit() }.ifEmpty { "§" }) ||
                c.email.contains(q, ignoreCase = true) ||
                normalizeName(c.address).contains(normalizeName(q))
        }
        .sortedBy { normalizeName(it.name) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Caută client (nume, telefon, e-mail, adresă)…") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Text("✕", fontSize = 16.sp) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 6.dp)
            )
            if (vm.clients.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Niciun client încă.\nApasă + pentru a adăuga unul sau salvează o lucrare cu date de client.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (list.isEmpty()) {
                Text(
                    "Niciun client nu se potrivește căutării.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 88.dp)
            ) {
                items(list, key = { "cl${it.id}" }) { c ->
                    ClientRow(
                        client = c,
                        worksCount = worksOfClient(vm.works, c).size,
                        onClick = { selectedId = c.id }
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Client nou") }
    }

    if (creating) ClientFormDialog(
        vm = vm, initial = null,
        onDismiss = { creating = false },
        onSaved = { creating = false; selectedId = it.id },
        onOpenExisting = { creating = false; selectedId = it.id }
    )
    editing?.let { c ->
        ClientFormDialog(
            vm = vm, initial = c,
            onDismiss = { editing = null },
            onSaved = { editing = null; selectedId = it.id }
        )
    }
    selectedId?.let { id ->
        val c = vm.clients.firstOrNull { it.id == id }
        if (c == null) selectedId = null
        else ClientDetailDialog(
            vm = vm, client = c,
            onDismiss = { selectedId = null },
            onEdit = { selectedId = null; editing = c },
            onNewWork = {
                vm.prefillNextWork(c)
                selectedId = null
                onNewWork()
            },
            onDelete = {
                vm.deleteClient(c.id)
                selectedId = null
                onDeleted("Clientul „${c.name}” a fost șters")
            }
        )
    }
}

@Composable
private fun ClientRow(client: Client, worksCount: Int, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    client.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val sub = listOf(client.phone, client.locality).filter { it.isNotBlank() }.joinToString("  ·  ")
                if (sub.isNotEmpty()) Text(
                    sub, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (worksCount > 0) Badge(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) { Text(if (worksCount == 1) "1 lucrare" else "$worksCount lucrări") }
        }
    }
}

/** Fișa clientului: date, acțiuni rapide, istoricul lucrărilor și butoanele de administrare. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClientDetailDialog(
    vm: AppViewModel,
    client: Client,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onNewWork: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val df = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val works = worksOfClient(vm.works, client).sortedByDescending { it.date }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Ștergi clientul?") },
        text = { Text("„${client.name}” dispare din listă. Lucrările lui rămân salvate, doar legătura cu fișa se pierde.") },
        confirmButton = {
            TextButton(
                onClick = { confirmDelete = false; onDelete() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Șterge") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anulează") } }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(client.name) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                @Composable
                fun infoRow(label: String, value: String) {
                    if (value.isBlank()) return
                    Row {
                        Text(
                            label, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(72.dp)
                        )
                        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
                infoRow("Telefon", client.phone)
                infoRow("E-mail", client.email)
                infoRow("Adresă", client.address)
                if (vm.settings.storeCnp) infoRow("CNP", maskCnp(client.cnp))
                infoRow("Notițe", client.notes)
                if (client.phone.isBlank() && client.address.isBlank() && client.email.isBlank()) Text(
                    "Fără date de contact — apasă „Editează”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (client.phone.isNotBlank()) {
                        AssistChip(onClick = { dialClient(context, client.phone) }, label = { Text("Sună") })
                        AssistChip(onClick = { smsClient(context, client.phone) }, label = { Text("SMS") })
                        AssistChip(onClick = { whatsappClient(context, client.phone) }, label = { Text("WhatsApp") })
                    }
                    if (client.email.isNotBlank())
                        AssistChip(onClick = { emailClient(context, client.email) }, label = { Text("E-mail") })
                    if (client.address.isNotBlank())
                        AssistChip(onClick = { navigateToClient(context, client.address) }, label = { Text("Hartă") })
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    if (works.isEmpty()) "Nicio lucrare salvată pentru acest client."
                    else "Lucrări (${works.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                works.forEach { w ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(w.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${df.format(Date(w.date))}  ·  ${w.totalTypes} tipuri  ·  ${w.totalPieces} buc",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (w.isTemplate) Badge { Text("ȘABLON") }
                    }
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { saveClientToContacts(context, client) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salvează în agenda telefonului") }
                OutlinedButton(onClick = onNewWork, modifier = Modifier.fillMaxWidth()) {
                    Text("Lucrare nouă pentru acest client")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Editează") }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("Șterge") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Închide") } }
    )
}
