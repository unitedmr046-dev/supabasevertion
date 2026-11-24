package com.iyonzkasir.di

import android.content.Context
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.IyonzDatabase
import com.iyonzkasir.data.remote.*
import com.iyonzkasir.data.repo.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppContainer(private val appContext: Context) {
    val db: IyonzDatabase by lazy { IyonzDatabase.get(appContext) }
    val gateway: CloudSyncGateway by lazy { FirebaseCloudGateway() }
    val syncCoordinator: SyncCoordinator by lazy { SyncCoordinator(appContext, db, gateway) }

    val prefRepo: PrefRepository by lazy { PrefRepository(db.localPrefDao()) }
    val teamRepo: TeamRepository by lazy { TeamRepository(db.outletDao(), db.staffDao(), db.auditDao()) }
    val catalogRepo: CatalogRepository by lazy { CatalogRepository(db.categoryDao(), db.productDao(), db.bundleDao()) }
    val stockRepo: StockRepository by lazy { StockRepository(db.stockDao(), db.auditDao()) }
    val orderRepo: OrderRepository by lazy { OrderRepository(db, stockRepo) }
    val expenseRepo: ExpenseRepository by lazy { ExpenseRepository(db.expenseDao(), db.auditDao()) }
    val shiftRepo: ShiftRepository by lazy { ShiftRepository(db.shiftDao(), db.orderDao()) }
    val crmRepo: CrmRepository by lazy { CrmRepository(db.crmDao()) }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val listeners = mutableListOf<CloudListenerHandle>()

    suspend fun loadInitialPreferences() {
        SessionManager.initFromPrefs(appContext)
        AppThemeState.updateMode(prefRepo.getThemeMode())
        AppThemeState.updatePreset(prefRepo.getBrandPreset())
        IyonzDatabase.seedDefaultsIfNeeded(db)
    }

    fun triggerSyncInBackground() {
        val bizId = SessionManager.businessId
        if (bizId.isBlank() || gateway.currentOwnerUid() == null) return
        ioScope.launch { syncNow() }
    }

    suspend fun syncNow(): Result<SyncSummary> {
        val bizId = SessionManager.businessId
        if (bizId.isBlank()) return Result.failure(IllegalStateException("Business ID kosong"))
        _isSyncing.value = true
        _syncError.value = null
        val res = syncCoordinator.syncAll(bizId)
        _isSyncing.value = false
        res.onFailure { _syncError.value = it.message }
        return res
    }

    fun startRealtimeListeners() {
        stopRealtimeListeners()
        val bizId = SessionManager.businessId
        if (bizId.isBlank() || gateway.currentOwnerUid() == null) return

        listeners += gateway.listenTeamUpdates(bizId) { remoteStaff ->
            ioScope.launch {
                remoteStaff.forEach { s ->
                    val local = db.staffDao().getById(s.id)
                    if (local == null || local.syncStatus == SyncStatus.SYNCED.id || s.updatedAt > local.updatedAt) {
                        db.staffDao().upsert(s)
                    }
                    val cur = SessionManager.currentUser
                    if (cur != null && cur.id == s.id) {
                        if (!s.active) SessionManager.logoutStaff(appContext)
                        else SessionManager.loginStaff(appContext, s.toSessionUser())
                    }
                }
            }
        }

        val devOutlet = SessionManager.deviceOutletId.ifBlank { "ALL" }
        listeners += gateway.listenKitchenOrders(bizId, devOutlet) { pairs ->
            ioScope.launch {
                pairs.forEach { (remoteOrder, remoteItems) ->
                    val local = db.orderDao().getOrder(remoteOrder.id)
                    if (local == null || local.syncStatus == SyncStatus.SYNCED.id || remoteOrder.updatedAt > local.updatedAt) {
                        db.orderDao().saveOrderWithItems(remoteOrder, remoteItems)
                    }
                }
            }
        }

        ioScope.launch {
            val slug = prefRepo.getWebSlug()
            if (slug.isNotBlank()) {
                listeners += gateway.listenWebOrders(slug, bizId) { webOrders ->
                    ioScope.launch { db.webOrderDao().upsertAll(webOrders) }
                }
            }
        }
    }

    fun stopRealtimeListeners() {
        listeners.forEach { runCatching { it.remove() } }
        listeners.clear()
    }
}
