package com.iyonzkasir.core

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

data class ActiveSessionUser(
    val id: String,
    val name: String,
    val jobTitle: String,
    val isOwner: Boolean,
    val outletId: String,
    val accesses: Set<AppAccess>
)

data class ActiveOutletContext(
    val id: String,
    val name: String
)

/**
 * Menyimpan sesi staff yang sedang aktif di perangkat,
 * serta menyimpan metadata ringan ke SharedPreferences agar aman
 * saat Android melakukan process death di background.
 */
object SessionManager {
    private const val PREFS = "iyonz_session_state"
    private const val KEY_BUSINESS_ID = "business_id"
    private const val KEY_DEVICE_OUTLET_ID = "device_outlet_id"
    private const val KEY_ACTIVE_STAFF_ID = "active_staff_id"
    private const val KEY_SELECTED_OUTLET_FILTER = "selected_outlet_filter"

    private val _user = mutableStateOf<ActiveSessionUser?>(null)
    val userState: State<ActiveSessionUser?> get() = _user
    val currentUser: ActiveSessionUser? get() = _user.value

    private val _businessId = mutableStateOf("")
    val businessIdState: State<String> get() = _businessId
    val businessId: String get() = _businessId.value

    // Cabang tempat HP ini ditugaskan (kosong = HP Owner bebas lihat semua cabang)
    private val _deviceOutletId = mutableStateOf("")
    val deviceOutletIdState: State<String> get() = _deviceOutletId
    val deviceOutletId: String get() = _deviceOutletId.value

    // Filter cabang yang sedang dilihat Owner ("ALL" atau outletId tertentu)
    private val _viewingOutletId = mutableStateOf("ALL")
    val viewingOutletIdState: State<String> get() = _viewingOutletId
    val viewingOutletId: String get() = _viewingOutletId.value

    fun initFromPrefs(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _businessId.value = sp.getString(KEY_BUSINESS_ID, "") ?: ""
        _deviceOutletId.value = sp.getString(KEY_DEVICE_OUTLET_ID, "") ?: ""
        _viewingOutletId.value = sp.getString(KEY_SELECTED_OUTLET_FILTER, "ALL") ?: "ALL"
    }

    fun bindBusiness(context: Context, businessId: String, deviceOutletId: String = "") {
        _businessId.value = businessId
        _deviceOutletId.value = deviceOutletId
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_BUSINESS_ID, businessId)
            .putString(KEY_DEVICE_OUTLET_ID, deviceOutletId)
            .apply()
    }

    fun setViewingOutlet(context: Context, outletId: String) {
        _viewingOutletId.value = outletId
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SELECTED_OUTLET_FILTER, outletId)
            .apply()
    }

    fun loginStaff(context: Context, user: ActiveSessionUser) {
        _user.value = user
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE_STAFF_ID, user.id)
            .apply()
    }

    fun getLastStaffId(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_STAFF_ID, "") ?: ""
    }

    fun logoutStaff(context: Context) {
        _user.value = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_ACTIVE_STAFF_ID)
            .apply()
    }

    fun clearAll(context: Context) {
        _user.value = null
        _businessId.value = ""
        _deviceOutletId.value = ""
        _viewingOutletId.value = "ALL"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = _user.value != null
    fun isOwner(): Boolean = _user.value?.isOwner == true

    fun can(access: AppAccess): Boolean {
        val u = _user.value ?: return false
        if (u.isOwner) return true
        return access in u.accesses
    }

    fun canAny(vararg accesses: AppAccess): Boolean = accesses.any { can(it) }

    /** Outlet aktif untuk transaksi baru (Kasir/Belanja). */
    fun activeTransactionOutletId(fallbackOutletId: String): String {
        val userOutlet = _user.value?.outletId.orEmpty()
        if (userOutlet.isNotBlank() && userOutlet != "ALL") return userOutlet
        if (_deviceOutletId.value.isNotBlank() && _deviceOutletId.value != "ALL") {
            return _deviceOutletId.value
        }
        val view = _viewingOutletId.value
        if (view.isNotBlank() && view != "ALL") return view
        return fallbackOutletId
    }
}
