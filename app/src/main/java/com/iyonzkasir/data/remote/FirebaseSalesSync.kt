package com.iyonzkasir.data.remote

import com.google.firebase.firestore.Query
import com.iyonzkasir.core.SyncStatus
import com.iyonzkasir.data.local.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

internal fun mapToOrderEntity(id: String, businessId: String, m: Map<String, Any?>): OrderEntity = OrderEntity(
    id = id, receiptCode = m["receiptCode"] as? String ?: "", businessId = businessId,
    outletId = m["outletId"] as? String ?: "", outletName = m["outletName"] as? String ?: "",
    timestamp = (m["timestamp"] as? Number)?.toLong() ?: 0L,
    orderType = m["orderType"] as? String ?: "DINE_IN",
    tableNumber = m["tableNumber"] as? String ?: "",
    queueNumber = (m["queueNumber"] as? Number)?.toInt() ?: 0,
    customerName = m["customerName"] as? String ?: "",
    memberId = m["memberId"] as? String ?: "", memberName = m["memberName"] as? String ?: "",
    subtotal = (m["subtotal"] as? Number)?.toLong() ?: 0L,
    discountType = m["discountType"] as? String ?: "NONE",
    discountValue = (m["discountValue"] as? Number)?.toLong() ?: 0L,
    discountAmount = (m["discountAmount"] as? Number)?.toLong() ?: 0L,
    voucherCode = m["voucherCode"] as? String ?: "",
    voucherAmount = (m["voucherAmount"] as? Number)?.toLong() ?: 0L,
    pointsRedeemed = (m["pointsRedeemed"] as? Number)?.toInt() ?: 0,
    pointsDiscountAmount = (m["pointsDiscountAmount"] as? Number)?.toLong() ?: 0L,
    taxPercent = (m["taxPercent"] as? Number)?.toInt() ?: 0,
    taxAmount = (m["taxAmount"] as? Number)?.toLong() ?: 0L,
    total = (m["total"] as? Number)?.toLong() ?: 0L,
    totalCostHpp = (m["totalCostHpp"] as? Number)?.toLong() ?: 0L,
    paymentMethod = m["paymentMethod"] as? String ?: "CASH",
    paidAmount = (m["paidAmount"] as? Number)?.toLong() ?: 0L,
    changeAmount = (m["changeAmount"] as? Number)?.toLong() ?: 0L,
    status = m["status"] as? String ?: "PAID",
    kitchenStatus = m["kitchenStatus"] as? String ?: "NONE",
    source = m["source"] as? String ?: "POS",
    webOrderId = m["webOrderId"] as? String ?: "",
    shiftId = m["shiftId"] as? String ?: "",
    staffId = m["staffId"] as? String ?: "", staffName = m["staffName"] as? String ?: "",
    voidReason = m["voidReason"] as? String ?: "", voidBy = m["voidBy"] as? String ?: "",
    pointsEarned = (m["pointsEarned"] as? Number)?.toInt() ?: 0,
    stockDeducted = m["stockDeducted"] as? Boolean ?: false,
    note = m["note"] as? String ?: "",
    updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
    syncStatus = SyncStatus.SYNCED.id
)

internal fun mapToOrderItemEntity(orderId: String, m: Map<String, Any?>): OrderItemEntity = OrderItemEntity(
    id = m["id"] as? String ?: "", orderId = orderId,
    productId = m["productId"] as? String ?: "", bundleId = m["bundleId"] as? String ?: "",
    isBundleHeader = m["isBundleHeader"] as? Boolean ?: false,
    isBundleComponent = m["isBundleComponent"] as? Boolean ?: false,
    parentItemId = m["parentItemId"] as? String ?: "", name = m["name"] as? String ?: "",
    unitPrice = (m["unitPrice"] as? Number)?.toLong() ?: 0L,
    unitCostHpp = (m["unitCostHpp"] as? Number)?.toLong() ?: 0L,
    qty = (m["qty"] as? Number)?.toInt() ?: 1,
    lineTotal = (m["lineTotal"] as? Number)?.toLong() ?: 0L,
    note = m["note"] as? String ?: "", picksJson = m["picksJson"] as? String ?: ""
)

