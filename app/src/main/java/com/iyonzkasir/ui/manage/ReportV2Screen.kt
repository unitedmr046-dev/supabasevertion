package com.iyonzkasir.ui.manage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import com.iyonzkasir.di.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportV2Screen(app: AppContainer, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by SessionManager.userState
    val viewingOutlet by SessionManager.viewingOutletIdState
    val outletFilter = remember(viewingOutlet, user) {
        val uo = user?.outletId.orEmpty()
        if (uo.isNotBlank() && uo != "ALL") uo else viewingOutlet.ifBlank { "ALL" }
    }
    val canSeeProfit = SessionManager.can(AppAccess.VIEW_PROFIT_HPP)

    var days by remember { mutableIntStateOf(1) }
    var summary by remember { mutableStateOf(OrderSummaryRow()) }
    var expenseSum by remember { mutableStateOf(0L) }
    var prodProfits by remember { mutableStateOf<List<ProductProfitRow>>(emptyList()) }
    var chart7 by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var exportNotice by remember { mutableStateOf<String?>(null) }

    val range = remember(days) {
        val end = endOfTodayMillis()
        val start = when (days) { 1 -> startOfTodayMillis(); 7 -> daysAgoStartMillis(6); 30 -> daysAgoStartMillis(29); else -> 0L }
        start to end
    }

    LaunchedEffect(outletFilter, range) {
        summary = app.orderRepo.getSummary(outletFilter, range.first, range.second)
        expenseSum = app.expenseRepo.sumInRange(outletFilter, range.first, range.second)
        prodProfits = app.orderRepo.productProfit(outletFilter, range.first, range.second)
        val orders7 = app.orderRepo.paidOrdersInRange(outletFilter, daysAgoStartMillis(6), endOfTodayMillis())
        chart7 = (6 downTo 0).map { d ->
            val s = daysAgoStartMillis(d); val e = s + 86_400_000L - 1L
            s.formatShortDate() to orders7.filter { it.timestamp in s..e }.sumOf { it.total }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val orders = app.orderRepo.paidOrdersInRange(outletFilter, range.first, range.second)
            val sb = StringBuilder("Kode,Tanggal,Cabang,Metode,Total\n")
            orders.forEach { o ->
                val outletSafe = "\"${o.outletName.replace("\"", "\"\"")}\""
                sb.append("${o.receiptCode},${o.timestamp.formatDateTime()},$outletSafe,${o.paymentMethod},${o.total}\n")
            }
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) } }
                .onSuccess { exportNotice = "CSV berhasil disimpan ✅" }.onFailure { exportNotice = "Gagal export: ${it.message}" }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan Penjualan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = { csvLauncher.launch("laporan-iyonzkasir.csv") }) { Icon(Icons.Default.FileDownload, null) } }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(AppSp.md), verticalArrangement = Arrangement.spacedBy(AppSp.md)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                    listOf(1 to "Hari Ini", 7 to "7 Hari", 30 to "30 Hari", 0 to "Semua").forEach { (d, l) ->
                        FilterChip(selected = days == d, onClick = { days = d }, label = { Text(l) })
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.brandSoft)) {
                    Column(Modifier.padding(AppSp.lg), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Ringkasan", fontWeight = FontWeight.Bold)
                        Row { Text("Transaksi", Modifier.weight(1f)); Text("${summary.txCount}x", fontWeight = FontWeight.Bold) }
                        Row { Text("Omzet Bersih", Modifier.weight(1f)); Text(summary.totalSales.toRupiah(), fontWeight = FontWeight.Bold, color = AppColors.brand) }
                        if (canSeeProfit) {
                            Row { Text("Modal (HPP)", Modifier.weight(1f)); Text("-${summary.totalCostHpp.toRupiah()}") }
                            Row { Text("Pengeluaran", Modifier.weight(1f)); Text("-${expenseSum.toRupiah()}", color = AppColors.danger) }
                            HorizontalDivider()
                            val net = summary.grossProfit - expenseSum
                            Row { Text("UNTUNG BERSIH", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(net.toRupiah(), fontWeight = FontWeight.Bold, color = if (net >= 0L) AppColors.success else AppColors.danger) }
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(AppSp.lg)) {
                        Text("Grafik Omzet 7 Hari", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(AppSp.md))
                        val maxVal = (chart7.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
                        val barColor = AppColors.brand
                        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                            val bw = size.width / (chart7.size * 2f).coerceAtLeast(1f)
                            chart7.forEachIndexed { idx, (_, v) ->
                                val h = (v.toFloat() / maxVal) * (size.height - 8f)
                                drawRoundRect(barColor, Offset(idx * bw * 2f + bw * 0.4f, size.height - h), Size(bw, h), CornerRadius(6f, 6f))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            chart7.forEach { (lbl, _) -> Text(lbl, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            item { Text("Performa Produk & Paket", fontWeight = FontWeight.Bold) }
            items(prodProfits, key = { it.itemKey }) { p ->
                Card(Modifier.fillMaxWidth(), shape = AppRd.card) {
                    Row(Modifier.padding(AppSp.md)) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text("${p.totalQty}x terjual • Omzet ${p.totalGrossSales.toRupiah()}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (canSeeProfit) Text(p.profit.toRupiah(), fontWeight = FontWeight.Bold, color = if (p.profit >= 0L) AppColors.success else AppColors.danger)
                    }
                }
            }
            exportNotice?.let { item { Text(it, color = AppColors.success, fontWeight = FontWeight.SemiBold) } }
        }
    }
}
