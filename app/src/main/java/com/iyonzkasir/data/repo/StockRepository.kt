package com.iyonzkasir.data.repo

import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import kotlinx.coroutines.flow.Flow

class StockRepository(
    private val stockDao: StockDao,
    private val auditDao: AuditDao
) {
    fun observeOutletStocks(outletId: String): Flow<List<OutletStockEntity>> = stockDao.observeOutletStocks(outletId)
    fun observeMovements(outletId: String): Flow<List<StockMovementEntity>> = stockDao.observeMovements(outletId)
    fun observeProductMovements(productId: String, outletId: String) = stockDao.observeMovementsForProduct(productId, outletId)

    suspend fun getStockQty(outletId: String, productId: String): Int =
        stockDao.getStock(outletId, productId)?.stockQty ?: 0

    suspend fun setOutletAvailable(businessId: String, outletId: String, productId: String, available: Boolean) {
        val cur = stockDao.getStock(outletId, productId) ?: OutletStockEntity(
            id = OutletStockEntity.key(outletId, productId),
            businessId = businessId, outletId = outletId, productId = productId
        )
        stockDao.upsertStock(cur.copy(available = available, updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING.id))
    }

    suspend fun adjustStock(
        businessId: String, outletId: String, product: ProductEntity,
        newQty: Int, minStock: Int, note: String, moveType: StockMoveType = StockMoveType.OPNAME
    ) {
        val cur = stockDao.getStock(outletId, product.id)
        val before = cur?.stockQty ?: 0
        val after = newQty.coerceAtLeast(0)
        val now = System.currentTimeMillis()
        stockDao.upsertStock(OutletStockEntity(
            id = OutletStockEntity.key(outletId, product.id), businessId = businessId,
            outletId = outletId, productId = product.id, available = cur?.available ?: true,
            stockQty = after, minStock = minStock.coerceAtLeast(0),
            updatedAt = now, syncStatus = SyncStatus.PENDING.id
        ))
        val u = SessionManager.currentUser
        stockDao.insertMovement(StockMovementEntity(
            businessId = businessId, outletId = outletId, productId = product.id, productName = product.name,
            moveType = moveType.id, delta = after - before, beforeQty = before, afterQty = after,
            note = note, staffId = u?.id ?: "", staffName = u?.name ?: "", timestamp = now
        ))
        auditDao.insert(AuditEntryEntity(
            businessId = businessId, outletId = outletId, staffId = u?.id ?: "", staffName = u?.name ?: "",
            action = "STOCK_ADJUST", targetId = product.id, detail = "${product.name}: $before -> $after ($note)"
        ))
    }

    suspend fun addStock(businessId: String, outletId: String, product: ProductEntity, addQty: Int, note: String) {
        if (addQty <= 0) return
        val cur = stockDao.getStock(outletId, product.id)
        adjustStock(businessId, outletId, product, (cur?.stockQty ?: 0) + addQty, cur?.minStock ?: 5, note.ifBlank { "Tambah stok" }, StockMoveType.IN)
    }

    suspend fun deductForSale(businessId: String, outletId: String, productId: String, productName: String, qty: Int, orderId: String) {
        if (qty <= 0 || productId.isBlank()) return
        val cur = stockDao.getStock(outletId, productId)
        val before = cur?.stockQty ?: 0
        val after = (before - qty).coerceAtLeast(0)
        val now = System.currentTimeMillis()
        stockDao.upsertStock(OutletStockEntity(
            id = OutletStockEntity.key(outletId, productId), businessId = businessId, outletId = outletId, productId = productId,
            available = cur?.available ?: true, stockQty = after, minStock = cur?.minStock ?: 5,
            updatedAt = now, syncStatus = SyncStatus.PENDING.id
        ))
        val u = SessionManager.currentUser
        stockDao.insertMovement(StockMovementEntity(
            businessId = businessId, outletId = outletId, productId = productId, productName = productName,
            moveType = StockMoveType.SALE.id, delta = -qty, beforeQty = before, afterQty = after,
            note = "Penjualan", orderId = orderId, staffId = u?.id ?: "", staffName = u?.name ?: "", timestamp = now
        ))
    }

    suspend fun returnForVoid(businessId: String, outletId: String, productId: String, productName: String, qty: Int, orderId: String, reason: String) {
        if (qty <= 0 || productId.isBlank()) return
        val cur = stockDao.getStock(outletId, productId)
        val before = cur?.stockQty ?: 0
        val after = before + qty
        val now = System.currentTimeMillis()
        stockDao.upsertStock(OutletStockEntity(
            id = OutletStockEntity.key(outletId, productId), businessId = businessId, outletId = outletId, productId = productId,
            available = cur?.available ?: true, stockQty = after, minStock = cur?.minStock ?: 5,
            updatedAt = now, syncStatus = SyncStatus.PENDING.id
        ))
        val u = SessionManager.currentUser
        stockDao.insertMovement(StockMovementEntity(
            businessId = businessId, outletId = outletId, productId = productId, productName = productName,
            moveType = StockMoveType.VOID_RETURN.id, delta = qty, beforeQty = before, afterQty = after,
            note = reason, orderId = orderId, staffId = u?.id ?: "", staffName = u?.name ?: "", timestamp = now
        ))
    }
}
