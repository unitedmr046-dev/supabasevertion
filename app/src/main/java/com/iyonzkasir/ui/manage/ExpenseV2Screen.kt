package com.iyonzkasir.ui.manage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import com.iyonzkasir.data.remote.ImageHelper
import com.iyonzkasir.di.AppContainer
import com.iyonzkasir.ui.pos.MenuPhotoBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseV2Screen(app: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val user by SessionManager.userState
    val viewingOutlet by SessionManager.viewingOutletIdState
    val filterOutlet = remember(viewingOutlet, user) {
        val uo = user?.outletId.orEmpty()
        if (uo.isNotBlank() && uo != "ALL") uo else viewingOutlet.ifBlank { "ALL" }
    }
    val outlets by app.teamRepo.activeOutlets.collectAsState(initial = emptyList())
    val txOutletId = SessionManager.activeTransactionOutletId(outlets.firstOrNull()?.id.orEmpty())
    val txOutletName = outlets.firstOrNull { it.id == txOutletId }?.name ?: ""

    val allExpenses by app.expenseRepo.observeExpenses(filterOutlet).collectAsState(initial = emptyList())
    val categories by app.expenseRepo.categories.collectAsState(initial = emptyList())
    var daysFilter by remember { mutableIntStateOf(1) }
    var showDlg by remember { mutableStateOf(false) }

    val startMs = remember(daysFilter) {
        when (daysFilter) { 1 -> startOfTodayMillis(); 7 -> daysAgoStartMillis(6); 30 -> daysAgoStartMillis(29); else -> 0L }
    }
    val filtered = remember(allExpenses, startMs) { allExpenses.filter { it.timestamp >= startMs } }
    val total = remember(filtered) { filtered.sumOf { it.amount } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pengeluaran / Belanja") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDlg = true }, containerColor = AppColors.brand) { Icon(Icons.Default.Add, null, tint = Color.White) }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(AppSp.md), colors = CardDefaults.cardColors(containerColor = AppColors.brandSoft)) {
                Column(Modifier.padding(AppSp.lg)) {
                    Text("Total Belanja", style = MaterialTheme.typography.bodySmall)
                    Text(total.toRupiah(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AppColors.danger)
                }
            }
            Row(Modifier.padding(horizontal = AppSp.md), horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                listOf(1 to "Hari Ini", 7 to "7 Hari", 30 to "30 Hari", 0 to "Semua").forEach { (d, lbl) ->
                    FilterChip(selected = daysFilter == d, onClick = { daysFilter = d }, label = { Text(lbl) })
                }
            }
            if (filtered.isEmpty()) {
                EmptyPlaceholder(Icons.Default.ReceiptLong, "Belum ada catatan belanja", "Catat pembelian bahan, gas, plastik, dll di sini")
            } else {
                LazyColumn(contentPadding = PaddingValues(start = AppSp.md, end = AppSp.md, top = AppSp.sm, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    items(filtered, key = { it.id }) { e ->
                        Card(Modifier.fillMaxWidth(), shape = AppRd.card) {
                            Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                                if (!e.localPhotoPath.isNullOrBlank() || !e.remotePhotoUrl.isNullOrBlank()) {
                                    MenuPhotoBox(e.localPhotoPath, e.remotePhotoUrl, "🧾", Modifier.size(44.dp).clip(AppRd.card))
                                    Spacer(Modifier.width(AppSp.sm))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(e.categoryName, fontWeight = FontWeight.Bold)
                                    if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall)
                                    Text("${e.outletName} • ${e.timestamp.formatDateTime()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("-${e.amount.toRupiah()}", fontWeight = FontWeight.Bold, color = AppColors.danger)
                                if (SessionManager.can(AppAccess.DELETE_DATA)) {
                                    IconButton(onClick = { scope.launch { app.expenseRepo.deleteExpense(e); app.triggerSyncInBackground() } }) {
                                        Icon(Icons.Default.Delete, null, tint = AppColors.danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDlg) ExpenseAddDlg(app, txOutletId, txOutletName, categories, onDismiss = { showDlg = false })
}

@Composable
private fun ExpenseAddDlg(app: AppContainer, outletId: String, outletName: String, cats: List<ExpenseCategoryEntity>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var catId by remember { mutableStateOf(cats.firstOrNull()?.id.orEmpty()) }
    var catName by remember { mutableStateOf(cats.firstOrNull()?.name ?: "Bahan Baku") }
    var amtTxt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var localPhoto by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { ImageHelper.savePickedPhoto(ctx, uri, "exp")?.let { localPhoto = it } }
    }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Catat Pengeluaran") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(cats, key = { it.id }) { c ->
                        FilterChip(selected = catId == c.id, onClick = { catId = c.id; catName = c.name }, label = { Text("${c.icon} ${c.name}") })
                    }
                }
                OutlinedTextField(amtTxt, { amtTxt = it.filter { c -> c.isDigit() }.take(10) }, label = { Text("Jumlah (Rp) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Keterangan (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (localPhoto == null) "Foto Nota Belanja (opsional)" else "Foto Terlampir ✓")
                }
            }
        },
        confirmButton = {
            val amt = amtTxt.toLongOrNull() ?: 0L
            TextButton(enabled = amt > 0L, onClick = {
                scope.launch {
                    val u = SessionManager.currentUser
                    app.expenseRepo.saveExpense(ExpenseEntity(
                        businessId = SessionManager.businessId, outletId = outletId, outletName = outletName,
                        categoryId = catId, categoryName = catName, amount = amt, note = note.trim(),
                        localPhotoPath = localPhoto, staffId = u?.id ?: "", staffName = u?.name ?: ""
                    ))
                    app.triggerSyncInBackground(); onDismiss()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
