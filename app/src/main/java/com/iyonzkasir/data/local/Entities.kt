package com.iyonzkasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.iyonzkasir.core.*

// ═══════ CABANG / GEROBAK ═══════
@Entity(tableName = "outlets")
data class OutletEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val name: String = "",
    val address: String = "",
    val phoneWa: String = "",
    val footerReceipt: String = "Terima kasih sudah berbelanja",
    val isMain: Boolean = false,
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncStatus.PENDING.id
)

// ═══════ KARYAWAN / STAFF (100% CUSTOM ACCESS) ═══════
@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val name: String = "",
    val jobTitle: String = "Kasir",
    val pinHash: String = "",
    val pinSalt: String = "",
    val isOwner: Boolean = false,
    val outletId: String = "ALL",          // "ALL" atau outlet.id spesifik
    val accessKeys: String = "",           // comma-separated AppAccess.key
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = 0L,
    val syncStatus: String = SyncStatus.PENDING.id
) {
    fun toSessionUser(): ActiveSessionUser = ActiveSessionUser(
        id = id,
        name = name,
        jobTitle = jobTitle,
        isOwner = isOwner,
        outletId = outletId,
        accesses = if (isOwner) AppAccess.entries.toSet() else AppAccess.decode(accessKeys)
    )
}

// ═══════ KATEGORI PRODUK ═══════
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val name: String = "",
    val colorHex: String = "#FF6B2C",
    val sortOrder: Int = 0,
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.PENDING.id
)

// ═══════ PRODUK / MENU PUSAT ═══════
@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"])]
)
data class ProductEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val name: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryName: String = "Umum",
    val sellPrice: Long = 0L,
    val costPrice: Long = 0L,
    val barcode: String = "",
    val localPhotoPath: String? = null,
    val remotePhotoUrl: String? = null,
    val photoHash: String = "",
    val available: Boolean = true,
    val trackStock: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.PENDING.id
)

// ═══════ STOK & KETERSEDIAAN PER CABANG ═══════
@Entity(
    tableName = "outlet_stocks",
    indices = [Index(value = ["outletId", "productId"], unique = true)]
)
data class OutletStockEntity(
    @PrimaryKey val id: String,            // "${outletId}_${productId}"
    val businessId: String = "",
    val outletId: String = "",
    val productId: String = "",
    val available: Boolean = true,
    val stockQty: Int = 0,
    val minStock: Int = 5,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncStatus.PENDING.id
) {
    companion object {
        fun key(outletId: String, productId: String) = "${outletId}_${productId}"
    }
}

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val outletId: String = "",
    val productId: String = "",
    val productName: String = "",
    val moveType: String = StockMoveType.OPNAME.id,
    val delta: Int = 0,
    val beforeQty: Int = 0,
    val afterQty: Int = 0,
    val note: String = "",
    val orderId: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncStatus.PENDING.id
)

// ═══════ PAKET BUNDLING ═══════
@Entity(tableName = "bundles")
data class BundleEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val name: String = "",
    val description: String = "",
    val bundlePrice: Long = 0L,
    val originalPrice: Long = 0L,
    val bundleType: String = BundleType.FIXED.id,
    val localPhotoPath: String? = null,
    val remotePhotoUrl: String? = null,
    val photoHash: String = "",
    val available: Boolean = true,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.PENDING.id
)

@Entity(
    tableName = "bundle_groups",
    indices = [Index(value = ["bundleId"])]
)
data class BundleGroupEntity(
    @PrimaryKey val id: String = newUuid(),
    val bundleId: String = "",
    val name: String = "",
    val minPick: Int = 1,
    val maxPick: Int = 1,
    val required: Boolean = true,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "bundle_options",
    indices = [Index(value = ["groupId"])]
)
data class BundleOptionEntity(
    @PrimaryKey val id: String = newUuid(),
    val groupId: String = "",
    val productId: String = "",
    val productName: String = "",
    val qty: Int = 1,
    val extraPrice: Long = 0L,
    val isDefaultPick: Boolean = false,
    val sortOrder: Int = 0
)

// ═══════ TRANSAKSI / ORDER & DAPUR ═══════
@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["outletId", "timestamp"]),
        Index(value = ["status"]),
        Index(value = ["kitchenStatus"])
    ]
)
data class OrderEntity(
    @PrimaryKey val id: String = newUuid(),
    val receiptCode: String = "",
    val businessId: String = "",
    val outletId: String = "",
    val outletName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val orderType: String = OrderType.DINE_IN.id,
    val tableNumber: String = "",
    val queueNumber: Int = 0,
    val customerName: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val subtotal: Long = 0L,
    val discountType: String = "NONE",     // NONE, NOMINAL, PERCENT
    val discountValue: Long = 0L,
    val discountAmount: Long = 0L,
    val voucherCode: String = "",
    val voucherAmount: Long = 0L,
    val pointsRedeemed: Int = 0,
    val pointsDiscountAmount: Long = 0L,
    val taxPercent: Int = 0,
    val taxAmount: Long = 0L,
    val total: Long = 0L,
    val totalCostHpp: Long = 0L,
    val paymentMethod: String = PaymentMethod.CASH.id,
    val paidAmount: Long = 0L,
    val changeAmount: Long = 0L,
    val status: String = OrderStatus.PAID.id,
    val kitchenStatus: String = KitchenStatus.WAITING.id,
    val source: String = "POS",            // POS | WEB
    val webOrderId: String = "",
    val shiftId: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val voidReason: String = "",
    val voidBy: String = "",
    val pointsEarned: Int = 0,
    val stockDeducted: Boolean = false,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncStatus.PENDING.id
)

