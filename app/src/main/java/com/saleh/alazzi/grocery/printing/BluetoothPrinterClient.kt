package com.saleh.alazzi.grocery.printing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class PairedPrinter(val name: String, val address: String)

class BluetoothPrinterClient(private val context: Context) {
    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private fun adapter(): BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: error("هذا الجهاز لا يدعم Bluetooth")

    private fun ensureConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("يلزم السماح للتطبيق بالوصول إلى أجهزة Bluetooth المقترنة")
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun pairedPrinters(): List<PairedPrinter> = withContext(Dispatchers.IO) {
        ensureConnectPermission()
        val bt = adapter()
        check(bt.isEnabled) { "فعّل Bluetooth أولاً" }
        bt.bondedDevices.orEmpty()
            .map { PairedPrinter(it.name ?: "طابعة Bluetooth", it.address) }
            .sortedBy { it.name.lowercase() }
    }

    @SuppressLint("MissingPermission")
    suspend fun print(bitmap: Bitmap, printerAddress: String, paperWidthDots: Int) = withContext(Dispatchers.IO) {
        require(bitmap.width == paperWidthDots) { "عرض صورة الإيصال لا يطابق عرض الورق" }
        ensureConnectPermission()
        val bt = adapter()
        check(bt.isEnabled) { "Bluetooth غير مفعل" }
        val device = bt.bondedDevices.orEmpty().firstOrNull { it.address == printerAddress }
            ?: error("الطابعة غير مقترنة بهذا الهاتف")

        var socket: BluetoothSocket? = null
        try {
            bt.cancelDiscovery()
            socket = connect(device)
            val output = socket.outputStream
            output.write(byteArrayOf(0x1B, 0x40)) // ESC @ initialize
            EscPosRasterEncoder.encode(bitmap).forEach { band ->
                output.write(band)
                output.flush()
            }
            output.write(byteArrayOf(0x1B, 0x64, 0x03)) // feed three lines
            output.flush()
        } finally {
            try { socket?.close() } catch (_: IOException) { }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice): BluetoothSocket {
        val secure = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            secure.connect()
            return secure
        } catch (first: IOException) {
            try { secure.close() } catch (_: IOException) { }
            val insecure = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            try {
                insecure.connect()
                return insecure
            } catch (second: IOException) {
                try { insecure.close() } catch (_: IOException) { }
                second.addSuppressed(first)
                throw IOException("تعذر الاتصال بالطابعة. تحقق من الاقتران وتشغيل الطابعة.", second)
            }
        }
    }
}
