package com.iyonzkasir.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iyonzkasir.BuildConfig
import com.iyonzkasir.core.*
import com.iyonzkasir.di.AppContainer

private data class ManageMenuRow(val title: String, val sub: String, val icon: ImageVector, val tint: Color, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageHubV2Screen(
    app: AppContainer,
    onOpenCatalog: (Int) -> Unit,
    onOpenBundles: () -> Unit,
    onOpenExpense: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenShift: () -> Unit,
    onOpenTeam: () -> Unit,
    onOpenWebStore: () -> Unit,
    onOpenPrinter: () -> Unit,
    onOpenThemeBackup: () -> Unit,
    onOpenAudit: () -> Unit,
    onSwitchUser: () -> Unit,
    onLogoutBusiness: () -> Unit
) {
    val ctx = LocalContext.current
    val user by SessionManager.userState
    var confirmLogoutBiz by remember { mutableStateOf(false) }

    val rows = remember(user) {
        buildList {
            if (SessionManager.can(AppAccess.MENU_MGMT)) {
                add(ManageMenuRow("Produk & Kategori", "Atur menu, harga & foto produk", Icons.Default.RestaurantMenu, AppColors.brand) { onOpenCatalog(0) })
                add(ManageMenuRow("Paket Hemat / Bundling", "Buat menu paket combo promo", Icons.Default.CardGiftcard, Color(0xFFFB8C00), onOpenBundles))
            }
            if (SessionManager.can(AppAccess.STOCK)) {
                add(ManageMenuRow("Stok Cabang", "Pantau stok menipis & opname", Icons.Default.Inventory, Color(0xFF00897B)) { onOpenCatalog(2) })
            }
            if (SessionManager.can(AppAccess.EXPENSE)) {
                add(ManageMenuRow("Belanja / Pengeluaran", "Catat biaya & modal harian", Icons.Default.Payments, AppColors.danger, onOpenExpense))
            }
            if (SessionManager.can(AppAccess.REPORT)) {
                add(ManageMenuRow("Laporan & Export CSV", "Omzet, grafik, laba & excel CSV", Icons.Default.BarChart, Color(0xFF3949AB), onOpenReport))
            }
            if (SessionManager.can(AppAccess.SHIFT)) {
                add(ManageMenuRow("Shift Kasir", "Buka/tutup shift & rekap kas", Icons.Default.Schedule, Color(0xFF6D4C41), onOpenShift))
            }
            if (SessionManager.can(AppAccess.MANAGE_TEAM)) {
                add(ManageMenuRow("Cabang & Karyawan", "Tambah gerobak & atur izin custom", Icons.Default.ManageAccounts, AppColors.info, onOpenTeam))
            }
            if (SessionManager.can(AppAccess.WEB_ORDERS)) {
                add(ManageMenuRow("Menu Online & Link Meja", "Publikasikan menu ke web pembeli", Icons.Default.QrCode2, AppColors.success, onOpenWebStore))
            }
            if (SessionManager.can(AppAccess.SETTINGS)) {
                add(ManageMenuRow("Printer, Struk & Pajak", "Bluetooth 58/80mm & PPN default", Icons.Default.Print, Color(0xFF546E7A), onOpenPrinter))
                add(ManageMenuRow("Tema & Backup (.iyonz)", "Mode gelap, warna & backup data", Icons.Default.Palette, Color(0xFF8E24AA), onOpenThemeBackup))
            }
            if (user?.isOwner == true) {
                add(ManageMenuRow("Catatan Audit", "Riwayat void, koreksi stok & hapus", Icons.Default.FactCheck, Color(0xFF455A64), onOpenAudit))
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Kelola & Setelan") }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(AppSp.md), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.brandSoft)) {
                    Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(user?.name ?: "", fontWeight = FontWeight.Bold)
                            Text("${user?.jobTitle ?: ""} • v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = onSwitchUser) { Text("Ganti Akun") }
                    }
                }
            }
            items(rows) { r ->
                Card(Modifier.fillMaxWidth().clickable(onClick = r.onClick), shape = AppRd.card) {
                    Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(r.tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(r.icon, null, tint = r.tint)
                        }
                        Spacer(Modifier.width(AppSp.md))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontWeight = FontWeight.SemiBold)
                            Text(r.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            if (user?.isOwner == true) {
                item {
                    OutlinedButton(onClick = { confirmLogoutBiz = true }, modifier = Modifier.fillMaxWidth().padding(top = AppSp.sm),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.danger)) {
                        Text("Keluar Akun Usaha (Ganti Email)")
                    }
                }
            }
        }
    }

    if (confirmLogoutBiz) {
        AlertDialog(onDismissRequest = { confirmLogoutBiz = false }, title = { Text("Keluar Akun Usaha?") },
            text = { Text("Perangkat ini akan diputus dari akun email Owner.") },
            confirmButton = { TextButton(onClick = {
                confirmLogoutBiz = false; app.stopRealtimeListeners(); app.gateway.logout(); SessionManager.clearAll(ctx); onLogoutBusiness()
            }) { Text("Keluar", color = AppColors.danger) } },
            dismissButton = { TextButton(onClick = { confirmLogoutBiz = false }) { Text("Batal") } })
    }
}
