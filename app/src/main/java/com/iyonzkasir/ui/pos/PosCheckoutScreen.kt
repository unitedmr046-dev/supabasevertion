package com.iyonzkasir.ui.pos

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.MemberEntity
import com.iyonzkasir.data.local.OrderEntity
import com.iyonzkasir.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosCheckoutScreen(
    app: AppContainer,
    controller: PosV2Controller,
    onBack: () -> Unit,
    onPaid: (OrderEntity) -> Unit,
    onSavedOpenBill: () -> Unit
) {
    val outlets by app.teamRepo.activeOutlets.collectAsState(initial = emptyList())
    val activeOutletId = SessionManager.activeTransactionOutletId(outlets.firstOrNull()?.id.orEmpty())
    val activeOutletName = outlets.firstOrNull { it.id == activeOutletId }?.name ?: ""
    val members by app.crmRepo.members.collectAsState(initial = emptyList())

    val cart by controller.cart.collectAsState()
    val subtotal by controller.subtotal.collectAsState()
    val disc = controller.calcDiscount(subtotal)
    val ptsRp = LoyaltyRules.pointsToRupiah(controller.pointsToRedeem)
    val afterDisc = (subtotal - disc - controller.voucherAmount - ptsRp).coerceAtLeast(0L)
    val taxAmt = controller.calcTax(afterDisc)
    val total = afterDisc + taxAmt

    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH.id) }
    var paidText by remember { mutableStateOf("") }
    var voucherInput by remember { mutableStateOf(controller.voucherCode) }
    var showDiscountDlg by remember { mutableStateOf(false) }
    var showMemberDlg by remember { mutableStateOf(false) }
    var noteFor by remember { mutableStateOf<CartItem?>(null) }

    val canDiscount = SessionManager.can(AppAccess.GIVE_DISCOUNT)
    val methods = remember(controller.activePayments, controller.selectedMember) {
        PaymentMethod.entries.filter {
            (controller.activePayments.isEmpty() || it.id in controller.activePayments) &&
                (it != PaymentMethod.KASBON || controller.selectedMember != null)
        }
    }
    LaunchedEffect(methods) { if (methods.none { it.id == paymentMethod } && methods.isNotEmpty()) paymentMethod = methods.first().id }

    val paidLong = paidText.toLongOrNull() ?: 0L
    val change = (paidLong - total).coerceAtLeast(0L)
    val canPay = cart.isNotEmpty() && (paymentMethod != PaymentMethod.CASH.id || paidLong >= total)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Keranjang & Bayar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(AppSp.md), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
            items(cart, key = { it.key }) { item ->
                Card(shape = AppRd.card, colors = CardDefaults.cardColors(containerColor = AppColors.card)) {
                    Column(Modifier.padding(AppSp.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                Text(item.unitPrice.toRupiah(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.note.isNotBlank()) Text("Catatan: ${item.note}", style = MaterialTheme.typography.labelSmall, color = AppColors.brand)
                            }
                            if (!item.isBundle) IconButton(onClick = { noteFor = item }) { Icon(Icons.Default.EditNote, null) }
                            IconButton(onClick = { controller.decrease(item.key) }) { Icon(Icons.Default.RemoveCircleOutline, null) }
                            Text("${item.qty}", fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                if (item.isBundle) controller.addBundle(item.bundle!!, item.picks)
                                else controller.addProduct(item.product!!, null, item.note)
                            }) { Icon(Icons.Default.AddCircleOutline, null) }
                        }
                    }
                }
            }
            item {
                Card(shape = AppRd.card) {
                    Column(Modifier.padding(AppSp.md), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                            OutlinedButton(onClick = { showMemberDlg = true }, modifier = Modifier.weight(1f)) {
                                Text(controller.selectedMember?.name ?: "Pilih Pelanggan", maxLines = 1)
                            }
                            if (canDiscount) {
                                OutlinedButton(onClick = { showDiscountDlg = true }, modifier = Modifier.weight(1f)) {
                                    Text(if (disc > 0L) "Disc -${disc.toRupiah()}" else "+ Diskon")
                                }
                            }
                        }
                        if (canDiscount) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                                OutlinedTextField(voucherInput, { voucherInput = it.uppercase().take(20) }, label = { Text("Kode Voucher") }, singleLine = true, modifier = Modifier.weight(1f))
                                Button(onClick = { controller.applyVoucher(voucherInput, subtotal) }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand)) {
                                    Text("Pakai")
                                }
                            }
                            controller.voucherError?.let { Text(it, color = AppColors.danger, style = MaterialTheme.typography.labelSmall) }
                        }
                        HorizontalDivider()
                        SummaryRow("Subtotal", subtotal.toRupiah())
                        if (disc > 0L) SummaryRow("Diskon", "-${disc.toRupiah()}", AppColors.danger)
                        if (controller.voucherAmount > 0L) SummaryRow("Voucher (${controller.voucherCode})", "-${controller.voucherAmount.toRupiah()}", AppColors.danger)
                        if (taxAmt > 0L) SummaryRow("Pajak ${controller.taxPercent}%", taxAmt.toRupiah())
                        Row {
                            Text("TOTAL", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(total.toRupiah(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = AppColors.brand)
                        }
                    }
                }
            }

            item {
                Text("Metode Pembayaran", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                    items(methods) { m -> FilterChip(selected = paymentMethod == m.id, onClick = { paymentMethod = m.id }, label = { Text(m.label) }) }
                }
                if (paymentMethod == PaymentMethod.CASH.id) {
                    Spacer(Modifier.height(AppSp.xs))
                    OutlinedTextField(
                        value = paidText, onValueChange = { paidText = it.filter { c -> c.isDigit() }.take(11) },
                        label = { Text("Uang Diterima") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSp.xs), modifier = Modifier.padding(top = AppSp.xs)) {
                        val quick = listOf(total, ((total + 4999L) / 5000L) * 5000L, 20_000L, 50_000L, 100_000L).distinct().filter { it >= total }
                        items(quick) { v -> AssistChip(onClick = { paidText = v.toString() }, label = { Text(v.toRupiah()) }) }
                    }
                    Row(Modifier.padding(top = AppSp.xs)) {
                        Text("Kembalian", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(change.toRupiah(), fontWeight = FontWeight.Bold, color = AppColors.brand)
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSp.sm), modifier = Modifier.padding(top = AppSp.sm)) {
                    OutlinedButton(
                        onClick = { controller.submitOrder(activeOutletId, activeOutletName, true, "", 0L) { onSavedOpenBill() } },
                        enabled = cart.isNotEmpty(), modifier = Modifier.weight(1f).height(52.dp)
                    ) { Text("Open Bill") }
                    Button(
                        onClick = { controller.submitOrder(activeOutletId, activeOutletName, false, paymentMethod, paidLong) { onPaid(it) } },
                        enabled = canPay, modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand)
                    ) { Text("Konfirmasi Bayar", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    noteFor?.let { line ->
        var txt by remember { mutableStateOf(line.note) }
        AlertDialog(
            onDismissRequest = { noteFor = null }, title = { Text("Catatan ${line.name}") },
            text = { OutlinedTextField(txt, { txt = it }, label = { Text("cth: pedas, tanpa es") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { controller.updateNote(line.key, txt.trim()); noteFor = null }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { noteFor = null }) { Text("Batal") } }
        )
    }
    if (showDiscountDlg) {
        var t by remember { mutableStateOf(controller.discountType) }
        var v by remember { mutableStateOf(if (controller.discountValue > 0L) controller.discountValue.toString() else "") }
        AlertDialog(
            onDismissRequest = { showDiscountDlg = false }, title = { Text("Atur Diskon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                        listOf("NONE" to "Nol", "NOMINAL" to "Rp", "PERCENT" to "%").forEach { (k, l) ->
                            FilterChip(selected = t == k, onClick = { t = k }, label = { Text(l) })
                        }
                    }
                    if (t != "NONE") OutlinedTextField(v, { v = it.filter { c -> c.isDigit() }.take(9) }, label = { Text("Nilai") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = { TextButton(onClick = { controller.discountType = t; controller.discountValue = v.toLongOrNull() ?: 0L; showDiscountDlg = false }) { Text("Pakai") } },
            dismissButton = { TextButton(onClick = { showDiscountDlg = false }) { Text("Batal") } }
        )
    }
    if (showMemberDlg) {
        MemberPickDialog(members, onDismiss = { showMemberDlg = false }) { m -> controller.selectedMember = m; showMemberDlg = false }
    }
}

@Composable
private fun SummaryRow(label: String, valStr: String, color: Color = Color.Unspecified) {
    Row {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(valStr, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun MemberPickDialog(members: List<MemberEntity>, onDismiss: () -> Unit, onPick: (MemberEntity?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Pilih Pelanggan / Member") },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item { TextButton(onClick = { onPick(null) }) { Text("Tanpa Member / Umum") } }
                items(members, key = { it.id }) { m ->
                    ListItem(
                        headlineContent = { Text(m.name) },
                        supportingContent = { Text("${m.phone} • Poin: ${m.points} • Kasbon: ${m.debt.toRupiah()}") },
                        modifier = Modifier.clickable { onPick(m) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
