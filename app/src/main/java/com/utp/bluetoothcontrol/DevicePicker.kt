package com.utp.bluetoothcontrol

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simple paired-device picker that lists bonded devices and calls onDeviceSelected when tapped.
 * Requests BLUETOOTH_CONNECT on Android 12+ before reading bondedDevices.
 */
@Composable
fun DevicePicker(
    modifier: Modifier = Modifier,
    adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter(),
    onDeviceSelected: (BluetoothDevice) -> Unit
) {
    var devices by remember { mutableStateOf(listOf<BluetoothDevice>()) }
    var error by remember { mutableStateOf<String?>(null) }

    // permission launcher for Android 12+
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // load after permission granted
            devices = loadPairedDevices(adapter)
        } else {
            error = "Bluetooth permission required"
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            devices = loadPairedDevices(adapter)
        }
    }

    Column(modifier = modifier.padding(8.dp)) {
        Text(text = "Paired devices", modifier = Modifier.padding(bottom = 8.dp))
        error?.let { Text(text = "Error: $it", modifier = Modifier.padding(bottom = 8.dp)) }

        if (devices.isEmpty()) {
            Text(text = "No paired devices found (pair in Settings first).", modifier = Modifier.padding(8.dp))
        } else {
            LazyColumn {
                items(devices) { dev ->
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onDeviceSelected(dev) }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = dev.name ?: "(unknown)")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = dev.address)
                        }
                    }
                }
            }
        }
    }
}

// helper: read bonded devices (call on background if needed)
fun loadPairedDevices(adapter: BluetoothAdapter?): List<BluetoothDevice> {
    return try {
        val bd = adapter?.bondedDevices ?: emptySet()
        bd.toList().sortedBy { it.name ?: it.address }
    } catch (se: SecurityException) {
        emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
