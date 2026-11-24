package com.iyonzkasir.ui.activity

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import com.iyonzkasir.di.AppContainer
import kotlinx.coroutines.launch

@Composable
fun KasbonAndCrmV2Screen(app: AppContainer) {
    val scope = rememberCoroutineScope()
    val members by app.crmRepo.members.collectAsState(initial = emptyList())
    val totalDebt by app.crmRepo.totalDebt.collectAsState(initial = 0L)
    val vouchers by app.crmRepo.vouchers.collectAsState(initial = emptyList())

    var tab by remember { mutableIntStateOf(0) }
    var onlyDebtors by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var editingMember by remember { mutableStateOf<MemberEntity?>(null) }
    var showAddMember by remember { mutableStateOf(false) }
    var detailMember by remember { mutableStateOf<MemberEntity?>(null) }
    var showAddVoucher by remember { mutableStateOf(false) }

    val filteredMembers = remember(members, onlyDebtors, search) {
        members.filter {
            (!onlyDebtors || it.debt > 0L) &&
                (search.isBlank() || it.name.contains(search, true) || it.phone.contains(search))
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(AppSp.md), horizontalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Pelanggan & Kasbon") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Voucher (${vouchers.size})") })
            }

            if (tab == 0) {
                Card(Modifier.fillMaxWidth().padding(horizontal = AppSp.md), colors = CardDefaults.cardColors(containerColor = AppColors.brandSoft)) {
                    Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Total Kasbon Belum Lunas", style = MaterialTheme.typography.labelSmall)
                            Text(totalDebt.toRupiah(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.danger)
                        }
                        FilterChip(selected = onlyDebtors, onClick = { onlyDebtors = !onlyDebtors }, label = { Text("Hanya Kasbon") })
                    }
                }
                OutlinedTextField(
                    value = search, onValueChange = { search = it }, placeholder = { Text("Cari nama / HP...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(AppSp.md)
                )
                if (filteredMembers.isEmpty()) {
                    EmptyPlaceholder(Icons.Default.People, "Belum ada data pelanggan", "Tap + untuk tambah pelanggan atau kasbon baru")
                } else {
                    LazyColumn(contentPadding = PaddingValues(start = AppSp.md, end = AppSp.md, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                        items(filteredMembers, key = { it.id }) { m ->
                            Card(Modifier.fillMaxWidth().clickable { detailMember = m }, shape = AppRd.card) {
                                Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(m.name, fontWeight = FontWeight.Bold)
                                        Text("${m.phone.ifBlank { "-" }} • ⭐ ${m.points} poin", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (m.debt > 0L) Text(m.debt.toRupiah(), fontWeight = FontWeight.Bold, color = AppColors.danger)
                                    else Text("Lunas", style = MaterialTheme.typography.labelSmall, color = AppColors.success)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = AppSp.md, end = AppSp.md, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    items(vouchers, key = { it.code }) { v ->
                        Card(Modifier.fillMaxWidth(), shape = AppRd.card) {
                            Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${v.code} — ${v.name}", fontWeight = FontWeight.Bold)
                                    Text(if (v.discountType == "PERCENT") "Diskon ${v.value}% • Pakai ${v.usedCount}" else "Potongan ${v.value.toRupiah()} • Pakai ${v.usedCount}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (SessionManager.can(AppAccess.DELETE_DATA)) {
                                    IconButton(onClick = { scope.launch { app.crmRepo.deleteVoucher(v); app.triggerSyncInBackground() } }) {
                                        Icon(Icons.Default.Delete, null, tint = AppColors.danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { if (tab == 0) { editingMember = null; showAddMember = true } else showAddVoucher = true },
            containerColor = AppColors.brand,
            modifier = Modifier.align(Alignment.BottomEnd).padding(AppSp.lg)
        ) { Icon(Icons.Default.Add, null) }
    }
    if (showAddMember) {
        var nama by remember { mutableStateOf(editingMember?.name ?: "") }
        var hp by remember { mutableStateOf(editingMember?.phone ?: "") }
        var addr by remember { mutableStateOf(editingMember?.address ?: "") }
        AlertDialog(
            onDismissRequest = { showAddMember = false },
            title = { Text(if (editingMember == null) "Pelanggan Baru" else "Edit Pelanggan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    OutlinedTextField(nama, { nama = it }, label = { Text("Nama *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(hp, { hp = it.filter { c -> c.isDigit() || c == '+' } }, label = { Text("No. WhatsApp") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(addr, { addr = it }, label = { Text("Alamat / Catatan") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = nama.isNotBlank(), onClick = {
                    scope.launch {
                        val m = (editingMember ?: MemberEntity(businessId = SessionManager.businessId)).copy(name = nama.trim(), phone = hp.trim(), address = addr.trim())
                        app.crmRepo.saveMember(m); app.triggerSyncInBackground(); showAddMember = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAddMember = false }) { Text("Batal") } }
        )
    }

    detailMember?.let { m ->
        MemberDetailDlg(app, m, onDismiss = { detailMember = null }, onEdit = { editingMember = m; detailMember = null; showAddMember = true })
    }

    if (showAddVoucher) {
        var code by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("NOMINAL") }
        var valTxt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddVoucher = false }, title = { Text("Voucher Promo Baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    OutlinedTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(16) }, label = { Text("Kode *") }, singleLine = true)
                    OutlinedTextField(name, { name = it }, label = { Text("Nama Promo") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                        FilterChip(selected = type == "NOMINAL", onClick = { type = "NOMINAL" }, label = { Text("Rp") })
                        FilterChip(selected = type == "PERCENT", onClick = { type = "PERCENT" }, label = { Text("%") })
                    }
                    OutlinedTextField(valTxt, { valTxt = it.filter { c -> c.isDigit() }.take(8) }, label = { Text("Nilai Diskon *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            },
            confirmButton = {
                val v = valTxt.toLongOrNull() ?: 0L
                TextButton(enabled = code.isNotBlank() && v > 0L, onClick = {
                    scope.launch {
                        app.crmRepo.saveVoucher(VoucherEntity(code = code, businessId = SessionManager.businessId, name = name.ifBlank { code }, discountType = type, value = v))
                        app.triggerSyncInBackground(); showAddVoucher = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showAddVoucher = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun MemberDetailDlg(app: AppContainer, member: MemberEntity, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val txs by app.crmRepo.observeMemberTx(member.id).collectAsState(initial = emptyList())
    var payText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(member.name) },
        text = {
            Column(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                Text("⭐ Poin: ${member.points}   •   💳 Kasbon: ${member.debt.toRupiah()}", fontWeight = FontWeight.Bold)
                if (member.debt > 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                        OutlinedTextField(payText, { payText = it.filter { c -> c.isDigit() }.take(10) }, label = { Text("Bayar Kasbon") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Button(onClick = {
                            val amt = payText.toLongOrNull() ?: member.debt
                            scope.launch {
                                val outletId = SessionManager.activeTransactionOutletId("")
                                app.crmRepo.payDebt(member.id, amt, outletId, "Bayar kasbon")
                                app.triggerSyncInBackground(); onDismiss()
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.success)) { Text("Bayar") }
                    }
                }
                if (member.phone.isNotBlank()) {
                    OutlinedButton(onClick = {
                        val wa = normalizeWaPhone(member.phone)
                        val msg = "Halo ${member.name}, catatan kasbon Anda saat ini: ${member.debt.toRupiah()}, poin: ${member.points}. Terima kasih!"
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa?text=${Uri.encode(msg)}"))) }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Hubungi / Tagih via WhatsApp") }
                }
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 180.dp)) {
                    items(txs, key = { it.id }) { t ->
                        Text("${t.timestamp.formatDateTime()} • ${t.note} (${if (t.debtDelta != 0L) t.debtDelta.toRupiah() else "${t.pointsDelta} pts"})", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Edit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
