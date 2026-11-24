package com.iyonzkasir.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.OrderSummaryRow
import com.iyonzkasir.di.AppContainer
import kotlinx.coroutines.launch

@Composable
fun HomeV2Screen(
    app: AppContainer,
    onOpenPos: () -> Unit,
    onOpenKitchen: () -> Unit,
    onOpenExpense: () -> Unit,
    onOpenKasbon: () -> Unit,
    onOpenWebOrders: () -> Unit,
    onOpenReport: () -> Unit,
    onSwitchUser: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by SessionManager.userState
    val viewingOutlet by SessionManager.viewingOutletIdState
    val isSyncing by app.isSyncing.collectAsState()

    val outlets by app.teamRepo.activeOutlets.collectAsState(initial = emptyList())
    val activeFilterOutlet = remember(viewingOutlet, user) {
        val uOutlet = user?.outletId.orEmpty()
        if (uOutlet.isNotBlank() && uOutlet != "ALL") uOutlet else viewingOutlet.ifBlank { "ALL" }
    }

    val startToday = remember { startOfTodayMillis() }
    val endToday = remember { endOfTodayMillis() }
    val summary by app.orderRepo.observeSummary(activeFilterOutlet, startToday, endToday).collectAsState(initial = OrderSummaryRow())
    val todayExpense by app.expenseRepo.observeSumInRange(activeFilterOutlet, startToday, endToday).collectAsState(initial = 0L)
    val outletPerf by app.orderRepo.observeOutletPerf(startToday, endToday).collectAsState(initial = emptyList())
    val pendingWeb by app.orderRepo.observePendingWebCount(activeFilterOutlet).collectAsState(initial = 0)
    val canSeeProfit = SessionManager.can(AppAccess.VIEW_PROFIT_HPP)
    val netProfit = summary.grossProfit - todayExpense

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(AppColors.brand, AppColors.brandDark))).padding(AppSp.xl)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Halo, ${user?.name ?: "Pengguna"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(user?.jobTitle ?: "", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                            }
                            IconButton(onClick = { scope.launch { app.syncNow() } }, enabled = !isSyncing) {
                                if (isSyncing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                else Icon(Icons.Default.CloudSync, null, tint = Color.White)
                            }
                            IconButton(onClick = onSwitchUser) { Icon(Icons.Default.SwitchAccount, null, tint = Color.White) }
                        }
                        if ((user?.outletId == "ALL" || user?.isOwner == true) && outlets.size > 1) {
                            Spacer(Modifier.height(AppSp.md))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                                item {
                                    FilterChip(
                                        selected = activeFilterOutlet == "ALL",
                                        onClick = { SessionManager.setViewingOutlet(ctx, "ALL") },
                                        label = { Text("Semua Cabang (${outlets.size})") },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = AppColors.brand, labelColor = Color.White)
                                    )
                                }
                                items(outlets, key = { it.id }) { o ->
                                    FilterChip(
                                        selected = activeFilterOutlet == o.id,
                                        onClick = { SessionManager.setViewingOutlet(ctx, o.id) },
                                        label = { Text(o.name) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = AppColors.brand, labelColor = Color.White)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(AppSp.md))
                        Surface(shape = AppRd.cardLg, color = Color.White.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(AppSp.lg)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Ringkasan Hari Ini", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                                    if (SessionManager.can(AppAccess.REPORT)) {
                                        Text("Detail ›", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onOpenReport))
                                    }
                                }
                                Spacer(Modifier.height(AppSp.md))
                                Row {
                                    HomeStatCell("Omzet", summary.totalSales.toRupiah(), Modifier.weight(1f))
                                    HomeStatCell("Transaksi", "${summary.txCount}x", Modifier.weight(1f))
                                }
                                if (canSeeProfit) {
                                    Spacer(Modifier.height(AppSp.md))
                                    Row {
                                        HomeStatCell("Pengeluaran", todayExpense.toRupiah(), Modifier.weight(1f))
                                        HomeStatCell("Untung Bersih", netProfit.toRupiah(), Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (pendingWeb > 0 && SessionManager.can(AppAccess.WEB_ORDERS)) {
                item {
                    Card(Modifier.fillMaxWidth().padding(AppSp.lg, AppSp.lg, AppSp.lg, 0.dp).clickable(onClick = onOpenWebOrders),
                        colors = CardDefaults.cardColors(containerColor = AppColors.brandSoft)) {
                        Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, null, tint = AppColors.brand)
                            Spacer(Modifier.width(AppSp.sm))
                            Text("$pendingWeb Pesanan Online Baru Menunggu!", fontWeight = FontWeight.Bold, color = AppColors.brand, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, tint = AppColors.brand)
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(AppSp.lg)) {
                    Text("Akses Cepat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(AppSp.sm))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                        if (SessionManager.can(AppAccess.EXPENSE)) QuickBox("Belanja", Icons.Default.Payments, AppColors.danger, Modifier.weight(1f), onOpenExpense)
                        if (SessionManager.can(AppAccess.KITCHEN)) QuickBox("Dapur", Icons.Default.SoupKitchen, AppColors.warning, Modifier.weight(1f), onOpenKitchen)
                        if (SessionManager.can(AppAccess.KASBON)) QuickBox("Kasbon", Icons.Default.AccountBalanceWallet, AppColors.info, Modifier.weight(1f), onOpenKasbon)
                        if (SessionManager.can(AppAccess.WEB_ORDERS)) QuickBox("Web QR", Icons.Default.QrCode2, AppColors.success, Modifier.weight(1f), onOpenWebOrders)
                    }
                }
            }
            if (activeFilterOutlet == "ALL" && outletPerf.size > 1) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = AppSp.lg), shape = AppRd.card) {
                        Column(Modifier.padding(AppSp.lg), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                            Text("Performa Cabang Hari Ini", fontWeight = FontWeight.Bold)
                            outletPerf.forEach { row ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.outletName.ifBlank { "Cabang" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${row.txCount}x • ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(row.totalSales.toRupiah(), fontWeight = FontWeight.Bold, color = AppColors.brand)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (SessionManager.can(AppAccess.POS)) {
            Button(
                onClick = onOpenPos,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(AppSp.lg).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand), shape = AppRd.card
            ) {
                Icon(Icons.Default.PointOfSale, null)
                Spacer(Modifier.width(AppSp.sm))
                Text("MULAI JUALAN", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HomeStatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickBox(label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = AppRd.card, colors = CardDefaults.cardColors(containerColor = AppColors.card)) {
        Column(Modifier.padding(vertical = AppSp.md, horizontal = AppSp.xs).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
