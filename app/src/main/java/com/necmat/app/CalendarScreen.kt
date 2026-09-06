package com.necmat.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// ------------------------------------------------------------------ culori și acțiuni

@Composable
fun typeColor(t: AppointmentType): Color = when (t) {
    AppointmentType.VIZITA -> MaterialTheme.colorScheme.primary
    AppointmentType.OFERTA -> MaterialTheme.colorScheme.tertiary
    AppointmentType.EXECUTIE -> Color(0xFF2E7D32)
    AppointmentType.REVIZIE -> Color(0xFFEF6C00)
    AppointmentType.ALTELE -> MaterialTheme.colorScheme.secondary
}

private fun open(context: Context, intent: Intent, error: String) {
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }
}

/** Adaugă programarea în calendarul telefonului (intent INSERT — fără permisiuni). */
fun addToPhoneCalendar(context: Context, a: Appointment) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, listOf(a.type.label, a.clientName).filter { it.isNotBlank() }.joinToString(" — ") + if (a.title.isNotBlank()) " · ${a.title}" else "")
        if (a.address.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, a.address)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, a.start)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, a.end)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, a.allDay)
        val desc = listOf(a.notes, if (a.phone.isNotBlank()) "Tel.: ${a.phone}" else "", "Creat cu NecMat")
            .filter { it.isNotBlank() }.joinToString("\n")
        putExtra(CalendarContract.Events.DESCRIPTION, desc)
    }
    AppLog.i("Calendar", "Programare trimisă în calendarul telefonului: ${a.clientName} ${formatDate(a.start)}")
    open(context, intent, "Nu am găsit o aplicație de calendar")
}

fun sendConfirmationSms(context: Context, phone: String, message: String) = open(
    context,
    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.trim()}")).putExtra("sms_body", message),
    "Nu pot deschide mesajele"
)

fun sendConfirmationWhatsApp(context: Context, phone: String, message: String) = open(
    context,
    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${phoneForWhatsApp(phone)}?text=${Uri.encode(message)}")),
    "WhatsApp nu e disponibil"
)

/** Ora de început propusă pentru o programare nouă: următoarea oră întreagă (după 18:00 → mâine 09:00). */
fun suggestedStart(now: Long): Long {
    val dt = toLocalDateTime(now)
    val next = if (dt.minute == 0) dt else dt.plusHours(1).withMinute(0)
    return if (next.hour >= 18 || next.hour < 7) epochOf(dt.toLocalDate().plusDays(if (next.hour >= 18) 1 else 0), LocalTime.of(9, 0))
    else epochOf(next.toLocalDate(), LocalTime.of(next.hour, 0))
}

// ------------------------------------------------------------------ ecranul Calendar (Agendă)

