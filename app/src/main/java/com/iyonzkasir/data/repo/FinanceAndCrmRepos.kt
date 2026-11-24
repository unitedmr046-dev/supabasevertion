package com.iyonzkasir.data.repo

import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import com.iyonzkasir.data.remote.ImageHelper
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val auditDao: AuditDao
) {
    val categories: Flow<List<ExpenseCategoryEntity>> = expenseDao.observeCategories()
    fun observeExpenses(outletId: String) = expenseDao.observeExpenses(outletId)
    fun observeSumInRange(outletId: String, start: Long, end: Long) = expenseDao.observeSumInRange(outletId, start, end)
    suspend fun sumInRange(outletId: String, start: Long, end: Long) = expenseDao.sumInRange(outletId, start, end)
    suspend fun categoryStats(outletId: String, start: Long, end: Long) = expenseDao.categoryStatsInRange(outletId, start, end)
    suspend fun saveCategory(cat: ExpenseCategoryEntity) = expenseDao.upsertCategory(cat)
    suspend fun deleteCategory(cat: ExpenseCategoryEntity) = expenseDao.deleteCategory(cat)

    suspend fun saveExpense(expense: ExpenseEntity) =
        expenseDao.upsertExpense(expense.copy(updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING.id))

    suspend fun deleteExpense(expense: ExpenseEntity) {
        val now = System.currentTimeMillis()
        ImageHelper.deleteLocalPhoto(expense.localPhotoPath)
        expenseDao.upsertExpense(expense.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING.id))
        val u = SessionManager.currentUser
        auditDao.insert(AuditEntryEntity(
            businessId = expense.businessId, outletId = expense.outletId,
            staffId = u?.id ?: "", staffName = u?.name ?: "",
            action = "DELETE_EXPENSE", targetId = expense.id, detail = "${expense.categoryName}: ${expense.amount.toRupiah()}"
        ))
    }
}

class ShiftRepository(
    private val shiftDao: ShiftDao,
    private val orderDao: OrderDao
) {
    fun observeActive(outletId: String) = shiftDao.observeActiveInOutlet(outletId)
    fun observeAll(outletId: String) = shiftDao.observeAll(outletId)
    suspend fun getActive(outletId: String) = shiftDao.getActiveInOutlet(outletId)

    suspend fun openShift(businessId: String, outletId: String, outletName: String, openingCash: Long): ShiftEntity {
        val u = SessionManager.currentUser
        val shift = ShiftEntity(
            businessId = businessId, outletId = outletId, outletName = outletName,
            staffId = u?.id ?: "", staffName = u?.name ?: "Owner",
            openingCash = openingCash, status = "OPEN", syncStatus = SyncStatus.PENDING.id
        )
        shiftDao.upsert(shift)
        return shift
    }

    suspend fun closeShift(shiftId: String, actualCash: Long, note: String): ShiftEntity? {
        val shift = shiftDao.getById(shiftId) ?: return null
        val txCount = orderDao.countByShift(shiftId)
        val total = orderDao.sumTotalByShift(shiftId)
        val cash = orderDao.sumCashByShift(shiftId)
        val kasbon = orderDao.sumKasbonByShift(shiftId)
        val nonCash = (total - cash - kasbon).coerceAtLeast(0L)
        val disc = orderDao.sumDiscountByShift(shiftId)
        val tax = orderDao.sumTaxByShift(shiftId)
        val closed = shift.copy(
            closedAt = System.currentTimeMillis(), txCount = txCount, totalSales = total,
            cashSales = cash, nonCashSales = nonCash, kasbonSales = kasbon,
            totalDiscount = disc, totalTax = tax, actualCash = actualCash,
            cashDiff = actualCash - (shift.openingCash + cash), note = note, status = "CLOSED",
            updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING.id
        )
        shiftDao.upsert(closed)
        return closed
    }
}

class CrmRepository(private val crmDao: CrmDao) {
    val members: Flow<List<MemberEntity>> = crmDao.observeMembers()
    val debtors: Flow<List<MemberEntity>> = crmDao.observeDebtors()
    val totalDebt: Flow<Long> = crmDao.observeTotalDebt()
    val vouchers: Flow<List<VoucherEntity>> = crmDao.observeVouchers()

    fun observeMemberTx(memberId: String) = crmDao.observeMemberTx(memberId)
    suspend fun getMember(id: String) = crmDao.getMember(id)
    suspend fun getVoucher(code: String) = crmDao.getVoucher(code.trim().uppercase())
    suspend fun saveMember(m: MemberEntity) =
        crmDao.upsertMember(m.copy(updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING.id))

    suspend fun deleteMember(m: MemberEntity) {
        val now = System.currentTimeMillis()
        crmDao.upsertMember(m.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING.id))
    }

    suspend fun payDebt(memberId: String, amount: Long, outletId: String, note: String) {
        val m = crmDao.getMember(memberId) ?: return
        if (amount <= 0L || m.debt <= 0L) return
        val paid = amount.coerceAtMost(m.debt)
        val newDebt = m.debt - paid
        val u = SessionManager.currentUser
        crmDao.upsertMember(m.copy(debt = newDebt, updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING.id))
        crmDao.insertMemberTx(MemberTxEntity(
            businessId = m.businessId, outletId = outletId, memberId = m.id,
            txType = "KASBON_PAY", debtDelta = -paid, pointsAfter = m.points, debtAfter = newDebt,
            note = note.ifBlank { "Bayar kasbon" }, staffName = u?.name ?: ""
        ))
    }

    suspend fun saveVoucher(v: VoucherEntity) =
        crmDao.upsertVoucher(v.copy(code = v.code.trim().uppercase(), updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING.id))

    suspend fun deleteVoucher(v: VoucherEntity) {
        val now = System.currentTimeMillis()
        crmDao.upsertVoucher(v.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING.id))
    }
}
