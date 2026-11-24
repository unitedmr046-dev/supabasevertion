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
import com.iyonzkasir.di.AppContainer
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun WebOrdersV2Screen(app: AppContainer, onOrderAccepted: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val user by SessionManager.userState
    val viewingOutlet by SessionManager.viewingOutletIdState
    val activeOutlet = remember(viewingOutlet, user) {
        val uo = user?.outletId.orEmpty()
        if (uo.isNotBlank() && uo != "ALL") uo else viewingOutlet.ifBlank { "ALL" }
    }
    val outlets by app.teamRepo.activeOutlets.collectAsState(initial = emptyList())
    val targetOutletId = SessionManager.activeTransactionOutletId(outlets.firstOrNull()?.id.orEmpty())
    val targetOutletName = outlets.firstOrNull { it.id == targetOutletId }?.name ?: ""
    val webOrders by app.orderRepo.observeWebOrders(activeOutlet).collectAsState(initial = emptyList())

    if (webOrders.isEmpty()) {
        EmptyPlaceholder(
            Icons.Default.QrCode2,
            "Belum ada pesanan online",
            "Aktifkan Menu Online di tab Kelola agar pembeli bisa pesan via QR / Link Web"
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(AppSp.md),
        verticalArrangement = Arrangement.spacedBy(AppSp.md)
    ) {
        items(webOrders, key = { it.id }) { wo ->
            val st = WebOrderStatus.fromId(wo.status)
            Card(Modifier.fillMaxWidth(), shape = AppRd.cardLg, colors = CardDefaults.cardColors(containerColor = AppColors.card)) {
                Column(Modifier.padding(AppSp.lg), verticalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(wo.customerName.ifBlank { "Pelanggan Web" }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Surface(
                            color = when (st) {
                                WebOrderStatus.PENDING -> AppColors.warning
                                WebOrderStatus.ACCEPTED -> AppColors.success
                                WebOrderStatus.REJECTED -> AppColors.danger
                            },
                            shape = AppRd.xs.let { androidx.compose.foundation.shape.RoundedCornerShape(it) }
                        ) {
                            Text(st.label, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(buildString {
                        if (wo.tableNumber.isNotBlank()) append("Meja ${wo.tableNumber} • ")
                        if (wo.customerPhone.isNotBlank()) append("${wo.customerPhone} • ")
                        append(wo.timestamp.formatDateTime())
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val parsed = remember(wo.itemsJson) {
                        runCatching {
                            val arr = JSONArray(wo.itemsJson)
                            (0 until arr.length()).map { i ->
                                val o = arr.getJSONObject(i)
                                "${o.optInt("qty", 1)}x ${o.optString("nama")} (${o.optLong("totalPrice").toRupiah()})"
                            }
                        }.getOrDefault(emptyList())
                    }
                    parsed.forEach { line -> Text("• $line", style = MaterialTheme.typography.bodySmall) }
                    if (wo.note.isNotBlank()) Text("Catatan: ${wo.note}", style = MaterialTheme.typography.bodySmall, color = AppColors.danger)
                    Row { Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(wo.total.toRupiah(), fontWeight = FontWeight.Bold, color = AppColors.brand) }

                    if (st == WebOrderStatus.PENDING) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    app.orderRepo.rejectWebOrder(wo.id)
                                    val slug = app.prefRepo.getWebSlug()
                                    runCatching { app.gateway.updateWebOrderStatus(slug, wo.id, WebOrderStatus.REJECTED.id) }
                                }
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.danger)) { Text("Tolak") }

                            Button(onClick = {
                                scope.launch {
                                    val created = app.orderRepo.acceptWebOrder(wo, targetOutletId, targetOutletName)
                                    val slug = app.prefRepo.getWebSlug()
                                    runCatching { app.gateway.updateWebOrderStatus(slug, wo.id, WebOrderStatus.ACCEPTED.id) }
                                    app.triggerSyncInBackground()
                                    onOrderAccepted(created.id)
                                }
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand)) { Text("Terima & Proses") }
                        }
                    }
                }
            }
        }
    }
}
