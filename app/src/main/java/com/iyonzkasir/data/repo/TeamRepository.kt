package com.iyonzkasir.data.repo

import android.content.Context
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import kotlinx.coroutines.flow.Flow

sealed interface PinVerifyResult {
    data class Success(val staff: StaffEntity) : PinVerifyResult
    data class Wrong(val attempts: Int) : PinVerifyResult
    data class Locked(val secondsLeft: Int) : PinVerifyResult
}

class TeamRepository(
    private val outletDao: OutletDao,
    private val staffDao: StaffDao,
    private val auditDao: AuditDao
) {
    val activeOutlets: Flow<List<OutletEntity>> = outletDao.observeActive()
    val allOutlets: Flow<List<OutletEntity>> = outletDao.observeAll()
    val allStaff: Flow<List<StaffEntity>> = staffDao.observeAll()
    val activeStaff: Flow<List<StaffEntity>> = staffDao.observeActive()

    fun observeStaffForOutlet(outletId: String): Flow<List<StaffEntity>> =
        if (outletId.isBlank() || outletId == "ALL") staffDao.observeActive()
        else staffDao.observeForOutlet(outletId)

    fun observeAudit(outletId: String): Flow<List<AuditEntryEntity>> = auditDao.observeRecent(outletId)

    suspend fun getOutlet(id: String) = outletDao.getById(id)
    suspend fun getActiveOutletsSync() = outletDao.getActiveSync()
    suspend fun getOwner() = staffDao.getOwner()
    suspend fun getStaff(id: String) = staffDao.getById(id)
    suspend fun hasActiveEmployees(): Boolean = staffDao.getActiveEmployeesSync().isNotEmpty()

    suspend fun createInitialSetup(
        businessId: String, ownerName: String, businessName: String
    ): Pair<OutletEntity, StaffEntity> {
        val outlet = OutletEntity(
            id = newUuid(), businessId = businessId,
            name = businessName.trim().ifBlank { "Outlet Utama" },
            isMain = true, active = true, syncStatus = SyncStatus.PENDING.id
        )
        val owner = StaffEntity(
            id = businessId, businessId = businessId,
            name = ownerName.trim().ifBlank { "Owner" },
            jobTitle = "Pemilik", isOwner = true, outletId = "ALL",
            accessKeys = AppAccess.encode(AppAccess.entries.toSet()),
            active = true, syncStatus = SyncStatus.PENDING.id
        )
        outletDao.upsert(outlet)
        staffDao.upsert(owner)
        return outlet to owner
    }

    suspend fun saveOutlet(outlet: OutletEntity) =
        outletDao.upsert(outlet.copy(updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING.id))

    suspend fun saveStaff(staff: StaffEntity) =
        staffDao.upsert(staff.copy(updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING.id))

    suspend fun setOwnerPin(pin: String) {
        val owner = staffDao.getOwner() ?: return
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash(pin, salt)
        staffDao.upsert(owner.copy(pinSalt = salt, pinHash = hash, updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING.id))
    }

    suspend fun verifyPin(context: Context, staff: StaffEntity, pin: String): PinVerifyResult {
        val lock = PinLockGuard.remainingLockSeconds(context, staff.id)
        if (lock > 0) return PinVerifyResult.Locked(lock)
        return if (PinHasher.verify(pin, staff.pinSalt, staff.pinHash)) {
            PinLockGuard.reset(context, staff.id)
            staffDao.updateLastLogin(staff.id)
            PinVerifyResult.Success(staff)
        } else {
            val fails = PinLockGuard.recordFailure(context, staff.id)
            val newLock = PinLockGuard.remainingLockSeconds(context, staff.id)
            if (newLock > 0) PinVerifyResult.Locked(newLock) else PinVerifyResult.Wrong(fails)
        }
    }

    suspend fun logAudit(outletId: String, action: String, targetId: String = "", detail: String = "") {
        val u = SessionManager.currentUser
        auditDao.insert(AuditEntryEntity(
            businessId = SessionManager.businessId, outletId = outletId,
            staffId = u?.id ?: "", staffName = u?.name ?: "",
            action = action, targetId = targetId, detail = detail, syncStatus = SyncStatus.PENDING.id
        ))
    }
}
