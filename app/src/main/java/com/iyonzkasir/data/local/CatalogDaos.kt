package com.iyonzkasir.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OutletDao {
    @Query("SELECT * FROM outlets WHERE active = 1 ORDER BY isMain DESC, name ASC")
    fun observeActive(): Flow<List<OutletEntity>>
    @Query("SELECT * FROM outlets ORDER BY isMain DESC, name ASC")
    fun observeAll(): Flow<List<OutletEntity>>
    @Query("SELECT * FROM outlets WHERE active = 1 ORDER BY isMain DESC, name ASC")
    suspend fun getActiveSync(): List<OutletEntity>
    @Query("SELECT * FROM outlets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): OutletEntity?
    @Query("SELECT * FROM outlets WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<OutletEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(outlet: OutletEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(outlets: List<OutletEntity>)
    @Query("UPDATE outlets SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)
}

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff ORDER BY isOwner DESC, name ASC")
    fun observeAll(): Flow<List<StaffEntity>>
    @Query("SELECT * FROM staff WHERE active = 1 ORDER BY isOwner DESC, name ASC")
    fun observeActive(): Flow<List<StaffEntity>>
    @Query("SELECT * FROM staff WHERE active = 1 AND (outletId = 'ALL' OR outletId = :outletId) ORDER BY isOwner DESC, name ASC")
    fun observeForOutlet(outletId: String): Flow<List<StaffEntity>>
    @Query("SELECT * FROM staff WHERE active = 1 AND isOwner = 0")
    suspend fun getActiveEmployeesSync(): List<StaffEntity>
    @Query("SELECT * FROM staff WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StaffEntity?
    @Query("SELECT * FROM staff WHERE isOwner = 1 LIMIT 1")
    suspend fun getOwner(): StaffEntity?
    @Query("SELECT * FROM staff WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<StaffEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(staff: StaffEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<StaffEntity>)
    @Query("UPDATE staff SET lastLoginAt = :ts WHERE id = :id")
    suspend fun updateLastLogin(id: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE staff SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllSync(): List<CategoryEntity>
    @Query("SELECT * FROM categories WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<CategoryEntity>
    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories WHERE deletedAt IS NULL")
    suspend fun maxSortOrder(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cat: CategoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<CategoryEntity>)
    @Query("UPDATE categories SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE deletedAt IS NULL ORDER BY categoryName ASC, name ASC")
    fun observeAll(): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE deletedAt IS NULL ORDER BY categoryName ASC, name ASC")
    suspend fun getAllSync(): List<ProductEntity>
    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProductEntity?
    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode != '' AND deletedAt IS NULL LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductEntity?
    @Query("SELECT * FROM products WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<ProductEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<ProductEntity>)
    @Query("UPDATE products SET available = :available, updatedAt = :ts, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun setAvailable(id: String, available: Boolean, ts: Long = System.currentTimeMillis())
    @Query("UPDATE products SET categoryName = :newName, updatedAt = :ts, syncStatus = 'PENDING' WHERE categoryId = :catId")
    suspend fun updateCategoryNameForCategory(catId: String, newName: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE products SET categoryId = '', categoryName = 'Umum', updatedAt = :ts, syncStatus = 'PENDING' WHERE categoryId = :catId")
    suspend fun clearDeletedCategory(catId: String, ts: Long = System.currentTimeMillis())
    @Query("UPDATE products SET syncStatus = :status, remotePhotoUrl = :remoteUrl, photoHash = :hash WHERE id = :id")
    suspend fun markSyncedWithPhoto(id: String, status: String, remoteUrl: String?, hash: String)
    @Query("UPDATE products SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)
}

@Dao
interface StockDao {
    @Query("SELECT * FROM outlet_stocks WHERE (:outletId = 'ALL' OR outletId = :outletId)")
    fun observeOutletStocks(outletId: String): Flow<List<OutletStockEntity>>
    @Query("SELECT * FROM outlet_stocks WHERE outletId = :outletId AND productId = :productId LIMIT 1")
    suspend fun getStock(outletId: String, productId: String): OutletStockEntity?
    @Query("SELECT * FROM outlet_stocks WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingStocksSync(): List<OutletStockEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStock(stock: OutletStockEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStocks(list: List<OutletStockEntity>)
    @Query("UPDATE outlet_stocks SET syncStatus = :status WHERE id = :id")
    suspend fun setStockSyncStatus(id: String, status: String)
    @Query("SELECT * FROM stock_movements WHERE (:outletId = 'ALL' OR outletId = :outletId) ORDER BY timestamp DESC LIMIT 400")
    fun observeMovements(outletId: String): Flow<List<StockMovementEntity>>
    @Query("SELECT * FROM stock_movements WHERE productId = :productId AND (:outletId = 'ALL' OR outletId = :outletId) ORDER BY timestamp DESC LIMIT 200")
    fun observeMovementsForProduct(productId: String, outletId: String): Flow<List<StockMovementEntity>>
    @Query("SELECT * FROM stock_movements WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingMovementsSync(): List<StockMovementEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(move: StockMovementEntity)
    @Query("UPDATE stock_movements SET syncStatus = :status WHERE id = :id")
    suspend fun setMovementSyncStatus(id: String, status: String)
}

@Dao
interface BundleDao {
    @Query("SELECT * FROM bundles WHERE deletedAt IS NULL ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<BundleEntity>>
    @Query("SELECT * FROM bundles WHERE deletedAt IS NULL ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllSync(): List<BundleEntity>
    @Query("SELECT * FROM bundles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BundleEntity?
    @Query("SELECT * FROM bundles WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<BundleEntity>
    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM bundles WHERE deletedAt IS NULL")
    suspend fun maxSortOrder(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bundle: BundleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<BundleEntity>)
    @Query("SELECT * FROM bundle_groups WHERE bundleId = :bundleId ORDER BY sortOrder ASC")
    suspend fun groupsOf(bundleId: String): List<BundleGroupEntity>
    @Query("SELECT * FROM bundle_options WHERE groupId = :groupId ORDER BY sortOrder ASC")
    suspend fun optionsOf(groupId: String): List<BundleOptionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroups(groups: List<BundleGroupEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOptions(options: List<BundleOptionEntity>)
    @Query("DELETE FROM bundle_options WHERE groupId IN (SELECT id FROM bundle_groups WHERE bundleId = :bundleId)")
    suspend fun deleteAllOptions(bundleId: String)
    @Query("DELETE FROM bundle_groups WHERE bundleId = :bundleId")
    suspend fun deleteGroups(bundleId: String)
    @Transaction
    suspend fun replaceStructure(
        bundle: BundleEntity,
        groups: List<BundleGroupEntity>,
        options: List<BundleOptionEntity>
    ) {
        upsert(bundle)
        deleteAllOptions(bundle.id)
        deleteGroups(bundle.id)
        if (groups.isNotEmpty()) upsertGroups(groups)
        if (options.isNotEmpty()) upsertOptions(options)
    }
    @Query("UPDATE bundles SET syncStatus = :status, remotePhotoUrl = :remoteUrl, photoHash = :hash WHERE id = :id")
    suspend fun markSyncedWithPhoto(id: String, status: String, remoteUrl: String?, hash: String)
    @Query("UPDATE bundles SET syncStatus = :status WHERE id = :id")
    suspend fun setSyncStatus(id: String, status: String)
}
