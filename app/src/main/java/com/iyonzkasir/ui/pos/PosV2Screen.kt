package com.iyonzkasir.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import com.iyonzkasir.di.AppContainer
import com.iyonzkasir.hardware.BarcodeScannerDialog

@Composable
fun PosV2Screen(
    app: AppContainer,
    controller: PosV2Controller,
    onOpenCart: () -> Unit
) {
    val outlets by app.teamRepo.activeOutlets.collectAsState(initial = emptyList())
    val activeOutletId = SessionManager.activeTransactionOutletId(outlets.firstOrNull()?.id.orEmpty())
    val products by app.catalogRepo.products.collectAsState(initial = emptyList())
    val bundles by app.catalogRepo.bundles.collectAsState(initial = emptyList())
    val outletStocks by app.stockRepo.observeOutletStocks(activeOutletId.ifBlank { "ALL" }).collectAsState(initial = emptyList())
    val stockMap = remember(outletStocks) { outletStocks.associateBy { it.productId } }

    val cart by controller.cart.collectAsState()
    val subtotal by controller.subtotal.collectAsState()
    val total = controller.calcTotal(subtotal)

    var showScanner by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }
    var showBundleTab by remember { mutableStateOf(false) }
    var pickingBundle by remember { mutableStateOf<BundleEntity?>(null) }

    val categories = remember(products) { listOf(null) + products.map { it.categoryName }.distinct() }
    val filteredProds = remember(products, controller.searchQuery, controller.categoryFilter) {
        products.filter {
            (controller.categoryFilter == null || it.categoryName == controller.categoryFilter) &&
            (controller.searchQuery.isBlank() || it.name.contains(controller.searchQuery, true))
        }
    }
    val filteredBundles = remember(bundles, controller.searchQuery) {
        bundles.filter { controller.searchQuery.isBlank() || it.name.contains(controller.searchQuery, true) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            // Search & Scan Bar
            Row(Modifier.fillMaxWidth().padding(AppSp.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                OutlinedTextField(
                    value = controller.searchQuery, onValueChange = { controller.searchQuery = it },
                    placeholder = { Text("Cari menu...") },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                    singleLine = true, shape = AppRd.pill, modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = { showScanner = true },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = AppColors.brand)
                ) { Icon(Icons.Default.QrCodeScanner, null, tint = Color.White) }
            }

            // Visible Barcode Feedback Banner
            controller.barcodeNotice?.let { msg ->
                Surface(color = AppColors.brandSoft, modifier = Modifier.fillMaxWidth().padding(horizontal = AppSp.md, vertical = AppSp.xs), shape = AppRd.card) {
                    Row(Modifier.padding(horizontal = AppSp.md, vertical = AppSp.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { controller.barcodeNotice = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Visible OrderMetaBar (Tipe Order + Meja / Pelanggan)
            LazyRow(contentPadding = PaddingValues(horizontal = AppSp.md), horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                item {
                    AssistChip(
                        onClick = { showMetaDialog = true },
                        leadingIcon = { Icon(Icons.Default.TableRestaurant, null, Modifier.size(16.dp)) },
                        label = { Text(if (controller.tableNumber.isBlank()) "Meja / Nama" else "Meja ${controller.tableNumber}") }
                    )
                }
                items(OrderType.entries) { t ->
                    FilterChip(selected = controller.orderType == t.id, onClick = { controller.orderType = t.id }, label = { Text(t.label) })
                }
            }
            // Kategori & Toggle Menu / Paket
            Row(Modifier.fillMaxWidth().padding(horizontal = AppSp.md, vertical = AppSp.xs), verticalAlignment = Alignment.CenterVertically) {
                if (bundles.isNotEmpty()) {
                    FilterChip(selected = !showBundleTab, onClick = { showBundleTab = false }, label = { Text("Menu") })
                    Spacer(Modifier.width(AppSp.xs))
                    FilterChip(selected = showBundleTab, onClick = { showBundleTab = true }, label = { Text("Paket (${bundles.size})") })
                    Spacer(Modifier.width(AppSp.sm))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSp.xs), modifier = Modifier.weight(1f)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = !showBundleTab && cat == controller.categoryFilter,
                            onClick = { showBundleTab = false; controller.categoryFilter = cat },
                            label = { Text(cat ?: "Semua") }
                        )
                    }
                }
            }

            // Grid Produk atau Paket
            BoxWithConstraints(Modifier.weight(1f)) {
                val cols = if (maxWidth >= 600.dp) 3 else 2
                if (!showBundleTab) {
                    if (filteredProds.isEmpty()) {
                        EmptyPlaceholder(Icons.Default.RestaurantMenu, "Menu masih kosong", "Tambahkan produk di tab Kelola > Menu")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            contentPadding = PaddingValues(start = AppSp.md, end = AppSp.md, top = AppSp.xs, bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(AppSp.md),
                            verticalArrangement = Arrangement.spacedBy(AppSp.md)
                        ) {
                            items(filteredProds, key = { it.id }) { p ->
                                val st = stockMap[p.id]
                                val isAvail = (st?.available ?: p.available) && (!p.trackStock || (st?.stockQty ?: 0) > 0)
                                Card(
                                    Modifier.fillMaxWidth().clickable(enabled = isAvail) { controller.addProduct(p, st) },
                                    shape = AppRd.cardLg, colors = CardDefaults.cardColors(containerColor = AppColors.card)
                                ) {
                                    Column {
                                        Box(Modifier.fillMaxWidth().height(110.dp)) {
                                            MenuPhotoBox(p.localPhotoPath, p.remotePhotoUrl, modifier = Modifier.fillMaxSize())
                                            if (!isAvail) {
                                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                                    Text("Habis", color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            } else if (p.trackStock) {
                                                val qty = st?.stockQty ?: 0
                                                Surface(Modifier.padding(6.dp).align(Alignment.TopEnd),
                                                    color = if (qty <= (st?.minStock ?: 5)) AppColors.danger else AppColors.brand, shape = AppRd.xs.let { androidx.compose.foundation.shape.RoundedCornerShape(it) }) {
                                                    Text("$qty", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Column(Modifier.padding(AppSp.md)) {
                                            Text(p.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Spacer(Modifier.height(4.dp))
                                            Text(p.sellPrice.toRupiah(), color = AppColors.brand, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        contentPadding = PaddingValues(start = AppSp.md, end = AppSp.md, top = AppSp.xs, bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(AppSp.md),
                        verticalArrangement = Arrangement.spacedBy(AppSp.md)
                    ) {
                        items(filteredBundles, key = { it.id }) { b ->
                            Card(Modifier.fillMaxWidth().clickable(enabled = b.available) { pickingBundle = b },
                                shape = AppRd.cardLg, colors = CardDefaults.cardColors(containerColor = AppColors.card)) {
                                Column {
                                    Box(Modifier.fillMaxWidth().height(100.dp)) {
                                        MenuPhotoBox(b.localPhotoPath, b.remotePhotoUrl, "🎁", Modifier.fillMaxSize())
                                    }
                                    Column(Modifier.padding(AppSp.md)) {
                                        Text(b.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(b.bundlePrice.toRupiah(), color = AppColors.brand, fontWeight = FontWeight.Bold)
                                            if (b.originalPrice > b.bundlePrice) {
                                                Spacer(Modifier.width(6.dp))
                                                Text(b.originalPrice.toRupiah(), style = MaterialTheme.typography.labelSmall, textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Cart Bar
        if (cart.isNotEmpty()) {
            val count = cart.sumOf { it.qty }
            Surface(
                onClick = onOpenCart,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(AppSp.lg),
                color = AppColors.brand, shape = AppRd.pill, shadowElevation = 6.dp
            ) {
                Row(Modifier.padding(horizontal = AppSp.lg, vertical = AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingCart, null, tint = Color.White)
                    Spacer(Modifier.width(AppSp.sm))
                    Column(Modifier.weight(1f)) {
                        Text("$count Item", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                        Text(total.toRupiah(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text("Keranjang ›", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showScanner) {
        BarcodeScannerDialog(onResult = { code -> controller.scanBarcode(code, stockMap); showScanner = false }, onDismiss = { showScanner = false })
    }
    if (showMetaDialog) {
        var tMeja by remember { mutableStateOf(controller.tableNumber) }
        var tNama by remember { mutableStateOf(controller.customerName) }
        AlertDialog(
            onDismissRequest = { showMetaDialog = false },
            title = { Text("Meja & Pelanggan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                    OutlinedTextField(tMeja, { tMeja = it.take(10) }, label = { Text("No. Meja (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tNama, { tNama = it }, label = { Text("Nama Pelanggan (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { controller.tableNumber = tMeja.trim(); controller.customerName = tNama.trim(); showMetaDialog = false }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { showMetaDialog = false }) { Text("Batal") } }
        )
    }
    pickingBundle?.let { b ->
        BundlePickDialog(app, b, products, onDismiss = { pickingBundle = null }) { picks ->
            controller.addBundle(b, picks); pickingBundle = null
        }
    }
}
@Composable
private fun BundlePickDialog(
    app: AppContainer,
    bundle: BundleEntity,
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onConfirm: (List<CartPick>) -> Unit
) {
    var groups by remember { mutableStateOf<List<BundleGroupEntity>>(emptyList()) }
    var optMap by remember { mutableStateOf<Map<String, List<BundleOptionEntity>>>(emptyMap()) }
    var picks by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    val prodMap = remember(products) { products.associateBy { it.id } }

    LaunchedEffect(bundle.id) {
        val (_, gs, om) = app.catalogRepo.loadBundleFull(bundle.id)
        groups = gs; optMap = om
        val init = mutableMapOf<String, Set<String>>()
        gs.forEach { g ->
            val opts = om[g.id].orEmpty()
            val defs = opts.filter { it.isDefaultPick }.map { it.id }.toSet()
            init[g.id] = if (defs.isNotEmpty()) defs else if (g.minPick > 0) opts.take(g.minPick).map { it.id }.toSet() else emptySet()
        }
        picks = init; loaded = true
    }

    val extra = picks.entries.sumOf { (gid, oids) ->
        optMap[gid].orEmpty().filter { it.id in oids }.sumOf { it.extraPrice * it.qty }
    }
    val valid = loaded && groups.all { g ->
        val sz = picks[g.id]?.size ?: 0
        if (g.required) sz in g.minPick..g.maxPick else sz <= g.maxPick
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${bundle.name} (${(bundle.bundlePrice + extra).toRupiah()})") },
        text = {
            if (!loaded) CircularProgressIndicator()
            else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                groups.forEach { g ->
                    Text("${g.name} (${if (g.required) "Wajib ${g.minPick}-${g.maxPick}" else "Opsional maks ${g.maxPick}"})", fontWeight = FontWeight.Bold)
                    val cur = picks[g.id].orEmpty()
                    optMap[g.id].orEmpty().forEach { o ->
                        val checked = o.id in cur
                        Row(Modifier.fillMaxWidth().clickable {
                            val next = if (g.maxPick == 1) setOf(o.id) else if (checked) cur - o.id else if (cur.size < g.maxPick) cur + o.id else cur
                            picks = picks + (g.id to next)
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(prodMap[o.productId]?.name ?: o.productName, Modifier.weight(1f))
                            if (o.extraPrice > 0L) Text("+${o.extraPrice.toRupiah()}", color = AppColors.success, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val res = mutableListOf<CartPick>()
                groups.forEach { g ->
                    val chosen = picks[g.id].orEmpty()
                    optMap[g.id].orEmpty().filter { it.id in chosen }.forEach { o ->
                        val p = prodMap[o.productId]
                        res += CartPick(o.id, g.id, g.name, o.productId, p?.name ?: o.productName, p?.costPrice ?: 0L, o.qty, o.extraPrice)
                    }
                }
                onConfirm(res)
            }) { Text("Tambahkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
