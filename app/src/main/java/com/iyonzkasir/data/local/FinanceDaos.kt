package com.iyonzkasir.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense_categories ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(): Flow<List<ExpenseCategoryEntity>>
    @Query("SELECT * FROM expense_categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getCategoriesSync(): List<ExpenseCategoryEntity>
    @Query("SELECT COUNT(*) FROM expense_categories")
    suspend fun categoryCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(cat: ExpenseCategoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(list: List<ExpenseCategoryEntity>)
    @Delete
    suspend fun deleteCategory(cat: ExpenseCategoryEntity)

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND (:outletId = 'ALL' OR outletId = :outletId) ORDER BY timestamp DESC")
    fun observeExpenses(outletId: String): Flow<List<ExpenseEntity>>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE deletedAt IS NULL AND timestamp >= :start AND timestamp <= :end AND (:outletId = 'ALL' OR outletId = :outletId)")
    fun observeSumInRange(outletId: String, start: Long, end: Long): Flow<Long>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE deletedAt IS NULL AND timestamp >= :start AND timestamp <= :end AND (:outletId = 'ALL' OR outletId = :outletId)")
    suspend fun sumInRange(outletId: String, start: Long, end: Long): Long
    @Query("""
        SELECT categoryName AS categoryName, COUNT(*) AS txCount, COALESCE(SUM(amount), 0) AS totalAmount
        FROM expenses
        WHERE deletedAt IS NULL AND timestamp >= :start AND timestamp <= :end
          AND (:outletId = 'ALL' OR outletId = :outletId)
        GROUP BY categoryName ORDER BY totalAmount DESC
    """)
    suspend fun categoryStatsInRange(outletId: String, start: Long, end: Long): List<ExpenseCategoryStatRow>
    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExpenseEntity?
    @Query("SELECT * FROM expenses WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<ExpenseEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(e: ExpenseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpenses(list: List<ExpenseEntity>)
    @Query("UPDATE expenses SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE status = 'OPEN' AND outletId = :outletId ORDER BY openedAt DESC LIMIT 1")
    fun observeActiveInOutlet(outletId: String): Flow<ShiftEntity?>
    @Query("SELECT * FROM shifts WHERE status = 'OPEN' AND outletId = :outletId ORDER BY openedAt DESC LIMIT 1")
    suspend fun getActiveInOutlet(outletId: String): ShiftEntity?
    @Query("SELECT * FROM shifts WHERE (:outletId = 'ALL' OR outletId = :outletId) ORDER BY openedAt DESC LIMIT 100")
    fun observeAll(outletId: String): Flow<List<ShiftEntity>>
    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ShiftEntity?
    @Query("SELECT * FROM shifts WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<ShiftEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(shift: ShiftEntity)
    @Query("UPDATE shifts SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)
}

@Dao
interface CrmDao {
    @Query("SELECT * FROM members WHERE deletedAt IS NULL ORDER BY name ASC")
    fun observeMembers(): Flow<List<MemberEntity>>
    @Query("SELECT * FROM members WHERE deletedAt IS NULL AND debt > 0 ORDER BY debt DESC")
    fun observeDebtors(): Flow<List<MemberEntity>>
    @Query("SELECT COALESCE(SUM(debt), 0) FROM members WHERE deletedAt IS NULL AND debt > 0")
    fun observeTotalDebt(): Flow<Long>
    @Query("SELECT * FROM members WHERE id = :id LIMIT 1")
    suspend fun getMember(id: String): MemberEntity?
    @Query("SELECT * FROM members WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingMembersSync(): List<MemberEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(m: MemberEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(list: List<MemberEntity>)
    @Query("UPDATE members SET syncStatus = :status WHERE id = :id")
    suspend fun setMemberSyncStatus(id: String, status: String)

    @Query("SELECT * FROM member_transactions WHERE memberId = :memberId ORDER BY timestamp DESC LIMIT 100")
    fun observeMemberTx(memberId: String): Flow<List<MemberTxEntity>>
    @Query("SELECT * FROM member_transactions WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingMemberTxSync(): List<MemberTxEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemberTx(tx: MemberTxEntity)
    @Query("UPDATE member_transactions SET syncStatus = :status WHERE id = :id")
    suspend fun setMemberTxSyncStatus(id: String, status: String)

    @Query("SELECT * FROM vouchers WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeVouchers(): Flow<List<VoucherEntity>>
    @Query("SELECT * FROM vouchers WHERE code = :code AND deletedAt IS NULL LIMIT 1")
    suspend fun getVoucher(code: String): VoucherEntity?
    @Query("SELECT * FROM vouchers WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingVouchersSync(): List<VoucherEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoucher(v: VoucherEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVouchers(list: List<VoucherEntity>)
    @Query("UPDATE vouchers SET syncStatus = :status WHERE code = :code")
    suspend fun setVoucherSyncStatus(code: String, status: String)
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_entries WHERE (:outletId = 'ALL' OR outletId = :outletId) ORDER BY timestamp DESC LIMIT 300")
    fun observeRecent(outletId: String): Flow<List<AuditEntryEntity>>
    @Query("SELECT * FROM audit_entries WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<AuditEntryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditEntryEntity)
    @Query("UPDATE audit_entries SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)
}

@Dao
interface LocalPrefDao {
    @Query("SELECT * FROM local_prefs WHERE key = :key LIMIT 1")
    suspend fun get(key: String): LocalPrefEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(pref: LocalPrefEntity)
}
