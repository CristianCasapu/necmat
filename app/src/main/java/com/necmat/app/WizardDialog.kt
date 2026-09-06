package com.necmat.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Asistentul pentru un necesar nou (v1.35): tip proiect → întrebări → rezumat → aplicare.
 * Nu scrie nimic în editor până la „Aplică”.
 */
@Composable
fun WizardDialog(vm: AppViewModel, onDismiss: () -> Unit, onApplied: (WizardPlan) -> Unit) {
    var step by remember { mutableStateOf(0) }          // 0 tip, 1 întrebări, 2 rezumat
    var kind by remember { mutableStateOf<ProjectKind?>(null) }
    var el by remember { mutableStateOf(ElectricInput()) }
    var pv by remember { mutableStateOf(PvInput()) }
    val hasQty = vm.categories.any { c -> c.materials.any { it.qty > 0 } }
    var replace by remember { mutableStateOf(true) }

    val plan: WizardPlan? = when (kind) {
        ProjectKind.ELECTRIC -> WizardEstimator.estimateElectric(el)
        ProjectKind.PV -> WizardEstimator.estimatePv(pv)
        null -> null
    }
    val title = when (step) {
        0 -> "Necesar nou — ce montăm?"
        1 -> kind?.label ?: ""
        else -> "Rezumat — ${plan?.totalPieces ?: 0} buc în ${plan?.items?.size ?: 0} linii"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (step) {
                    0 -> KindStep(kind) { kind = it }
                    1 -> when (kind) {
                        ProjectKind.ELECTRIC -> ElectricStep(el) { el = it }
                        ProjectKind.PV -> PvStep(pv) { pv = it }
                        null -> {}
                    }
                    else -> SummaryStep(plan!!, hasQty, replace) { replace = it }
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> Button(enabled = kind != null, onClick = { step = 1 }) { Text("Continuă") }
                1 -> Button(
                    enabled = plan != null && plan.items.isNotEmpty(),
                    onClick = { step = 2 }
                ) { Text("Vezi rezumatul") }
                else -> Button(onClick = {
                    val p = plan ?: return@Button
                    vm.applyWizardPlan(p, if (kind == ProjectKind.PV) "pv" else "electric", replace || !hasQty)
                    onApplied(p)
                }) { Text("Aplică") }
            }
        },
        dismissButton = {
            Row {
                if (step > 0) TextButton(onClick = { step-- }) { Text("Înapoi") }
                TextButton(onClick = onDismiss) { Text("Renunță") }
            }
        }
    )
}