class FirebaseCloudGateway : CloudSyncGateway {
    private val catalog = FirebaseCatalogHelper()

    override fun currentOwnerUid(): String? = FbRefs.auth.currentUser?.uid
    override fun currentOwnerEmail(): String? = FbRefs.auth.currentUser?.email
    override suspend fun registerOwner(email: String, password: String, ownerName: String, businessName: String) =
        catalog.registerOwner(email, password, ownerName, businessName)
    override suspend fun loginOwner(email: String, password: String) = catalog.loginOwner(email, password)
    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        FbRefs.auth.sendPasswordResetEmail(email.trim()).await(); Unit
    }
    override fun logout() = FbRefs.auth.signOut()
    override suspend fun pullBusinessSnapshot(businessId: String, sinceDays: Int) = catalog.pullSnapshot(businessId, sinceDays)

    override suspend fun pushOutlets(businessId: String, items: List<OutletEntity>) {
        val col = FbRefs.biz(businessId).collection("outlets")
        items.forEach { o ->
            col.document(o.id).set(mapOf(
                "name" to o.name, "address" to o.address, "phoneWa" to o.phoneWa,
                "footerReceipt" to o.footerReceipt, "isMain" to o.isMain,
                "active" to o.active, "updatedAt" to o.updatedAt
            )).await()
        }
    }

    override suspend fun pushStaff(businessId: String, items: List<StaffEntity>) {
        val col = FbRefs.biz(businessId).collection("staff")
        items.forEach { s ->
            col.document(s.id).set(mapOf(
                "name" to s.name, "jobTitle" to s.jobTitle, "pinHash" to s.pinHash,
                "pinSalt" to s.pinSalt, "isOwner" to s.isOwner, "outletId" to s.outletId,
                "accessKeys" to s.accessKeys, "active" to s.active,
                "createdAt" to s.createdAt, "updatedAt" to s.updatedAt
            )).await()
        }
    }

    override suspend fun pushCategories(businessId: String, items: List<CategoryEntity>) {
        val col = FbRefs.biz(businessId).collection("categories")
        items.forEach { c ->
            col.document(c.id).set(mapOf(
                "name" to c.name, "colorHex" to c.colorHex, "sortOrder" to c.sortOrder,
                "active" to c.active, "updatedAt" to c.updatedAt, "deletedAt" to c.deletedAt
            )).await()
        }
    }

    override suspend fun pushProduct(businessId: String, item: ProductEntity) {
        FbRefs.biz(businessId).collection("products").document(item.id).set(mapOf(
            "name" to item.name, "description" to item.description,
            "categoryId" to item.categoryId, "categoryName" to item.categoryName,
            "sellPrice" to item.sellPrice, "costPrice" to item.costPrice, "barcode" to item.barcode,
            "remotePhotoUrl" to item.remotePhotoUrl, "photoHash" to item.photoHash,
            "available" to item.available, "trackStock" to item.trackStock,
            "updatedAt" to item.updatedAt, "deletedAt" to item.deletedAt
        )).await()
    }

    override suspend fun pushStocks(businessId: String, items: List<OutletStockEntity>) {
        val col = FbRefs.biz(businessId).collection("outlet_stocks")
        items.forEach { s ->
            col.document(s.id).set(mapOf(
                "outletId" to s.outletId, "productId" to s.productId,
                "available" to s.available, "stockQty" to s.stockQty,
                "minStock" to s.minStock, "updatedAt" to s.updatedAt
            )).await()
        }
    }

    override suspend fun pushBundle(
        businessId: String, bundle: BundleEntity,
        groups: List<BundleGroupEntity>, options: List<BundleOptionEntity>
    ) {
        val optByGroup = options.groupBy { it.groupId }
        val gMaps = groups.map { g ->
            mapOf("id" to g.id, "name" to g.name, "minPick" to g.minPick, "maxPick" to g.maxPick, "required" to g.required,
                "options" to optByGroup[g.id].orEmpty().map { o ->
                    mapOf("id" to o.id, "productId" to o.productId, "productName" to o.productName,
                        "qty" to o.qty, "extraPrice" to o.extraPrice, "isDefaultPick" to o.isDefaultPick)
                })
        }
        FbRefs.biz(businessId).collection("bundles").document(bundle.id).set(mapOf(
            "name" to bundle.name, "description" to bundle.description, "bundlePrice" to bundle.bundlePrice,
            "originalPrice" to bundle.originalPrice, "bundleType" to bundle.bundleType,
            "remotePhotoUrl" to bundle.remotePhotoUrl, "photoHash" to bundle.photoHash,
            "available" to bundle.available, "sortOrder" to bundle.sortOrder, "groups" to gMaps,
            "updatedAt" to bundle.updatedAt, "deletedAt" to bundle.deletedAt
        )).await()
    }
    override suspend fun pushOrders(businessId: String, orders: List<OrderEntity>, itemsByOrder: Map<String, List<OrderItemEntity>>) {
        val col = FbRefs.biz(businessId).collection("orders")
        orders.forEach { o ->
            val itemsMap = itemsByOrder[o.id].orEmpty().map { it ->
                mapOf("id" to it.id, "productId" to it.productId, "bundleId" to it.bundleId,
                    "isBundleHeader" to it.isBundleHeader, "isBundleComponent" to it.isBundleComponent,
                    "parentItemId" to it.parentItemId, "name" to it.name, "unitPrice" to it.unitPrice,
                    "unitCostHpp" to it.unitCostHpp, "qty" to it.qty, "lineTotal" to it.lineTotal,
                    "note" to it.note, "picksJson" to it.picksJson)
            }
            col.document(o.id).set(mapOf(
                "receiptCode" to o.receiptCode, "outletId" to o.outletId, "outletName" to o.outletName,
                "timestamp" to o.timestamp, "orderType" to o.orderType, "tableNumber" to o.tableNumber,
                "queueNumber" to o.queueNumber, "customerName" to o.customerName, "memberId" to o.memberId,
                "memberName" to o.memberName, "subtotal" to o.subtotal, "discountType" to o.discountType,
                "discountValue" to o.discountValue, "discountAmount" to o.discountAmount,
                "voucherCode" to o.voucherCode, "voucherAmount" to o.voucherAmount,
                "pointsRedeemed" to o.pointsRedeemed, "pointsDiscountAmount" to o.pointsDiscountAmount,
                "taxPercent" to o.taxPercent, "taxAmount" to o.taxAmount, "total" to o.total,
                "totalCostHpp" to o.totalCostHpp, "paymentMethod" to o.paymentMethod,
                "paidAmount" to o.paidAmount, "changeAmount" to o.changeAmount, "status" to o.status,
                "kitchenStatus" to o.kitchenStatus, "source" to o.source, "webOrderId" to o.webOrderId,
                "shiftId" to o.shiftId, "staffId" to o.staffId, "staffName" to o.staffName,
                "voidReason" to o.voidReason, "voidBy" to o.voidBy, "pointsEarned" to o.pointsEarned,
                "stockDeducted" to o.stockDeducted, "note" to o.note, "updatedAt" to o.updatedAt,
                "items" to itemsMap
            )).await()
        }
    }

    override suspend fun pushExpenses(businessId: String, items: List<ExpenseEntity>) {
        val col = FbRefs.biz(businessId).collection("expenses")
        items.forEach { e ->
            col.document(e.id).set(mapOf(
                "outletId" to e.outletId, "outletName" to e.outletName, "timestamp" to e.timestamp,
                "categoryId" to e.categoryId, "categoryName" to e.categoryName, "amount" to e.amount,
                "note" to e.note, "remotePhotoUrl" to e.remotePhotoUrl, "staffId" to e.staffId,
                "staffName" to e.staffName, "updatedAt" to e.updatedAt, "deletedAt" to e.deletedAt
            )).await()
        }
    }

    override suspend fun pushShifts(businessId: String, items: List<ShiftEntity>) {
        val col = FbRefs.biz(businessId).collection("shifts")
        items.forEach { s ->
            col.document(s.id).set(mapOf(
                "outletId" to s.outletId, "outletName" to s.outletName, "staffId" to s.staffId,
                "staffName" to s.staffName, "openedAt" to s.openedAt, "closedAt" to s.closedAt,
                "openingCash" to s.openingCash, "txCount" to s.txCount, "totalSales" to s.totalSales,
                "cashSales" to s.cashSales, "nonCashSales" to s.nonCashSales, "kasbonSales" to s.kasbonSales,
                "totalDiscount" to s.totalDiscount, "totalTax" to s.totalTax, "actualCash" to s.actualCash,
                "cashDiff" to s.cashDiff, "note" to s.note, "status" to s.status, "updatedAt" to s.updatedAt
            )).await()
        }
    }

    override suspend fun pushMembers(businessId: String, items: List<MemberEntity>) {
        val col = FbRefs.biz(businessId).collection("members")
        items.forEach { m ->
            col.document(m.id).set(mapOf(
                "name" to m.name, "phone" to m.phone, "address" to m.address, "note" to m.note,
                "points" to m.points, "totalSpent" to m.totalSpent, "debt" to m.debt,
                "active" to m.active, "updatedAt" to m.updatedAt, "deletedAt" to m.deletedAt
            )).await()
        }
    }

    override suspend fun pushMemberTx(businessId: String, items: List<MemberTxEntity>) {
        val col = FbRefs.biz(businessId).collection("member_transactions")
        items.forEach { t ->
            col.document(t.id).set(mapOf(
                "outletId" to t.outletId, "memberId" to t.memberId, "orderId" to t.orderId,
                "txType" to t.txType, "pointsDelta" to t.pointsDelta, "debtDelta" to t.debtDelta,
                "pointsAfter" to t.pointsAfter, "debtAfter" to t.debtAfter,
                "note" to t.note, "staffName" to t.staffName, "timestamp" to t.timestamp
            )).await()
        }
    }

    override suspend fun pushVouchers(businessId: String, items: List<VoucherEntity>) {
        val col = FbRefs.biz(businessId).collection("vouchers")
        items.forEach { v ->
            col.document(v.code).set(mapOf(
                "name" to v.name, "discountType" to v.discountType, "value" to v.value,
                "minSpend" to v.minSpend, "maxDiscount" to v.maxDiscount, "quota" to v.quota,
                "usedCount" to v.usedCount, "startAt" to v.startAt, "endAt" to v.endAt,
                "active" to v.active, "updatedAt" to v.updatedAt, "deletedAt" to v.deletedAt
            )).await()
        }
    }

    override suspend fun pushAuditEntries(businessId: String, items: List<AuditEntryEntity>) {
        val col = FbRefs.biz(businessId).collection("audit_entries")
        items.forEach { a ->
            col.document(a.id).set(mapOf(
                "outletId" to a.outletId, "staffId" to a.staffId, "staffName" to a.staffName,
                "action" to a.action, "targetId" to a.targetId, "detail" to a.detail, "timestamp" to a.timestamp
            )).await()
        }
    }

    override suspend fun checkSlugAvailable(slug: String, businessId: String): Result<Boolean> = runCatching {
        val doc = FbRefs.store(slug).get().await()
        if (!doc.exists()) true else (doc.getString("ownerUid") ?: "") == businessId
    }

    override suspend fun publishWebStorefront(
        businessId: String, slug: String, storeName: String, address: String,
        phoneWa: String, themeColorHex: String, taxPercent: Int,
        products: List<ProductEntity>, bundles: List<BundleEntity>,
        groupsMap: Map<String, List<BundleGroupEntity>>, optionsMap: Map<String, List<BundleOptionEntity>>
    ) = catalog.publishWebStorefront(
        businessId, slug, storeName, address, phoneWa, themeColorHex,
        taxPercent, products, bundles, groupsMap, optionsMap
    )

    override suspend fun updateWebOrderStatus(storeSlug: String, webOrderId: String, status: String) {
        if (storeSlug.isBlank()) return
        FbRefs.store(storeSlug).collection("orders").document(webOrderId).update("status", status).await()
    }

    override fun listenWebOrders(storeSlug: String, businessId: String, onReceive: (List<WebOrderInboxEntity>) -> Unit): CloudListenerHandle {
        if (storeSlug.isBlank()) return object : CloudListenerHandle { override fun remove() {} }
        val reg = FbRefs.store(storeSlug).collection("orders")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { d ->
                    val ts = d.getTimestamp("timestamp")?.toDate()?.time
                        ?: d.getLong("timestamp") ?: System.currentTimeMillis()
                    @Suppress("UNCHECKED_CAST")
                    val rawItems = d.get("items") as? List<Map<String, Any?>> ?: emptyList()
                    val arr = JSONArray()
                    rawItems.forEach { m ->
                        arr.put(JSONObject().apply {
                            put("menuId", m["menuId"] ?: "")
                            put("nama", m["nama"] ?: "")
                            put("harga", (m["harga"] as? Number)?.toLong() ?: 0L)
                            put("qty", (m["qty"] as? Number)?.toInt() ?: 1)
                            put("totalPrice", (m["totalPrice"] as? Number)?.toLong() ?: 0L)
                            put("isBundle", m["isBundle"] as? Boolean ?: false)
                        })
                    }
                    WebOrderInboxEntity(
                        id = d.id, businessId = businessId, outletId = d.getString("outletId") ?: "",
                        customerName = d.getString("customerNama") ?: "", customerPhone = d.getString("customerTelepon") ?: "",
                        tableNumber = d.getString("meja") ?: "", note = d.getString("catatan") ?: "",
                        itemsJson = arr.toString(), subtotal = d.getLong("subtotal") ?: 0L,
                        taxAmount = d.getLong("pajak") ?: 0L, total = d.getLong("total") ?: 0L,
                        status = d.getString("status") ?: "PENDING", timestamp = ts
                    )
                }
                onReceive(list)
            }
        return object : CloudListenerHandle { override fun remove() = reg.remove() }
    }

    override fun listenKitchenOrders(
        businessId: String, outletId: String,
        onReceive: (List<Pair<OrderEntity, List<OrderItemEntity>>>) -> Unit
    ): CloudListenerHandle {
        val reg = FbRefs.biz(businessId).collection("orders")
            .whereIn("kitchenStatus", listOf("WAITING", "COOKING", "READY"))
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val pairs = snap.documents.mapNotNull { d ->
                    val order = mapToOrderEntity(d.id, businessId, d.data ?: return@mapNotNull null)
                    if (outletId != "ALL" && order.outletId != outletId) return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val items = (d.get("items") as? List<Map<String, Any?>>).orEmpty()
                        .map { mapToOrderItemEntity(d.id, it) }
                    order to items
                }
                onReceive(pairs)
            }
        return object : CloudListenerHandle { override fun remove() = reg.remove() }
    }

    override fun listenTeamUpdates(businessId: String, onStaffUpdate: (List<StaffEntity>) -> Unit): CloudListenerHandle {
        val reg = FbRefs.biz(businessId).collection("staff").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            val list = snap.documents.mapNotNull { d ->
                StaffEntity(
                    id = d.id, businessId = businessId,
                    name = d.getString("name") ?: return@mapNotNull null,
                    jobTitle = d.getString("jobTitle") ?: "Kasir",
                    pinHash = d.getString("pinHash") ?: "", pinSalt = d.getString("pinSalt") ?: "",
                    isOwner = d.getBoolean("isOwner") ?: false, outletId = d.getString("outletId") ?: "ALL",
                    accessKeys = d.getString("accessKeys") ?: "", active = d.getBoolean("active") ?: true,
                    createdAt = d.getLong("createdAt") ?: 0L, updatedAt = d.getLong("updatedAt") ?: 0L,
                    syncStatus = SyncStatus.SYNCED.id
                )
            }
            onStaffUpdate(list)
        }
        return object : CloudListenerHandle { override fun remove() = reg.remove() }
    }
}
