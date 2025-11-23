// BtSppManager.kt (replace existing object contents with this)
package com.utp.bluetoothcontrol

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import java.io.OutputStream
import java.util.*

object BtSppManager {
    private val TAG = "BtSppManager"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val btAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var isConnected: Boolean = false
        private set

    /**
     * Connect to a paired BluetoothDevice (HC-05). onResult(true, null) on success, otherwise (false, errorMessage)
     * This method will try secure SPP, insecure SPP (via reflection) and a reflection fallback.
     */
    fun connect(device: BluetoothDevice, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        scope.launch {
            var lastError: String? = null
            try {
                try { socket?.close() } catch (_: Exception) {}
                btAdapter?.cancelDiscovery()

                // 1) Secure socket
                try {
                    val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket = s
                    socket!!.connect()
                    out = socket!!.outputStream
                    isConnected = true
                    Log.i(TAG, "Connected via secure socket to ${device.name}")
                    withContext(Dispatchers.Main) { onResult(true, null) }
                    return@launch
                } catch (e: Exception) {
                    lastError = "secure failed: ${e.message}"
                    Log.w(TAG, lastError, e)
                    try { socket?.close() } catch (_: Exception) {}
                }

                // 2) Insecure via reflection (some devices prefer this)
                try {
                    val m = device.javaClass.getMethod("createInsecureRfcommSocketToServiceRecord", UUID::class.java)
                    val s = m.invoke(device, SPP_UUID) as BluetoothSocket
                    socket = s
                    socket!!.connect()
                    out = socket!!.outputStream
                    isConnected = true
                    Log.i(TAG, "Connected via insecure (reflection) to ${device.name}")
                    withContext(Dispatchers.Main) { onResult(true, null) }
                    return@launch
                } catch (e: Exception) {
                    val msg = "insecure failed: ${e.message}"
                    lastError = if (lastError == null) msg else "$lastError | $msg"
                    Log.w(TAG, msg, e)
                    try { socket?.close() } catch (_: Exception) {}
                }

                // 3) Reflection fallback createRfcommSocket(int)
                try {
                    val m2 = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    val s = m2.invoke(device, 1) as BluetoothSocket
                    socket = s
                    socket!!.connect()
                    out = socket!!.outputStream
                    isConnected = true
                    Log.i(TAG, "Connected via reflection fallback to ${device.name}")
                    withContext(Dispatchers.Main) { onResult(true, null) }
                    return@launch
                } catch (e: Exception) {
                    val msg = "reflection fallback failed: ${e.message}"
                    lastError = if (lastError == null) msg else "$lastError | $msg"
                    Log.e(TAG, msg, e)
                    try { socket?.close() } catch (_: Exception) {}
                }

                // All attempts failed
                isConnected = false
                withContext(Dispatchers.Main) { onResult(false, lastError ?: "Unknown connect error") }
            } catch (se: SecurityException) {
                isConnected = false
                withContext(Dispatchers.Main) { onResult(false, "Missing BLUETOOTH_CONNECT permission") }
            } catch (e: Exception) {
                isConnected = false
                val msg = "Unexpected: ${e.message}"
                Log.e(TAG, msg, e)
                withContext(Dispatchers.Main) { onResult(false, msg) }
            }
        }
    }

    // convenience: keep your name-based helper (optional)
    fun connectToPairedDeviceByName(deviceName: String = "HC-05", onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            try {
                val device = btAdapter?.bondedDevices?.firstOrNull { it.name == deviceName }
                if (device == null) {
                    withContext(Dispatchers.Main) { onResult(false, "Paired device '$deviceName' not found") }
                    return@launch
                }
                connect(device, onResult)
            } catch (e: Exception) {
                Log.e(TAG, "connect error", e)
                withContext(Dispatchers.Main) { onResult(false, e.message) }
            }
        }
    }

    fun send(cmd: String) {
        Log.d(TAG, "send requested: $cmd ; isConnected=$isConnected")
        if (!isConnected) {
            Log.w(TAG, "Not connected, dropping: $cmd")
            return
        }
        scope.launch {
            try {
                out?.write((cmd + "\n").toByteArray())
                out?.flush()
                Log.d(TAG, "Sent: $cmd")
            } catch (e: Exception) {
                Log.e(TAG, "Send failed", e)
                isConnected = false
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try { out?.close(); socket?.close() } catch (_: Exception) {}
            isConnected = false
        }
    }
}
