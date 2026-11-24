package com.iyonzkasir.core

import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val ID_LOCALE = Locale("id", "ID")

fun Long.toRupiah(): String {
    val prefix = if (this < 0) "-Rp " else "Rp "
    return prefix + NumberFormat.getNumberInstance(ID_LOCALE).format(kotlin.math.abs(this))
}

fun Int.toRupiah(): String = this.toLong().toRupiah()

fun Long.formatDateTime(): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", ID_LOCALE).format(Date(this))

fun Long.formatDate(): String =
    SimpleDateFormat("dd MMM yyyy", ID_LOCALE).format(Date(this))

fun Long.formatShortDate(): String =
    SimpleDateFormat("dd/MM", ID_LOCALE).format(Date(this))

fun Long.formatTime(): String =
    SimpleDateFormat("HH:mm", ID_LOCALE).format(Date(this))

fun Long.toDayKey(): String =
    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(this))

fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun endOfTodayMillis(): Long = startOfTodayMillis() + 86_400_000L - 1L

fun daysAgoStartMillis(daysAgo: Int): Long =
    startOfTodayMillis() - (daysAgo.toLong() * 86_400_000L)

fun normalizeWaPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.isBlank() -> ""
        digits.startsWith("0") -> "62" + digits.drop(1)
        digits.startsWith("620") -> "62" + digits.drop(3)
        digits.startsWith("62") -> digits
        else -> "62$digits"
    }
}

fun String.toColorSafe(fallback: Color = AppColors.brand): Color = try {
    Color(android.graphics.Color.parseColor(this))
} catch (_: Exception) {
    fallback
}

fun newUuid(): String = UUID.randomUUID().toString()