@Entity(
    tableName = "order_items",
    indices = [Index(value = ["orderId"])]
)
data class OrderItemEntity(
    @PrimaryKey val id: String = newUuid(),
    val orderId: String = "",
    val productId: String = "",
    val bundleId: String = "",
    val isBundleHeader: Boolean = false,
    val isBundleComponent: Boolean = false,
    val parentItemId: String = "",
    val name: String = "",
    val unitPrice: Long = 0L,
    val unitCostHpp: Long = 0L,
    val qty: Int = 1,
    val lineTotal: Long = 0L,
    val note: String = "",
    val picksJson: String = ""
)

// ═══════ PENGELUARAN / BELANJA ═══════
@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val name: String = "",
    val icon: String = "📝",
    val colorHex: String = "#FF6B2C",
    val sortOrder: Int = 0,
    val isDefault: Boolean = false
)

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["outletId", "timestamp"])]
)
data class ExpenseEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val outletId: String = "",
    val outletName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val categoryId: String = "",
    val categoryName: String = "",
    val amount: Long = 0L,
    val note: String = "",
    val localPhotoPath: String? = null,
    val remotePhotoUrl: String? = null,
    val staffId: String = "",
    val staffName: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.PENDING.id
)

// ═══════ SHIFT KASIR ═══════
@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val outletId: String = "",
    val outletName: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long = 0L,
    val openingCash: Long = 0L,
    val txCount: Int = 0,
    val totalSales: Long = 0L,
    val cashSales: Long = 0L,
    val nonCashSales: Long = 0L,
    val kasbonSales: Long = 0L,
    val totalDiscount: Long = 0L,
    val totalTax: Long = 0L,
    val actualCash: Long = 0L,
    val cashDiff: Long = 0L,
    val note: String = "",
    val status: String = "OPEN",
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncStatus.PENDING.id
)

// ═══════ PELANGGAN, POIN, KASBON & VOUCHER ═══════
@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val note: String = "",
    val points: Int = 0,
    val totalSpent: Long = 0L,
    val debt: Long = 0L,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.PENDING.id
)

@Entity(
    tableName = "member_transactions",
    indices = [Index(value = ["memberId"])]
)
data class MemberTxEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val outletId: String = "",
    val memberId: String = "",
    val orderId: String = "",
    val txType: String = "POIN_EARN",      // POIN_EARN, POIN_REDEEM, KASBON_ADD, KASBON_PAY, VOID_REVERT
    val pointsDelta: Int = 0,
    val debtDelta: Long = 0L,
    val pointsAfter: Int = 0,
    val debtAfter: Long = 0L,
    val note: String = "",
    val staffName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncStatus.PENDING.id
)

@Entity(tableName = "vouchers")
data class VoucherEntity(
    @PrimaryKey val code: String,
    val businessId: String = "",
    val name: String = "",
    val discountType: String = "NOMINAL",  // NOMINAL | PERCENT
    val value: Long = 0L,
    val minSpend: Long = 0L,
    val maxDiscount: Long = 0L,
    val quota: Int = 0,
    val usedCount: Int = 0,
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.PENDING.id
)

// ═══════ INBOX PESANAN WEB, AUDIT & PREFS ═══════
@Entity(tableName = "web_orders_inbox")
data class WebOrderInboxEntity(
    @PrimaryKey val id: String,
    val businessId: String = "",
    val outletId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val tableNumber: String = "",
    val note: String = "",
    val itemsJson: String = "[]",
    val subtotal: Long = 0L,
    val taxAmount: Long = 0L,
    val total: Long = 0L,
    val status: String = WebOrderStatus.PENDING.id,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_entries")
data class AuditEntryEntity(
    @PrimaryKey val id: String = newUuid(),
    val businessId: String = "",
    val outletId: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val action: String = "",
    val targetId: String = "",
    val detail: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncStatus.PENDING.id
)

@Entity(tableName = "local_prefs")
data class LocalPrefEntity(
    @PrimaryKey val key: String,
    val value: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

// ═══════ PROJECTION MODELS UNTUK LAPORAN ═══════
data class OrderSummaryRow(
    val txCount: Int = 0,
    val totalSales: Long = 0L,
    val totalCostHpp: Long = 0L,
    val totalDiscount: Long = 0L,
    val totalTax: Long = 0L
) {
    val grossProfit: Long get() = totalSales - totalCostHpp
}

data class OutletPerformanceRow(
    val outletId: String,
    val outletName: String,
    val txCount: Int,
    val totalSales: Long
)

data class ProductProfitRow(
    val itemKey: String,
    val name: String,
    val totalQty: Int,
    val totalGrossSales: Long,
    val totalHpp: Long
) {
    val profit: Long get() = totalGrossSales - totalHpp
    val marginPercent: Int get() = if (totalGrossSales > 0L) ((profit * 100L) / totalGrossSales).toInt() else 0
}

data class TopProductRow(
    val itemKey: String,
    val name: String,
    val totalQty: Int,
    val totalGrossSales: Long
)

data class PaymentStatRow(
    val paymentMethod: String,
    val txCount: Int,
    val totalSales: Long
)

data class ExpenseCategoryStatRow(
    val categoryName: String,
    val txCount: Int,
    val totalAmount: Long
)
