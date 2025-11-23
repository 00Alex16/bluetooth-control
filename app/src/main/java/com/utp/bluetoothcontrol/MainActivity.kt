package com.utp.bluetoothcontrol

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.utp.bluetoothcontrol.ui.theme.BluetoothControlTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    // Permissions launcher (Android 12+ needs BLUETOOTH_CONNECT/SCAN)
    private val requestPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val ok = results.values.all { it }
            Log.i(TAG, "Permissions granted: $ok")
        }

    // ----------------- Constants & state -----------------
    private val JOYSTICK_DEAD = 0.25f

    private var lastSentCmd: Char? = null

    // Map keys/buttons to single chars
    private val keyToCmd: Map<Int, Char> = mapOf(
        // face buttons
        KeyEvent.KEYCODE_BUTTON_X to 'X',  // emergency brake
        KeyEvent.KEYCODE_BUTTON_A to 'P',  // pivot right
        KeyEvent.KEYCODE_BUTTON_B to 'O',  // pivot left
        KeyEvent.KEYCODE_BUTTON_Y to 'Z',  // stop or something else

        // dpads
        KeyEvent.KEYCODE_DPAD_UP to 'F',
        KeyEvent.KEYCODE_DPAD_DOWN to 'B',
        KeyEvent.KEYCODE_DPAD_LEFT to 'L',
        KeyEvent.KEYCODE_DPAD_RIGHT to 'R',

        // bumpers (index fingers) - big increments
        KeyEvent.KEYCODE_BUTTON_R1 to 'T', // R (big increase)
        KeyEvent.KEYCODE_BUTTON_L1 to 'Y', // L (big decrease)

        // triggers / secondary - small increments (often R2/L2 map to these)
        KeyEvent.KEYCODE_BUTTON_R2 to 't', // R2 small increase
        KeyEvent.KEYCODE_BUTTON_L2 to 'y'  // L2 small decrease
    )

    // Helper: only send when changed or after min interval
    private fun sendCharIfChanged(c: Char) {
        if (c == lastSentCmd) return
        lastSentCmd = c
        BtSppManager.send(c.toString())
        SessionLog.add(LogLevel.OUT, c.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestBluetoothPermissionsIfNeeded()

        setContent {
            BluetoothControlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainContent()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BtSppManager.disconnect()
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPerms.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        }
    }

    // ----------------- Joystick (axis) handling -----------------
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        try {
            val lx = event.getAxisValue(MotionEvent.AXIS_X)
            val ly = event.getAxisValue(MotionEvent.AXIS_Y)

            // apply dead region
            val ax = kotlin.math.abs(lx)
            val ay = kotlin.math.abs(ly)

            val cmd: Char = when {
                // diagonal forward-right
                ly < -JOYSTICK_DEAD && lx > JOYSTICK_DEAD -> 'U' // FR
                // diagonal forward-left
                ly < -JOYSTICK_DEAD && lx < -JOYSTICK_DEAD -> 'I' // FL
                // diagonal back-right
                ly > JOYSTICK_DEAD && lx > JOYSTICK_DEAD -> 'J' // BR
                // diagonal back-left
                ly > JOYSTICK_DEAD && lx < -JOYSTICK_DEAD -> 'K' // BF

                // vertical dominant -> forward/back
                ly < -JOYSTICK_DEAD -> 'F'
                ly > JOYSTICK_DEAD -> 'B'

                // horizontal dominant -> left/right
                lx < -JOYSTICK_DEAD -> 'L'
                lx > JOYSTICK_DEAD -> 'R'

                else -> 'S'
            }

            if (cmd != 'S') {
                sendCharIfChanged(cmd)
            }
            // optionally log raw values occasionally:
            // SessionLog.add(LogLevel.INFO, "AX:${String.format("%.2f", lx)} AY:${String.format("%.2f", ly)}")
        } catch (t: Throwable) {
            // ignore controllers without axis support
        }
        return super.onGenericMotionEvent(event)
    }

    // ----------------- Button handling -----------------
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return super.onKeyDown(keyCode, event)

        val mapped = keyToCmd[keyCode]
        if (mapped != null && mapped != 'S') {
            // For speed-adjust keys (T/t/Y/y) we send once on press (Arduino will increment/decrement).
            sendCharIfChanged(mapped)
            SessionLog.add(LogLevel.IN, "BTN:$keyCode -> $mapped")
            return true
        } else {
            SessionLog.add(LogLevel.IN, "Unknown KEYDOWN: $keyCode")
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // For momentary movement buttons (D-pad, bumpers), send 'S' on release to stop
        if (keyToCmd.containsKey(keyCode)) {
            // speed adjust keys are one-shot, but it's harmless to send S on release for direction keys
            val mapped = keyToCmd[keyCode]
            // if mapped is a speed adjust (T/t/Y/y), don't send S on release
            if (mapped != 'T' && mapped != 't' && mapped != 'Y' && mapped != 'y') {
                // sendCharIfChanged('S')
                SessionLog.add(LogLevel.IN, "BTN:$keyCode released")
            }
            return true
        } else {
            SessionLog.add(LogLevel.IN, "Unknown KEYUP: $keyCode")
        }
        return super.onKeyUp(keyCode, event)
    }
}

