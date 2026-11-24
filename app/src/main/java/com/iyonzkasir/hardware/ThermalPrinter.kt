package com.iyonzkasir.hardware

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

object EscPos {
    val INIT = byteArrayOf(0x1B, 0x40)
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    val SIZE_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    val SIZE_DOUBLE = byteArrayOf(0x1D, 0x21, 0x11)
    val CUT = byteArrayOf(0x1D, 0x56, 0x00)
}

object ThermalPrinter {
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var pendingSocket: BluetoothSocket? = null
    @Volatile private var output: OutputStream? = null

    var lastError: String? = null
        private set

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun isConnected(): Boolean = socket?.isConnected == true

    suspend fun connect(context: Context, mac: String): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        lastError = null
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            lastError = "Bluetooth belum dinyalakan"; return@withContext false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            lastError = "Izin Bluetooth belum diberikan"; return@withContext false
        }
        val device = runCatching { adapter.getRemoteDevice(mac) }.getOrElse {
            lastError = "Alamat printer tidak valid"; return@withContext false
        }
        runCatching { adapter.cancelDiscovery() }
        delay(350)

        val sock = tryConnect(device) ?: tryInsecureConnect(device) ?: tryChannelOne(device)
        if (sock == null) {
            lastError = "Gagal tersambung. Pastikan printer menyala & sudah di-pair"
            return@withContext false
        }
        socket = sock
        output = sock.outputStream
        pendingSocket = null
        delay(400)
        true
    }

    private fun tryConnect(device: BluetoothDevice): BluetoothSocket? = runCatching {
        device.createRfcommSocketToServiceRecord(SPP_UUID).also {
            pendingSocket = it; it.connect()
        }.takeIf { it.isConnected }
    }.getOrNull()

    private fun tryInsecureConnect(device: BluetoothDevice): BluetoothSocket? = runCatching {
        val m = device.javaClass.getMethod("createInsecureRfcommSocketToServiceRecord", UUID::class.java)
        (m.invoke(device, SPP_UUID) as BluetoothSocket).also {
            pendingSocket = it; it.connect()
        }.takeIf { it.isConnected }
    }.getOrNull()

    private fun tryChannelOne(device: BluetoothDevice): BluetoothSocket? = runCatching {
        val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType!!)
        (m.invoke(device, 1) as BluetoothSocket).also {
            pendingSocket = it; it.connect()
        }.takeIf { it.isConnected }
    }.getOrNull()

    fun disconnect() {
        runCatching { pendingSocket?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        pendingSocket = null; output = null; socket = null
    }

    suspend fun send(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (socket?.isConnected != true) { lastError = "Printer terputus"; return@withContext false }
            output?.write(bytes)
            output?.flush()
            lastError = null
            true
        }.getOrElse {
            lastError = "Gagal cetak: ${it.message}"; false
        }
    }
}
