package com.iyonzkasir.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.iyonzkasir.core.SyncStatus
import com.iyonzkasir.data.local.*
import kotlinx.coroutines.tasks.await

internal object FbRefs {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun biz(id: String) = db.collection("businesses").document(id)
    fun store(slug: String) = db.collection("stores").document(slug)
}

internal class FirebaseCatalogHelper {
    private val db get() = FbRefs.db

    suspend fun registerOwner(
        email: String, password: String, ownerName: String, businessName: String
    ): Result<CloudOwnerInfo> = runCatching {
        val res = FbRefs.auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val u = res.user ?: error("Akun gagal dibuat")
        FbRefs.biz(u.uid).set(mapOf(
            "ownerUid" to u.uid,
            "ownerEmail" to email.trim(),
            "ownerName" to ownerName.trim(),
            "businessName" to businessName.trim(),
            "createdAt" to System.currentTimeMillis()
        ), SetOptions.merge()).await()
        CloudOwnerInfo(u.uid, u.email ?: email.trim(), ownerName.trim())
    }

    suspend fun loginOwner(email: String, password: String): Result<CloudOwnerInfo> = runCatching {
        val res = FbRefs.auth.signInWithEmailAndPassword(email.trim(), password).await()
        val u = res.user ?: error("User tidak ditemukan")
        CloudOwnerInfo(u.uid, u.email ?: email.trim(), u.displayName ?: "")
    }

