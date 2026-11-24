package com.iyonzkasir.ui.manage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.ShiftEntity
import com.iyonzkasir.di.AppContainer
import com.iyonzkasir.hardware.ReceiptFormatter
import com.iyonzkasir.hardware.ThermalPrinter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftV2Screen(app: AppContainer, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val outlets by app.teamRepo.activeOutlets.collectAsState(initial = emptyList())
    val outletId = SessionManager.activeTransactionOutletId(outlets.firstOrNull()?.id.orEmpty())
    val outletName = outlets.firstOrNull { it.id == outletId }?.name ?: ""
    val activeShift by app.shiftRepo.observeActive(outletId).collectAsState(initial = null)
    val history by app.shiftRepo.observeAll(outletId.ifBlank { "ALL" }).collectAsState(initial = emptyList())

    var showOpen by remember { mutableStateOf(false) }
    var showClose by remember { mutableStateOf(false) }
    var lastClosed by remember { mutableStateOf<ShiftEntity?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Shift Kasir ($outletName)") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(AppSp.md), verticalArrangement = Arrangement.spacedBy(AppSp.md)) {
            Card(colors = CardDefaults.cardColors(containerColor = AppColors.brandSoft), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AppSp.lg), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    if (activeShift == null) {
                        Text("Shift Belum Dibuka", fontWeight = FontWeight.Bold)
                        Button(onClick = { showOpen = true }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand), modifier = Modifier.fillMaxWidth()) { Text("Buka Shift") }
                    } else {
                        Text("Shift Aktif — ${activeShift!!.staffName}", fontWeight = FontWeight.Bold)
                        Text("Modal Awal: ${activeShift!!.openingCash.toRupiah()} • ${activeShift!!.openedAt.formatDateTime()}", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { showClose = true }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.danger), modifier = Modifier.fillMaxWidth()) { Text("Tutup Shift & Hitung Kas") }
                    }
                }
            }
            Text("Riwayat Shift", fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                items(history, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth(), shape = AppRd.card) {
                        Column(Modifier.padding(AppSp.md)) {
                            Row { Text("${s.staffName} • ${s.status}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(s.totalSales.toRupiah(), color = AppColors.brand, fontWeight = FontWeight.Bold) }
                            Text("Tunai: ${s.cashSales.toRupiah()} • Non-Tunai: ${s.nonCashSales.toRupiah()} • Kasbon: ${s.kasbonSales.toRupiah()}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showOpen) {
        var modalTxt by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showOpen = false }, title = { Text("Modal Awal Laci") },
            text = { OutlinedTextField(modalTxt, { modalTxt = it.filter { c -> c.isDigit() }.take(10) }, label = { Text("Jumlah Uang Tunai (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(onClick = { scope.launch { app.shiftRepo.openShift(SessionManager.businessId, outletId, outletName, modalTxt.toLongOrNull() ?: 0L); app.triggerSyncInBackground(); showOpen = false } }) { Text("Buka") } },
            dismissButton = { TextButton(onClick = { showOpen = false }) { Text("Batal") } })
    }
    if (showClose && activeShift != null) {
        var cashTxt by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showClose = false }, title = { Text("Tutup Shift") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    OutlinedTextField(cashTxt, { cashTxt = it.filter { c -> c.isDigit() }.take(10) }, label = { Text("Uang Fisik di Laci (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(note, { note = it }, label = { Text("Catatan (opsional)") })
                }
            },
            confirmButton = {
                TextButton(enabled = cashTxt.isNotBlank(), onClick = {
                    scope.launch {
                        lastClosed = app.shiftRepo.closeShift(activeShift!!.id, cashTxt.toLongOrNull() ?: 0L, note.trim())
                        app.triggerSyncInBackground(); showClose = false
                    }
                }) { Text("Tutup", color = AppColors.danger) }
            },
            dismissButton = { TextButton(onClick = { showClose = false }) { Text("Batal") } })
    }
    lastClosed?.let { s ->
        AlertDialog(onDismissRequest = { lastClosed = null }, title = { Text("Rekap Shift") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Transaksi: ${s.txCount}x • Omzet: ${s.totalSales.toRupiah()}")
                    Text("Tunai: ${s.cashSales.toRupiah()} | Non-Tunai: ${s.nonCashSales.toRupiah()} | Kasbon: ${s.kasbonSales.toRupiah()}")
                    Text("Selisih Kas: ${s.cashDiff.toRupiah()}", fontWeight = FontWeight.Bold, color = if (s.cashDiff == 0L) AppColors.success else AppColors.danger)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val mac = app.prefRepo.getPrinterMac()
                        if (!ThermalPrinter.isConnected() && mac.isNotBlank()) ThermalPrinter.connect(ctx, mac)
                        if (ThermalPrinter.isConnected()) ThermalPrinter.send(ReceiptFormatter.buildShiftEscPos(app.prefRepo, s))
                    }
                }) { Text("Cetak Struk") }
            },
            dismissButton = { TextButton(onClick = { lastClosed = null }) { Text("Tutup") } })
    }
}
