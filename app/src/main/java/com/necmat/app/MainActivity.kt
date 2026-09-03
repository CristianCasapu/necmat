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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
        AppLog.i("Pdf", "PDF materiale generat: ${result.fileName}")
    } catch (e: Exception) {
        AppLog.e("Pdf", "Eroare la generarea PDF de materiale", e)
        Toast.makeText(context, "Eroare la generarea PDF", Toast.LENGTH_LONG).show()
    }
}

/** Generează PDF-ul ofertei de manoperă și deschide partajarea. */
private fun exportLaborPdfAndShare(
    context: Context,
    vm: AppViewModel,
    work: Work,
    quote: LaborQuote
) {
    try {
        val s = vm.settings
        val result = PdfExporter.exportLaborQuote(
            context, work, quote, s.installerName, s.installerPhone, s.installerCompany,
            detailExpenses = s.detailExpensesInOffer
        )
        Toast.makeText(
            context,
            if (result.savedToDownloads) "PDF salvat în Descărcări/NecMat: ${result.fileName}"
            else "PDF generat: ${result.fileName}",
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, result.shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Trimite oferta"))
        AppLog.i("Pdf", "Ofertă manoperă generată: ${result.fileName}, total=${String.format(java.util.Locale.US, "%.2f", quote.total)}")
    } catch (e: Exception) {
        AppLog.e("Pdf", "Eroare la generarea ofertei de manoperă", e)
        Toast.makeText(context, "Eroare la generarea ofertei", Toast.LENGTH_LONG).show()
    }
}

/** Trimite jurnalul de activitate (cu antet de diagnostic) prin partajare. */
private fun shareLogs(context: Context) {
    try {
        val header = "NecMat v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})  ·  " +
            "Android ${android.os.Build.VERSION.RELEASE}  ·  " +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
            "-".repeat(60) + "\n"
        val body = AppLog.currentFile()?.takeIf { it.exists() }?.readText().orEmpty()
        val dir = File(context.cacheDir, "backup").apply { mkdirs() }
        val out = File(dir, "NecMat-loguri.txt")
        out.writeText(header + body.ifBlank { "(jurnal gol)" })
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", out
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Loguri NecMat v${BuildConfig.VERSION_NAME}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Trimite logurile"))
        AppLog.i("Loguri", "Jurnal partajat (${body.length} caractere)")
    } catch (e: Exception) {
        AppLog.e("Loguri", "Eroare la partajarea jurnalului", e)
        Toast.makeText(context, "Eroare la trimiterea logurilor", Toast.LENGTH_LONG).show()
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
        AppLog.i("Backup", "Backup exportat")
    } catch (e: Exception) {
        AppLog.e("Backup", "Eroare la exportul backup-ului", e)
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ștergere cu opțiune de anulare (Undo)
    val onDeleted: (String) -> Unit = { msg ->
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "Anulează",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.undoDelete()
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                Screen.MATERIALS -> MaterialsScreen(vm, onDeleted = onDeleted)
                Screen.SUMMARY -> SummaryScreen(vm, onSaved = { screen = Screen.WORKS })
                Screen.WORKS -> WorksScreen(
                    vm,
                    onLoaded = { screen = Screen.MATERIALS },
                    onDeleted = onDeleted
                )
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
private fun MaterialsScreen(vm: AppViewModel, onDeleted: (String) -> Unit) {
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
            onDeleteForever = {
                vm.deleteCategory(cat.id)
                deleteCat = null
                onDeleted("Categoria „${cat.name}” a fost ștearsă")
            }
        )
    }
    editMaterial?.let { (cat, mat) ->
        EditMaterialDialog(
            mat = mat,
            showPrice = vm.settings.materialPrices,
            onDismiss = { editMaterial = null },
            onSave = { name, price ->
                vm.updateMaterial(cat.id, mat.id, name, price); editMaterial = null
            },
            onRemoveFromWork = { vm.setQty(cat.id, mat.id, 0); editMaterial = null },
            onDelete = {
                vm.deleteMaterial(cat.id, mat.id)
                editMaterial = null
                onDeleted("Materialul „${mat.name}” a fost șters")
            }
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
    var showLaborName by remember { mutableStateOf(false) }
    var laborWork by remember { mutableStateOf<Work?>(null) }
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
                // sumar live al calculului de accesorii pentru dozele modulare
                item(key = "accsum") {
                    accessorySummary(vm.categories)?.let { s ->
                        Surface(
                            color = if (s.overflow)
                                MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                if (s.overflow)
                                    "⚠ Modulele folosite (${s.usedModules}) depășesc sloturile " +
                                        "din dozele modulare (${s.slots}). Verifică dozele!"
                                else buildString {
                                    append("Accesorii calculate pentru PDF: ")
                                    append("${s.boxes} rame suport + ${s.boxes} rame ornament")
                                    if (s.obturatoare > 0) append(" + ${s.obturatoare} obturatoare")
                                    append("  (${s.slots} sloturi · ${s.usedModules} module)")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (s.overflow)
                                    MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
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
                if (vm.settings.materialPrices && totalValue > 0.0) Text(
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
                OutlinedButton(
                    onClick = { showLaborName = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ofertă manoperă (PDF)") }
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
    if (showLaborName) WorkDetailsDialog(
        title = "Ofertă manoperă — detalii",
        confirmLabel = "Continuă",
        onDismiss = { showLaborName = false },
        onConfirm = { name, client, address, phone, _ ->
            showLaborName = false
            laborWork = vm.snapshot(name, client, address, phone)
        }
    )
    laborWork?.let { work ->
        LaborQuoteDialog(vm, work, onDismiss = { laborWork = null })
    }
}

@Composable
private fun WorkDetailsDialog(
    title: String,
    confirmLabel: String,
    works: List<Work> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Long?) -> Unit
) {
    val initialName = remember {
        "Necesar materiale " +
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
    }
    var name by remember { mutableStateOf(initialName) }
    var client by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var overwriteId by remember { mutableStateOf<Long?>(null) }
    var filter by remember { mutableStateOf("") }
    var confirmExit by remember { mutableStateOf(false) }

    // protecție la ieșire: dacă s-a completat ceva, cerem confirmare
    val dirty = name != initialName || client.isNotBlank() ||
        address.isNotBlank() || phone.isNotBlank() || overwriteId != null
    val requestDismiss = { if (dirty) confirmExit = true else onDismiss() }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Ieși din formular?") },
            text = { Text("Datele completate se vor pierde.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmExit = false; onDismiss() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Ies fără salvare") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("Rămân în formular") }
            }
        )
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locBusy by remember { mutableStateOf(false) }

    // adresa din locația curentă, prin OpenStreetMap
    fun fetchAddress() {
        if (locBusy) return
        locBusy = true
        scope.launch {
            val loc = LocationHelper.currentLocation(context)
            val addr = loc?.let { LocationHelper.reverseGeocode(it.latitude, it.longitude) }
            locBusy = false
            when {
                addr != null -> address = addr
                loc == null -> Toast.makeText(
                    context, "Locația nu a putut fi determinată — pornește GPS-ul",
                    Toast.LENGTH_LONG
                ).show()
                else -> Toast.makeText(
                    context, "Nu am găsit o adresă pentru locația curentă",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val locPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.any { it }) fetchAddress()
        else Toast.makeText(
            context, "Fără permisiunea de locație nu pot detecta adresa",
            Toast.LENGTH_LONG
        ).show()
    }

    // numărul de telefon din agenda de contacte
    val contactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { phone = it }
                    val displayName = c.getString(1)
                    if (client.isBlank() && !displayName.isNullOrBlank()) client = displayName
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Nu am putut citi contactul", Toast.LENGTH_LONG).show()
        }
    }

    AlertDialog(
        onDismissRequest = { requestDismiss() },
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
                    singleLine = true,
                    trailingIcon = {
                        if (locBusy) CircularProgressIndicator(
                            Modifier.size(18.dp), strokeWidth = 2.dp
                        ) else IconButton(onClick = {
                            if (LocationHelper.hasPermission(context)) fetchAddress()
                            else locPermLauncher.launch(LocationHelper.PERMISSIONS)
                        }) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Detectează adresa din locație",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Telefon — opțional") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    trailingIcon = {
                        IconButton(onClick = {
                            try {
                                contactLauncher.launch(
                                    Intent(Intent.ACTION_PICK).apply {
                                        type = android.provider.ContactsContract
                                            .CommonDataKinds.Phone.CONTENT_TYPE
                                    }
                                )
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context, "Agenda de contacte nu e disponibilă",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Alege din contacte",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
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
        dismissButton = { TextButton(onClick = { requestDismiss() }) { Text("Anulează") } }
    )
}

@Composable
private fun WorksScreen(vm: AppViewModel, onLoaded: () -> Unit, onDeleted: (String) -> Unit) {
    val context = LocalContext.current
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    var deleteWork by remember { mutableStateOf<Work?>(null) }
    var loadWork by remember { mutableStateOf<Work?>(null) }
    var laborQuoteFor by remember { mutableStateOf<Work?>(null) }
    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    // șabloanele sunt afișate primele
    val sortedWorks = vm.works.sortedByDescending { it.isTemplate }

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
        items(sortedWorks, key = { "w${it.id}" }) { work ->
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            work.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (work.isTemplate) {
                            Spacer(Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ) { Text("ȘABLON") }
                        }
                    }
                    Text(
                        buildString {
                            append("${df.format(Date(work.date))}  ·  ${work.totalTypes} tipuri  ·  ${work.totalPieces} buc")
                            if (vm.settings.materialPrices && work.hasPrices)
                                append("  ·  ${String.format(Locale.US, "%.2f", work.totalValue)} lei")
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
                        TextButton(onClick = { laborQuoteFor = work }) { Text("Ofertă") }
                        TextButton(onClick = { vm.toggleTemplate(work.id) }) {
                            Text(if (work.isTemplate) "Șablon ✓" else "Șablon")
                        }
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
            onConfirm = {
                vm.deleteWork(work.id)
                deleteWork = null
                onDeleted("Lucrarea „${work.name}” a fost ștearsă")
            }
        )
    }
    laborQuoteFor?.let { work ->
        LaborQuoteDialog(vm, work, onDismiss = { laborQuoteFor = null })
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
    showPrice: Boolean,
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
                if (showPrice) OutlinedTextField(
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
    val context = LocalContext.current
    val s = vm.settings
    var showReorder by remember { mutableStateOf(false) }
    var showBrands by remember { mutableStateOf(false) }
    var showDozaLabor by remember { mutableStateOf(false) }
    var showMaterialPrices by remember { mutableStateOf(false) }
    var showManageMaterials by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }

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
            title = "Include dozele și carcasele de tablou în PDF",
            subtitle = "Dezactivat: dozele și carcasa tabloului sunt deja montate și nu apar " +
                "în PDF; componentele tabloului (MCB, diferențiale, busbar-uri) apar mereu",
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

        SettingsHeader("Manoperă")
        OutlinedButton(
            onClick = { showDozaLabor = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manoperă doze + corpuri iluminat (${vm.labor.dozaPrices.count { it.value > 0 }} setate)")
        }
        Spacer(Modifier.height(4.dp))
        SwitchRow(
            title = "Prețuri de achiziție materiale",
            subtitle = "Dezactivat: prețurile materialelor sunt ascunse peste tot — " +
                "clientul primește doar lista și își cumpără singur materialele",
            checked = s.materialPrices,
            onChange = { vm.saveSettings(s.copy(materialPrices = it)) }
        )
        if (s.materialPrices) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { showMaterialPrices = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Prețuri materiale") }
        }
        Spacer(Modifier.height(4.dp))
        SwitchRow(
            title = "Detaliază cheltuielile în oferta PDF",
            subtitle = "Dezactivat: deplasarea, hrana, consumabilele și ajutorul sunt incluse " +
                "în totalul ofertei, fără linii separate",
            checked = s.detailExpensesInOffer,
            onChange = { vm.saveSettings(s.copy(detailExpensesInOffer = it)) }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Echipare tablou — preț per etaj, după câte module încap:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(13, 18, 24).forEach { size ->
                PriceField(
                    "$size mod.", vm.labor.rowPrices[size] ?: 0.0,
                    Modifier.weight(1f)
                ) { p ->
                    vm.saveLabor(vm.labor.copy(rowPrices = vm.labor.rowPrices + (size to p)))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Cheltuieli implicite pentru ofertă:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceField("Deplasare", vm.labor.travel, Modifier.weight(1f)) {
                vm.saveLabor(vm.labor.copy(travel = it))
            }
            PriceField("Mâncare", vm.labor.food, Modifier.weight(1f)) {
                vm.saveLabor(vm.labor.copy(food = it))
            }
            PriceField("Consumab.", vm.labor.consumables, Modifier.weight(1f)) {
                vm.saveLabor(vm.labor.copy(consumables = it))
            }
        }
        Spacer(Modifier.height(8.dp))
        PriceField(
            "Ajutor electrician (lei/zi)", vm.labor.helperPerDay,
            Modifier.fillMaxWidth()
        ) { vm.saveLabor(vm.labor.copy(helperPerDay = it)) }

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
            onClick = {
                val added = vm.repairCatalog()
                Toast.makeText(
                    context,
                    if (added > 0) "S-au adăugat $added materiale lipsă"
                    else "Lista e completă — nimic de adăugat",
                    Toast.LENGTH_LONG
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Adaugă materialele lipsă (reparare listă)") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showManageMaterials = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Gestionare materiale") }
        Spacer(Modifier.height(8.dp))
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

        SettingsHeader("Depanare")
        SwitchRow(
            title = "Jurnal de activitate (logging)",
            subtitle = "Înregistrează operațiile aplicației; la o problemă, trimite " +
                "logurile ca defectul să poată fi identificat și reparat",
            checked = vm.logEnabled,
            onChange = { vm.setLogging(it, vm.logLevel) }
        )
        if (vm.logEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    AppLog.Level.DEBUG to "Debug",
                    AppLog.Level.INFO to "Info",
                    AppLog.Level.WARN to "Warn",
                    AppLog.Level.ERROR to "Error"
                ).forEach { (level, label) ->
                    FilterChip(
                        selected = vm.logLevel == level,
                        onClick = { vm.setLogging(true, level) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { shareLogs(context) }, modifier = Modifier.weight(1f)) {
                    Text("Trimite logurile")
                }
                OutlinedButton(onClick = { showLogs = true }, modifier = Modifier.weight(1f)) {
                    Text("Vezi")
                }
                OutlinedButton(
                    onClick = {
                        AppLog.clear()
                        Toast.makeText(context, "Jurnal golit", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Șterge") }
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
    if (showDozaLabor) DozaLaborDialog(vm, onDismiss = { showDozaLabor = false })
    if (showMaterialPrices) MaterialPricesDialog(vm, onDismiss = { showMaterialPrices = false })
    if (showManageMaterials) ManageMaterialsDialog(vm, onDismiss = { showManageMaterials = false })
    if (showLogs) AlertDialog(
        onDismissRequest = { showLogs = false },
        title = { Text("Jurnal de activitate") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    AppLog.readTail().ifBlank { "(jurnal gol)" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = { showLogs = false }) { Text("Închide") } }
    )
}

/** Gestionarea centralizată a materialelor: redenumire, ștergere, adăugare. */
@Composable
private fun ManageMaterialsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Pair<Category, Material>?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<Category, Material>?>(null) }
    var addTarget by remember { mutableStateOf<Category?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gestionare materiale") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("Caută material") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Apasă pe nume pentru redenumire; coșul șterge definitiv.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    vm.categories.forEach { cat ->
                        val mats = cat.materials.filter {
                            filter.isBlank() ||
                                it.name.contains(filter.trim(), ignoreCase = true)
                        }
                        if (mats.isEmpty() && filter.isNotBlank()) return@forEach
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                cat.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { addTarget = cat }) {
                                Icon(
                                    Icons.Default.Add, contentDescription = "Adaugă material",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        mats.forEach { mat ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    mat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { renameTarget = cat to mat }
                                        .padding(vertical = 7.dp)
                                )
                                IconButton(onClick = { deleteTarget = cat to mat }) {
                                    Icon(
                                        Icons.Default.Delete, contentDescription = "Șterge",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gata") } }
    )

    renameTarget?.let { (cat, mat) ->
        TextDialog(
            title = "Redenumește materialul",
            label = "Nume material",
            initial = mat.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                vm.updateMaterial(cat.id, mat.id, newName, mat.price)
                renameTarget = null
            }
        )
    }
    deleteTarget?.let { (cat, mat) ->
        ConfirmDialog(
            title = "Ștergi „${mat.name}”?",
            text = "Materialul dispare definitiv din categoria „${cat.name}”.",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                vm.deleteMaterial(cat.id, mat.id)
                deleteTarget = null
            }
        )
    }
    addTarget?.let { cat ->
        TextDialog(
            title = "Material nou în ${cat.name}",
            label = "Nume material",
            onDismiss = { addTarget = null },
            onConfirm = { vm.addMaterial(cat.id, it); addTarget = null }
        )
    }
}

/** Prețurile de achiziție ale materialelor, editabile centralizat. */
@Composable
private fun MaterialPricesDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prețuri materiale") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("Caută material") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    vm.categories.forEach { cat ->
                        val mats = cat.materials.filter {
                            filter.isBlank() ||
                                it.name.contains(filter.trim(), ignoreCase = true)
                        }
                        if (mats.isEmpty()) return@forEach
                        Text(
                            cat.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                        mats.forEach { mat ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    mat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                PriceField(
                                    "lei", mat.price,
                                    Modifier.widthIn(min = 96.dp, max = 96.dp)
                                ) { p -> vm.updateMaterial(cat.id, mat.id, mat.name, p) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gata") } }
    )
}

/** Câmp de preț (lei) cu tastatură zecimală. */
@Composable
private fun PriceField(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    onChange: (Double) -> Unit
) {
    var text by remember {
        mutableStateOf(if (value > 0) String.format(Locale.US, "%.2f", value) else "")
    }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.replace(',', '.').filter { it.isDigit() || it == '.' }.take(9)
            onChange(text.toDoubleOrNull() ?: 0.0)
        },
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

/** Oferta de manoperă: liniile calculate + cele 3 cheltuieli editabile. */
@Composable
private fun LaborQuoteDialog(vm: AppViewModel, work: Work, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cfg = vm.labor
    var travel by remember { mutableStateOf(cfg.travel) }
    var food by remember { mutableStateOf(cfg.food) }
    var consumables by remember { mutableStateOf(cfg.consumables) }
    var daysText by remember { mutableStateOf("") }
    var helperPerDay by remember { mutableStateOf(cfg.helperPerDay) }
    val days = daysText.toIntOrNull() ?: 0
    val baseQuote = remember(work, cfg) { laborQuote(work, cfg) }
    val quote = baseQuote.copy(
        travel = travel, food = food, consumables = consumables,
        days = days, helperPerDay = helperPerDay
    )
    fun lei(v: Double) = String.format(Locale.US, "%.2f", v)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ofertă manoperă — ${work.name}") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // componente de tablou selectate, dar fără carcasă -> etajele nu pot fi calculate
                val hasTablouParts = work.categories.any { c ->
                    c.name.contains("tablou", ignoreCase = true) &&
                        c.materials.any { it.qty > 0 && !isTablouCarcasa(it.name) }
                }
                val hasCarcasa = work.categories.any { c ->
                    c.materials.any { it.qty > 0 && isTablouCarcasa(it.name) }
                }
                if (hasTablouParts && !hasCarcasa) {
                    Text(
                        "⚠ Ai componente de tablou, dar nicio carcasă selectată. " +
                            "Bifează „Tablou 1/2/3 rânduri…” în Materiale ca etajele " +
                            "să intre în ofertă.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                if (quote.lines.isEmpty()) {
                    Text(
                        "Nicio linie de manoperă calculată. Setează prețurile per doză " +
                            "și per etaj de tablou în Setări → Manoperă, apoi revino.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    quote.lines.forEach { l ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                if (l.unitPrice > 0)
                                    "${l.name} — ${l.qty} × ${lei(l.unitPrice)} lei"
                                else "${l.name} — ${l.qty} buc",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                lei(l.value),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(
                        "Manoperă: ${lei(quote.laborTotal)} lei",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriceField("Deplasare", travel, Modifier.weight(1f)) { travel = it }
                    PriceField("Mâncare", food, Modifier.weight(1f)) { food = it }
                    PriceField("Consumabile", consumables, Modifier.weight(1f)) { consumables = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { v -> daysText = v.filter { it.isDigit() }.take(3) },
                        label = { Text("Zile estimate") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    PriceField("Ajutor lei/zi", helperPerDay, Modifier.weight(1f)) {
                        helperPerDay = it
                    }
                }
                if (quote.helperCost > 0) Text(
                    "Ajutor electrician: $days zile × ${lei(helperPerDay)} = ${lei(quote.helperCost)} lei",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "TOTAL OFERTĂ: ${lei(quote.total)} lei",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = quote.total > 0,
                onClick = {
                    // cheltuielile devin noile valori implicite
                    vm.saveLabor(
                        cfg.copy(
                            travel = travel, food = food,
                            consumables = consumables, helperPerDay = helperPerDay
                        )
                    )
                    exportLaborPdfAndShare(context, vm, work, quote)
                    onDismiss()
                }
            ) { Text("Generează PDF") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } }
    )
}

/** Prețurile de manoperă per doză. */
@Composable
private fun DozaLaborDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val cfg = vm.labor
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manoperă per doză") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Prețul de montaj pentru fiecare tip de doză (lei/buc).",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                vm.laborItems().forEach { name ->
                    val key = name.trim().lowercase()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        PriceField(
                            "lei", cfg.dozaPrices[key] ?: 0.0,
                            Modifier.widthIn(min = 96.dp, max = 96.dp)
                        ) { p ->
                            vm.saveLabor(
                                vm.labor.copy(dozaPrices = vm.labor.dozaPrices + (key to p))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gata") } }
    )
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
