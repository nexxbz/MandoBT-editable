package com.mandobt

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import java.util.concurrent.Executors

/**
 * Registra el teléfono como dispositivo HID Bluetooth compuesto:
 *  - Reporte 1: control (16 botones, hat, 4 ejes, 2 gatillos)  -> 9 bytes
 *  - Reporte 2: ratón (3 botones, x, y, rueda)     -> 4 bytes
 *  - Reporte 3: teclado (modificadores + 6 teclas) -> 8 bytes
 */
@SuppressLint("MissingPermission")
class HidController(private val ctx: Context, private val onStatus: (String) -> Unit) {

    private val adapter: BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private val exec = Executors.newSingleThreadExecutor()

    val bonded: List<BluetoothDevice> get() = adapter?.bondedDevices?.toList() ?: emptyList()

    private val cb = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            onStatus(if (registered) "Listo. Empareja o conecta un equipo." else "Control Bluetooth no registrado")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    onStatus("Conectado a " + (device.name ?: device.address))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host == device) host = null
                    onStatus("Desconectado")
                }
            }
        }
    }

    fun start() {
        val ok = adapter?.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.HID_DEVICE) return
                val h = proxy as BluetoothHidDevice
                hid = h
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "Mando BT", "Control, ratón y teclado", "Mando BT",
                    BluetoothHidDevice.SUBCLASS1_COMBO, DESC
                )
                h.registerApp(sdp, null, null, exec, cb)
            }

            override fun onServiceDisconnected(profile: Int) { hid = null }
        }, BluetoothProfile.HID_DEVICE)
        if (ok != true) onStatus("Este teléfono no admite el modo control Bluetooth (HID)")
    }

    fun stop() {
        try {
            hid?.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        } catch (_: Exception) {}
        hid = null
    }

    fun connect(d: BluetoothDevice) { hid?.connect(d) }

    fun send(id: Int, data: ByteArray) {
        val h = host ?: return
        hid?.sendReport(h, id, data)
    }

    companion object {
        private val RAW = intArrayOf(
            // ---- Control (ID 1)
            0x05, 0x01, 0x09, 0x05, 0xA1, 0x01, 0x85, 0x01,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x10, 0x81, 0x02,
            0x05, 0x01, 0x09, 0x39, 0x15, 0x01, 0x25, 0x08, 0x75, 0x04, 0x95, 0x01, 0x81, 0x42,
            0x75, 0x04, 0x95, 0x01, 0x81, 0x03,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35,
            0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x04, 0x81, 0x02,
            // gatillos analógicos: Acelerador (R2) y Freno (L2), 0 a 255
            0x05, 0x02, 0x09, 0xC4, 0x09, 0xC5, 0x15, 0x00, 0x26, 0xFF, 0x00, 0x75, 0x08, 0x95, 0x02, 0x81, 0x02,
            0xC0,
            // ---- Ratón (ID 2)
            0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x85, 0x02, 0x09, 0x01, 0xA1, 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01, 0x95, 0x03, 0x75, 0x01, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x05, 0x81, 0x03,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38, 0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
            0xC0, 0xC0,
            // ---- Teclado (ID 3)
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, 0x03, 0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7,
            0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x08, 0x81, 0x03,
            0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
            0xC0
        )
        val DESC: ByteArray = ByteArray(RAW.size) { RAW[it].toByte() }
    }
}
