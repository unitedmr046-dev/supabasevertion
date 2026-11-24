package com.iyonzkasir.core

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object PinHasher {
    private val random = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray(Charsets.UTF_8))
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean {
        if (pin.isBlank() || salt.isBlank() || expectedHash.isBlank()) return false
        val actual = hash(pin, salt).toByteArray(Charsets.UTF_8)
        val expected = expectedHash.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(actual, expected)
    }
}

/**
 * Lockout protection: 5x salah PIN berturut-turut -> kunci 30 detik.
 * Tersimpan di SharedPreferences supaya tidak reset meski layar ditutup.
 */
object PinLockGuard {
    private const val PREFS = "iyonz_pin_guard"
    private const val MAX_ATTEMPTS = 5
    private const val LOCK_MS = 30_000L

    fun remainingLockSeconds(context: Context, userId: String): Int {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lockUntil = sp.getLong("lock_$userId", 0L)
        val diff = lockUntil - System.currentTimeMillis()
        return if (diff > 0) ((diff + 999L) / 1000L).toInt() else 0
    }

    fun recordFailure(context: Context, userId: String): Int {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fails = sp.getInt("fail_$userId", 0) + 1
        val editor = sp.edit().putInt("fail_$userId", fails)
        if (fails >= MAX_ATTEMPTS) {
            editor.putLong("lock_$userId", System.currentTimeMillis() + LOCK_MS)
            editor.putInt("fail_$userId", 0)
        }
        editor.apply()
        return fails
    }

    fun reset(context: Context, userId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("fail_$userId")
            .remove("lock_$userId")
            .apply()
    }
}
