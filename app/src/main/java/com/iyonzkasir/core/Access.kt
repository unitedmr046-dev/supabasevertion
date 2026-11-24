package com.iyonzkasir.core

// ═══════ 100% CUSTOM ACCESS CHECKLIST ═══════
enum class AccessCategory(val label: String) {
    MENU("Halaman / Menu yang Tampil"),
    ACTION("Izin Tindakan Khusus")
}

enum class AppAccess(
    val key: String,
    val label: String,
    val description: String,
    val category: AccessCategory
) {
    // ── Halaman / Menu ──
    HOME("menu_home", "Beranda", "Lihat ringkasan jualan & shortcut", AccessCategory.MENU),
    POS("menu_pos", "Kasir / Jualan", "Buat pesanan & terima pembayaran", AccessCategory.MENU),
    KITCHEN("menu_kitchen", "Layar Dapur", "Lihat & proses antrian masak", AccessCategory.MENU),
    ORDERS("menu_orders", "Open Bill & Riwayat", "Lihat tagihan terbuka & riwayat struk", AccessCategory.MENU),
    KASBON("menu_kasbon", "Pelanggan & Kasbon", "Kelola member, poin, voucher & hutang", AccessCategory.MENU),
    MENU_MGMT("menu_products", "Kelola Menu & Paket", "Tambah & ubah produk, kategori, paket", AccessCategory.MENU),
    STOCK("menu_stock", "Kelola Stok", "Pantau stok menipis & koreksi stok", AccessCategory.MENU),
    EXPENSE("menu_expense", "Catat Pengeluaran", "Catat belanja & biaya operasional", AccessCategory.MENU),
    REPORT("menu_report", "Laporan & Grafik", "Lihat laporan omzet, grafik & export CSV", AccessCategory.MENU),
    WEB_ORDERS("menu_web", "Pesanan Online & QR", "Terima pesanan dari web & atur QR toko", AccessCategory.MENU),
    SHIFT("menu_shift", "Shift Kasir", "Buka & tutup shift serta rekap laci", AccessCategory.MENU),
    SETTINGS("menu_settings", "Setelan & Printer", "Atur printer Bluetooth, struk & tema", AccessCategory.MENU),

    // ── Tindakan Khusus ──
    GIVE_DISCOUNT("act_discount", "Beri Diskon & Voucher", "Potong harga / pasang kode promo", AccessCategory.ACTION),
    VOID_REFUND("act_void", "Batalkan / Void Transaksi", "Batalkan atau refund struk lunas", AccessCategory.ACTION),
    EDIT_PRICE("act_price", "Ubah Harga Jual & Modal", "Edit harga produk saat kelola menu", AccessCategory.ACTION),
    VIEW_PROFIT_HPP("act_profit", "Lihat Modal (HPP) & Untung Bersih", "Jika dimatikan, karyawan hanya lihat omzet", AccessCategory.ACTION),
    DELETE_DATA("act_delete", "Hapus Data", "Hapus produk, pengeluaran, atau pelanggan", AccessCategory.ACTION),
    MANAGE_TEAM("act_team", "Kelola Cabang & Karyawan", "Tambah/ubah cabang & akun karyawan", AccessCategory.ACTION);

    companion object {
        fun fromKey(k: String): AppAccess? = entries.firstOrNull { it.key == k }
        fun menuItems(): List<AppAccess> = entries.filter { it.category == AccessCategory.MENU }
        fun actionItems(): List<AppAccess> = entries.filter { it.category == AccessCategory.ACTION }
        fun encode(set: Set<AppAccess>): String = set.joinToString(",") { it.key }
        fun decode(raw: String): Set<AppAccess> = raw.split(",")
            .mapNotNull { fromKey(it.trim()) }
            .toSet()
    }
}