    suspend fun pullSnapshot(businessId: String, sinceDays: Int): Result<CloudBundleData> = runCatching {
        val bizRef = FbRefs.biz(businessId)
        val bizDoc = bizRef.get().await()
        val webSlug = bizDoc.getString("webSlug") ?: ""

        val outlets = bizRef.collection("outlets").get().await().documents.mapNotNull { d ->
            OutletEntity(
                id = d.id, businessId = businessId,
                name = d.getString("name") ?: return@mapNotNull null,
                address = d.getString("address") ?: "",
                phoneWa = d.getString("phoneWa") ?: "",
                footerReceipt = d.getString("footerReceipt") ?: "Terima kasih",
                isMain = d.getBoolean("isMain") ?: false,
                active = d.getBoolean("active") ?: true,
                updatedAt = d.getLong("updatedAt") ?: 0L,
                syncStatus = SyncStatus.SYNCED.id
            )
        }
        val staff = bizRef.collection("staff").get().await().documents.mapNotNull { d ->
            StaffEntity(
                id = d.id, businessId = businessId,
                name = d.getString("name") ?: return@mapNotNull null,
                jobTitle = d.getString("jobTitle") ?: "Kasir",
                pinHash = d.getString("pinHash") ?: "",
                pinSalt = d.getString("pinSalt") ?: "",
                isOwner = d.getBoolean("isOwner") ?: false,
                outletId = d.getString("outletId") ?: "ALL",
                accessKeys = d.getString("accessKeys") ?: "",
                active = d.getBoolean("active") ?: true,
                createdAt = d.getLong("createdAt") ?: 0L,
                updatedAt = d.getLong("updatedAt") ?: 0L,
                syncStatus = SyncStatus.SYNCED.id
            )
        }
        val categories = bizRef.collection("categories").get().await().documents.mapNotNull { d ->
            CategoryEntity(
                id = d.id, businessId = businessId,
                name = d.getString("name") ?: return@mapNotNull null,
                colorHex = d.getString("colorHex") ?: "#FF6B2C",
                sortOrder = (d.getLong("sortOrder") ?: 0L).toInt(),
                active = d.getBoolean("active") ?: true,
                updatedAt = d.getLong("updatedAt") ?: 0L,
                deletedAt = d.getLong("deletedAt"),
                syncStatus = SyncStatus.SYNCED.id
            )
        }
        val products = bizRef.collection("products").get().await().documents.mapNotNull { d ->
            ProductEntity(
                id = d.id, businessId = businessId,
                name = d.getString("name") ?: return@mapNotNull null,
                description = d.getString("description") ?: "",
                categoryId = d.getString("categoryId") ?: "",
                categoryName = d.getString("categoryName") ?: "Umum",
                sellPrice = d.getLong("sellPrice") ?: 0L,
                costPrice = d.getLong("costPrice") ?: 0L,
                barcode = d.getString("barcode") ?: "",
                remotePhotoUrl = d.getString("remotePhotoUrl"),
                photoHash = d.getString("photoHash") ?: "",
                available = d.getBoolean("available") ?: true,
                trackStock = d.getBoolean("trackStock") ?: false,
                updatedAt = d.getLong("updatedAt") ?: 0L,
                deletedAt = d.getLong("deletedAt"),
                syncStatus = SyncStatus.SYNCED.id
            )
        }
        val stocks = bizRef.collection("outlet_stocks").get().await().documents.mapNotNull { d ->
            OutletStockEntity(
                id = d.id, businessId = businessId,
                outletId = d.getString("outletId") ?: return@mapNotNull null,
                productId = d.getString("productId") ?: return@mapNotNull null,
                available = d.getBoolean("available") ?: true,
                stockQty = (d.getLong("stockQty") ?: 0L).toInt(),
                minStock = (d.getLong("minStock") ?: 5L).toInt(),
                updatedAt = d.getLong("updatedAt") ?: 0L,
                syncStatus = SyncStatus.SYNCED.id
            )
        }
        val bundles = mutableListOf<BundleEntity>()
        val groups = mutableListOf<BundleGroupEntity>()
        val options = mutableListOf<BundleOptionEntity>()
        bizRef.collection("bundles").get().await().documents.forEach { d ->
            val name = d.getString("name") ?: return@forEach
            bundles += BundleEntity(
                id = d.id, businessId = businessId, name = name,
                description = d.getString("description") ?: "",
                bundlePrice = d.getLong("bundlePrice") ?: 0L,
                originalPrice = d.getLong("originalPrice") ?: 0L,
                bundleType = d.getString("bundleType") ?: "FIXED",
                remotePhotoUrl = d.getString("remotePhotoUrl"),
                photoHash = d.getString("photoHash") ?: "",
                available = d.getBoolean("available") ?: true,
                sortOrder = (d.getLong("sortOrder") ?: 0L).toInt(),
                updatedAt = d.getLong("updatedAt") ?: 0L,
                deletedAt = d.getLong("deletedAt"),
                syncStatus = SyncStatus.SYNCED.id
            )
            @Suppress("UNCHECKED_CAST")
            val gList = d.get("groups") as? List<Map<String, Any?>> ?: emptyList()
            gList.forEachIndexed { gIdx, gm ->
                val gid = gm["id"] as? String ?: return@forEachIndexed
                groups += BundleGroupEntity(
                    id = gid, bundleId = d.id, name = gm["name"] as? String ?: "",
                    minPick = (gm["minPick"] as? Number)?.toInt() ?: 1,
                    maxPick = (gm["maxPick"] as? Number)?.toInt() ?: 1,
                    required = gm["required"] as? Boolean ?: true, sortOrder = gIdx
                )
                @Suppress("UNCHECKED_CAST")
                val oList = gm["options"] as? List<Map<String, Any?>> ?: emptyList()
                oList.forEachIndexed { oIdx, om ->
                    options += BundleOptionEntity(
                        id = om["id"] as? String ?: return@forEachIndexed, groupId = gid,
                        productId = om["productId"] as? String ?: "",
                        productName = om["productName"] as? String ?: "",
                        qty = (om["qty"] as? Number)?.toInt() ?: 1,
                        extraPrice = (om["extraPrice"] as? Number)?.toLong() ?: 0L,
                        isDefaultPick = om["isDefaultPick"] as? Boolean ?: false, sortOrder = oIdx
                    )
                }
            }
        }
        val members = bizRef.collection("members").get().await().documents.mapNotNull { d ->
            MemberEntity(
                id = d.id, businessId = businessId,
                name = d.getString("name") ?: return@mapNotNull null,
                phone = d.getString("phone") ?: "",
                address = d.getString("address") ?: "",
                note = d.getString("note") ?: "",
                points = (d.getLong("points") ?: 0L).toInt(),
                totalSpent = d.getLong("totalSpent") ?: 0L,
                debt = d.getLong("debt") ?: 0L,
                active = d.getBoolean("active") ?: true,
                updatedAt = d.getLong("updatedAt") ?: 0L,
                deletedAt = d.getLong("deletedAt"),
                syncStatus = SyncStatus.SYNCED.id
            )
        }
        val vouchers = bizRef.collection("vouchers").get().await().documents.mapNotNull { d ->
            VoucherEntity(
                code = d.id, businessId = businessId,
                name = d.getString("name") ?: "",
                discountType = d.getString("discountType") ?: "NOMINAL",
                value = d.getLong("value") ?: 0L,
                minSpend = d.getLong("minSpend") ?: 0L,
                maxDiscount = d.getLong("maxDiscount") ?: 0L,
                quota = (d.getLong("quota") ?: 0L).toInt(),
                usedCount = (d.getLong("usedCount") ?: 0L).toInt(),
                startAt = d.getLong("startAt") ?: 0L,
                endAt = d.getLong("endAt") ?: 0L,
                active = d.getBoolean("active") ?: true,
                updatedAt = d.getLong("updatedAt") ?: 0L,
                deletedAt = d.getLong("deletedAt"),
                syncStatus = SyncStatus.SYNCED.id
            )
        }
        val sinceMs = System.currentTimeMillis() - sinceDays * 86_400_000L
        val orders = mutableListOf<OrderEntity>()
        val orderItems = mutableListOf<OrderItemEntity>()
        bizRef.collection("orders").whereGreaterThanOrEqualTo("timestamp", sinceMs)
            .get().await().documents.forEach { d ->
                orders += mapToOrderEntity(d.id, businessId, d.data ?: return@forEach)
                @Suppress("UNCHECKED_CAST")
                val rawItems = d.get("items") as? List<Map<String, Any?>> ?: emptyList()
                rawItems.forEach { im ->
                    orderItems += mapToOrderItemEntity(d.id, im)
                }
            }
        val expenses = bizRef.collection("expenses").whereGreaterThanOrEqualTo("timestamp", sinceMs)
            .get().await().documents.mapNotNull { d ->
                ExpenseEntity(
                    id = d.id, businessId = businessId,
                    outletId = d.getString("outletId") ?: "",
                    outletName = d.getString("outletName") ?: "",
                    timestamp = d.getLong("timestamp") ?: 0L,
                    categoryId = d.getString("categoryId") ?: "",
                    categoryName = d.getString("categoryName") ?: "",
                    amount = d.getLong("amount") ?: 0L,
                    note = d.getString("note") ?: "",
                    remotePhotoUrl = d.getString("remotePhotoUrl"),
                    staffId = d.getString("staffId") ?: "",
                    staffName = d.getString("staffName") ?: "",
                    updatedAt = d.getLong("updatedAt") ?: 0L,
                    deletedAt = d.getLong("deletedAt"),
                    syncStatus = SyncStatus.SYNCED.id
                )
            }

        CloudBundleData(
            outlets = outlets, staff = staff, categories = categories, products = products,
            stocks = stocks, bundles = bundles, bundleGroups = groups, bundleOptions = options,
            members = members, vouchers = vouchers, recentOrders = orders,
            recentOrderItems = orderItems, recentExpenses = expenses, webSlug = webSlug
        )
    }

