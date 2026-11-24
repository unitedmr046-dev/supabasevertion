package com.iyonzkasir.data.repo

import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*

class PrefRepository(private val dao: LocalPrefDao) {
    suspend fun get(key: String, default: String = ""): String = dao.get(key)?.value ?: default
    suspend fun set(key: String, value: String) = dao.set(LocalPrefEntity(key, value))

    suspend fun getThemeMode() = DisplayThemeMode.fromId(get(K_THEME_MODE, "system"))
    suspend fun setThemeMode(m: DisplayThemeMode) = set(K_THEME_MODE, m.id)
    suspend fun getBrandPreset() = BrandPreset.fromId(get(K_BRAND_PRESET, "orange"))
    suspend fun setBrandPreset(p: BrandPreset) = set(K_BRAND_PRESET, p.id)

    suspend fun getPrinterMac() = get(K_PRINTER_MAC)
    suspend fun setPrinterMac(v: String) = set(K_PRINTER_MAC, v)
    suspend fun getPrinterName() = get(K_PRINTER_NAME)
    suspend fun setPrinterName(v: String) = set(K_PRINTER_NAME, v)
    suspend fun getPaperWidth() = get(K_PAPER_WIDTH, "58").toIntOrNull() ?: 58
    suspend fun setPaperWidth(w: Int) = set(K_PAPER_WIDTH, w.toString())
    suspend fun isAutoPrint() = get(K_AUTO_PRINT, "0") == "1"
    suspend fun setAutoPrint(v: Boolean) = set(K_AUTO_PRINT, if (v) "1" else "0")

    suspend fun getDefaultTaxPercent() = get(K_DEFAULT_TAX, "0").toIntOrNull() ?: 0
    suspend fun setDefaultTaxPercent(v: Int) = set(K_DEFAULT_TAX, v.coerceIn(0, 100).toString())
    suspend fun isSoundEnabled() = get(K_SOUND, "1") == "1"
    suspend fun setSoundEnabled(v: Boolean) = set(K_SOUND, if (v) "1" else "0")
    suspend fun getActivePayments(): Set<String> =
        get(K_PAYMENTS, "CASH,QRIS,TRANSFER,EWALLET,DEBIT,KASBON").split(",").filter { it.isNotBlank() }.toSet()
    suspend fun setActivePayments(v: Set<String>) = set(K_PAYMENTS, v.joinToString(","))

    suspend fun isQueueEnabled() = get(K_QUEUE_ON, "0") == "1"
    suspend fun setQueueEnabled(v: Boolean) = set(K_QUEUE_ON, if (v) "1" else "0")
    suspend fun getQueuePrefix() = get(K_QUEUE_PREFIX, "")
    suspend fun setQueuePrefix(v: String) = set(K_QUEUE_PREFIX, v)

    suspend fun getWebSlug() = get(K_WEB_SLUG, "")
    suspend fun setWebSlug(v: String) = set(K_WEB_SLUG, v)
    suspend fun getLastSyncTs() = get(K_LAST_SYNC, "0").toLongOrNull() ?: 0L
    suspend fun setLastSyncTs(ts: Long) = set(K_LAST_SYNC, ts.toString())
    suspend fun getLastBackupTs() = get(K_LAST_BACKUP, "0").toLongOrNull() ?: 0L
    suspend fun setLastBackupTs(ts: Long) = set(K_LAST_BACKUP, ts.toString())

    suspend fun isReceiptFlag(key: String, default: Boolean = true) = get(key, if (default) "1" else "0") == "1"
    suspend fun setReceiptFlag(key: String, v: Boolean) = set(key, if (v) "1" else "0")

    companion object {
        const val K_THEME_MODE = "v2_theme_mode"
        const val K_BRAND_PRESET = "v2_brand_preset"
        const val K_PRINTER_MAC = "v2_printer_mac"
        const val K_PRINTER_NAME = "v2_printer_name"
        const val K_PAPER_WIDTH = "v2_paper_width"
        const val K_AUTO_PRINT = "v2_auto_print"
        const val K_DEFAULT_TAX = "v2_default_tax"
        const val K_SOUND = "v2_sound"
        const val K_PAYMENTS = "v2_payments"
        const val K_QUEUE_ON = "v2_queue_on"
        const val K_QUEUE_PREFIX = "v2_queue_prefix"
        const val K_WEB_SLUG = "v2_web_slug"
        const val K_LAST_SYNC = "v2_last_sync"
        const val K_LAST_BACKUP = "v2_last_backup"
        const val R_ADDRESS = "v2_r_address"
        const val R_PHONE = "v2_r_phone"
        const val R_STAFF = "v2_r_staff"
        const val R_TABLE = "v2_r_table"
        const val R_CUSTOMER = "v2_r_customer"
        const val R_QUEUE = "v2_r_queue"
        const val R_NOTE = "v2_r_note"
        const val R_POINTS = "v2_r_points"
    }
}
