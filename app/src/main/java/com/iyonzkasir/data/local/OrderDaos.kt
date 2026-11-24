package com.iyonzkasir.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<OrderItemEntity>)
    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteItems(orderId: String)

    @Transaction
    suspend fun saveOrderWithItems(order: OrderEntity, items: List<OrderItemEntity>) {
        insertOrder(order)
        deleteItems(order.id)
        if (items.isNotEmpty()) insertItems(items)
    }

    @Query("SELECT * FROM orders WHERE status = 'OPEN' AND (:outletId = 'ALL' OR outletId = :outletId) ORDER BY timestamp DESC")
    fun observeOpenBills(outletId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status IN ('PAID', 'VOID', 'REFUND') AND (:outletId = 'ALL' OR outletId = :outletId) ORDER BY timestamp DESC LIMIT 300")
    fun observeHistory(outletId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status = 'PAID' AND timestamp >= :start AND timestamp <= :end AND (:outletId = 'ALL' OR outletId = :outletId) ORDER BY timestamp DESC")
    fun observePaidInRange(outletId: String, start: Long, end: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE kitchenStatus IN ('WAITING', 'COOKING', 'READY') AND status != 'VOID' AND status != 'REFUND' AND (:outletId = 'ALL' OR outletId = :outletId) ORDER BY timestamp ASC")
    fun observeKitchenQueue(outletId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    suspend fun getOrder(id: String): OrderEntity?
    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun itemsOf(orderId: String): List<OrderItemEntity>
    @Query("SELECT * FROM orders WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<OrderEntity>
    @Query("UPDATE orders SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)

    @Query("UPDATE orders SET kitchenStatus = :kitchenStatus, updatedAt = :ts, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun updateKitchenStatus(id: String, kitchenStatus: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE orders SET status = :status, voidReason = :reason, voidBy = :voidBy, kitchenStatus = 'NONE', updatedAt = :ts, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun updateOrderVoidOrRefund(id: String, status: String, reason: String, voidBy: String, ts: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(MAX(queueNumber), 0) FROM orders WHERE outletId = :outletId AND timestamp >= :startOfDay")
    suspend fun maxQueueSince(outletId: String, startOfDay: Long): Int
    @Query("SELECT COUNT(*) FROM orders WHERE outletId = :outletId AND timestamp >= :startOfDay")
    suspend fun countTodayInOutlet(outletId: String, startOfDay: Long): Int

    @Query("""
        SELECT COUNT(*) AS txCount,
               COALESCE(SUM(total), 0) AS totalSales,
               COALESCE(SUM(totalCostHpp), 0) AS totalCostHpp,
               COALESCE(SUM(discountAmount + voucherAmount + pointsDiscountAmount), 0) AS totalDiscount,
               COALESCE(SUM(taxAmount), 0) AS totalTax
        FROM orders
        WHERE status = 'PAID' AND timestamp >= :start AND timestamp <= :end
          AND (:outletId = 'ALL' OR outletId = :outletId)
    """)
    fun observeSummaryInRange(outletId: String, start: Long, end: Long): Flow<OrderSummaryRow>

    @Query("""
        SELECT COUNT(*) AS txCount,
               COALESCE(SUM(total), 0) AS totalSales,
               COALESCE(SUM(totalCostHpp), 0) AS totalCostHpp,
               COALESCE(SUM(discountAmount + voucherAmount + pointsDiscountAmount), 0) AS totalDiscount,
               COALESCE(SUM(taxAmount), 0) AS totalTax
        FROM orders
        WHERE status = 'PAID' AND timestamp >= :start AND timestamp <= :end
          AND (:outletId = 'ALL' OR outletId = :outletId)
    """)
    suspend fun getSummaryInRange(outletId: String, start: Long, end: Long): OrderSummaryRow

    @Query("""
        SELECT outletId AS outletId, outletName AS outletName,
               COUNT(*) AS txCount, COALESCE(SUM(total), 0) AS totalSales
        FROM orders
        WHERE status = 'PAID' AND timestamp >= :start AND timestamp <= :end
        GROUP BY outletId, outletName ORDER BY totalSales DESC
    """)
    fun observeOutletPerformance(start: Long, end: Long): Flow<List<OutletPerformanceRow>>

    @Query("SELECT * FROM orders WHERE status = 'PAID' AND timestamp >= :start AND timestamp <= :end AND (:outletId = 'ALL' OR outletId = :outletId) ORDER BY timestamp ASC")
    suspend fun paidOrdersInRange(outletId: String, start: Long, end: Long): List<OrderEntity>

    @Query("""
        SELECT CASE WHEN oi.bundleId != '' THEN 'B:' || oi.bundleId ELSE 'P:' || oi.productId END AS itemKey,
               oi.name AS name, SUM(oi.qty) AS totalQty,
               SUM(oi.lineTotal) AS totalGrossSales, SUM(oi.qty * oi.unitCostHpp) AS totalHpp
        FROM order_items oi
        JOIN orders o ON o.id = oi.orderId
        WHERE o.status = 'PAID' AND o.timestamp >= :start AND o.timestamp <= :end
          AND (:outletId = 'ALL' OR o.outletId = :outletId) AND oi.isBundleComponent = 0
        GROUP BY itemKey, oi.name
        ORDER BY (SUM(oi.lineTotal) - SUM(oi.qty * oi.unitCostHpp)) DESC
    """)
    suspend fun productProfitInRange(outletId: String, start: Long, end: Long): List<ProductProfitRow>

    @Query("""
        SELECT CASE WHEN oi.bundleId != '' THEN 'B:' || oi.bundleId ELSE 'P:' || oi.productId END AS itemKey,
               oi.name AS name, SUM(oi.qty) AS totalQty, SUM(oi.lineTotal) AS totalGrossSales
        FROM order_items oi
        JOIN orders o ON o.id = oi.orderId
        WHERE o.status = 'PAID' AND o.timestamp >= :start AND o.timestamp <= :end
          AND (:outletId = 'ALL' OR o.outletId = :outletId) AND oi.isBundleComponent = 0
        GROUP BY itemKey, oi.name ORDER BY SUM(oi.qty) DESC LIMIT :limit
    """)
    suspend fun topProductsInRange(outletId: String, start: Long, end: Long, limit: Int = 5): List<TopProductRow>

    @Query("""
        SELECT paymentMethod AS paymentMethod, COUNT(*) AS txCount, COALESCE(SUM(total), 0) AS totalSales
        FROM orders
        WHERE status = 'PAID' AND timestamp >= :start AND timestamp <= :end
          AND (:outletId = 'ALL' OR outletId = :outletId)
        GROUP BY paymentMethod ORDER BY totalSales DESC
    """)
    suspend fun paymentStatsInRange(outletId: String, start: Long, end: Long): List<PaymentStatRow>

    @Query("SELECT COUNT(*) FROM orders WHERE shiftId = :shiftId AND status = 'PAID'")
    suspend fun countByShift(shiftId: String): Int
    @Query("SELECT COALESCE(SUM(total), 0) FROM orders WHERE shiftId = :shiftId AND status = 'PAID'")
    suspend fun sumTotalByShift(shiftId: String): Long
    @Query("SELECT COALESCE(SUM(total), 0) FROM orders WHERE shiftId = :shiftId AND status = 'PAID' AND paymentMethod = 'CASH'")
    suspend fun sumCashByShift(shiftId: String): Long
    @Query("SELECT COALESCE(SUM(total), 0) FROM orders WHERE shiftId = :shiftId AND status = 'PAID' AND paymentMethod = 'KASBON'")
    suspend fun sumKasbonByShift(shiftId: String): Long
    @Query("SELECT COALESCE(SUM(discountAmount + voucherAmount + pointsDiscountAmount), 0) FROM orders WHERE shiftId = :shiftId AND status = 'PAID'")
    suspend fun sumDiscountByShift(shiftId: String): Long
    @Query("SELECT COALESCE(SUM(taxAmount), 0) FROM orders WHERE shiftId = :shiftId AND status = 'PAID'")
    suspend fun sumTaxByShift(shiftId: String): Long
}

@Dao
interface WebOrderDao {
    @Query("SELECT * FROM web_orders_inbox WHERE (:outletId = 'ALL' OR outletId = '' OR outletId = :outletId) ORDER BY timestamp DESC LIMIT 100")
    fun observeAll(outletId: String): Flow<List<WebOrderInboxEntity>>
    @Query("SELECT COUNT(*) FROM web_orders_inbox WHERE status = 'PENDING' AND (:outletId = 'ALL' OR outletId = '' OR outletId = :outletId)")
    fun observePendingCount(outletId: String): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<WebOrderInboxEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WebOrderInboxEntity)
    @Query("UPDATE web_orders_inbox SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)
}