    suspend fun publishWebStorefront(
        businessId: String, slug: String, storeName: String, address: String,
        phoneWa: String, themeColorHex: String, taxPercent: Int,
        products: List<ProductEntity>, bundles: List<BundleEntity>,
        groupsMap: Map<String, List<BundleGroupEntity>>, optionsMap: Map<String, List<BundleOptionEntity>>
    ): Result<Unit> = runCatching {
        val storeRef = FbRefs.store(slug)
        val existing = storeRef.get().await()
        if (existing.exists()) {
            val owner = existing.getString("ownerUid") ?: ""
            if (owner.isNotBlank() && owner != businessId) {
                error("Link '$slug' sudah dipakai toko lain")
            }
        }
        storeRef.set(mapOf("ownerUid" to businessId, "updatedAt" to System.currentTimeMillis()), SetOptions.merge()).await()
        FbRefs.biz(businessId).set(mapOf("webSlug" to slug), SetOptions.merge()).await()

        storeRef.collection("info").document("config").set(mapOf(
            "storeId" to slug, "ownerUid" to businessId, "nama" to storeName,
            "alamat" to address, "teleponWA" to phoneWa, "temaWarna" to themeColorHex,
            "pajakPersen" to taxPercent, "active" to true, "updatedAt" to System.currentTimeMillis()
        )).await()

        // Sinkron menu & hapus menu web yang sudah dihapus di HP
        val activeProdIds = products.filter { it.deletedAt == null }.map { it.id }.toSet()
        val remoteMenus = storeRef.collection("menu").get().await()
        remoteMenus.documents.forEach { if (it.id !in activeProdIds) it.reference.delete().await() }

        products.filter { it.deletedAt == null }.forEachIndexed { idx, p ->
            storeRef.collection("menu").document(p.id).set(mapOf(
                "id" to p.id, "nama" to p.name, "deskripsi" to p.description,
                "kategori" to p.categoryName, "harga" to p.sellPrice,
                "fotoUrl" to (p.remotePhotoUrl ?: ""), "tersedia" to p.available,
                "trackStok" to p.trackStock, "urutan" to idx, "updatedAt" to p.updatedAt
            )).await()
        }

        val activeBundleIds = bundles.filter { it.deletedAt == null }.map { it.id }.toSet()
        val remoteBundles = storeRef.collection("bundles").get().await()
        remoteBundles.documents.forEach { if (it.id !in activeBundleIds) it.reference.delete().await() }

        bundles.filter { it.deletedAt == null }.forEachIndexed { idx, b ->
            val gs = groupsMap[b.id].orEmpty().map { g ->
                mapOf(
                    "nama" to g.name, "minPilih" to g.minPick, "maxPilih" to g.maxPick, "wajib" to g.required,
                    "items" to optionsMap[g.id].orEmpty().map { o ->
                        mapOf("menuId" to o.productId, "nama" to o.productName, "qty" to o.qty, "hargaExtra" to o.extraPrice, "isDefaultPick" to o.isDefaultPick)
                    }
                )
            }
            storeRef.collection("bundles").document(b.id).set(mapOf(
                "id" to b.id, "nama" to b.name, "deskripsi" to b.description,
                "hargaBundle" to b.bundlePrice, "hargaAsli" to b.originalPrice,
                "tipe" to b.bundleType, "fotoUrl" to (b.remotePhotoUrl ?: ""),
                "tersedia" to b.available, "urutan" to idx, "groups" to gs, "updatedAt" to b.updatedAt
            )).await()
        }
    }
}
