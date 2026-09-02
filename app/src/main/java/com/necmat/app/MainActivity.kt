package com.necmat.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.necmat.app.ui.NecMatTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            NecMatTheme(vm.themeMode) {
                App(vm)
            }
        }
    }
}

private enum class Screen { MATERIALS, SUMMARY, WORKS, SETTINGS }

/** Generează PDF-ul unei lucrări, anunță salvarea în Descărcări și deschide partajarea. */
private fun exportPdfAndShare(context: Context, vm: AppViewModel, work: Work) {
    try {
        val s = vm.settings
        val result = PdfExporter.export(
            context, vm.preparePdfWork(work),
            s.installerName, s.installerPhone, s.installerCompany
        )
        val msg = if (result.savedToDownloads)
            "PDF salvat în Descărcări/NecMat: ${result.fileName}"
        else "PDF generat: ${result.fileName}"
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, result.shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Trimite PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Eroare la generarea PDF", Toast.LENGTH_LONG).show()
    }
}

/** Scrie backup-ul ca fișier JSON și deschide partajarea. */
private fun exportBackupAndShare(context: Context, json: String) {
    try {
        val df = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault())
        val fileName = "NecMat-backup-${df.format(Date())}.json"
        val dir = File(context.cacheDir, "backup").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(json)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Salvează backup-ul"))
    } catch (e: Exception) {
        Toast.makeText(context, "Eroare la exportul datelor", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: AppViewModel) {
    var screen by remember { mutableStateOf(Screen.MATERIALS) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDefaults by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val ok = try {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?.let { vm.restoreBackup(it) } ?: false
            } catch (e: Exception) {
                false
            }
            Toast.makeText(
                context,
                if (ok) "Date restaurate din backup" else "Fișier de backup invalid",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) { vm.autoCheckUpdate() }

    val totalPieces = vm.categories.sumOf { c -> c.materials.sumOf { it.qty } }
    val totalTypes = vm.categories.sumOf { c -> c.materials.count { it.qty > 0 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NecMat", fontWeight = FontWeight.Bold)
                        if (totalPieces > 0) Text(
                            "$totalTypes tipuri · $totalPieces buc",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        vm.setTheme(
                            when (vm.themeMode) {
                                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.SYSTEM
                            }
                        )
                    }) {
                        Text(
                            when (vm.themeMode) {
                                ThemeMode.SYSTEM -> "🌓"
                                ThemeMode.LIGHT -> "☀️"
                                ThemeMode.DARK -> "🌙"
                            },
                            fontSize = 20.sp
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Meniu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Adaugă categorie") },
                            onClick = { menuOpen = false; showAddCategory = true })
                        DropdownMenuItem(
                            text = { Text("Golește cantitățile") },
                            onClick = { menuOpen = false; confirmReset = true })
                        DropdownMenuItem(
                            text = { Text("Pliază toate categoriile") },
                            onClick = { menuOpen = false; vm.setAllCollapsed(true) })
                        DropdownMenuItem(
                            text = { Text("Despliază toate categoriile") },
                            onClick = { menuOpen = false; vm.setAllCollapsed(false) })
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == Screen.MATERIALS,
                    onClick = { screen = Screen.MATERIALS },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Materiale") }
                )
                NavigationBarItem(
                    selected = screen == Screen.SUMMARY,
                    onClick = { screen = Screen.SUMMARY },
                    icon = {
                        if (totalTypes > 0) BadgedBox(badge = { Badge { Text("$totalTypes") } }) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        } else Icon(Icons.Default.Check, contentDescription = null)
                    },
                    label = { Text("Necesar") }
                )
                NavigationBarItem(
                    selected = screen == Screen.WORKS,
                    onClick = { screen = Screen.WORKS },
                    icon = {
                        if (vm.works.isNotEmpty()) BadgedBox(badge = { Badge { Text("${vm.works.size}") } }) {
                            Icon(Icons.Default.Build, contentDescription = null)
                        } else Icon(Icons.Default.Build, contentDescription = null)
                    },
                    label = { Text("Lucrări") }
                )
                NavigationBarItem(
                    selected = screen == Screen.SETTINGS,
                    onClick = { screen = Screen.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Setări") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (screen) {
                Screen.MATERIALS -> MaterialsScreen(vm)
                Screen.SUMMARY -> SummaryScreen(vm, onSaved = { screen = Screen.WORKS })
                Screen.WORKS -> WorksScreen(vm, onLoaded = { screen = Screen.MATERIALS })
                Screen.SETTINGS -> SettingsScreen(
                    vm = vm,
                    onExport = { exportBackupAndShare(context, vm.backupJson()) },
                    onImport = { confirmImport = true },
                    onRestoreDefaults = { confirmDefaults = true },
                    onCheckUpdates = {
                        Toast.makeText(context, "Se verifică…", Toast.LENGTH_SHORT).show()
                        vm.checkUpdateNow { found ->
                            if (!found) Toast.makeText(
                                context,
                                "Ai deja ultima versiune (v${BuildConfig.VERSION_NAME})",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
    }

    if (showAddCategory) TextDialog(
        title = "Categorie nouă",
        label = "Nume categorie",
        onDismiss = { showAddCategory = false },
        onConfirm = { vm.addCategory(it); showAddCategory = false }
    )
    if (confirmReset) ConfirmDialog(
        title = "Golești cantitățile?",
        text = "Toate cantitățile revin la 0. Lista de materiale rămâne neschimbată.",
        onDismiss = { confirmReset = false },
        onConfirm = { vm.resetQuantities(); confirmReset = false }
    )
    if (confirmDefaults) CountdownConfirmDialog(
        title = "⚠️ Restaurezi lista implicită?",
        text = "Se ȘTERG toate categoriile și materialele tale — inclusiv cantitățile, " +
            "prețurile, mărcile și ordinea setată — și se înlocuiesc cu lista inițială " +
            "a aplicației.\n\nLucrările salvate NU sunt afectate.",
        confirmLabel = "Restaurează",
        onDismiss = { confirmDefaults = false },
        onConfirm = { vm.restoreDefaults(); confirmDefaults = false }
    )
    if (confirmImport) ConfirmDialog(
        title = "Imporți date din backup?",
        text = "Materialele și lucrările actuale vor fi ÎNLOCUITE cu cele din fișierul de backup.",
        onDismiss = { confirmImport = false },
        onConfirm = { confirmImport = false; importLauncher.launch("application/json") }
    )
    vm.updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!vm.updateBusy) vm.dismissUpdate() },
            title = { Text("Versiune nouă: v${info.versionName}") },
            text = {
                Column {
                    if (vm.updateBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Se descarcă actualizarea…")
                        }
                    } else {
                        Text(
                            if (info.notes.isBlank()) "Este disponibilă o versiune nouă a aplicației."
                            else info.notes.take(600),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !vm.updateBusy,
                    onClick = {
                        vm.installUpdate {
                            Toast.makeText(
                                context, "Descărcarea a eșuat. Încearcă mai târziu.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) { Text("Actualizează") }
            },
            dismissButton = {
                TextButton(enabled = !vm.updateBusy, onClick = { vm.dismissUpdate() }) {
                    Text("Mai târziu")
                }
            }
        )
    }
}

@Composable
private fun MaterialsScreen(vm: AppViewModel) {
    var query by remember { mutableStateOf("") }
    var onlySelected by remember { mutableStateOf(false) }
    var editMaterial by remember { mutableStateOf<Pair<Category, Material>?>(null) }
    var qtyMaterial by remember { mutableStateOf<Pair<Category, Material>?>(null) }
    var addToCategory by remember { mutableStateOf<Category?>(null) }
    var editCategory by remember { mutableStateOf<Category?>(null) }
    var deleteCat by remember { mutableStateOf<Category?>(null) }
    var brandCategory by remember { mutableStateOf<Category?>(null) }

    val filterActive = query.isNotBlank() || onlySelected

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Caută material…") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                        Text("✕", fontSize = 16.sp)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = onlySelected,
                onClick = { onlySelected = !onlySelected },
                label = { Text("Bifate") }
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            vm.categories.forEachIndexed { catIndex, cat ->
                // starea de pliere e persistentă; filtrele o ignoră temporar
                val isCollapsed = !filterActive && cat.id in vm.collapsedIds
                // la tablou monofazic ascundem componentele trifazice nefolosite
                // (nu se șterge nimic — reapar la Trifazic sau fără fază setată)
                val baseMats = if (cat.phase == "mono")
                    cat.materials.filter { it.qty > 0 || !isTriphasicItem(it.name) }
                else cat.materials
                val shownMats = baseMats.filter { m ->
                    (!onlySelected || m.qty > 0) &&
                        (query.isBlank() || m.name.contains(query.trim(), ignoreCase = true))
                }
                if (filterActive && shownMats.isEmpty()) return@forEachIndexed
                item(key = "c${cat.id}") {
                    CategoryHeader(
                        cat = cat,
                        collapsed = isCollapsed,
                        canMoveUp = catIndex > 0,
                        canMoveDown = catIndex < vm.categories.lastIndex,
                        onToggle = { vm.toggleCollapsed(cat.id) },
                        onAdd = { addToCategory = cat },
                        onRename = { editCategory = cat },
                        onDelete = { deleteCat = cat },
                        onMoveUp = { vm.moveCategory(cat.id, -1) },
                        onMoveDown = { vm.moveCategory(cat.id, +1) },
                        onBrand = { brandCategory = cat }
                    )
                }
                if (!isCollapsed) items(shownMats, key = { "m${it.id}" }) { mat ->
                    MaterialRow(
                        mat = mat,
                        onMinus = { vm.changeQty(cat.id, mat.id, -1) },
                        onPlus = { vm.changeQty(cat.id, mat.id, +1) },
                        onEdit = { editMaterial = cat to mat },
                        onQtyClick = { qtyMaterial = cat to mat }
                    )
                }
                item(key = "sp${cat.id}") { Spacer(Modifier.height(10.dp)) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    addToCategory?.let { cat ->
        TextDialog(
            title = "Material nou în ${cat.name}",
            label = "Nume material",
            onDismiss = { addToCategory = null },
            onConfirm = { vm.addMaterial(cat.id, it); addToCategory = null }
        )
    }
    editCategory?.let { cat ->
        TextDialog(
            title = "Redenumește categoria",
            label = "Nume categorie",
            initial = cat.name,
            onDismiss = { editCategory = null },
            onConfirm = { vm.renameCategory(cat.id, it); editCategory = null }
        )
    }
    deleteCat?.let { cat ->
        DeleteChoiceDialog(
            title = "Ștergi categoria ${cat.name}?",
            removeLabel = "Scoate din lucrare (cantități la 0)",
            deleteLabel = "Șterge definitiv categoria și cele ${cat.materials.size} materiale",
            onDismiss = { deleteCat = null },
            onRemoveFromWork = { vm.zeroCategory(cat.id); deleteCat = null },
            onDeleteForever = { vm.deleteCategory(cat.id); deleteCat = null }
        )
    }
    editMaterial?.let { (cat, mat) ->
        EditMaterialDialog(
            mat = mat,
            onDismiss = { editMaterial = null },
            onSave = { name, price ->
                vm.updateMaterial(cat.id, mat.id, name, price); editMaterial = null
            },
            onRemoveFromWork = { vm.setQty(cat.id, mat.id, 0); editMaterial = null },
            onDelete = { vm.deleteMaterial(cat.id, mat.id); editMaterial = null }
        )
    }
    qtyMaterial?.let { (cat, mat) ->
        NumberDialog(
            title = mat.name,
            initial = mat.qty,
            onDismiss = { qtyMaterial = null },
            onConfirm = { vm.setQty(cat.id, mat.id, it); qtyMaterial = null }
        )
    }
    brandCategory?.let { cat ->
        CategoryBrandDialog(
            cat = cat,
            brands = vm.brands,
            onDismiss = { brandCategory = null },
            onSave = { brand, model, phase ->
                vm.setCategoryBrand(cat.id, brand, model, phase)
                brandCategory = null
            }
        )
    }
}

@Composable
private fun CategoryHeader(
    cat: Category,
    collapsed: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onBrand: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val selected = cat.materials.count { it.qty > 0 }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Row(
            Modifier
                .clickable(onClick = onToggle)
                .padding(start = 12.dp, end = 2.dp)
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    cat.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (cat.brandLabel.isNotEmpty()) Text(
                    cat.brandLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected > 0) Badge(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) { Text("$selected") }
            IconButton(onClick = onAdd) {
                Icon(
                    Icons.Default.Add, contentDescription = "Adaugă material",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Default.MoreVert, contentDescription = "Opțiuni categorie",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Marcă / model") },
                        onClick = { menu = false; onBrand() })
                    if (canMoveUp) DropdownMenuItem(text = { Text("⬆ Mută mai sus") },
                        onClick = { menu = false; onMoveUp() })
                    if (canMoveDown) DropdownMenuItem(text = { Text("⬇ Mută mai jos") },
                        onClick = { menu = false; onMoveDown() })
                    DropdownMenuItem(text = { Text("Redenumește") },
                        onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Șterge categoria") },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun MaterialRow(
    mat: Material,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onEdit: () -> Unit,
    onQtyClick: () -> Unit
) {
    val active = mat.qty > 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .background(
                if (active) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(10.dp)
            )
            .padding(start = 12.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            mat.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(vertical = 8.dp)
        )
        QtyButton("−", enabled = mat.qty > 0, onClick = onMinus)
        Text(
            "${mat.qty}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .widthIn(min = 44.dp)
                .clickable(onClick = onQtyClick)
                .padding(vertical = 10.dp),
            textAlign = TextAlign.Center
        )
        QtyButton("+", enabled = true, onClick = onPlus)
    }
}

/** Buton de cantitate: apăsare = ±1; ținut apăsat = repetă accelerând. */
@Composable
private fun QtyButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val bg = if (enabled) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val fg = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(bg, CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        // apăsare (sau ținut sub 2 secunde) = exact ±1
                        currentOnClick()
                        val job = scope.launch {
                            // abia după 2 secunde de ținut apăsat începe
                            // incrementarea automată, lent și apoi accelerând
                            delay(2000)
                            var interval = 350L
                            while (true) {
                                currentOnClick()
                                delay(interval)
                                if (interval > 120) interval -= 20
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                )
            }
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun SummaryScreen(vm: AppViewModel, onSaved: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showSave by remember { mutableStateOf(false) }
    var showPdfName by remember { mutableStateOf(false) }
    val selected = vm.categories
        .map { c -> c to c.materials.filter { it.qty > 0 } }
        .filter { it.second.isNotEmpty() }

    Column(Modifier.fillMaxSize()) {
        if (selected.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(), contentAlignment = Alignment.Center
            ) {
                Text(
                    "Niciun material selectat.\nApasă + la materialele de care ai nevoie.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                selected.forEach { (cat, mats) ->
                    item(key = "sc${cat.id}") {
                        Text(
                            if (cat.brandLabel.isEmpty()) cat.name
                            else "${cat.name} — ${cat.brandLabel}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(mats, key = { "sm${it.id}" }) { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                m.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${m.qty} buc",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
            val totalValue = selected.sumOf { (_, mats) -> mats.sumOf { it.qty * it.price } }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (totalValue > 0.0) Text(
                    "Valoare estimată: ${String.format(Locale.US, "%.2f", totalValue)} lei",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(vm.summaryText()))
                            Toast.makeText(context, "Copiat în clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Copiază") }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, vm.summaryText())
                            }
                            context.startActivity(Intent.createChooser(intent, "Trimite necesarul"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Trimite")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showPdfName = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("PDF") }
                    Button(
                        onClick = { showSave = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Salvează lucrarea") }
                }
            }
        }
    }

    if (showSave) WorkDetailsDialog(
        title = "Salvează lucrarea",
        confirmLabel = "Salvează",
        works = vm.works,
        onDismiss = { showSave = false },
        onConfirm = { name, client, address, phone, overwriteId ->
            showSave = false
            if (vm.saveWork(name, client, address, phone, overwriteId)) {
                Toast.makeText(
                    context,
                    if (overwriteId != null) "Lucrare actualizată: $name"
                    else "Lucrare salvată: $name",
                    Toast.LENGTH_SHORT
                ).show()
                onSaved()
            }
        }
    )
    if (showPdfName) WorkDetailsDialog(
        title = "Detalii pentru PDF",
        confirmLabel = "Generează PDF",
        onDismiss = { showPdfName = false },
        onConfirm = { name, client, address, phone, _ ->
            showPdfName = false
            exportPdfAndShare(context, vm, vm.snapshot(name, client, address, phone))
        }
    )
}

@Composable
private fun WorkDetailsDialog(
    title: String,
    confirmLabel: String,
    works: List<Work> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Long?) -> Unit
) {
    var name by remember { mutableStateOf("Necesar materiale ") }
    var client by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var overwriteId by remember { mutableStateOf<Long?>(null) }
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (works.isNotEmpty()) {
                    Text(
                        "Salvează ca:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = { Text("Caută lucrare (nume, client, adresă)") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val filtered = works.filter { w ->
                        filter.isBlank() || listOf(w.name, w.client, w.address)
                            .any { it.contains(filter.trim(), ignoreCase = true) }
                    }
                    Column(
                        Modifier
                            .heightIn(max = 190.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { overwriteId = null },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = overwriteId == null,
                                onClick = { overwriteId = null }
                            )
                            Text("Lucrare nouă", style = MaterialTheme.typography.bodyMedium)
                        }
                        filtered.forEach { w ->
                            val select = {
                                overwriteId = w.id
                                name = w.name
                                client = w.client
                                address = w.address
                                phone = w.phone
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { select() },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = overwriteId == w.id,
                                    onClick = select
                                )
                                Column {
                                    Text(
                                        w.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = listOf(w.client, w.address)
                                        .filter { it.isNotBlank() }.joinToString("  ·  ")
                                    if (sub.isNotEmpty()) Text(
                                        sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (filtered.isEmpty()) Text(
                            "Nicio lucrare nu se potrivește căutării.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nume lucrare (ex: Casa familia Ciuvică)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = client, onValueChange = { client = it },
                    label = { Text("Client — opțional") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Adresă — opțional") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Telefon — opțional") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name, client, address, phone, overwriteId)
                },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } }
    )
}

@Composable
private fun WorksScreen(vm: AppViewModel, onLoaded: () -> Unit) {
    val context = LocalContext.current
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    var deleteWork by remember { mutableStateOf<Work?>(null) }
    var loadWork by remember { mutableStateOf<Work?>(null) }
    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    if (vm.works.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nicio lucrare salvată.\nDin ecranul Necesar apasă „Salvează lucrarea”.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        items(vm.works, key = { "w${it.id}" }) { work ->
            val isOpen = expanded[work.id] == true
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(
                    Modifier
                        .clickable { expanded[work.id] = !isOpen }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        work.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildString {
                            append("${df.format(Date(work.date))}  ·  ${work.totalTypes} tipuri  ·  ${work.totalPieces} buc")
                            if (work.hasPrices) append("  ·  ${String.format(Locale.US, "%.2f", work.totalValue)} lei")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    val clientInfo = listOf(work.client, work.address, work.phone)
                        .filter { it.isNotBlank() }.joinToString("  ·  ")
                    if (clientInfo.isNotEmpty()) Text(
                        clientInfo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOpen) {
                        Spacer(Modifier.height(6.dp))
                        work.categories.forEach { cat ->
                            Text(
                                if (cat.brandLabel.isEmpty()) cat.name
                                else "${cat.name} — ${cat.brandLabel}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            cat.materials.forEach { m ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(
                                        m.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${m.qty} buc",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { exportPdfAndShare(context, vm, work) }) { Text("PDF") }
                        TextButton(onClick = {
                            vm.duplicateWork(work)
                            Toast.makeText(context, "Lucrare duplicată", Toast.LENGTH_SHORT).show()
                        }) { Text("Duplică") }
                        TextButton(onClick = { loadWork = work }) { Text("Încarcă") }
                        TextButton(
                            onClick = { deleteWork = work },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Șterge")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    deleteWork?.let { work ->
        ConfirmDialog(
            title = "Ștergi lucrarea?",
            text = "„${work.name}” va fi ștearsă definitiv.",
            onDismiss = { deleteWork = null },
            onConfirm = { vm.deleteWork(work.id); deleteWork = null }
        )
    }
    loadWork?.let { work ->
        ConfirmDialog(
            title = "Încarci lucrarea în editor?",
            text = "Cantitățile din „${work.name}” înlocuiesc cantitățile curente din ecranul Materiale.",
            onDismiss = { loadWork = null },
            onConfirm = {
                vm.loadWork(work)
                loadWork = null
                onLoaded()
                Toast.makeText(context, "Lucrare încărcată", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun TextDialog(
    title: String,
    label: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value) },
                enabled = value.isNotBlank()
            ) { Text("Salvează") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } }
    )
}

@Composable
private fun NumberDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var value by remember { mutableStateOf(if (initial == 0) "" else "$initial") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { v -> value = v.filter { it.isDigit() }.take(4) },
                label = { Text("Cantitate") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.toIntOrNull() ?: 0) }) { Text("Salvează") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } }
    )
}

@Composable
private fun EditMaterialDialog(
    mat: Material,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit,
    onRemoveFromWork: () -> Unit,
    onDelete: () -> Unit
) {
    var value by remember { mutableStateOf(mat.name) }
    var priceText by remember {
        mutableStateOf(if (mat.price > 0.0) String.format(Locale.US, "%.2f", mat.price) else "")
    }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        DeleteChoiceDialog(
            title = "Ștergi ${mat.name}?",
            removeLabel = "Scoate din lucrare (cantitate la 0)",
            deleteLabel = "Șterge definitiv din listă",
            onDismiss = { confirmDelete = false },
            onRemoveFromWork = onRemoveFromWork,
            onDeleteForever = onDelete
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editează materialul") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Nume material") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { v ->
                        priceText = v.replace(',', '.')
                            .filter { it.isDigit() || it == '.' }.take(9)
                    },
                    label = { Text("Preț / buc (lei) — opțional") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value.isNotBlank())
                        onSave(value, priceText.toDoubleOrNull() ?: 0.0)
                },
                enabled = value.isNotBlank()
            ) { Text("Salvează") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Șterge") }
                TextButton(onClick = onDismiss) { Text("Anulează") }
            }
        }
    )
}

@Composable
private fun SettingsScreen(
    vm: AppViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    val s = vm.settings
    var showReorder by remember { mutableStateOf(false) }
    var showBrands by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        SettingsHeader("Instalator (apare în PDF)")
        OutlinedTextField(
            value = s.installerName,
            onValueChange = { vm.saveSettings(s.copy(installerName = it)) },
            label = { Text("Nume instalator") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = s.installerPhone,
            onValueChange = { vm.saveSettings(s.copy(installerPhone = it)) },
            label = { Text("Telefon") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = s.installerCompany,
            onValueChange = { vm.saveSettings(s.copy(installerCompany = it)) },
            label = { Text("Firmă — opțional") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        SettingsHeader("PDF")
        SwitchRow(
            title = "Include dozele și tabloul în PDF",
            subtitle = "Dezactivat: dozele și tabloul sunt deja montate — rămân informative " +
                "și servesc doar calculelor; nu apar în PDF",
            checked = s.includeBoxesInPdf,
            onChange = { vm.saveSettings(s.copy(includeBoxesInPdf = it)) }
        )
        SwitchRow(
            title = "Accesorii calculate automat",
            subtitle = "Rame suport, rame ornament și obturatoare (priza dublă = 2 module)",
            checked = s.autoAccessories,
            onChange = { vm.saveSettings(s.copy(autoAccessories = it)) }
        )

        SettingsHeader("Comportament")
        SwitchRow(
            title = "Golește materialele după salvare",
            subtitle = "După salvarea lucrării, cantitățile revin la 0 și treci la Lucrări",
            checked = s.clearAfterSave,
            onChange = { vm.saveSettings(s.copy(clearAfterSave = it)) }
        )
        if (vm.selfUpdateAvailable) SwitchRow(
            title = "Caută actualizări la pornire",
            subtitle = "Verifică automat GitHub la deschiderea aplicației",
            checked = s.autoUpdateCheck,
            onChange = { vm.saveSettings(s.copy(autoUpdateCheck = it)) }
        )

        SettingsHeader("Temă")
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(
                ThemeMode.SYSTEM to "Sistem",
                ThemeMode.LIGHT to "Luminoasă",
                ThemeMode.DARK to "Întunecată"
            ).forEach { (mode, label) ->
                Row(
                    Modifier
                        .weight(1f)
                        .clickable { vm.setTheme(mode) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = vm.themeMode == mode,
                        onClick = { vm.setTheme(mode) }
                    )
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SettingsHeader("Listă și date")
        OutlinedButton(
            onClick = { showBrands = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Mărci și modele (${vm.brands.size})") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showReorder = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ordinea categoriilor (drag & drop)")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                Text("Exportă backup")
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Text("Importă backup")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRestoreDefaults,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Listă implicită")
            }
            if (vm.selfUpdateAvailable) OutlinedButton(
                onClick = onCheckUpdates, modifier = Modifier.weight(1f)
            ) {
                Text("Caută actualizări")
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "NecMat v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showReorder) ReorderCategoriesDialog(vm, onDismiss = { showReorder = false })
    if (showBrands) BrandTableDialog(vm, onDismiss = { showBrands = false })
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ReorderCategoriesDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    // listă locală: reordonăm aici și aplicăm la "Gata"
    val order = remember {
        androidx.compose.runtime.mutableStateListOf<Category>().apply { addAll(vm.categories) }
    }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 54.dp.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ordinea categoriilor") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Ține apăsat pe o categorie și trage-o în sus sau în jos, apoi apasă Salvează.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                order.forEach { cat ->
                    androidx.compose.runtime.key(cat.id) {
                        val isDragged = cat.id == draggedId
                        Surface(
                            color = if (isDragged) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            tonalElevation = if (isDragged) 6.dp else 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragged) dragOffset else 0f
                                }
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedId = cat.id
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val from = order.indexOfFirst { it.id == cat.id }
                                            val steps = (dragOffset / rowHeightPx).roundToInt()
                                            if (steps != 0 && from >= 0) {
                                                val to = (from + steps)
                                                    .coerceIn(0, order.lastIndex)
                                                if (to != from) {
                                                    val item = order.removeAt(from)
                                                    order.add(to, item)
                                                    dragOffset -= (to - from) * rowHeightPx
                                                }
                                            }
                                        },
                                        onDragEnd = { draggedId = null; dragOffset = 0f },
                                        onDragCancel = { draggedId = null; dragOffset = 0f }
                                    )
                                }
                        ) {
                            Row(
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Menu, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    cat.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setCategoryOrder(order.map { it.id })
                onDismiss()
            }) { Text("Salvează") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } }
    )
}

@Composable
private fun CategoryBrandDialog(
    cat: Category,
    brands: List<BrandEntry>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var brand by remember { mutableStateOf(cat.brand) }
    var model by remember { mutableStateOf(cat.model) }
    var phase by remember { mutableStateOf(cat.phase) }
    var smartMode by remember { mutableStateOf(false) }
    val group = remember(cat.name) { BrandGroups.infer(cat.name) }
    val showSmartFilter = group in listOf(
        BrandGroups.MODULAR, BrandGroups.APARATAJ, BrandGroups.TABLOU
    )
    val suggestions = remember(brands, group, smartMode) {
        if (smartMode) {
            brands.filter { BrandGroups.SMART in it.groups }.sortedBy { it.label }
        } else {
            val classic = brands.filter { BrandGroups.SMART !in it.groups }
            classic.filter { group in it.groups }.sortedBy { it.label } +
                classic.filter { group !in it.groups }.sortedBy { it.label }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marcă / model — ${cat.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = brand, onValueChange = { brand = it },
                    label = { Text("Marcă") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Model / serie") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (group == BrandGroups.TABLOU) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        listOf("" to "—", "mono" to "Monofazic", "tri" to "Trifazic")
                            .forEach { (value, label) ->
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .clickable { phase = value },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = phase == value,
                                        onClick = { phase = value }
                                    )
                                    Text(label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (showSmartFilter) {
                        FilterChip(
                            selected = !smartMode,
                            onClick = { smartMode = false },
                            label = { Text("Clasic") }
                        )
                        FilterChip(
                            selected = smartMode,
                            onClick = { smartMode = true },
                            label = { Text("Smart") }
                        )
                    }
                }
                Text(
                    if (smartMode) "Sugestii aparataj smart:"
                    else "Sugestii (${BrandGroups.label(group)} întâi):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
                Column(
                    Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    suggestions.forEach { entry ->
                        val matches = group in entry.groups
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { brand = entry.brand; model = entry.series }
                                .padding(vertical = 7.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (matches) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (matches) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                entry.groups.joinToString(", ") { BrandGroups.label(it) },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Text(
                    "Lista completă se editează în Setări → Mărci și modele.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(brand, model, phase) }) { Text("Salvează") }
        },
        dismissButton = {
            Row {
                if (cat.brandLabel.isNotEmpty()) TextButton(
                    onClick = { onSave("", "", "") },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Fără marcă") }
                TextButton(onClick = onDismiss) { Text("Anulează") }
            }
        }
    )
}

@Composable
private fun BrandTableDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var editEntry by remember { mutableStateOf<BrandEntry?>(null) }
    var addNew by remember { mutableStateOf(false) }
    var confirmSeed by remember { mutableStateOf(false) }
    val sorted = vm.brands.sortedWith(compareBy({ it.brand.lowercase() }, { it.series.lowercase() }))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mărci și modele") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { addNew = true }, modifier = Modifier.weight(1f)) {
                        Text("Adaugă")
                    }
                    OutlinedButton(onClick = { confirmSeed = true }, modifier = Modifier.weight(1f)) {
                        Text("Lista implicită")
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(sorted, key = { it.id }) { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editEntry = entry }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    entry.groups.joinToString(", ") { BrandGroups.label(it) },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { vm.deleteBrand(entry.id) }) {
                                Icon(
                                    Icons.Default.Delete, contentDescription = "Șterge",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gata") } }
    )

    if (addNew) BrandEditDialog(
        entry = null,
        onDismiss = { addNew = false },
        onSave = { vm.upsertBrand(it); addNew = false }
    )
    editEntry?.let { entry ->
        BrandEditDialog(
            entry = entry,
            onDismiss = { editEntry = null },
            onSave = { vm.upsertBrand(it); editEntry = null }
        )
    }
    if (confirmSeed) ConfirmDialog(
        title = "Restaurezi lista implicită de mărci?",
        text = "Mărcile adăugate sau modificate de tine vor fi înlocuite cu lista inițială.",
        onDismiss = { confirmSeed = false },
        onConfirm = { vm.restoreBrandDefaults(); confirmSeed = false }
    )
}

@Composable
private fun BrandEditDialog(
    entry: BrandEntry?,
    onDismiss: () -> Unit,
    onSave: (BrandEntry) -> Unit
) {
    var brand by remember { mutableStateOf(entry?.brand ?: "") }
    var series by remember { mutableStateOf(entry?.series ?: "") }
    var groups by remember { mutableStateOf(entry?.groups?.toSet() ?: emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry == null) "Marcă nouă" else "Editează marca") },
        text = {
            Column {
                OutlinedTextField(
                    value = brand, onValueChange = { brand = it },
                    label = { Text("Marcă (ex: Noark)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = series, onValueChange = { series = it },
                    label = { Text("Model / serie — opțional (ex: Ex9BN)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Se aplică grupurilor:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
                BrandGroups.all.forEach { g ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                groups = if (g in groups) groups - g else groups + g
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = g in groups,
                            onCheckedChange = {
                                groups = if (g in groups) groups - g else groups + g
                            }
                        )
                        Text(BrandGroups.label(g), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = brand.isNotBlank() && groups.isNotEmpty(),
                onClick = {
                    onSave(
                        BrandEntry(
                            id = entry?.id ?: (System.currentTimeMillis() + (0..999).random()),
                            brand = brand,
                            series = series,
                            groups = groups.toList()
                        )
                    )
                }
            ) { Text("Salvează") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } }
    )
}

@Composable
private fun DeleteChoiceDialog(
    title: String,
    removeLabel: String,
    deleteLabel: String,
    onDismiss: () -> Unit,
    onRemoveFromWork: () -> Unit,
    onDeleteForever: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRemoveFromWork,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(removeLabel, textAlign = TextAlign.Center) }
                Button(
                    onClick = onDeleteForever,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(deleteLabel, textAlign = TextAlign.Center) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } }
    )
}

@Composable
private fun CountdownConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    seconds: Int = 10,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var remaining by remember { mutableStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.error) },
        text = { Text(text) },
        confirmButton = {
            Button(
                enabled = remaining == 0,
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(if (remaining > 0) "$confirmLabel ($remaining)" else confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Da") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Nu") } }
    )
}
