package com.iyonzkasir.ui.manage

import android.content.Context
import android.content.Intent
import com.iyonzkasir.data.local.IyonzDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object V2BackupHelper {
    private const val MAGIC = "IYONZ2"

    fun defaultFileName(): String =
        "iyonzkasir-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.iyonz"

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 10_000, 256)
        val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }

    suspend fun backup(context: Context, password: String, outStream: OutputStream): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(BufferedOutputStream(baos)).use { zos ->
                val dbFile = context.getDatabasePath(IyonzDatabase.DB_NAME)
                if (dbFile.exists()) {
                    zos.putNextEntry(ZipEntry(IyonzDatabase.DB_NAME))
                    dbFile.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                val photosDir = File(context.filesDir, "v2_photos")
                photosDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                    zos.putNextEntry(ZipEntry("v2_photos/${f.name}"))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), IvParameterSpec(iv))
            }
            val dos = DataOutputStream(BufferedOutputStream(outStream))
            dos.write(MAGIC.toByteArray(Charsets.US_ASCII))
            dos.writeInt(salt.size); dos.write(salt)
            dos.writeInt(iv.size); dos.write(iv)
            CipherOutputStream(dos, cipher).use { it.write(baos.toByteArray()) }
        }
    }

    suspend fun restore(context: Context, password: String, inStream: InputStream): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dis = DataInputStream(BufferedInputStream(inStream))
            val magic = ByteArray(MAGIC.length).also { dis.readFully(it) }
            if (String(magic, Charsets.US_ASCII) != MAGIC) error("File bukan backup iyonzkasir v2")
            val salt = ByteArray(dis.readInt()).also { dis.readFully(it) }
            val iv = ByteArray(dis.readInt()).also { dis.readFully(it) }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, deriveKey(password, salt), IvParameterSpec(iv))
            }
            val zipBytes = ByteArrayOutputStream().also {
                CipherInputStream(dis, cipher).use { cis -> cis.copyTo(it) }
            }.toByteArray()

            var foundDb: ByteArray? = null
            val photos = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == IyonzDatabase.DB_NAME) foundDb = zis.readBytes()
                    else if (entry.name.startsWith("v2_photos/")) {
                        val fname = entry.name.substringAfter("v2_photos/")
                        if (fname.isNotBlank()) photos[fname] = zis.readBytes()
                    }
                    zis.closeEntry(); entry = zis.nextEntry
                }
            }
            val dbBytes = foundDb ?: error("File rusak atau password salah")
            IyonzDatabase.closeAndReset()
            val dbFile = context.getDatabasePath(IyonzDatabase.DB_NAME)
            dbFile.parentFile?.mkdirs()
            FileOutputStream(dbFile).use { it.write(dbBytes) }
            File(dbFile.parent, "${IyonzDatabase.DB_NAME}-wal").delete()
            File(dbFile.parent, "${IyonzDatabase.DB_NAME}-shm").delete()
            val dir = File(context.filesDir, "v2_photos").apply { mkdirs() }
            photos.forEach { (fname, bytes) -> FileOutputStream(File(dir, fname)).use { it.write(bytes) } }
        }
    }

    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
