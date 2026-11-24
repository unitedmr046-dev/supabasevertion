package com.iyonzkasir.data.repo

import com.iyonzkasir.core.SyncStatus
import com.iyonzkasir.data.local.*
import com.iyonzkasir.data.remote.ImageHelper
import kotlinx.coroutines.flow.Flow

class CatalogRepository(
    private val categoryDao: CategoryDao,
    private val productDao: ProductDao,
    private val bundleDao: BundleDao
) {
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val products: Flow<List<ProductEntity>> = productDao.observeAll()
    val bundles: Flow<List<BundleEntity>> = bundleDao.observeAll()

    suspend fun saveCategory(cat: CategoryEntity) {
        val now = System.currentTimeMillis()
        val isNew = categoryDao.getAllSync().none { it.id == cat.id }
        val order = if (isNew && cat.sortOrder == 0) categoryDao.maxSortOrder() + 1 else cat.sortOrder
        categoryDao.upsert(cat.copy(sortOrder = order, updatedAt = now, syncStatus = SyncStatus.PENDING.id))
        if (!isNew) productDao.updateCategoryNameForCategory(cat.id, cat.name, now)
    }

    suspend fun deleteCategory(cat: CategoryEntity) {
        val now = System.currentTimeMillis()
        categoryDao.upsert(cat.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING.id))
        productDao.clearDeletedCategory(cat.id, now)
    }

    suspend fun getProduct(id: String) = productDao.getById(id)
    suspend fun getByBarcode(code: String) = productDao.getByBarcode(code.trim())

    suspend fun isBarcodeDuplicate(barcode: String, excludeProductId: String): Boolean {
        val code = barcode.trim()
        if (code.isBlank()) return false
        val found = productDao.getByBarcode(code) ?: return false
        return found.id != excludeProductId
    }

    suspend fun saveProduct(product: ProductEntity) {
        productDao.upsert(product.copy(updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING.id))
    }

    suspend fun setProductAvailable(id: String, available: Boolean) = productDao.setAvailable(id, available)

    suspend fun deleteProduct(product: ProductEntity) {
        val now = System.currentTimeMillis()
        ImageHelper.deleteLocalPhoto(product.localPhotoPath)
        productDao.upsert(product.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING.id))
    }

    suspend fun loadBundleFull(bundleId: String): Triple<BundleEntity?, List<BundleGroupEntity>, Map<String, List<BundleOptionEntity>>> {
        val b = bundleDao.getById(bundleId) ?: return Triple(null, emptyList(), emptyMap())
        val gs = bundleDao.groupsOf(bundleId)
        val optMap = gs.associate { it.id to bundleDao.optionsOf(it.id) }
        return Triple(b, gs, optMap)
    }

    suspend fun saveBundle(bundle: BundleEntity, groups: List<BundleGroupEntity>, options: List<BundleOptionEntity>) {
        val now = System.currentTimeMillis()
        val isNew = bundleDao.getById(bundle.id) == null
        val order = if (isNew && bundle.sortOrder == 0) bundleDao.maxSortOrder() + 1 else bundle.sortOrder
        val updated = bundle.copy(sortOrder = order, updatedAt = now, syncStatus = SyncStatus.PENDING.id)
        bundleDao.replaceStructure(updated, groups, options)
    }

    suspend fun deleteBundle(bundle: BundleEntity) {
        val now = System.currentTimeMillis()
        ImageHelper.deleteLocalPhoto(bundle.localPhotoPath)
        bundleDao.upsert(bundle.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING.id))
    }
}
