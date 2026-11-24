package com.iyonzkasir.ui.manage

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.iyonzkasir.core.*
import com.iyonzkasir.data.repo.PrefRepository
import com.iyonzkasir.di.AppContainer
import com.iyonzkasir.hardware.ReceiptFormatter
import com.iyonzkasir.hardware.ThermalPrinter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterAndReceiptV2Screen(app: AppContainer, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mac by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var paper by remember { mutableIntStateOf(58) }
    var autoPrint by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(ThermalPrinter.isConnected()) }
    var msg by remember { mutableStateOf<String?>(null) }
    var showPick by remember { mutableStateOf(false) }
    var tax by remember { mutableIntStateOf(0) }
    var queueOn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        mac = app.prefRepo.getPrinterMac(); name = app.prefRepo.getPrinterName()
        paper = app.prefRepo.getPaperWidth(); autoPrint = app.prefRepo.isAutoPrint()
        tax = app.prefRepo.getDefaultTaxPercent(); queueOn = app.prefRepo.isQueueEnabled()
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) showPick = true else msg = "Izin Bluetooth ditolak"
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Printer, Struk & Pajak") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSp.lg), verticalArrangement = Arrangement.spacedBy(AppSp.md)) {
            Card(colors = CardDefaults.cardColors(containerColor = if (connected) AppColors.brandSoft else AppColors.cardAlt)) {
                Row(Modifier.padding(AppSp.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (connected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled, null, tint = AppColors.brand)
                    Spacer(Modifier.width(AppSp.md))
                    Column(Modifier.weight(1f)) {
                        Text(if (connected) "Terhubung: $name" else name.ifBlank { "Belum ada printer" }, fontWeight = FontWeight.Bold)
                        Text(mac.ifBlank { "Pilih perangkat Bluetooth" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        permLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
                    } else showPick = true
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand)) { Text("Pilih Printer") }
                if (mac.isNotBlank()) OutlinedButton(onClick = {
                    scope.launch {
                        if (connected) { ThermalPrinter.disconnect(); connected = false }
                        else { connected = ThermalPrinter.connect(ctx, mac); msg = if (connected) "Terhubung" else ThermalPrinter.lastError }
                    }
                }, modifier = Modifier.weight(1f)) { Text(if (connected) "Putus" else "Sambung") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                listOf(58, 80).forEach { w -> FilterChip(selected = paper == w, onClick = { paper = w; scope.launch { app.prefRepo.setPaperWidth(w) } }, label = { Text("Kertas ${w}mm") }) }
                OutlinedButton(enabled = connected, onClick = {
                    scope.launch {
                        val outlet = app.teamRepo.getActiveOutletsSync().firstOrNull()
                        val ok = ThermalPrinter.send(ReceiptFormatter.buildTestPrintEscPos(app.prefRepo, outlet))
                        msg = if (ok) "Test print terkirim" else ThermalPrinter.lastError
                    }
                }) { Text("Test Print") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cetak Otomatis Setelah Bayar", Modifier.weight(1f)); Switch(checked = autoPrint, onCheckedChange = { autoPrint = it; scope.launch { app.prefRepo.setAutoPrint(it) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nomor Antrian Otomatis", Modifier.weight(1f)); Switch(checked = queueOn, onCheckedChange = { queueOn = it; scope.launch { app.prefRepo.setQueueEnabled(it) } })
            }
            Text("Pajak / PPN Default:", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                listOf(0, 5, 10, 11, 12).forEach { p -> FilterChip(selected = tax == p, onClick = { tax = p; scope.launch { app.prefRepo.setDefaultTaxPercent(p) } }, label = { Text(if (p == 0) "Off" else "$p%") }) }
            }
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.brand) }
        }
    }

    if (showPick) {
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val devs: List<BluetoothDevice> = runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList())
        AlertDialog(onDismissRequest = { showPick = false }, title = { Text("Pilih Printer Ter-pair") },
            text = {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(devs, key = { it.address }) { d ->
                        ListItem(headlineContent = { Text(d.name ?: "Printer") }, supportingContent = { Text(d.address) },
                            modifier = Modifier.clickable {
                                mac = d.address; name = d.name ?: "Printer"; showPick = false
                                scope.launch { app.prefRepo.setPrinterMac(mac); app.prefRepo.setPrinterName(name); connected = ThermalPrinter.connect(ctx, mac) }
                            })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPick = false }) { Text("Tutup") } })
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeAndBackupV2Screen(app: AppContainer, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(AppThemeState.mode) }
    var preset by remember { mutableStateOf(AppThemeState.preset) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMode by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { pendingUri = uri; pendingMode = "backup"; pwd = "" }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingUri = uri; pendingMode = "restore"; pwd = "" }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Tema & Backup Data") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSp.lg), verticalArrangement = Arrangement.spacedBy(AppSp.md)) {
            Text("Mode Tampilan", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                DisplayThemeMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = {
                        mode = m; AppThemeState.updateMode(m); scope.launch { app.prefRepo.setThemeMode(m) }
                    }, label = { Text(m.label) })
                }
            }
            Text("Warna Brand", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                BrandPreset.entries.take(4).forEach { p ->
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(p.primaryHex)).clickable {
                        preset = p; AppThemeState.updatePreset(p); scope.launch { app.prefRepo.setBrandPreset(p) }
                    }, contentAlignment = Alignment.Center) { if (preset == p) Icon(Icons.Default.Check, null, tint = Color.White) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                BrandPreset.entries.drop(4).forEach { p ->
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(p.primaryHex)).clickable {
                        preset = p; AppThemeState.updatePreset(p); scope.launch { app.prefRepo.setBrandPreset(p) }
                    }, contentAlignment = Alignment.Center) { if (preset == p) Icon(Icons.Default.Check, null, tint = Color.White) }
                }
            }
            HorizontalDivider()
            Text("Backup & Pulihkan File (.iyonz)", fontWeight = FontWeight.Bold)
            Button(onClick = { saveLauncher.launch(V2BackupHelper.defaultFileName()) }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand)) { Text("Backup ke File (.iyonz)") }
            OutlinedButton(onClick = { openLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Pulihkan (Restore) dari File") }
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.brand) }
        }
    }

    if (pendingUri != null) {
        AlertDialog(onDismissRequest = { pendingUri = null }, title = { Text(if (pendingMode == "backup") "Buat Password Backup" else "Masukkan Password Backup") },
            text = { OutlinedTextField(pwd, { pwd = it }, label = { Text("Password (min 6 karakter)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) },
            confirmButton = {
                TextButton(enabled = pwd.length >= 6, onClick = {
                    val uri = pendingUri!!; val isBak = pendingMode == "backup"; pendingUri = null
                    scope.launch {
                        if (isBak) {
                            val r = ctx.contentResolver.openOutputStream(uri)?.use { V2BackupHelper.backup(ctx, pwd, it) } ?: Result.failure(Exception("Gagal buka file"))
                            msg = if (r.isSuccess) "✅ Backup tersimpan!" else "❌ ${r.exceptionOrNull()?.message}"
                        } else {
                            val r = ctx.contentResolver.openInputStream(uri)?.use { V2BackupHelper.restore(ctx, pwd, it) } ?: Result.failure(Exception("Gagal buka file"))
                            if (r.isSuccess) { msg = "✅ Restore berhasil! Restart..."; delay(1000); V2BackupHelper.restartApp(ctx) }
                            else msg = "❌ ${r.exceptionOrNull()?.message}"
                        }
                    }
                }) { Text("Lanjut") }
            },
            dismissButton = { TextButton(onClick = { pendingUri = null }) { Text("Batal") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogV2Screen(app: AppContainer, onBack: () -> Unit) {
    val logs by app.teamRepo.observeAudit("ALL").collectAsState(initial = emptyList())
    Scaffold(topBar = { TopAppBar(title = { Text("Catatan Aktivitas (Audit)") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        if (logs.isEmpty()) Box(Modifier.padding(pad).fillMaxSize()) { EmptyPlaceholder(Icons.Default.FactCheck, "Belum ada catatan audit") }
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(AppSp.md), verticalArrangement = Arrangement.spacedBy(AppSp.xs)) {
            items(logs, key = { it.id }) { l ->
                Card(Modifier.fillMaxWidth(), shape = AppRd.card) {
                    Column(Modifier.padding(AppSp.md)) {
                        Row { Text(l.action, fontWeight = FontWeight.Bold, color = AppColors.brand, modifier = Modifier.weight(1f)); Text(l.timestamp.formatDateTime(), style = MaterialTheme.typography.labelSmall) }
                        Text("${l.staffName}: ${l.detail}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