@Composable
private fun KindStep(kind: ProjectKind?, onPick: (ProjectKind) -> Unit) {
    Text(
        "Asistentul pregătește un necesar de start cu cantități estimate. " +
            "După aplicare poți modifica orice în Materiale, ca de obicei.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ProjectKind.entries.forEach { k ->
        Surface(
            onClick = { onPick(k) },
            shape = RoundedCornerShape(12.dp),
            color = if (kind == k) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    (if (k == ProjectKind.PV) "☀️ " else "⚡ ") + k.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (k == ProjectKind.ELECTRIC)
                        "Prize, întrerupătoare, doze, cabluri, tablou — pe încăperi"
                    else "Panouri, invertor, baterii, tablouri DC/AC, cabluri + manoperă pe kW",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    // chip-urile se împart pe rânduri de câte două ca să încapă pe telefon
    options.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { o ->
                FilterChip(
                    selected = o == selected,
                    onClick = { onPick(o) },
                    label = { Text(label(o), maxLines = 2) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun CounterRow(label: String, value: Int, onChange: (Int) -> Unit, min: Int = 0, max: Int = 30) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        QtyButton("−", enabled = value > min) { onChange(value - 1) }
        Text(
            "$value", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        QtyButton("+", enabled = value < max) { onChange(value + 1) }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, suffix: String = "") {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.replace(',', '.').filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        suffix = if (suffix.isNotBlank()) ({ Text(suffix) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ElectricStep(i: ElectricInput, onChange: (ElectricInput) -> Unit) {
    var atr by remember { mutableStateOf(if (i.atrKw > 0) fmt(i.atrKw) else "") }
    SectionLabel("Tipul spațiului")
    ChipRow(ElectricType.entries, i.type, { it.label }) { onChange(i.copy(type = it)) }
    SectionLabel("Branșament și ATR")
    ChipRow(Supply.entries, i.supply, { it.label }) { onChange(i.copy(supply = it)) }
    NumberField("Putere aprobată (ATR)", atr, { atr = it; onChange(i.copy(atrKw = it.toDoubleOrNull() ?: 0.0)) }, "kW")
    if (i.type == ElectricType.REZIDENTIAL) {
        SectionLabel("Locuință")
        ChipRow(Dwelling.entries, i.dwelling, { it.label }) { onChange(i.copy(dwelling = it)) }
    }
    SectionLabel("Încăperi (2 prize, 1 întrerupător, 1 bec pe fiecare)")
    CounterRow("Dormitoare", i.bedrooms, { onChange(i.copy(bedrooms = it)) })
    CounterRow("Băi", i.bathrooms, { onChange(i.copy(bathrooms = it)) })
    CounterRow("Bucătării", i.kitchens, { onChange(i.copy(kitchens = it)) })
    CounterRow("Salon / living", i.living, { onChange(i.copy(living = it)) })
    CounterRow(if (i.type == ElectricType.REZIDENTIAL) "Alte încăperi (hol, cămară…)" else "Alte spații (birouri, hale…)",
        i.otherRooms, { onChange(i.copy(otherRooms = it)) }, max = 60)
    SectionLabel("Aparataj")
    ChipRow(ApparatusKind.entries, i.apparatus, { it.label }) { onChange(i.copy(apparatus = it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = i.brand, onValueChange = { onChange(i.copy(brand = it)) },
            label = { Text("Marcă (opțional)") }, singleLine = true, modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = i.brandModel, onValueChange = { onChange(i.copy(brandModel = it)) },
            label = { Text("Model") }, singleLine = true, modifier = Modifier.weight(1f)
        )
    }
    SectionLabel("Starea instalației")
    ChipRow(listOf(true, false), i.newInstall, { if (it) "Nouă, de la zero" else "Existentă / renovare" }) {
        onChange(i.copy(newInstall = it, boxesMounted = if (it) false else i.boxesMounted,
            cablesPulled = if (it) false else i.cablesPulled, keepPanel = if (it) false else i.keepPanel))
    }
    if (i.newInstall) Text(
        "Dozele, tuburile și carcasa tabloului intră în PDF-ul pentru furnizor la această lucrare.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    ) else {
        CheckRow("Dozele sunt deja montate", i.boxesMounted) { onChange(i.copy(boxesMounted = it)) }
        CheckRow("Cablurile sunt deja trase", i.cablesPulled) { onChange(i.copy(cablesPulled = it)) }
        CheckRow("Tabloul există și se păstrează", i.keepPanel) { onChange(i.copy(keepPanel = it)) }
    }
}

@Composable
private fun PvStep(i: PvInput, onChange: (PvInput) -> Unit) {
    var kw by remember { mutableStateOf(fmt(i.kw)) }
    var inv by remember { mutableStateOf(fmt(i.inverterKw)) }
    var bat by remember { mutableStateOf(if (i.batteryKwh > 0) fmt(i.batteryKwh) else "") }
    var rate by remember { mutableStateOf(String.format(Locale.US, "%.2f", i.eurRate)) }
    SectionLabel("Panouri")
    NumberField("Putere instalată", kw, { kw = it; onChange(i.copy(kw = it.toDoubleOrNull() ?: 0.0)) }, "kW")
    ChipRow(listOf(410, 450, 500, 550, 600), i.panelW, { "$it W" }) { onChange(i.copy(panelW = it)) }
    if (i.panels > 0) Text(
        "≈ ${i.panels} panouri de ${i.panelW} W, ${i.strings} ${if (i.strings == 1) "string" else "string-uri"}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    SectionLabel("Invertor și baterii")
    ChipRow(Supply.entries, i.supply, { it.label }) { onChange(i.copy(supply = it)) }
    NumberField("Putere invertor", inv, { inv = it; onChange(i.copy(inverterKw = it.toDoubleOrNull() ?: 0.0)) }, "kW")
    NumberField("Baterii (0 = fără)", bat, { bat = it; onChange(i.copy(batteryKwh = it.toDoubleOrNull() ?: 0.0)) }, "kWh")
    SectionLabel("Montaj")
    ChipRow(RoofKind.entries, i.roof, { it.label }) { onChange(i.copy(roof = it)) }
    SectionLabel("Manoperă pe kW instalat")
    ChipRow(PvDifficulty.entries, i.difficulty, { it.label }) { onChange(i.copy(difficulty = it)) }
    NumberField("Curs euro", rate, { rate = it; onChange(i.copy(eurRate = it.toDoubleOrNull() ?: 0.0)) }, "lei/€")
    if (i.kw > 0 && i.eurRate > 0) Text(
        "Manoperă: ${fmt(i.kw)} kW × ${i.difficulty.eurPerKw.toInt()} € × ${String.format(Locale.US, "%.2f", i.eurRate)} lei " +
            "= ${String.format(Locale.US, "%.0f", i.laborLei)} lei",
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SummaryStep(plan: WizardPlan, hasQty: Boolean, replace: Boolean, onReplace: (Boolean) -> Unit) {
    plan.notes.forEach { n ->
        Text("• $n", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (plan.extraLabor.isNotEmpty()) {
        HorizontalDivider()
        plan.extraLabor.forEach { l ->
            Text("Manoperă: ${l.name} — ${String.format(Locale.US, "%.2f", l.value)} lei",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
    HorizontalDivider()
    plan.items.groupBy { it.category }.forEach { (cat, items) ->
        Text(cat, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp))
        items.forEach { it ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it.material, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${it.qty}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (hasQty) {
        HorizontalDivider()
        Text("Necesarul curent are deja cantități:", style = MaterialTheme.typography.bodyMedium)
        ChipRow(listOf(true, false), replace, { if (it) "Înlocuiește-l" else "Adaugă peste el" }, onReplace)
    }
}

private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
