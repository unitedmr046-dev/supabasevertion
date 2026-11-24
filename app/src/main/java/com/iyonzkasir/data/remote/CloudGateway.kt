package com.iyonzkasir.data.remote

import com.iyonzkasir.data.local.*

data class CloudOwnerInfo(
    val uid: String,
    val email: String,
    val displayName: String = ""
)

data class CloudBundleData(
    val outlets: List<OutletEntity> = emptyList(),
    val staff: List<StaffEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val stocks: List<OutletStockEntity> = emptyList(),
    val bundles: List<BundleEntity> = emptyList(),
    val bundleGroups: List<BundleGroupEntity> = emptyList(),
    val bundleOptions: List<BundleOptionEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val vouchers: List<VoucherEntity> = emptyList(),
    val recentOrders: List<OrderEntity> = emptyList(),
    val recentOrderItems: List<OrderItemEntity> = emptyList(),
    val recentExpenses: List<ExpenseEntity> = emptyList(),
    val webSlug: String = ""
)

interface CloudListenerHandle {
    fun remove()
}

interface CloudSyncGateway {
    fun currentOwnerUid(): String?
    fun currentOwnerEmail(): String?

    suspend fun registerOwner(email: String, password: String, ownerName: String, businessName: String): Result<CloudOwnerInfo>
    suspend fun loginOwner(email: String, password: String): Result<CloudOwnerInfo>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    fun logout()

    suspend fun pullBusinessSnapshot(businessId: String, sinceDays: Int = 30): Result<CloudBundleData>

    suspend fun pushOutlets(businessId: String, items: List<OutletEntity>)
    suspend fun pushStaff(businessId: String, items: List<StaffEntity>)
    suspend fun pushCategories(businessId: String, items: List<CategoryEntity>)
    suspend fun pushProduct(businessId: String, item: ProductEntity)
    suspend fun pushStocks(businessId: String, items: List<OutletStockEntity>)
    suspend fun pushBundle(
        businessId: String,
        bundle: BundleEntity,
        groups: List<BundleGroupEntity>,
        options: List<BundleOptionEntity>
    )
    suspend fun pushOrders(businessId: String, orders: List<OrderEntity>, itemsByOrder: Map<String, List<OrderItemEntity>>)
    suspend fun pushExpenses(businessId: String, items: List<ExpenseEntity>)
    suspend fun pushShifts(businessId: String, items: List<ShiftEntity>)
    suspend fun pushMembers(businessId: String, items: List<MemberEntity>)
    suspend fun pushMemberTx(businessId: String, items: List<MemberTxEntity>)
    suspend fun pushVouchers(businessId: String, items: List<VoucherEntity>)
    suspend fun pushAuditEntries(businessId: String, items: List<AuditEntryEntity>)

    suspend fun checkSlugAvailable(slug: String, businessId: String): Result<Boolean>
    suspend fun publishWebStorefront(
        businessId: String,
        slug: String,
        storeName: String,
        address: String,
        phoneWa: String,
        themeColorHex: String,
        taxPercent: Int,
        products: List<ProductEntity>,
        bundles: List<BundleEntity>,
        groupsMap: Map<String, List<BundleGroupEntity>>,
        optionsMap: Map<String, List<BundleOptionEntity>>
    ): Result<Unit>

    suspend fun updateWebOrderStatus(storeSlug: String, webOrderId: String, status: String)

    fun listenWebOrders(storeSlug: String, businessId: String, onReceive: (List<WebOrderInboxEntity>) -> Unit): CloudListenerHandle
    fun listenKitchenOrders(businessId: String, outletId: String, onReceive: (List<Pair<OrderEntity, List<OrderItemEntity>>>) -> Unit): CloudListenerHandle
    fun listenTeamUpdates(businessId: String, onStaffUpdate: (List<StaffEntity>) -> Unit): CloudListenerHandle
}
