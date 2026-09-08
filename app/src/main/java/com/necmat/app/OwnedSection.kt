package com.necmat.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Materialul ales pentru introducerea cantității deținute de client. */
private data class OwnedPick(val item: OwnedMaterial, val needed: Int)

/**
 * Secțiunea expandabilă „Materiale existente la client” din pagina Necesar.
 * Ce se adaugă aici se scade din lista de cumpărături DUPĂ calculul
 * accesoriilor automate (vezi [Work.shoppingList]). Din v1.36 liniile sunt
 * grupate pe categorii, iar potrivirea se face după cheia materialului.
 */
@Composable
fun OwnedSection(vm: AppViewModel) {
    val lines = vm.purchasableLines()
    val needed = remember(lines) {
        val m = mutableMapOf<String, Int>()
        lines.forEach { c ->
            c.materials.forEach { mat -> m.merge(mat.matchKey(), mat.qty, Int::plus) }
        }
        m
    }
    var showPicker by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<OwnedPick?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val expanded = vm.ownedExpanded
    val totalOwned = vm.owned.sumOf { it.qty }

    // ordinea categoriilor din lista de cumpărături (grupuri → categorii)
    val catOrder = remember(lines) { lines.mapIndexed { i, c -> c.name to i }.toMap() }
    val grouped = vm.owned
        .groupBy { it.category }
        .entries
        .sortedBy { catOrder[it.key] ?: Int.MAX_VALUE }

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.toggleOwnedExpanded() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Materiale existente la client",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (vm.owned.isNotEmpty()) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ) { Text("${vm.owned.size} · $totalOwned buc") }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (expanded) "▲" else "▼",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Text(
                    "Se scad din lista de cumpărături (PDF), după calculul ramelor și " +
                        "obturatoarelor. Oferta de manoperă rămâne pe necesarul complet.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                if (vm.owned.isEmpty()) Text(
                    "Nimic adăugat încă.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                grouped.forEach { (catName, items) ->
                    if (catName.isNotBlank()) Text(
                        catName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                    items.forEach { o ->
                        val need = needed[o.matchKey()] ?: 0
                        OwnedRow(
                            item = o,
                            needed = need,
                            onMinus = { vm.setOwned(o, o.qty - 1) },
                            onPlus = { vm.setOwned(o, o.qty + 1) },
                            onQtyClick = { pick = OwnedPick(o, need) }
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showPicker = true },
                        enabled = lines.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Adaugă material existent") }
                    if (vm.owned.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Golește") }
                }
                Text(
                    "Verifică dacă materialele clientului sunt compatibile cu marca aleasă " +
                        "(ex. modulele altui producător nu intră în ramele alese).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    if (confirmClear) ConfirmDialog(
        title = "Golești lista?",
        text = "Toate materialele existente la client sunt scoase din această lucrare.",
        onDismiss = { confirmClear = false },
        onConfirm = { vm.clearOwned(); confirmClear = false }
    )

    if (showPicker) OwnedPickerDialog(
        lines = lines,
        owned = vm.owned,
        onDismiss = { showPicker = false },
        onPick = { cat, mat ->
            showPicker = false
            pick = OwnedPick(
                OwnedMaterial(mat.name, 0, cat.name, key = mat.key, catKey = cat.kind),
                mat.qty
            )
        }
    )
    pick?.let { p ->
        val current = vm.owned.firstOrNull { it.matchKey() == p.item.matchKey() }?.qty ?: 0
        NumberDialog(
            title = "${p.item.name} — câte are clientul? (necesar ${p.needed} ${p.item.unit})",
            initial = if (current > 0) current else p.needed,
            onDismiss = { pick = null },
            onConfirm = { q ->
                vm.setOwned(p.item, q.coerceAtMost(p.needed))
                pick = null
            }
        )
    }
}

@Composable
private fun OwnedRow(
    item: OwnedMaterial,
    needed: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onQtyClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Necesar $needed · Are ${item.qty} · De cumpărat ${(needed - item.qty).coerceAtLeast(0)} ${item.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        QtyButton("−", enabled = item.qty > 0, onClick = onMinus)
        Text(
            "${item.qty}",
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(40.dp)
                .clickable(onClick = onQtyClick)
        )
        QtyButton("+", enabled = item.qty < needed, onClick = onPlus)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

/** Alege dintre liniile care ar intra în PDF (necesar + accesorii calculate), pe grupuri. */
@Composable
private fun OwnedPickerDialog(
    lines: List<Category>,
    owned: List<OwnedMaterial>,
    onDismiss: () -> Unit,
    onPick: (Category, Material) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val ownedMap = owned.associate { it.matchKey() to it.qty }
    val filtered = lines
        .map { c ->
            c.copy(materials = c.materials.filter {
                query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)
            })
        }
        .filter { it.materials.isNotEmpty() }
    val sections = groupSections(filtered)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ce are deja clientul?") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Caută în necesar…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) Text(
                    "Niciun material în lista de cumpărături.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    sections.forEach { section ->
                        if (section.showHeader) item(key = "pg${section.group.name}") {
                            Text(
                                section.group.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                        section.categories.forEach { cat ->
                            item(key = "pc${cat.id}") {
                                Text(
                                    cat.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(cat.materials, key = { "pm${cat.id}_${it.id}" }) { m ->
                                val have = ownedMap[m.matchKey()] ?: 0
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(cat, m) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        m.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (have > 0) "are $have / ${m.qty}" else "necesar ${m.qty} ${cat.unit}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (have > 0) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Închide") } }
    )
}