@Composable
fun CalendarScreen(vm: AppViewModel, onCreateWork: () -> Unit, onDeleted: (String) -> Unit) {
    val context = LocalContext.current
    var creating by remember { mutableStateOf<Appointment?>(null) }
    var editing by remember { mutableStateOf<Appointment?>(null) }
    var deleting by remember { mutableStateOf<Appointment?>(null) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    val now = System.currentTimeMillis()
    val groups = groupForAgenda(vm.appointments, now)
    val next = nextUpcoming(vm.appointments, now)

    Box(Modifier.fillMaxSize()) {
        if (vm.appointments.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nicio programare.\nApasă + pentru o vizită, o ofertă sau o zi de execuție.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 88.dp)
        ) {
            if (next != null) item(key = "next") {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("Următoarea programare", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            buildString {
                                val d = toLocalDate(next.start)
                                append(
                                    when (d) {
                                        toLocalDate(now) -> "Azi"
                                        toLocalDate(now).plusDays(1) -> "Mâine"
                                        else -> formatDayLong(d, toLocalDate(now))
                                    }
                                )
                                append(" · ").append(next.timeLabel())
                                if (next.clientName.isNotBlank()) append(" · ").append(next.clientName)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (next.address.isNotBlank()) Text(
                            next.address, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            groups.forEach { (bucket, list) ->
                item(key = "h${bucket.name}") {
                    Text(
                        "${bucket.label} (${list.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (bucket == AgendaBucket.OVERDUE) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(list, key = { "a${it.id}" }) { a ->
                    AppointmentCard(
                        vm = vm, a = a, now = now,
                        expanded = expandedId == a.id,
                        showDay = true,
                        onToggle = { expandedId = if (expandedId == a.id) null else a.id },
                        onEdit = { editing = a },
                        onDelete = { deleting = a },
                        onCreateWork = {
                            val c = a.clientId?.let { id -> vm.clients.firstOrNull { it.id == id } }
                                ?: Client(0L, a.clientName, a.phone, a.address)
                            vm.prefillNextWork(c)
                            onCreateWork()
                        }
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = {
                creating = Appointment(
                    0L, "", suggestedStart(now), vm.settings.defaultDurationMin,
                    type = AppointmentType.VIZITA
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Programare nouă") }
    }

    creating?.let { a ->
        AppointmentDialog(vm, a, isNew = true, onDismiss = { creating = null }, onSave = {
            vm.upsertAppointment(it)
            creating = null
            Toast.makeText(context, "Programare salvată", Toast.LENGTH_SHORT).show()
        })
    }
    editing?.let { a ->
        AppointmentDialog(vm, a, isNew = false, onDismiss = { editing = null }, onSave = {
            vm.upsertAppointment(it)
            editing = null
        })
    }
    deleting?.let { a ->
        ConfirmDialog(
            title = "Ștergi programarea?",
            text = "„${a.type.label}${if (a.clientName.isNotBlank()) " — ${a.clientName}" else ""}” din ${formatDate(a.start)} va fi ștearsă.",
            onDismiss = { deleting = null },
            onConfirm = {
                vm.deleteAppointment(a.id)
                deleting = null
                onDeleted("Programarea a fost ștearsă")
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppointmentCard(
    vm: AppViewModel,
    a: Appointment,
    now: Long,
    expanded: Boolean,
    showDay: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCreateWork: () -> Unit
) {
    val context = LocalContext.current
    val color = typeColor(a.type)
    val faded = !a.isActive
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onToggle)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(if (faded) color.copy(alpha = 0.3f) else color)
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            if (showDay) append(formatDate(a.start)).append("  ")
                            append(a.timeLabel())
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (faded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Badge(containerColor = color.copy(alpha = if (faded) 0.4f else 1f), contentColor = Color.White) {
                        Text(a.type.label)
                    }
                    if (a.status != AppointmentStatus.PROGRAMAT) {
                        Spacer(Modifier.width(4.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) { Text(a.status.label) }
                    }
                }
                val headline = listOf(a.clientName, a.title).filter { it.isNotBlank() }.joinToString(" · ")
                if (headline.isNotEmpty()) Text(
                    headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (a.address.isNotBlank()) Text(
                    a.address, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis
                )
                if (expanded) {
                    if (a.notes.isNotBlank()) Text(a.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    a.workId?.let { wid ->
                        vm.works.firstOrNull { it.id == wid }?.let { w ->
                            Text("Lucrare: ${w.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    val msg = confirmationMessage(
                        vm.settings.confirmTemplate, a,
                        vm.settings.installerCompany.ifBlank { vm.settings.installerName }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        if (a.phone.isNotBlank()) {
                            AssistChip(onClick = { dialClient(context, a.phone) }, label = { Text("Sună") })
                            AssistChip(onClick = { sendConfirmationSms(context, a.phone, msg) }, label = { Text("Confirmare SMS") })
                            AssistChip(onClick = { sendConfirmationWhatsApp(context, a.phone, msg) }, label = { Text("WhatsApp") })
                        }
                        if (a.address.isNotBlank())
                            AssistChip(onClick = { navigateToClient(context, a.address) }, label = { Text("Navighează") })
                        AssistChip(onClick = { addToPhoneCalendar(context, a) }, label = { Text("Calendarul telefonului") })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (a.status == AppointmentStatus.PROGRAMAT)
                            AssistChip(onClick = { vm.setAppointmentStatus(a.id, AppointmentStatus.CONFIRMAT) }, label = { Text("Confirmat de client") })
                        if (a.isActive)
                            AssistChip(onClick = { vm.setAppointmentStatus(a.id, AppointmentStatus.FINALIZAT) }, label = { Text("Finalizează") })
                        if (a.isActive)
                            AssistChip(onClick = { vm.setAppointmentStatus(a.id, AppointmentStatus.ANULAT) }, label = { Text("Anulează") })
                        if (!a.isActive)
                            AssistChip(onClick = { vm.setAppointmentStatus(a.id, AppointmentStatus.PROGRAMAT) }, label = { Text("Reactivează") })
                        if (a.type == AppointmentType.VIZITA || a.type == AppointmentType.OFERTA)
                            AssistChip(onClick = onCreateWork, label = { Text("Creează lucrare") })
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onEdit) { Text("Editează") }
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Șterge") }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ dialogul de programare

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentDialog(
    vm: AppViewModel,
    initial: Appointment,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Appointment) -> Unit
) {
    var type by remember { mutableStateOf(initial.type) }
    var title by remember { mutableStateOf(initial.title) }
    var clientId by remember { mutableStateOf(initial.clientId) }
    var clientName by remember { mutableStateOf(initial.clientName) }
    var address by remember { mutableStateOf(initial.address) }
    var phone by remember { mutableStateOf(initial.phone) }
    var date by remember { mutableStateOf(toLocalDate(initial.start)) }
    var time by remember { mutableStateOf(toLocalDateTime(initial.start).toLocalTime().withSecond(0).withNano(0)) }
    var allDay by remember { mutableStateOf(initial.allDay) }
    var duration by remember { mutableStateOf(initial.durationMin) }
    var notes by remember { mutableStateOf(initial.notes) }
    var workId by remember { mutableStateOf(initial.workId) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showWorkPicker by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    fun build(): Appointment = initial.copy(
        title = title.trim(), type = type, clientId = clientId, clientName = clientName.trim(),
        address = address.trim(), phone = phone.trim(),
        start = if (allDay) epochOf(date, LocalTime.MIDNIGHT) else epochOf(date, time),
        allDay = allDay, durationMin = duration.coerceIn(15, 24 * 60), notes = notes.trim(), workId = workId
    )

    val candidate = build()
    val conflicts = conflictsOf(candidate, vm.appointments).filter { it.isActive }
    val dirty = candidate != initial
    val requestDismiss = { if (dirty) confirmExit = true else onDismiss() }

    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Ieși din formular?") },
        text = { Text("Modificările se vor pierde.") },
        confirmButton = {
            TextButton(onClick = { confirmExit = false; onDismiss() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Ies fără salvare") }
        },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Rămân") } }
    )

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Anulează") } }
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Ora de început") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); showTime = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Anulează") } }
        )
    }
    if (showWorkPicker) {
        val candidates = vm.works.filter { w ->
            clientId == null || w.clientId == clientId || (phone.isNotBlank() && normalizePhone(w.phone) == normalizePhone(phone))
        }.ifEmpty { vm.works }
        AlertDialog(
            onDismissRequest = { showWorkPicker = false },
            title = { Text("Leagă de o lucrare") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("Fără lucrare", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clickable { workId = null; showWorkPicker = false }.padding(vertical = 8.dp))
                    HorizontalDivider()
                    candidates.forEach { w ->
                        Column(Modifier.fillMaxWidth().clickable {
                            workId = w.id
                            if (clientName.isBlank()) clientName = w.client
                            if (address.isBlank()) address = w.address
                            if (phone.isBlank()) phone = w.phone
                            if (clientId == null) clientId = w.clientId
                            showWorkPicker = false
                        }.padding(vertical = 8.dp)) {
                            Text(w.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            val sub = listOf(w.client, w.address).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWorkPicker = false }) { Text("Închide") } }
        )
    }

    AlertDialog(
        onDismissRequest = { requestDismiss() },
        title = { Text(if (isNew) "Programare nouă" else "Editează programarea") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppointmentType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }
                // client: sugestii din fișele existente
                OutlinedTextField(
                    value = clientName, onValueChange = { clientName = it; if (clientId != null) clientId = null },
                    label = { Text("Client") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (clientId == null) {
                    val sugg = suggestClients(clientName, vm.clients)
                    if (sugg.isNotEmpty()) Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            sugg.forEach { c ->
                                Column(Modifier.fillMaxWidth().clickable {
                                    clientId = c.id; clientName = c.name; address = c.address; phone = c.phone
                                }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(c.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    val sub = listOf(c.phone, c.address).filter { it.isNotBlank() }.joinToString(" · ")
                                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                } else Text("✓ Client din listă", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it }, label = { Text("Telefon") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                        Text("📅 ${date.dayOfMonth}.${date.monthValue.toString().padStart(2, '0')}.${date.year}")
                    }
                    OutlinedButton(onClick = { showTime = true }, enabled = !allDay, modifier = Modifier.weight(1f)) {
                        Text(if (allDay) "toată ziua" else "🕘 ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Toată ziua", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                if (!allDay) {
                    Text("Durata", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 60, 90, 120, 240, 480).forEach { d ->
                            FilterChip(selected = duration == d, onClick = { duration = d },
                                label = { Text(if (d < 60) "$d min" else if (d % 60 == 0) "${d / 60} h" else "${d / 60}h${d % 60}") })
                        }
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titlu / detaliu — opțional") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notițe — opțional") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { showWorkPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    val w = workId?.let { id -> vm.works.firstOrNull { it.id == id } }
                    Text(if (w == null) "Leagă de o lucrare (opțional)" else "Lucrare: ${w.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (conflicts.isNotEmpty()) Text(
                    "⚠ Se suprapune cu: " + conflicts.joinToString("; ") {
                        "${formatDate(it.start)} ${it.timeLabel()}${if (it.clientName.isNotBlank()) " (${it.clientName})" else ""}"
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(candidate) }, enabled = clientName.isNotBlank() || title.isNotBlank()) {
                Text(if (conflicts.isEmpty()) "Salvează" else "Salvează oricum")
            }
        },
        dismissButton = { TextButton(onClick = { requestDismiss() }) { Text("Anulează") } }
    )
}
