package com.iyonzkasir.ui.pos

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import com.iyonzkasir.di.AppContainer
import com.iyonzkasir.hardware.AppSound
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CartPick(
    val optionId: String, val groupId: String, val groupName: String,
    val productId: String, val productName: String, val costPrice: Long,
    val qty: Int, val extraPrice: Long
)

data class CartItem(
    val key: String, val product: ProductEntity? = null, val qty: Int = 1, val note: String = "",
    val bundle: BundleEntity? = null, val picks: List<CartPick> = emptyList(), val extraUnitPrice: Long = 0L
) {
    val isBundle: Boolean get() = bundle != null
    val name: String get() = bundle?.name ?: product?.name ?: ""
    val unitPrice: Long get() = if (isBundle) (bundle!!.bundlePrice + extraUnitPrice) else (product?.sellPrice ?: 0L)
    val unitCostHpp: Long get() = if (isBundle) picks.sumOf { it.costPrice * it.qty } else (product?.costPrice ?: 0L)
    val lineTotal: Long get() = unitPrice * qty
}

@Composable
fun MenuPhotoBox(localPath: String?, remoteUrl: String?, fallbackEmoji: String = "🍽️", modifier: Modifier = Modifier) {
    val model: Any? = remember(localPath, remoteUrl) {
        when {
            !localPath.isNullOrBlank() && File(localPath).exists() -> File(localPath)
            !remoteUrl.isNullOrBlank() && remoteUrl.startsWith("data:image") ->
                runCatching { Base64.decode(remoteUrl.substringAfter("base64,"), Base64.DEFAULT) }.getOrNull()
            !remoteUrl.isNullOrBlank() -> remoteUrl
            else -> null
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (model != null) AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(fallbackEmoji, style = MaterialTheme.typography.headlineMedium)
    }
}

class PosV2Controller(private val app: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var orderType by mutableStateOf(OrderType.DINE_IN.id)
    var tableNumber by mutableStateOf("")
    var customerName by mutableStateOf("")
    var editingOrderId by mutableStateOf<String?>(null)
    var editingReceiptCode by mutableStateOf("")
    var searchQuery by mutableStateOf("")
    var categoryFilter by mutableStateOf<String?>(null)
    var discountType by mutableStateOf("NONE")
    var discountValue by mutableStateOf(0L)
    var voucherCode by mutableStateOf("")
    var voucherAmount by mutableStateOf(0L)
    var voucherError by mutableStateOf<String?>(null)
    var selectedMember by mutableStateOf<MemberEntity?>(null)
    var pointsToRedeem by mutableStateOf(0)
    var taxPercent by mutableIntStateOf(0)
    var barcodeNotice by mutableStateOf<String?>(null)
    var activePayments by mutableStateOf(PaymentMethod.entries.map { it.id }.toSet())
    var soundEnabled by mutableStateOf(true)

    private val _cart = MutableStateFlow<Map<String, CartItem>>(emptyMap())
    val cart: StateFlow<List<CartItem>> = _cart.map { it.values.toList() }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val subtotal: StateFlow<Long> = cart.map { list -> list.sumOf { it.lineTotal } }.stateIn(scope, SharingStarted.Eagerly, 0L)

    init { refreshPrefs() }

    fun refreshPrefs() {
        scope.launch {
            runCatching {
                val defTax = app.prefRepo.getDefaultTaxPercent()
                if (editingOrderId == null) taxPercent = defTax
                soundEnabled = app.prefRepo.isSoundEnabled()
                activePayments = app.prefRepo.getActivePayments()
            }
        }
    }
    fun calcDiscount(sub: Long): Long = when (discountType) {
        "NOMINAL" -> discountValue.coerceIn(0L, sub)
        "PERCENT" -> (sub * discountValue.coerceIn(0L, 100L)) / 100L
        else -> 0L
    }

    fun calcTax(taxable: Long): Long = (taxable * taxPercent.coerceIn(0, 100)) / 100L

    fun calcTotal(sub: Long): Long {
        val afterDisc = (sub - calcDiscount(sub) - voucherAmount - LoyaltyRules.pointsToRupiah(pointsToRedeem)).coerceAtLeast(0L)
        return afterDisc + calcTax(afterDisc)
    }

    fun addProduct(p: ProductEntity, stock: OutletStockEntity?, note: String = "") {
        val availFlag = stock?.available ?: p.available
        if (!availFlag) { barcodeNotice = "${p.name} sedang habis"; return }
        val key = "P:${p.id}|$note"
        val curInCart = _cart.value.values.filter { !it.isBundle && it.product?.id == p.id }.sumOf { it.qty }
        if (p.trackStock) {
            val maxQty = stock?.stockQty ?: 0
            if (curInCart >= maxQty) {
                barcodeNotice = "Stok ${p.name} hanya tersisa $maxQty"
                if (soundEnabled) AppSound.playError(); return
            }
        }
        _cart.update { map ->
            val old = map[key]
            if (old == null) map + (key to CartItem(key = key, product = p, qty = 1, note = note))
            else map + (key to old.copy(qty = old.qty + 1))
        }
        if (soundEnabled) AppSound.playClick()
    }

    fun addBundle(b: BundleEntity, picks: List<CartPick>) {
        if (!b.available) { barcodeNotice = "${b.name} sedang habis"; return }
        val sig = picks.sortedBy { it.optionId }.joinToString(",") { "${it.productId}:${it.qty}:${it.extraPrice}" }
        val key = "B:${b.id}:$sig"
        val extra = picks.sumOf { it.extraPrice * it.qty }
        _cart.update { map ->
            val old = map[key]
            if (old == null) map + (key to CartItem(key = key, bundle = b, picks = picks, extraUnitPrice = extra, qty = 1))
            else map + (key to old.copy(qty = old.qty + 1))
        }
        if (soundEnabled) AppSound.playClick()
    }

    fun decrease(key: String) = _cart.update { map ->
        val old = map[key] ?: return@update map
        if (old.qty <= 1) map - key else map + (key to old.copy(qty = old.qty - 1))
    }

    fun remove(key: String) { _cart.update { it - key } }

    fun updateNote(key: String, newNote: String) {
        val item = _cart.value[key] ?: return
        val p = item.product ?: return
        val newKey = "P:${p.id}|$newNote"
        _cart.update { map ->
            val withoutOld = map - key
            val existing = withoutOld[newKey]
            if (existing == null) withoutOld + (newKey to item.copy(key = newKey, note = newNote))
            else withoutOld + (newKey to existing.copy(qty = existing.qty + item.qty))
        }
    }

    fun scanBarcode(code: String, stocksMap: Map<String, OutletStockEntity>) {
        scope.launch {
            val found = app.catalogRepo.getByBarcode(code)
            if (found == null) {
                barcodeNotice = "Barcode '$code' tidak ditemukan"
                if (soundEnabled) AppSound.playError()
            } else {
                addProduct(found, stocksMap[found.id])
                barcodeNotice = "✓ ${found.name} ditambahkan"
            }
        }
    }

    fun applyVoucher(rawCode: String, sub: Long) {
        scope.launch {
            voucherError = null
            val k = rawCode.trim().uppercase()
            if (k.isBlank()) { clearVoucher(); return@launch }
            val v = app.crmRepo.getVoucher(k) ?: run { voucherError = "Kode voucher tidak ditemukan"; return@launch }
            val now = System.currentTimeMillis()
            val err = when {
                !v.active -> "Voucher nonaktif"
                v.quota > 0 && v.usedCount >= v.quota -> "Kuota voucher sudah habis"
                v.endAt > 0L && v.endAt < now -> "Voucher kadaluarsa"
                v.startAt > 0L && v.startAt > now -> "Voucher belum berlaku"
                sub < v.minSpend -> "Minimal belanja ${v.minSpend.toRupiah()}"
                else -> null
            }
            if (err != null) { voucherError = err; if (soundEnabled) AppSound.playError(); return@launch }
            val amt = if (v.discountType == "PERCENT") {
                val raw = (sub * v.value) / 100L
                if (v.maxDiscount > 0L) raw.coerceAtMost(v.maxDiscount) else raw
            } else v.value.coerceAtMost(sub)
            voucherCode = k; voucherAmount = amt
        }
    }

    fun clearVoucher() { voucherCode = ""; voucherAmount = 0L; voucherError = null }

    fun resetOrder() {
        orderType = OrderType.DINE_IN.id; tableNumber = ""; customerName = ""
        editingOrderId = null; editingReceiptCode = ""; discountType = "NONE"; discountValue = 0L
        clearVoucher(); selectedMember = null; pointsToRedeem = 0; barcodeNotice = null
        _cart.value = emptyMap(); refreshPrefs()
    }
    fun loadOpenBill(orderId: String) {
        scope.launch {
            val order = app.orderRepo.getOrder(orderId) ?: return@launch
            val items = app.orderRepo.itemsOf(orderId)
            orderType = order.orderType; tableNumber = order.tableNumber
            customerName = order.customerName; editingOrderId = order.id
            editingReceiptCode = order.receiptCode; discountType = order.discountType
            discountValue = order.discountValue; voucherCode = order.voucherCode
            voucherAmount = order.voucherAmount; taxPercent = order.taxPercent
            selectedMember = if (order.memberId.isNotBlank()) app.crmRepo.getMember(order.memberId) else null

            val map = linkedMapOf<String, CartItem>()
            for (it in items) {
                if (it.isBundleComponent) continue
                if (it.isBundleHeader) {
                    val b = app.catalogRepo.loadBundleFull(it.bundleId).first ?: BundleEntity(
                        id = it.bundleId, name = it.name, bundlePrice = it.unitPrice
                    )
                    val picks = decodePicks(it.picksJson)
                    val extra = picks.sumOf { p -> p.extraPrice * p.qty }
                    val fixedBundle = b.copy(bundlePrice = (it.unitPrice - extra).coerceAtLeast(0L))
                    val key = "B:${fixedBundle.id}:${it.id}"
                    map[key] = CartItem(key = key, bundle = fixedBundle, picks = picks, extraUnitPrice = extra, qty = it.qty)
                } else {
                    val prod = app.catalogRepo.getProduct(it.productId) ?: ProductEntity(
                        id = it.productId, name = it.name, sellPrice = it.unitPrice, costPrice = it.unitCostHpp
                    )
                    val key = "P:${prod.id}|${it.note}"
                    map[key] = CartItem(key = key, product = prod, qty = it.qty, note = it.note)
                }
            }
            _cart.value = map
        }
    }

    fun submitOrder(
        outletId: String, outletName: String, isOpenBill: Boolean,
        paymentMethod: String, paidAmount: Long, onDone: (OrderEntity) -> Unit
    ) {
        val lines = cart.value
        if (lines.isEmpty()) return
        scope.launch {
            val sub = lines.sumOf { it.lineTotal }
            val disc = calcDiscount(sub)
            val ptsRp = LoyaltyRules.pointsToRupiah(pointsToRedeem)
            val taxable = (sub - disc - voucherAmount - ptsRp).coerceAtLeast(0L)
            val taxAmt = calcTax(taxable)
            val total = taxable + taxAmt
            val orderId = editingOrderId ?: newUuid()
            val queueNo = if (app.prefRepo.isQueueEnabled()) app.orderRepo.nextQueueNumber(outletId) else 0
            val u = SessionManager.currentUser
            val shiftId = if (!isOpenBill) app.shiftRepo.getActive(outletId)?.id.orEmpty() else ""

            val orderItems = mutableListOf<OrderItemEntity>()
            lines.forEach { line ->
                if (line.isBundle) {
                    val hdrId = newUuid()
                    orderItems += OrderItemEntity(
                        id = hdrId, orderId = orderId, bundleId = line.bundle!!.id,
                        isBundleHeader = true, name = line.name, unitPrice = line.unitPrice,
                        unitCostHpp = line.unitCostHpp, qty = line.qty, lineTotal = line.lineTotal,
                        picksJson = encodePicks(line.picks)
                    )
                    line.picks.forEach { pk ->
                        orderItems += OrderItemEntity(
                            id = newUuid(), orderId = orderId, productId = pk.productId,
                            bundleId = line.bundle.id, isBundleComponent = true, parentItemId = hdrId,
                            name = pk.productName, unitPrice = 0L, unitCostHpp = pk.costPrice,
                            qty = pk.qty * line.qty, lineTotal = 0L
                        )
                    }
                } else {
                    val p = line.product!!
                    orderItems += OrderItemEntity(
                        id = newUuid(), orderId = orderId, productId = p.id,
                        name = p.name, unitPrice = line.unitPrice, unitCostHpp = line.unitCostHpp,
                        qty = line.qty, lineTotal = line.lineTotal, note = line.note
                    )
                }
            }

            val earnedPts = if (!isOpenBill && selectedMember != null) LoyaltyRules.earnedPoints(total) else 0
            val finalPaid = if (isOpenBill) 0L else if (paymentMethod == PaymentMethod.CASH.id) paidAmount else total
            val order = OrderEntity(
                id = orderId, receiptCode = editingReceiptCode, businessId = SessionManager.businessId,
                outletId = outletId, outletName = outletName, timestamp = System.currentTimeMillis(),
                orderType = orderType, tableNumber = tableNumber, queueNumber = queueNo,
                customerName = customerName.ifBlank { selectedMember?.name ?: "" },
                memberId = selectedMember?.id ?: "", memberName = selectedMember?.name ?: "",
                subtotal = sub, discountType = discountType, discountValue = discountValue, discountAmount = disc,
                voucherCode = voucherCode, voucherAmount = voucherAmount,
                pointsRedeemed = pointsToRedeem, pointsDiscountAmount = ptsRp,
                taxPercent = taxPercent, taxAmount = taxAmt, total = total,
                totalCostHpp = lines.sumOf { it.unitCostHpp * it.qty },
                paymentMethod = if (isOpenBill) "" else paymentMethod,
                paidAmount = finalPaid, changeAmount = if (isOpenBill) 0L else (finalPaid - total).coerceAtLeast(0L),
                status = if (isOpenBill) OrderStatus.OPEN.id else OrderStatus.PAID.id,
                kitchenStatus = KitchenStatus.WAITING.id, shiftId = shiftId,
                staffId = u?.id ?: "", staffName = u?.name ?: "", pointsEarned = earnedPts
            )
            val saved = app.orderRepo.saveOrderAtomically(order, orderItems)
            if (soundEnabled) AppSound.playSuccess()
            app.triggerSyncInBackground()
            resetOrder()
            onDone(saved)
        }
    }

    private fun encodePicks(picks: List<CartPick>): String = runCatching {
        JSONArray().apply {
            picks.forEach { p ->
                put(JSONObject().apply {
                    put("o", p.optionId); put("g", p.groupId); put("gn", p.groupName)
                    put("p", p.productId); put("pn", p.productName); put("c", p.costPrice)
                    put("q", p.qty); put("e", p.extraPrice)
                })
            }
        }.toString()
    }.getOrDefault("[]")

    private fun decodePicks(json: String): List<CartPick> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CartPick(
                optionId = o.optString("o"), groupId = o.optString("g"), groupName = o.optString("gn"),
                productId = o.optString("p"), productName = o.optString("pn"),
                costPrice = o.optLong("c"), qty = o.optInt("q", 1), extraPrice = o.optLong("e")
            )
        }
    }.getOrDefault(emptyList())
}
