package com.iyonzkasir.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.iyonzkasir.core.newUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        OutletEntity::class, StaffEntity::class, CategoryEntity::class,
        ProductEntity::class, OutletStockEntity::class, StockMovementEntity::class,
        BundleEntity::class, BundleGroupEntity::class, BundleOptionEntity::class,
        OrderEntity::class, OrderItemEntity::class, ExpenseCategoryEntity::class,
        ExpenseEntity::class, ShiftEntity::class, MemberEntity::class,
        MemberTxEntity::class, VoucherEntity::class, WebOrderInboxEntity::class,
        AuditEntryEntity::class, LocalPrefEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class IyonzDatabase : RoomDatabase() {
    abstract fun outletDao(): OutletDao
    abstract fun staffDao(): StaffDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun stockDao(): StockDao
    abstract fun bundleDao(): BundleDao
    abstract fun orderDao(): OrderDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun shiftDao(): ShiftDao
    abstract fun crmDao(): CrmDao
    abstract fun webOrderDao(): WebOrderDao
    abstract fun auditDao(): AuditDao
    abstract fun localPrefDao(): LocalPrefDao

    companion object {
        const val DB_NAME = "iyonzkasir_v2.db"

        @Volatile
        private var INSTANCE: IyonzDatabase? = null

        fun get(context: Context): IyonzDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDb(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun closeAndReset() {
            synchronized(this) {
                try { INSTANCE?.close() } catch (_: Exception) {}
                INSTANCE = null
            }
        }

        private fun buildDb(appContext: Context): IyonzDatabase {
            return Room.databaseBuilder(appContext, IyonzDatabase::class.java, DB_NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedDefaultsIfNeeded(get(appContext))
                        }
                    }
                }).build()
        }

        suspend fun seedDefaultsIfNeeded(db: IyonzDatabase) {
            if (db.expenseDao().categoryCount() == 0) {
                val defaults = listOf(
                    Triple("Bahan Baku / Belanja Pasar", "📦", "#43A047"),
                    Triple("Gaji / Uang Harian Karyawan", "👥", "#1E88E5"),
                    Triple("Sewa Tempat / Lapak", "🏠", "#8E24AA"),
                    Triple("Listrik, Air & Gas", "💡", "#F9A825"),
                    Triple("Bensin & Transport", "🚗", "#FB8C00"),
                    Triple("Kemasan / Plastik / Cup", "🥤", "#00897B"),
                    Triple("Alat & Perbaikan", "🔧", "#546E7A"),
                    Triple("Lain-lain", "📝", "#6D4C41")
                ).mapIndexed { idx, (name, icon, color) ->
                    ExpenseCategoryEntity(
                        id = newUuid(), name = name, icon = icon,
                        colorHex = color, sortOrder = idx, isDefault = true
                    )
                }
                db.expenseDao().upsertCategories(defaults)
            }
        }
    }
}
