package com.iyonzkasir.ui.manage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import com.iyonzkasir.data.remote.ImageHelper
import com.iyonzkasir.di.AppContainer
import com.iyonzkasir.hardware.BarcodeScannerDialog
import com.iyonzkasir.ui.pos.MenuPhotoBox
import kotlinx.coroutines.launch

@Composable
fun ProductEditorDlg(app: AppContainer, initial: ProductEntity?, categories: List<CategoryEntity>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val canEditPrice = initial == null || SessionManager.can(AppAccess.EDIT_PRICE)
    val canSeeCost = SessionManager.can(AppAccess.VIEW_PROFIT_HPP)
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var priceTxt by remember { mutableStateOf(if ((initial?.sellPrice ?: 0L) > 0L) initial!!.sellPrice.toString() else "") }
    var costTxt by remember { mutableStateOf(if ((initial?.costPrice ?: 0L) > 0L) initial!!.costPrice.toString() else "") }
    var catId by remember { mutableStateOf(initial?.categoryId ?: categories.firstOrNull()?.id.orEmpty()) }
    var catName by remember { mutableStateOf(initial?.categoryName ?: categories.firstOrNull()?.name ?: "Umum") }
    var barcode by remember { mutableStateOf(initial?.barcode ?: "") }
    var trackStock by remember { mutableStateOf(initial?.trackStock ?: false) }
    var localPath by remember { mutableStateOf(initial?.localPhotoPath) }
    var showScan by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { ImageHelper.savePickedPhoto(ctx, uri, "prod")?.let { localPath = it } }
    }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initial == null) "Produk Baru" else "Edit Produk") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                if (localPath != null || initial?.remotePhotoUrl != null) {
                    MenuPhotoBox(localPath, initial?.remotePhotoUrl, modifier = Modifier.fillMaxWidth().height(120.dp).clip(AppRd.card))
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (localPath == null) "Pilih Foto Produk" else "Ganti Foto")
                }
                OutlinedTextField(name, { name = it; err = null }, label = { Text("Nama Produk *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(priceTxt, { priceTxt = it.filter { c -> c.isDigit() }.take(9) }, enabled = canEditPrice,
                    label = { Text("Harga Jual *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (canSeeCost) {
                    OutlinedTextField(costTxt, { costTxt = it.filter { c -> c.isDigit() }.take(9) }, enabled = canEditPrice,
                        label = { Text("Harga Modal / HPP (opsional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (categories.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        categories.take(3).forEach { c -> FilterChip(selected = catName == c.name, onClick = { catId = c.id; catName = c.name }, label = { Text(c.name) }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(barcode, { barcode = it.take(40); err = null }, label = { Text("Barcode (opsional)") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showScan = true }) { Icon(Icons.Default.QrCodeScanner, null, tint = AppColors.brand) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lacak Stok Per Cabang", Modifier.weight(1f)); Switch(checked = trackStock, onCheckedChange = { trackStock = it })
                }
                err?.let { Text(it, color = AppColors.danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = priceTxt.toLongOrNull() ?: 0L
                if (name.isBlank() || price <= 0L) { err = "Nama & harga jual wajib diisi"; return@TextButton }
                scope.launch {
                    val prodId = initial?.id ?: newUuid()
                    if (app.catalogRepo.isBarcodeDuplicate(barcode, prodId)) { err = "Barcode sudah dipakai produk lain"; return@launch }
                    val prod = (initial ?: ProductEntity(id = prodId, businessId = SessionManager.businessId)).copy(
                        name = name.trim(), sellPrice = price, costPrice = costTxt.toLongOrNull() ?: 0L,
                        categoryId = catId, categoryName = catName, barcode = barcode.trim(), trackStock = trackStock, localPhotoPath = localPath
                    )
                    app.catalogRepo.saveProduct(prod); app.triggerSyncInBackground(); onDismiss()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
    if (showScan) BarcodeScannerDialog(onResult = { barcode = it; showScan = false }, onDismiss = { showScan = false })
}
@Composable
fun CategoryEditorDlg(app: AppContainer, initial: CategoryEntity?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.colorHex ?: CATEGORY_COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initial == null) "Kategori Baru" else "Edit Kategori") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama Kategori *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.height(100.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CATEGORY_COLORS) { hex ->
                        Box(Modifier.size(34.dp).clip(CircleShape).background(hex.toColorSafe())
                            .border(if (color == hex) 3.dp else 0.dp, Color.Black, CircleShape).clickable { color = hex })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                scope.launch {
                    val cat = (initial ?: CategoryEntity(businessId = SessionManager.businessId)).copy(name = name.trim(), colorHex = color)
                    app.catalogRepo.saveCategory(cat); app.triggerSyncInBackground(); onDismiss()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun StockAdjustDlg(app: AppContainer, outletId: String, prod: ProductEntity, cur: OutletStockEntity?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var qtyTxt by remember { mutableStateOf((cur?.stockQty ?: 0).toString()) }
    var minTxt by remember { mutableStateOf((cur?.minStock ?: 5).toString()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Stok: ${prod.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                OutlinedTextField(qtyTxt, { qtyTxt = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("Jumlah Stok Saat Ini") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(minTxt, { minTxt = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("Batas Alert Stok Menipis") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Keterangan (cth: restock pasar)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    app.stockRepo.adjustStock(SessionManager.businessId, outletId, prod, qtyTxt.toIntOrNull() ?: 0, minTxt.toIntOrNull() ?: 5, note.ifBlank { "Update stok" })
                    app.triggerSyncInBackground(); onDismiss()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
