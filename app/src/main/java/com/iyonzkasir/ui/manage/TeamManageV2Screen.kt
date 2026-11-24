package com.iyonzkasir.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.OutletEntity
import com.iyonzkasir.data.local.StaffEntity
import com.iyonzkasir.di.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamManageV2Screen(app: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val staffList by app.teamRepo.allStaff.collectAsState(initial = emptyList())
    val outlets by app.teamRepo.allOutlets.collectAsState(initial = emptyList())
    val owner = staffList.firstOrNull { it.isOwner }

    var tab by remember { mutableIntStateOf(0) }
    var editingStaff by remember { mutableStateOf<StaffEntity?>(null) }
    var showStaffDlg by remember { mutableStateOf(false) }
    var editingOutlet by remember { mutableStateOf<OutletEntity?>(null) }
    var showOutletDlg by remember { mutableStateOf(false) }
    var showOwnerPinDlg by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Cabang & Karyawan") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (tab == 0) {
                        if (owner != null && owner.pinHash.isBlank()) showOwnerPinDlg = true
                        else { editingStaff = null; showStaffDlg = true }
                    } else { editingOutlet = null; showOutletDlg = true }
                },
                containerColor = AppColors.brand
            ) { Icon(Icons.Default.Add, null, tint = Color.White) }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Karyawan (${staffList.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Cabang (${outlets.size})") })
            }
            if (tab == 0) {
                if (owner != null && owner.pinHash.isBlank()) {
                    Card(Modifier.fillMaxWidth().padding(AppSp.md).clickable { showOwnerPinDlg = true }, colors = CardDefaults.cardColors(containerColor = AppColors.brandSoft)) {
                        Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = AppColors.brand)
                            Spacer(Modifier.width(AppSp.sm))
                            Text("Set PIN Owner dulu sebelum menambah karyawan agar akun Owner aman.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                LazyColumn(contentPadding = PaddingValues(start = AppSp.md, end = AppSp.md, top = AppSp.sm, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    items(staffList, key = { it.id }) { s ->
                        val outName = if (s.outletId == "ALL") "Semua Cabang" else outlets.firstOrNull { it.id == s.outletId }?.name ?: "Cabang"
                        Card(Modifier.fillMaxWidth().clickable { if (s.isOwner) showOwnerPinDlg = true else { editingStaff = s; showStaffDlg = true } }, shape = AppRd.card) {
                            Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${s.name} (${s.jobTitle})", fontWeight = FontWeight.Bold)
                                    Text("$outName • ${if (s.isOwner) "Akses Penuh" else "${AppAccess.decode(s.accessKeys).size} izin"}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!s.active) Text("NONAKTIF", color = AppColors.danger, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                else Icon(Icons.Default.Edit, null, tint = AppColors.brand)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = AppSp.md, end = AppSp.md, top = AppSp.md, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    items(outlets, key = { it.id }) { o ->
                        Card(Modifier.fillMaxWidth().clickable { editingOutlet = o; showOutletDlg = true }, shape = AppRd.card) {
                            Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Store, null, tint = AppColors.brand)
                                Spacer(Modifier.width(AppSp.md))
                                Column(Modifier.weight(1f)) {
                                    Text(o.name, fontWeight = FontWeight.Bold)
                                    if (o.address.isNotBlank()) Text(o.address, style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.Default.Edit, null)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOwnerPinDlg) {
        var p1 by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showOwnerPinDlg = false }, title = { Text("Atur PIN Owner") },
            text = { OutlinedTextField(p1, { p1 = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("PIN Owner (4-6 digit) *") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true) },
            confirmButton = { TextButton(enabled = p1.length in 4..6, onClick = { scope.launch { app.teamRepo.setOwnerPin(p1); app.triggerSyncInBackground(); showOwnerPinDlg = false } }) { Text("Simpan PIN") } },
            dismissButton = { TextButton(onClick = { showOwnerPinDlg = false }) { Text("Batal") } })
    }
    if (showStaffDlg) StaffEditorDlg(app, editingStaff, outlets, onDismiss = { showStaffDlg = false })
    if (showOutletDlg) OutletEditorDlg(app, editingOutlet, onDismiss = { showOutletDlg = false })
}
@Composable
private fun StaffEditorDlg(app: AppContainer, initial: StaffEntity?, outlets: List<OutletEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var job by remember { mutableStateOf(initial?.jobTitle ?: "Kasir") }
    var pin by remember { mutableStateOf("") }
    var outletId by remember { mutableStateOf(initial?.outletId ?: outlets.firstOrNull()?.id ?: "ALL") }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var accesses by remember { mutableStateOf(if (initial == null) AccessPreset.KASIR.accesses else AppAccess.decode(initial.accessKeys)) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initial == null) "Tambah Karyawan" else "Edit Karyawan") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                OutlinedTextField(name, { name = it; err = null }, label = { Text("Nama Karyawan *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(job, { job = it }, label = { Text("Jabatan (bebas ketik)") }, placeholder = { Text("cth: Dapur 1, Kasir Malam") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pin, { pin = it.filter { c -> c.isDigit() }.take(6); err = null },
                    label = { Text(if (initial == null) "PIN Login (4-6 digit) *" else "PIN Baru (kosong jika tetap)") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Cabang Tugas:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { FilterChip(selected = outletId == "ALL", onClick = { outletId = "ALL" }, label = { Text("Semua Cabang") }) }
                    items(outlets, key = { it.id }) { o -> FilterChip(selected = outletId == o.id, onClick = { outletId = o.id }, label = { Text(o.name) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Akun Aktif", Modifier.weight(1f)); Switch(checked = active, onCheckedChange = { active = it }) }
                Text("Template Cepat (bisa diubah):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(AccessPreset.entries) { preset ->
                        AssistChip(onClick = { accesses = preset.accesses; if (initial == null) job = preset.defaultJobTitle }, label = { Text(preset.label) })
                    }
                    item { AssistChip(onClick = { accesses = AppAccess.entries.toSet() }, label = { Text("Semua") }) }
                    item { AssistChip(onClick = { accesses = emptySet() }, label = { Text("Kosong") }) }
                }
                Text("A. Halaman yang Tampil:", fontWeight = FontWeight.Bold, color = AppColors.brand)
                AppAccess.menuItems().forEach { item ->
                    val chk = item in accesses
                    Row(Modifier.fillMaxWidth().clickable { accesses = if (chk) accesses - item else accesses + item }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = chk, onCheckedChange = null)
                        Column { Text(item.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold); Text(item.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Text("B. Tindakan Khusus:", fontWeight = FontWeight.Bold, color = AppColors.brand)
                AppAccess.actionItems().forEach { item ->
                    val chk = item in accesses
                    Row(Modifier.fillMaxWidth().clickable { accesses = if (chk) accesses - item else accesses + item }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = chk, onCheckedChange = null)
                        Column { Text(item.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold); Text(item.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                err?.let { Text(it, color = AppColors.danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.isBlank() -> err = "Nama wajib diisi"
                    initial == null && pin.length < 4 -> err = "PIN minimal 4 digit"
                    pin.isNotBlank() && pin.length < 4 -> err = "PIN baru minimal 4 digit"
                    accesses.none { it.category == AccessCategory.MENU } -> err = "Centang minimal 1 halaman"
                    else -> scope.launch {
                        val salt = if (pin.isNotBlank()) PinHasher.generateSalt() else initial!!.pinSalt
                        val hash = if (pin.isNotBlank()) PinHasher.hash(pin, salt) else initial!!.pinHash
                        val s = (initial ?: StaffEntity(businessId = SessionManager.businessId)).copy(
                            name = name.trim(), jobTitle = job.trim().ifBlank { "Karyawan" },
                            pinSalt = salt, pinHash = hash, outletId = outletId, accessKeys = AppAccess.encode(accesses), active = active
                        )
                        app.teamRepo.saveStaff(s); app.triggerSyncInBackground(); onDismiss()
                    }
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun OutletEditorDlg(app: AppContainer, initial: OutletEntity?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var addr by remember { mutableStateOf(initial?.address ?: "") }
    var phone by remember { mutableStateOf(initial?.phoneWa ?: "") }
    var footer by remember { mutableStateOf(initial?.footerReceipt ?: "Terima kasih sudah berbelanja") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initial == null) "Cabang / Gerobak Baru" else "Edit Cabang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama Cabang / Gerobak *") }, singleLine = true)
                OutlinedTextField(addr, { addr = it }, label = { Text("Alamat / Lokasi") })
                OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, label = { Text("No. WhatsApp") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(footer, { footer = it }, label = { Text("Footer Struk") })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                scope.launch {
                    val o = (initial ?: OutletEntity(businessId = SessionManager.businessId)).copy(name = name.trim(), address = addr.trim(), phoneWa = phone.trim(), footerReceipt = footer.trim())
                    app.teamRepo.saveOutlet(o); app.triggerSyncInBackground(); onDismiss()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
