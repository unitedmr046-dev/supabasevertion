package com.iyonzkasir.data.remote

import android.content.Context
import com.iyonzkasir.core.SyncStatus
import com.iyonzkasir.data.local.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncSummary(val pushedCount: Int, val pulledCount: Int)

class SyncCoordinator(
    private val context: Context,
    private val db: IyonzDatabase,
    private val gateway: CloudSyncGateway
) {
    private val mutex = Mutex()

    suspend fun syncAll(businessId: String): Result<SyncSummary> = mutex.withLock {
        if (businessId.isBlank() || gateway.currentOwnerUid() == null) {
            return@withLock Result.failure(IllegalStateException("Belum login akun usaha"))
        }
        return@withLock runCatching {
            var pushed = 0

            val pendingOutlets = db.outletDao().getPendingSync()
            if (pendingOutlets.isNotEmpty()) {
                gateway.pushOutlets(businessId, pendingOutlets)
                pendingOutlets.forEach { db.outletDao().setSyncStatus(it.id, SyncStatus.SYNCED.id) }
                pushed += pendingOutlets.size
            }
            val pendingStaff = db.staffDao().getPendingSync()
            if (pendingStaff.isNotEmpty()) {
                gateway.pushStaff(businessId, pendingStaff)
                pendingStaff.forEach { db.staffDao().setSyncStatus(it.id, SyncStatus.SYNCED.id) }
                pushed += pendingStaff.size
            }
            val pendingCats = db.categoryDao().getPendingSync()
            if (pendingCats.isNotEmpty()) {
                gateway.pushCategories(businessId, pendingCats)
                pendingCats.forEach { db.categoryDao().setSyncStatus(it.id, SyncStatus.SYNCED.id) }
                pushed += pendingCats.size
            }
            val pendingProds = db.productDao().getPendingSync()
            for (p in pendingProds) {
                var remoteUrl = p.remotePhotoUrl
                var hash = p.photoHash
                if (!p.localPhotoPath.isNullOrBlank()) {
                    val compressed = ImageHelper.compressForCloud(context, p.localPhotoPath)
                    if (compressed != null && compressed.second != hash) {
                        remoteUrl = compressed.first
                        hash = compressed.second
                    }
                }
                gateway.pushProduct(businessId, p.copy(remotePhotoUrl = remoteUrl, photoHash = hash))
                db.productDao().markSyncedWithPhoto(p.id, SyncStatus.SYNCED.id, remoteUrl, hash)
                pushed++
            }
            val pendingStocks = db.stockDao().getPendingStocksSync()
            if (pendingStocks.isNotEmpty()) {
                gateway.pushStocks(businessId, pendingStocks)
                pendingStocks.forEach { db.stockDao().setStockSyncStatus(it.id, SyncStatus.SYNCED.id) }
                pushed += pendingStocks.size
            }
            val pendingBundles = db.bundleDao().getPendingSync()
            for (b in pendingBundles) {
                var remoteUrl = b.remotePhotoUrl
                var hash = b.photoHash
                if (!b.localPhotoPath.isNullOrBlank()) {
                    val compressed = ImageHelper.compressForCloud(context, b.localPhotoPath)
                    if (compressed != null && compressed.second != hash) {
                        remoteUrl = compressed.first
                        hash = compressed.second
                    }
                }
                val groups = db.bundleDao().groupsOf(b.id)
                val opts = groups.flatMap { db.bundleDao().optionsOf(it.id) }
                gateway.pushBundle(businessId, b.copy(remotePhotoUrl = remoteUrl, photoHash = hash), groups, opts)
                db.bundleDao().markSyncedWithPhoto(b.id, SyncStatus.SYNCED.id, remoteUrl, hash)
                pushed++
            }
            val pendingOrders = db.orderDao().getPendingSync()
            if (pendingOrders.isNotEmpty()) {
                val itemsMap = pendingOrders.associate { it.id to db.orderDao().itemsOf(it.id) }
                gateway.pushOrders(businessId, pendingOrders, itemsMap)
                pendingOrders.forEach { db.orderDao().setSyncStatus(it.id, SyncStatus.SYNCED.id) }
                pushed += pendingOrders.size
            }
            val pendingExpenses = db.expenseDao().getPendingSync()
            if (pendingExpenses.isNotEmpty()) {
                gateway.pushExpenses(businessId, pendingExpenses)
                pendingExpenses.forEach { db.expenseDao().setSyncStatus(it.id, SyncStatus.SYNCED.id) }
                pushed += pendingExpenses.size
            }
            val pendingShifts = db.shiftDao().getPendingSync()
            if (pendingShifts.isNotEmpty()) {
                gateway.pushShifts(businessId, pendingShifts)
                pendingShifts.forEach { db.shiftDao().setSyncStatus(it.id, SyncStatus.SYNCED.id) }
                pushed += pendingShifts.size
            }
            val pendingMembers = db.crmDao().getPendingMembersSync()
            if (pendingMembers.isNotEmpty()) {
                gateway.pushMembers(businessId, pendingMembers)
                pendingMembers.forEach { db.crmDao().setMemberSyncStatus(it.id, SyncStatus.SYNCED.id) }
                pushed += pendingMembers.size
            }
            val pendingMemberTx = db.crmDao().getPendingMemberTxSync()
            if (pendingMemberTx.isNotEmpty()) {
                gateway.pushMemberTx(businessId, pendingMemberTx)
                pendingMemberTx.forEach { db.crmDao().setMemberTxSyncStatus(it.id, SyncStatus.SYNCED.id) }
                pushed += pendingMemberTx.size
            }
            val pendingVouchers = db.crmDao().getPendingVouchersSync()
            if (pendingVouchers.isNotEmpty()) {
                gateway.pushVouchers(businessId, pendingVouchers)
                pendingVouchers.forEach { db.crmDao().setVoucherSyncStatus(it.code, SyncStatus.SYNCED.id) }
                pushed += pendingVouchers.size
            }
            val pendingAudits = db.auditDao().getPendingSync()
            if (pendingAudits.isNotEmpty()) {
                gateway.pushAuditEntries(businessId, pendingAudits)
                pendingAudits.forEach { db.auditDao().setSyncStatus(it.id, SyncStatus.SYNCED.id) }
                pushed += pendingAudits.size
            }

            val snap = gateway.pullBusinessSnapshot(businessId).getOrThrow()
            var pulled = 0
            if (snap.webSlug.isNotBlank()) db.localPrefDao().set(LocalPrefEntity("v2_web_slug", snap.webSlug))

            snap.outlets.forEach { r ->
                val l = db.outletDao().getById(r.id)
                if (l == null || l.syncStatus == SyncStatus.SYNCED.id || r.updatedAt > l.updatedAt) { db.outletDao().upsert(r); pulled++ }
            }
            snap.staff.forEach { r ->
                val l = db.staffDao().getById(r.id)
                if (l == null || l.syncStatus == SyncStatus.SYNCED.id || r.updatedAt > l.updatedAt) { db.staffDao().upsert(r); pulled++ }
            }
            snap.categories.forEach { r -> db.categoryDao().upsert(r); pulled++ }
            snap.products.forEach { r ->
                val l = db.productDao().getById(r.id)
                if (l == null || l.syncStatus == SyncStatus.SYNCED.id || r.updatedAt > l.updatedAt) {
                    db.productDao().upsert(r.copy(localPhotoPath = l?.localPhotoPath)); pulled++
                }
            }
            snap.stocks.forEach { r ->
                val l = db.stockDao().getStock(r.outletId, r.productId)
                if (l == null || l.syncStatus == SyncStatus.SYNCED.id || r.updatedAt > l.updatedAt) { db.stockDao().upsertStock(r); pulled++ }
            }
            snap.bundles.forEach { b ->
                val gs = snap.bundleGroups.filter { it.bundleId == b.id }
                val gIds = gs.map { it.id }.toSet()
                val opts = snap.bundleOptions.filter { it.groupId in gIds }
                val l = db.bundleDao().getById(b.id)
                if (l == null || l.syncStatus == SyncStatus.SYNCED.id || b.updatedAt > l.updatedAt) {
                    db.bundleDao().replaceStructure(b.copy(localPhotoPath = l?.localPhotoPath), gs, opts); pulled++
                }
            }
            snap.members.forEach { r ->
                val l = db.crmDao().getMember(r.id)
                if (l == null || l.syncStatus == SyncStatus.SYNCED.id || r.updatedAt > l.updatedAt) { db.crmDao().upsertMember(r); pulled++ }
            }
            snap.vouchers.forEach { r -> db.crmDao().upsertVoucher(r); pulled++ }
            snap.recentOrders.forEach { r ->
                val l = db.orderDao().getOrder(r.id)
                if (l == null || l.syncStatus == SyncStatus.SYNCED.id || r.updatedAt > l.updatedAt) {
                    val items = snap.recentOrderItems.filter { it.orderId == r.id }
                    db.orderDao().saveOrderWithItems(r, items); pulled++
                }
            }
            snap.recentExpenses.forEach { r ->
                val l = db.expenseDao().getById(r.id)
                if (l == null || l.syncStatus == SyncStatus.SYNCED.id || r.updatedAt > l.updatedAt) {
                    db.expenseDao().upsertExpense(r.copy(localPhotoPath = l?.localPhotoPath)); pulled++
                }
            }
            db.localPrefDao().set(LocalPrefEntity("v2_last_sync", System.currentTimeMillis().toString()))
            SyncSummary(pushed, pulled)
        }
    }
}
