package com.iyonzkasir.ui.manage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import com.iyonzkasir.data.remote.ImageHelper
import com.iyonzkasir.di.AppContainer
import com.iyonzkasir.ui.pos.MenuPhotoBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BundleManageV2Screen(app: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val bundles by app.catalogRepo.bundles.collectAsState(initial = emptyList())
    val products by app.catalogRepo.products.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<BundleEntity?>(null) }
    var showDlg by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Paket Hemat / Bundling") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showDlg = true }, containerColor = AppColors.brand) { Icon(Icons.Default.Add, null, tint = Color.White) }
        }
    ) { pad ->
        if (bundles.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize()) { EmptyPlaceholder(Icons.Default.CardGiftcard, "Belum ada paket hemat", "Gabungkan beberapa menu jadi harga paket promo") }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = AppSp.md, end = AppSp.md, top = AppSp.md, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                items(bundles, key = { it.id }) { b ->
                    Card(Modifier.fillMaxWidth().clickable { editing = b; showDlg = true }, shape = AppRd.card) {
                        Row(Modifier.padding(AppSp.md), verticalAlignment = Alignment.CenterVertically) {
                            MenuPhotoBox(b.localPhotoPath, b.remotePhotoUrl, "🎁", Modifier.size(52.dp).clip(AppRd.card))
                            Spacer(Modifier.width(AppSp.md))
                            Column(Modifier.weight(1f)) {
                                Text(b.name, fontWeight = FontWeight.Bold)
                                Text("${BundleType.fromId(b.bundleType).label} • ${b.bundlePrice.toRupiah()}", style = MaterialTheme.typography.bodySmall, color = AppColors.brand)
                            }
                            if (SessionManager.can(AppAccess.DELETE_DATA)) {
                                IconButton(onClick = { scope.launch { app.catalogRepo.deleteBundle(b); app.triggerSyncInBackground() } }) {
                                    Icon(Icons.Default.Delete, null, tint = AppColors.danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDlg) BundleEditorDlg(app, editing, products, onDismiss = { showDlg = false })
}
@Composable
private fun BundleEditorDlg(app: AppContainer, initial: BundleEntity?, products: List<ProductEntity>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var priceTxt by remember { mutableStateOf(if ((initial?.bundlePrice ?: 0L) > 0L) initial!!.bundlePrice.toString() else "") }
    var origTxt by remember { mutableStateOf(if ((initial?.originalPrice ?: 0L) > 0L) initial!!.originalPrice.toString() else "") }
    var type by remember { mutableStateOf(initial?.bundleType ?: BundleType.FIXED.id) }
    var localPath by remember { mutableStateOf(initial?.localPhotoPath) }
    var pickedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(initial?.id) {
        if (initial != null) {
            val (_, gs, om) = app.catalogRepo.loadBundleFull(initial.id)
            pickedIds = gs.flatMap { om[it.id].orEmpty() }.map { it.productId }.toSet()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { ImageHelper.savePickedPhoto(ctx, uri, "bundle")?.let { localPath = it } }
    }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initial == null) "Paket Baru" else "Edit Paket") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                if (localPath != null || initial?.remotePhotoUrl != null) MenuPhotoBox(localPath, initial?.remotePhotoUrl, "🎁", Modifier.fillMaxWidth().height(100.dp).clip(AppRd.card))
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Pilih Foto Paket") }
                OutlinedTextField(name, { name = it }, label = { Text("Nama Paket *") }, singleLine = true)
                OutlinedTextField(priceTxt, { priceTxt = it.filter { c -> c.isDigit() }.take(9) }, label = { Text("Harga Jual Paket *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(origTxt, { origTxt = it.filter { c -> c.isDigit() }.take(9) }, label = { Text("Harga Coret Asli (opsional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(AppSp.xs)) {
                    BundleType.entries.forEach { t -> FilterChip(selected = type == t.id, onClick = { type = t.id }, label = { Text(t.label) }) }
                }
                Text("Pilih Isi Menu Paket:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                products.forEach { p ->
                    val chk = p.id in pickedIds
                    Row(Modifier.fillMaxWidth().clickable { pickedIds = if (chk) pickedIds - p.id else pickedIds + p.id }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = chk, onCheckedChange = null)
                        Text("${p.name} (${p.sellPrice.toRupiah()})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            val price = priceTxt.toLongOrNull() ?: 0L
            TextButton(enabled = name.isNotBlank() && price > 0L && pickedIds.isNotEmpty(), onClick = {
                scope.launch {
                    val bId = initial?.id ?: newUuid(); val gId = newUuid()
                    val selectedProds = products.filter { it.id in pickedIds }; val isFixed = type == BundleType.FIXED.id
                    val group = BundleGroupEntity(id = gId, bundleId = bId, name = if (isFixed) "Isi Paket" else "Pilih Menu",
                        minPick = if (isFixed) selectedProds.size else 1, maxPick = selectedProds.size, required = true)
                    val options = selectedProds.mapIndexed { i, p ->
                        BundleOptionEntity(id = newUuid(), groupId = gId, productId = p.id, productName = p.name, qty = 1, isDefaultPick = isFixed || i == 0, sortOrder = i)
                    }
                    val bundle = (initial ?: BundleEntity(id = bId, businessId = SessionManager.businessId)).copy(
                        name = name.trim(), bundlePrice = price, originalPrice = origTxt.toLongOrNull() ?: 0L, bundleType = type, localPhotoPath = localPath
                    )
                    app.catalogRepo.saveBundle(bundle, listOf(group), options); app.triggerSyncInBackground(); onDismiss()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
