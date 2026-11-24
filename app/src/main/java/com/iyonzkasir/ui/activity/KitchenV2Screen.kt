package com.iyonzkasir.ui.activity

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.OrderEntity
import com.iyonzkasir.data.local.OrderItemEntity
import com.iyonzkasir.di.AppContainer
import kotlinx.coroutines.launch

@Composable
fun KitchenV2Screen(app: AppContainer) {
    val scope = rememberCoroutineScope()
    val user by SessionManager.userState
    val viewingOutlet by SessionManager.viewingOutletIdState
    val activeOutlet = remember(viewingOutlet, user) {
        val uo = user?.outletId.orEmpty()
        if (uo.isNotBlank() && uo != "ALL") uo else viewingOutlet.ifBlank { "ALL" }
    }
    val queue by app.orderRepo.observeKitchenQueue(activeOutlet).collectAsState(initial = emptyList())

    if (queue.isEmpty()) {
        EmptyPlaceholder(
            Icons.Default.SoupKitchen,
            "Dapur sedang kosong",
            "Pesanan baru dari Kasir atau Web akan muncul otomatis di sini"
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(AppSp.md),
        verticalArrangement = Arrangement.spacedBy(AppSp.md)
    ) {
        items(queue, key = { it.id }) { order ->
            KitchenOrderCard(order, app) { nextStatus ->
                scope.launch {
                    app.orderRepo.updateKitchenStatus(order.id, nextStatus)
                    app.triggerSyncInBackground()
                }
            }
        }
    }
}

@Composable
private fun KitchenOrderCard(
    order: OrderEntity,
    app: AppContainer,
    onAdvance: (KitchenStatus) -> Unit
) {
    var items by remember(order.id) { mutableStateOf<List<OrderItemEntity>>(emptyList()) }
    LaunchedEffect(order.id, order.updatedAt) { items = app.orderRepo.itemsOf(order.id) }

    val status = KitchenStatus.fromId(order.kitchenStatus)
    val badgeColor = when (status) {
        KitchenStatus.WAITING -> AppColors.danger
        KitchenStatus.COOKING -> AppColors.warning
        KitchenStatus.READY -> AppColors.success
        else -> AppColors.info
    }

    Card(Modifier.fillMaxWidth(), shape = AppRd.cardLg, colors = CardDefaults.cardColors(containerColor = AppColors.card)) {
        Column(Modifier.padding(AppSp.lg), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = badgeColor, shape = AppRd.xs.let { androidx.compose.foundation.shape.RoundedCornerShape(it) }) {
                    Text(status.label.uppercase(), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(AppSp.sm))
                Text(
                    buildString {
                        if (order.queueNumber > 0) append("#${order.queueNumber} ")
                        if (order.tableNumber.isNotBlank()) append("• Meja ${order.tableNumber} ")
                        append("• ${OrderType.fromId(order.orderType).label}")
                    },
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                Text(order.timestamp.formatTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (order.customerName.isNotBlank() || order.outletName.isNotBlank()) {
                Text("${order.outletName} • ${order.customerName}".trim(' ', '•'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            items.forEach { it ->
                if (it.isBundleComponent) {
                    Text("   ↳ ${it.name} x${it.qty}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row {
                        Text("${it.qty}x", Modifier.width(34.dp), fontWeight = FontWeight.Bold, color = AppColors.brand)
                        Column(Modifier.weight(1f)) {
                            Text(it.name, fontWeight = FontWeight.SemiBold)
                            if (it.note.isNotBlank()) Text("Catatan: ${it.note}", style = MaterialTheme.typography.bodySmall, color = AppColors.danger)
                        }
                    }
                }
            }
            if (order.note.isNotBlank()) {
                Text("Note Order: ${order.note}", style = MaterialTheme.typography.bodySmall, color = AppColors.danger, fontWeight = FontWeight.SemiBold)
            }
            val (btnText, next) = when (status) {
                KitchenStatus.WAITING -> "Mulai Masak" to KitchenStatus.COOKING
                KitchenStatus.COOKING -> "Tandai Siap" to KitchenStatus.READY
                else -> "Selesai Diantar" to KitchenStatus.SERVED
            }
            Button(
                onClick = { onAdvance(next) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = badgeColor)
            ) { Text(btnText, fontWeight = FontWeight.Bold) }
        }
    }
}
