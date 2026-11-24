package com.iyonzkasir.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import com.iyonzkasir.di.AppContainer
import com.iyonzkasir.ui.pos.MenuPhotoBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogManageV2Screen(app: AppContainer, initialTab: Int = 0, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val products by app.catalogRepo.products.collectAsState(initial = emptyList())
    val categories by app.catalogRepo.categories.collectAsState(initial = emptyList())
    val outlets by app.teamRepo.activeOutlets.collectAsState(initial = emptyList())
    val activeOutletId = SessionManager.activeTransactionOutletId(outlets.firstOrNull()?.id.orEmpty())
    val stocks by app.stockRepo.observeOutletStocks(activeOutletId.ifBlank { "ALL" }).collectAsState(initial = emptyList())
    val stockMap = remember(stocks) { stocks.associateBy { it.productId } }

    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var search by remember { mutableStateOf("") }
    var editingProd by remember { mutableStateOf<ProductEntity?>(null) }
    var showProdDlg by remember { mutableStateOf(false) }
    var editingCat by remember { mutableStateOf<CategoryEntity?>(null) }
    var showCatDlg by remember { mutableStateOf(false) }
    var stockTarget by remember { mutableStateOf<ProductEntity?>(null) }

    val canEditMenu = SessionManager.can(AppAccess.MENU_MGMT)
    val canDelete = SessionManager.can(AppAccess.DELETE_DATA)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Produk, Kategori & Stok") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
        },
        floatingActionButton = {
            if (tab == 0 && canEditMenu) {
                FloatingActionButton(onClick = { editingProd = null; showProdDlg = true }, containerColor = AppColors.brand) {
                    Icon(Icons.Default.Add, null, tint = Color.White)
                }
            } else if (tab == 1 && canEditMenu) {
                FloatingActionButton(onClick = { editingCat = null; showCatDlg = true }, containerColor = AppColors.brand) {
                    Icon(Icons.Default.Add, null, tint = Color.White)
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Produk (${products.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Kategori (${categories.size})") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Stok Cabang") })
            }
            if (tab != 1) {
                OutlinedTextField(search, { search = it }, placeholder = { Text("Cari produk...") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(AppSp.md))
            }
            when (tab) {
                0 -> {
                    val list = products.filter { search.isBlank() || it.name.contains(search, true) }
                    if (list.isEmpty()) EmptyPlaceholder(Icons.Default.RestaurantMenu, "Belum ada produk", "Tap + untuk tambah menu pertama")
                    else LazyColumn(contentPadding = PaddingValues(start = AppSp.md, end = AppSp.md, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                        items(list, key = { it.id }) { p ->
                            Card(Modifier.fillMaxWidth().clickable(enabled = canEditMenu) { editingProd = p; showProdDlg = true }, shape = AppRd.card) {
                                Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                                    MenuPhotoBox(p.localPhotoPath, p.remotePhotoUrl, modifier = Modifier.size(52.dp).clip(AppRd.card))
                                    Spacer(Modifier.width(AppSp.md))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.name, fontWeight = FontWeight.Bold)
                                        Text("${p.categoryName} • ${p.sellPrice.toRupiah()}", style = MaterialTheme.typography.bodySmall, color = AppColors.brand)
                                        if (p.trackStock) Text("Stok cabang ini: ${stockMap[p.id]?.stockQty ?: 0}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Switch(checked = p.available, onCheckedChange = { v -> scope.launch { app.catalogRepo.setProductAvailable(p.id, v); app.triggerSyncInBackground() } })
                                    if (canDelete) IconButton(onClick = { scope.launch { app.catalogRepo.deleteProduct(p); app.triggerSyncInBackground() } }) {
                                        Icon(Icons.Default.Delete, null, tint = AppColors.danger)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> LazyColumn(contentPadding = PaddingValues(start = AppSp.md, end = AppSp.md, top = AppSp.md, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    items(categories, key = { it.id }) { c ->
                        Card(Modifier.fillMaxWidth().clickable(enabled = canEditMenu) { editingCat = c; showCatDlg = true }, shape = AppRd.card) {
                            Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(c.colorHex.toColorSafe()))
                                Spacer(Modifier.width(AppSp.md))
                                Text(c.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (canDelete) IconButton(onClick = { scope.launch { app.catalogRepo.deleteCategory(c); app.triggerSyncInBackground() } }) {
                                    Icon(Icons.Default.Delete, null, tint = AppColors.danger)
                                }
                            }
                        }
                    }
                }
                2 -> {
                    val tracked = products.filter { it.trackStock && (search.isBlank() || it.name.contains(search, true)) }
                    if (tracked.isEmpty()) EmptyPlaceholder(Icons.Default.Inventory, "Belum ada produk dilacak stoknya", "Aktifkan 'Lacak Stok' saat edit produk")
                    else LazyColumn(contentPadding = PaddingValues(AppSp.md), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                        items(tracked, key = { it.id }) { p ->
                            val st = stockMap[p.id]
                            val qty = st?.stockQty ?: 0
                            val isLow = qty <= (st?.minStock ?: 5)
                            Card(Modifier.fillMaxWidth().clickable { stockTarget = p }, shape = AppRd.card) {
                                Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.name, fontWeight = FontWeight.Bold)
                                        Text(if (isLow) "⚠️ Stok menipis (min ${st?.minStock ?: 5})" else "Stok aman",
                                            style = MaterialTheme.typography.bodySmall, color = if (isLow) AppColors.danger else AppColors.success)
                                    }
                                    Text("$qty pcs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isLow) AppColors.danger else AppColors.brand)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProdDlg) ProductEditorDlg(app, editingProd, categories, onDismiss = { showProdDlg = false })
    if (showCatDlg) CategoryEditorDlg(app, editingCat, onDismiss = { showCatDlg = false })
    stockTarget?.let { prod -> StockAdjustDlg(app, activeOutletId, prod, stockMap[prod.id], onDismiss = { stockTarget = null }) }
}
