package com.iyonzkasir.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.OrderEntity
import com.iyonzkasir.data.local.OrderItemEntity
import com.iyonzkasir.di.AppContainer
import com.iyonzkasir.hardware.ReceiptFormatter
import com.iyonzkasir.hardware.ThermalPrinter
import kotlinx.coroutines.launch

@Composable
fun OrdersHistoryV2Screen(
    app: AppContainer,
    onResumeOpenBill: (String) -> Unit
) {
    val user by SessionManager.userState
    val viewingOutlet by SessionManager.viewingOutletIdState
    val activeOutlet = remember(viewingOutlet, user) {
        val uo = user?.outletId.orEmpty()
        if (uo.isNotBlank() && uo != "ALL") uo else viewingOutlet.ifBlank { "ALL" }
    }

    var subTab by remember { mutableIntStateOf(0) }
    val openBills by app.orderRepo.observeOpenBills(activeOutlet).collectAsState(initial = emptyList())
    val history by app.orderRepo.observeHistory(activeOutlet).collectAsState(initial = emptyList())
    var selectedOrder by remember { mutableStateOf<OrderEntity?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(AppSp.md), horizontalArrangement = Arrangement.spacedBy(AppSp.sm)) {
            FilterChip(selected = subTab == 0, onClick = { subTab = 0 }, label = { Text("Belum Bayar (${openBills.size})") })
            FilterChip(selected = subTab == 1, onClick = { subTab = 1 }, label = { Text("Riwayat (${history.size})") })
        }

        if (subTab == 0) {
            if (openBills.isEmpty()) {
                EmptyPlaceholder(Icons.Default.ReceiptLong, "Belum ada Open Bill", "Pesanan yang ditahan/belum bayar muncul di sini")
            } else {
                LazyColumn(contentPadding = PaddingValues(AppSp.md), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    items(openBills, key = { it.id }) { o ->
                        Card(Modifier.fillMaxWidth().clickable { onResumeOpenBill(o.id) }, shape = AppRd.card) {
                            Row(Modifier.padding(AppSp.lg), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (o.tableNumber.isNotBlank()) "Meja ${o.tableNumber}" else o.receiptCode, fontWeight = FontWeight.Bold)
                                    Text("${OrderType.fromId(o.orderType).label} • ${o.customerName}".trim(' ', '•'), style = MaterialTheme.typography.bodySmall)
                                    Text(o.timestamp.formatDateTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(o.total.toRupiah(), fontWeight = FontWeight.Bold, color = AppColors.brand)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Lanjut Bayar ›", style = MaterialTheme.typography.labelSmall, color = AppColors.brand, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (history.isEmpty()) {
                EmptyPlaceholder(Icons.Default.History, "Belum ada transaksi", "Riwayat struk lunas akan muncul di sini")
            } else {
                LazyColumn(contentPadding = PaddingValues(AppSp.md), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    items(history, key = { it.id }) { o ->
                        Card(Modifier.fillMaxWidth().clickable { selectedOrder = o }, shape = AppRd.card) {
                            Row(Modifier.padding(AppSp.lg), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(o.receiptCode.ifBlank { "#${o.id.take(8)}" }, fontWeight = FontWeight.Bold)
                                        if (o.status != OrderStatus.PAID.id) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(o.status, style = MaterialTheme.typography.labelSmall, color = AppColors.danger, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text("${PaymentMethod.fromId(o.paymentMethod).label} • ${o.outletName} • ${o.timestamp.formatDateTime()}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(o.total.toRupiah(), fontWeight = FontWeight.Bold,
                                    color = if (o.status == OrderStatus.PAID.id) AppColors.brand else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedOrder?.let { order ->
        OrderDetailModal(app, order, onDismiss = { selectedOrder = null })
    }
}
@Composable
private fun OrderDetailModal(app: AppContainer, order: OrderEntity, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<OrderItemEntity>>(emptyList()) }
    var voidPrompt by remember { mutableStateOf<Boolean?>(null) } // false = void, true = refund
    var reason by remember { mutableStateOf("") }
    var printInfo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(order.id) { items = app.orderRepo.itemsOf(order.id) }
    val canVoid = SessionManager.can(AppAccess.VOID_REFUND) && order.status == OrderStatus.PAID.id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(order.receiptCode.ifBlank { "Detail Order" }) },
        text = {
            Column(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${order.outletName} • ${order.timestamp.formatDateTime()}", style = MaterialTheme.typography.bodySmall)
                if (order.voidReason.isNotBlank()) Text("${order.status}: ${order.voidReason} (${order.voidBy})", color = AppColors.danger, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                items.forEach { it ->
                    if (it.isBundleComponent) Text("  - ${it.name} x${it.qty}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Row { Text("${it.qty}x ${it.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(it.lineTotal.toRupiah(), style = MaterialTheme.typography.bodySmall) }
                }
                HorizontalDivider()
                Row { Text("TOTAL", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(order.total.toRupiah(), fontWeight = FontWeight.Bold, color = AppColors.brand) }
                printInfo?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = AppColors.brand) }

                Row(horizontalArrangement = Arrangement.spacedBy(AppSp.xs), modifier = Modifier.padding(top = AppSp.sm)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val outlet = app.teamRepo.getOutlet(order.outletId)
                            val mac = app.prefRepo.getPrinterMac()
                            if (!ThermalPrinter.isConnected() && mac.isNotBlank()) ThermalPrinter.connect(ctx, mac)
                            val ok = ThermalPrinter.isConnected() && ThermalPrinter.send(ReceiptFormatter.buildEscPosOrder(app.prefRepo, outlet, order, items))
                            printInfo = if (ok) "Struk terkirim ke printer" else (ThermalPrinter.lastError ?: "Printer belum tersambung")
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Cetak") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val outlet = app.teamRepo.getOutlet(order.outletId)
                            ReceiptFormatter.shareReceiptText(ctx, ReceiptFormatter.buildText(app.prefRepo, outlet, order, items))
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Kirim WA") }
                }
                if (canVoid) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                        OutlinedButton(onClick = { voidPrompt = false }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.danger)) { Text("Void") }
                        OutlinedButton(onClick = { voidPrompt = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.warning)) { Text("Refund") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )

    voidPrompt?.let { isRefund ->
        AlertDialog(
            onDismissRequest = { voidPrompt = null },
            title = { Text(if (isRefund) "Alasan Refund" else "Alasan Batalkan (Void)") },
            text = { OutlinedTextField(reason, { reason = it }, label = { Text("Alasan wajib diisi") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = reason.isNotBlank(), onClick = {
                    scope.launch {
                        app.orderRepo.voidOrRefund(order.id, isRefund, reason.trim())
                        app.triggerSyncInBackground()
                        voidPrompt = null; onDismiss()
                    }
                }) { Text("Konfirmasi", color = AppColors.danger) }
            },
            dismissButton = { TextButton(onClick = { voidPrompt = null }) { Text("Batal") } }
        )
    }
}