// ═══════ TEMPLATE CEPAT (HANYA BANTU ISI CENTANGAN) ═══════
enum class AccessPreset(
    val label: String,
    val defaultJobTitle: String,
    val accesses: Set<AppAccess>
) {
    KASIR(
        label = "Template Kasir",
        defaultJobTitle = "Kasir",
        accesses = setOf(
            AppAccess.HOME,
            AppAccess.POS,
            AppAccess.ORDERS,
            AppAccess.KASBON,
            AppAccess.EXPENSE,
            AppAccess.WEB_ORDERS,
            AppAccess.SHIFT,
            AppAccess.SETTINGS
        )
    ),
    DAPUR(
        label = "Template Dapur",
        defaultJobTitle = "Dapur",
        accesses = setOf(
            AppAccess.KITCHEN
        )
    ),
    KASIR_DAPUR(
        label = "Kasir + Dapur",
        defaultJobTitle = "Penjaga Gerobak",
        accesses = setOf(
            AppAccess.HOME,
            AppAccess.POS,
            AppAccess.KITCHEN,
            AppAccess.ORDERS,
            AppAccess.KASBON,
            AppAccess.EXPENSE,
            AppAccess.WEB_ORDERS,
            AppAccess.SHIFT,
            AppAccess.SETTINGS
        )
    ),
    ADMIN(
        label = "Template Admin",
        defaultJobTitle = "Admin Cabang",
        accesses = AppAccess.entries.toSet() - AppAccess.MANAGE_TEAM
    )
}

// ═══════ BUSINESS & TRANSACTION ENUMS ═══════
enum class OrderType(val id: String, val label: String) {
    DINE_IN("DINE_IN", "Makan Sini"),
    TAKE_AWAY("TAKE_AWAY", "Bungkus"),
    DELIVERY("DELIVERY", "Antar / Delivery");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: DINE_IN
    }
}

enum class OrderStatus(val id: String, val label: String) {
    OPEN("OPEN", "Belum Bayar"),
    PAID("PAID", "Lunas"),
    VOID("VOID", "Dibatalkan"),
    REFUND("REFUND", "Refund");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: PAID
    }
}

enum class KitchenStatus(val id: String, val label: String) {
    NONE("NONE", "-"),
    WAITING("WAITING", "Baru Masuk"),
    COOKING("COOKING", "Dimasak"),
    READY("READY", "Siap"),
    SERVED("SERVED", "Selesai");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: WAITING
    }
}

enum class PaymentMethod(val id: String, val label: String) {
    CASH("CASH", "Tunai"),
    QRIS("QRIS", "QRIS"),
    TRANSFER("TRANSFER", "Transfer"),
    EWALLET("EWALLET", "E-Wallet"),
    DEBIT("DEBIT", "Debit"),
    KASBON("KASBON", "Kasbon / Hutang");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: CASH
    }
}

enum class BundleType(val id: String, val label: String, val emoji: String) {
    FIXED("FIXED", "Paket Tetap", "📦"),
    CHOICE("CHOICE", "Paket Pilihan", "🎁");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: FIXED
    }
}

enum class StockMoveType(val id: String, val label: String) {
    IN("IN", "Stok Masuk"),
    OUT("OUT", "Stok Keluar"),
    SALE("SALE", "Terjual"),
    VOID_RETURN("VOID_RETURN", "Batal / Retur"),
    OPNAME("OPNAME", "Opname");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: OPNAME
    }
}

enum class SyncStatus(val id: String) {
    PENDING("PENDING"),
    SYNCED("SYNCED"),
    FAILED("FAILED");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: PENDING
    }
}

enum class WebOrderStatus(val id: String, val label: String) {
    PENDING("PENDING", "Pesanan Baru"),
    ACCEPTED("ACCEPTED", "Diterima"),
    REJECTED("REJECTED", "Ditolak");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: PENDING
    }
}

object LoyaltyRules {
    const val RUPIAH_PER_POINT = 10_000L
    const val POINT_VALUE_RUPIAH = 100L

    fun earnedPoints(totalPaid: Long): Int =
        if (totalPaid > 0) (totalPaid / RUPIAH_PER_POINT).toInt() else 0

    fun pointsToRupiah(points: Int): Long = points.toLong() * POINT_VALUE_RUPIAH

    fun rupiahToPoints(rupiah: Long): Int =
        if (rupiah > 0) (rupiah / POINT_VALUE_RUPIAH).toInt() else 0
}
