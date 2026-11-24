package com.iyonzkasir.data.repo

import androidx.room.withTransaction
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class OrderRepository(
    private val db: IyonzDatabase,
    private val stockRepo: StockRepository
) {
    private val orderDao = db.orderDao()
    private val crmDao = db.crmDao()
    private val productDao = db.productDao()
    private val auditDao = db.auditDao()
    private val webDao = db.webOrderDao()

    fun observeOpenBills(outletId: String) = orderDao.observeOpenBills(outletId)
    fun observeHistory(outletId: String) = orderDao.observeHistory(outletId)
    fun observeKitchenQueue(outletId: String) = orderDao.observeKitchenQueue(outletId)
    fun observeSummary(outletId: String, start: Long, end: Long) = orderDao.observeSummaryInRange(outletId, start, end)
    fun observeOutletPerf(start: Long, end: Long) = orderDao.observeOutletPerformance(start, end)
    fun observeWebOrders(outletId: String) = webDao.observeAll(outletId)
    fun observePendingWebCount(outletId: String) = webDao.observePendingCount(outletId)

    suspend fun getOrder(id: String) = orderDao.getOrder(id)
    suspend fun itemsOf(orderId: String) = orderDao.itemsOf(orderId)
    suspend fun getSummary(outletId: String, start: Long, end: Long) = orderDao.getSummaryInRange(outletId, start, end)
    suspend fun paidOrdersInRange(outletId: String, start: Long, end: Long) = orderDao.paidOrdersInRange(outletId, start, end)
    suspend fun productProfit(outletId: String, start: Long, end: Long) = orderDao.productProfitInRange(outletId, start, end)
    suspend fun topProducts(outletId: String, start: Long, end: Long, limit: Int = 5) = orderDao.topProductsInRange(outletId, start, end, limit)
    suspend fun paymentStats(outletId: String, start: Long, end: Long) = orderDao.paymentStatsInRange(outletId, start, end)

    suspend fun nextQueueNumber(outletId: String): Int =
        orderDao.maxQueueSince(outletId, startOfTodayMillis()) + 1

    suspend fun generateReceiptCode(outletId: String): String {
        val count = orderDao.countTodayInOutlet(outletId, startOfTodayMillis()) + 1
        val datePart = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        return "INV-$datePart-${count.toString().padStart(3, '0')}"
    }

    suspend fun saveOrderAtomically(order: OrderEntity, items: List<OrderItemEntity>): OrderEntity = db.withTransaction {
        val now = System.currentTimeMillis()
        val isPaid = order.status == OrderStatus.PAID.id
        var finalOrder = order.copy(
            receiptCode = order.receiptCode.ifBlank { generateReceiptCode(order.outletId) },
            updatedAt = now, syncStatus = SyncStatus.PENDING.id
        )
        if (isPaid && !finalOrder.stockDeducted) {
            items.filter { !it.isBundleHeader && it.productId.isNotBlank() }.forEach { it ->
                val prod = productDao.getById(it.productId)
                if (prod != null && prod.trackStock) {
                    stockRepo.deductForSale(finalOrder.businessId, finalOrder.outletId, prod.id, prod.name, it.qty, finalOrder.id)
                }
            }
            finalOrder = finalOrder.copy(stockDeducted = true)
        }
        if (isPaid && finalOrder.voucherCode.isNotBlank()) {
            crmDao.getVoucher(finalOrder.voucherCode)?.let { v ->
                crmDao.upsertVoucher(v.copy(usedCount = v.usedCount + 1, updatedAt = now, syncStatus = SyncStatus.PENDING.id))
            }
        }
        if (isPaid && finalOrder.memberId.isNotBlank()) {
            crmDao.getMember(finalOrder.memberId)?.let { m ->
                var pts = m.points
                var debt = m.debt
                if (finalOrder.pointsRedeemed > 0 && pts >= finalOrder.pointsRedeemed) {
                    pts -= finalOrder.pointsRedeemed
                    crmDao.insertMemberTx(MemberTxEntity(
                        businessId = finalOrder.businessId, outletId = finalOrder.outletId,
                        memberId = m.id, orderId = finalOrder.id, txType = "POIN_REDEEM",
                        pointsDelta = -finalOrder.pointsRedeemed, pointsAfter = pts, debtAfter = debt,
                        note = "Tukar poin #${finalOrder.receiptCode}", staffName = finalOrder.staffName
                    ))
                }
                if (finalOrder.pointsEarned > 0) {
                    pts += finalOrder.pointsEarned
                    crmDao.insertMemberTx(MemberTxEntity(
                        businessId = finalOrder.businessId, outletId = finalOrder.outletId,
                        memberId = m.id, orderId = finalOrder.id, txType = "POIN_EARN",
                        pointsDelta = finalOrder.pointsEarned, pointsAfter = pts, debtAfter = debt,
                        note = "Poin #${finalOrder.receiptCode}", staffName = finalOrder.staffName
                    ))
                }
                if (finalOrder.paymentMethod == PaymentMethod.KASBON.id) {
                    debt += finalOrder.total
                    crmDao.insertMemberTx(MemberTxEntity(
                        businessId = finalOrder.businessId, outletId = finalOrder.outletId,
                        memberId = m.id, orderId = finalOrder.id, txType = "KASBON_ADD",
                        debtDelta = finalOrder.total, pointsAfter = pts, debtAfter = debt,
                        note = "Kasbon #${finalOrder.receiptCode}", staffName = finalOrder.staffName
                    ))
                }
                crmDao.upsertMember(m.copy(
                    points = pts, debt = debt, totalSpent = m.totalSpent + finalOrder.total,
                    updatedAt = now, syncStatus = SyncStatus.PENDING.id
                ))
            }
        }
        orderDao.saveOrderWithItems(finalOrder, items)
        finalOrder
    }
    suspend fun voidOrRefund(orderId: String, isRefund: Boolean, reason: String) = db.withTransaction {
        val order = orderDao.getOrder(orderId) ?: return@withTransaction
        if (order.status == OrderStatus.VOID.id || order.status == OrderStatus.REFUND.id) return@withTransaction
        val items = orderDao.itemsOf(orderId)
        val now = System.currentTimeMillis()
        val u = SessionManager.currentUser
        val newStatus = if (isRefund) OrderStatus.REFUND.id else OrderStatus.VOID.id

        orderDao.updateOrderVoidOrRefund(orderId, newStatus, reason, u?.name ?: "", now)

        if (order.stockDeducted) {
            items.filter { !it.isBundleHeader && it.productId.isNotBlank() }.forEach { it ->
                val prod = productDao.getById(it.productId)
                if (prod != null && prod.trackStock) {
                    stockRepo.returnForVoid(order.businessId, order.outletId, prod.id, prod.name, it.qty, order.id, "$newStatus: $reason")
                }
            }
        }
        if (order.voucherCode.isNotBlank()) {
            crmDao.getVoucher(order.voucherCode)?.let { v ->
                crmDao.upsertVoucher(v.copy(usedCount = (v.usedCount - 1).coerceAtLeast(0), updatedAt = now, syncStatus = SyncStatus.PENDING.id))
            }
        }
        if (order.memberId.isNotBlank()) {
            crmDao.getMember(order.memberId)?.let { m ->
                val ptsDelta = order.pointsRedeemed - order.pointsEarned
                val newPts = (m.points + ptsDelta).coerceAtLeast(0)
                val debtDelta = if (order.paymentMethod == PaymentMethod.KASBON.id) -order.total else 0L
                val newDebt = (m.debt + debtDelta).coerceAtLeast(0L)
                crmDao.upsertMember(m.copy(
                    points = newPts, debt = newDebt, totalSpent = (m.totalSpent - order.total).coerceAtLeast(0L),
                    updatedAt = now, syncStatus = SyncStatus.PENDING.id
                ))
                crmDao.insertMemberTx(MemberTxEntity(
                    businessId = order.businessId, outletId = order.outletId, memberId = m.id, orderId = order.id,
                    txType = "VOID_REVERT", pointsDelta = ptsDelta, debtDelta = debtDelta,
                    pointsAfter = newPts, debtAfter = newDebt,
                    note = "$newStatus #${order.receiptCode}: $reason", staffName = u?.name ?: ""
                ))
            }
        }
        auditDao.insert(AuditEntryEntity(
            businessId = order.businessId, outletId = order.outletId,
            staffId = u?.id ?: "", staffName = u?.name ?: "",
            action = newStatus, targetId = order.id, detail = "#${order.receiptCode} ($reason)"
        ))
    }

    suspend fun updateKitchenStatus(orderId: String, status: KitchenStatus) =
        orderDao.updateKitchenStatus(orderId, status.id)

    suspend fun acceptWebOrder(inbox: WebOrderInboxEntity, outletId: String, outletName: String): OrderEntity {
        val u = SessionManager.currentUser
        val orderId = newUuid()
        val arr = JSONArray(inbox.itemsJson)
        val items = mutableListOf<OrderItemEntity>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val qty = obj.optInt("qty", 1)
            val price = obj.optLong("harga", 0L)
            val prodId = obj.optString("menuId", "")
            val prod = if (prodId.isNotBlank()) productDao.getById(prodId) else null
            items += OrderItemEntity(
                id = newUuid(), orderId = orderId, productId = prod?.id ?: prodId,
                name = obj.optString("nama", "Item Web"), unitPrice = price,
                unitCostHpp = prod?.costPrice ?: 0L, qty = qty, lineTotal = price * qty
            )
        }
        val order = OrderEntity(
            id = orderId, receiptCode = generateReceiptCode(outletId),
            businessId = inbox.businessId, outletId = outletId, outletName = outletName,
            orderType = if (inbox.tableNumber.isNotBlank()) OrderType.DINE_IN.id else OrderType.TAKE_AWAY.id,
            tableNumber = inbox.tableNumber, queueNumber = nextQueueNumber(outletId),
            customerName = inbox.customerName, subtotal = inbox.subtotal,
            taxAmount = inbox.taxAmount, total = inbox.total,
            totalCostHpp = items.sumOf { it.unitCostHpp * it.qty },
            status = OrderStatus.OPEN.id, kitchenStatus = KitchenStatus.WAITING.id,
            source = "WEB", webOrderId = inbox.id,
            staffId = u?.id ?: "", staffName = u?.name ?: "", note = inbox.note
        )
        orderDao.saveOrderWithItems(order, items)
        webDao.setStatus(inbox.id, WebOrderStatus.ACCEPTED.id)
        return order
    }

    suspend fun rejectWebOrder(id: String) = webDao.setStatus(id, WebOrderStatus.REJECTED.id)
}
