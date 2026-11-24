package com.iyonzkasir.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.OrderEntity
import com.iyonzkasir.data.local.OrderItemEntity
import com.iyonzkasir.di.AppContainer
import com.iyonzkasir.hardware.ReceiptFormatter
import com.iyonzkasir.hardware.ThermalPrinter
import kotlinx.coroutines.launch

@Composable
fun OrderSuccessV2Screen(
    app: AppContainer,
    order: OrderEntity,
    onNewTransaction: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<OrderItemEntity>>(emptyList()) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(order.id) {
        items = app.orderRepo.itemsOf(order.id)
        if (app.prefRepo.isAutoPrint()) {
            val outlet = app.teamRepo.getOutlet(order.outletId)
            val mac = app.prefRepo.getPrinterMac()
            if (!ThermalPrinter.isConnected() && mac.isNotBlank()) ThermalPrinter.connect(ctx, mac)
            if (ThermalPrinter.isConnected()) {
                ThermalPrinter.send(ReceiptFormatter.buildEscPosOrder(app.prefRepo, outlet, order, items))
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(AppSp.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(AppSp.xl))
        Box(Modifier.size(96.dp).clip(CircleShape).background(AppColors.success.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CheckCircle, null, tint = AppColors.success, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(AppSp.md))
        Text("Transaksi Berhasil!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(order.receiptCode.ifBlank { "#${order.id.take(8)}" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (order.queueNumber > 0) Text("Antrian #${order.queueNumber}", fontWeight = FontWeight.Bold, color = AppColors.brand)

        Spacer(Modifier.height(AppSp.xl))
        Card(Modifier.fillMaxWidth(), shape = AppRd.cardLg) {
            Column(Modifier.padding(AppSp.lg), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                Row { Text("Total", Modifier.weight(1f)); Text(order.total.toRupiah(), fontWeight = FontWeight.Bold) }
                Row { Text("Dibayar", Modifier.weight(1f)); Text(order.paidAmount.toRupiah(), fontWeight = FontWeight.Bold) }
                HorizontalDivider()
                Row {
                    Text("Kembalian", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = AppColors.brand)
                    Text(order.changeAmount.toRupiah(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.brand)
                }
            }
        }

        statusMsg?.let { Spacer(Modifier.height(AppSp.sm)); Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.brand) }
        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSp.sm)) {
            OutlinedButton(onClick = {
                scope.launch {
                    val outlet = app.teamRepo.getOutlet(order.outletId)
                    val mac = app.prefRepo.getPrinterMac()
                    if (!ThermalPrinter.isConnected() && mac.isNotBlank()) ThermalPrinter.connect(ctx, mac)
                    val ok = ThermalPrinter.isConnected() && ThermalPrinter.send(ReceiptFormatter.buildEscPosOrder(app.prefRepo, outlet, order, items))
                    statusMsg = if (ok) "Struk terkirim ke printer" else (ThermalPrinter.lastError ?: "Printer belum tersambung")
                }
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Print, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Cetak") }

            OutlinedButton(onClick = {
                scope.launch {
                    val outlet = app.teamRepo.getOutlet(order.outletId)
                    previewText = ReceiptFormatter.buildText(app.prefRepo, outlet, order, items)
                }
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Receipt, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Preview") }

            OutlinedButton(onClick = {
                scope.launch {
                    val outlet = app.teamRepo.getOutlet(order.outletId)
                    ReceiptFormatter.shareReceiptText(ctx, ReceiptFormatter.buildText(app.prefRepo, outlet, order, items))
                }
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("WA") }
        }

        Spacer(Modifier.height(AppSp.md))
        Button(onClick = onNewTransaction, modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand), shape = AppRd.card) {
            Text("TRANSAKSI BARU", fontWeight = FontWeight.Bold)
        }
    }

    previewText?.let { txt ->
        AlertDialog(
            onDismissRequest = { previewText = null }, title = { Text("Preview Struk") },
            text = { Box(Modifier.horizontalScroll(rememberScrollState())) { Text(txt, fontFamily = FontFamily.Monospace, fontSize = 11.sp) } },
            confirmButton = { TextButton(onClick = { previewText = null }) { Text("Tutup") } }
        )
    }
}