/* ---------- UI layer (Compose) ---------- */

private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

// Main composable
@Composable
fun MainContent() {
    var status by remember { mutableStateOf("Not connected") }
    var connecting by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    // keep a local wrapper so existing UI code can still call addLog(...)
    fun addLog(level: LogLevel, text: String) {
        SessionLog.add(level, text)
    }

    // connect callback
    val onDeviceSelected: (BluetoothDevice) -> Unit = { device ->
        showPicker = false
        connecting = true
        status = "Connecting to ${device.name ?: device.address}..."
        addLog(LogLevel.INFO, "Attempt connect -> ${device.name ?: device.address}")
        BtSppManager.connect(device) { success, error ->
            connecting = false
            if (success) {
                status = "Connected to ${device.name ?: device.address}"
                addLog(LogLevel.INFO, "Connected to ${device.name ?: device.address}")
            } else {
                status = "Connect failed: ${error ?: "unknown"}"
                addLog(LogLevel.ERR, "Connect failed: ${error ?: "unknown"}")
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Top status row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Status:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(status, modifier = Modifier.weight(1f))
            if (connecting) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        // Buttons row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showPicker = true },
                modifier = Modifier.weight(1f),
                enabled = !connecting
            ) {
                Text("Connect")
            }

            Button(
                onClick = {
                    BtSppManager.disconnect()
                    status = "Disconnected"
                    addLog(LogLevel.INFO, "Disconnected by user")
                },
                modifier = Modifier.weight(1f),
                enabled = BtSppManager.isConnected
            ) {
                Text("Disconnect")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Log title + clear button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Log", fontWeight = FontWeight.Bold)
            Row {
                Text(
                    text = "Clear",
                    modifier = Modifier
                        .clickable {
                            SessionLog.clear()
                            addLog(LogLevel.INFO, "Log cleared")
                        }
                        .padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Log list (newest at top)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (SessionLog.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No log entries yet")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    itemsIndexed(SessionLog.items) { index, entry ->
                        LogRow(entry)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Small footer note
        Text(
            "Tip: pair HC-05 and your controller in Android Settings first. Use Connect to open device picker.",
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Device picker modal dialog
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {},
            dismissButton = {},
            title = { Text("Select paired device") },
            text = {
                // put the DevicePicker inside the dialog content
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    DevicePicker(
                        modifier = Modifier.fillMaxSize(),
                        onDeviceSelected = onDeviceSelected
                    )
                }
            }
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val bg = when (entry.level) {
        LogLevel.INFO -> null
        LogLevel.IN -> null
        LogLevel.OUT -> null
        LogLevel.WARN -> null
        LogLevel.ERR -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("[${entry.time}]", modifier = Modifier.width(78.dp))
        Text(entry.level.name, modifier = Modifier.width(48.dp), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(8.dp))
        Text(entry.text)
    }
}
