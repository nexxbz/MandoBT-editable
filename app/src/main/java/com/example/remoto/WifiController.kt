package com.example.remoto

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

const val CONTROL_PORT = 47321
const val SERVICE_TYPE = "_controlremoto._tcp."

/** IP wifi de este teléfono (por ejemplo 192.168.1.20) o null si no hay red. */
fun localIpv4(context: Context): String? {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
    return props.linkAddresses
        .map { it.address }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
}

private fun androidVersionName(sdk: Int): String = when {
    sdk >= 35 -> "15"
    sdk == 34 -> "14"
    sdk == 33 -> "13"
    sdk >= 31 -> "12"
    sdk == 30 -> "11"
    sdk == 29 -> "10"
    sdk == 28 -> "9"
    else -> sdk.toString()
}

/**
 * Cliente: se conecta por wifi (TCP) a otro teléfono que tenga esta app con el servicio activado
 * y le envía las órdenes como líneas de texto. Si la conexión se cae (por ejemplo tras apagar la pantalla),
 * se vuelve a conectar sola con el mismo PIN.
 */
class WifiController : RemoteController {

    var status by mutableStateOf("")
        private set
    var connected by mutableStateOf(false)
        private set

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var socket: Socket? = null
    private var out: OutputStream? = null
    private var lastHost: String? = null
    private var lastPort = CONTROL_PORT
    private var lastPin = ""
    private var infoText = "Conectado"

    /** 0 = conectado, 1 = PIN incorrecto, 2 = no se pudo / rechazado. Se llama desde el hilo de red. */
    private fun handshake(host: String, port: Int, pin: String): Int {
        closeQuietly()
        return try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 4000)
            s.tcpNoDelay = true
            s.soTimeout = 5000
            val o = s.getOutputStream()
            o.write("HELLO $pin\n".toByteArray(Charsets.UTF_8))
            o.flush()
            val reply = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).readLine()
            if (reply != null && reply.startsWith("OK")) {
                s.soTimeout = 0
                socket = s
                out = o
                val sdk = reply.substringAfter('|', "").trim().toIntOrNull()
                infoText = when {
                    sdk == null -> "Conectado"
                    sdk >= 33 -> "Conectado · Android ${androidVersionName(sdk)}"
                    else -> "Conectado · Android ${androidVersionName(sdk)} (cruceta en modo compatible)"
                }
                0
            } else {
                s.close()
                if (reply == "NO") 1 else 2
            }
        } catch (e: Exception) {
            2
        }
    }

    fun connect(host: String, port: Int, pin: String, onDone: (ok: Boolean, pinWrong: Boolean) -> Unit) {
        status = "Conectando a $host…"
        lastHost = host
        lastPort = port
        lastPin = pin
        io.execute {
            val result = handshake(host, port, pin)
            main.post {
                connected = result == 0
                status = when (result) {
                    0 -> infoText
                    1 -> "PIN incorrecto."
                    else -> "No se pudo conectar. Revisa que el servicio esté activado en el otro teléfono y que ambos estén en la misma wifi."
                }
                onDone(result == 0, result == 1)
            }
        }
    }

    fun disconnect() {
        lastHost = null
        io.execute { closeQuietly() }
        connected = false
        status = "Desconectado"
    }

    fun stop() {
        lastHost = null
        io.execute { closeQuietly() }
        io.shutdown()
    }

    private fun closeQuietly() {
        try {
            socket?.close()
        } catch (e: Exception) {
        }
        socket = null
        out = null
    }

    private fun write(line: String): Boolean {
        val o = out ?: return false
        return try {
            o.write((line + "\n").toByteArray(Charsets.UTF_8))
            o.flush()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun sendLine(line: String) {
        io.execute {
            if (write(line)) return@execute
            // Conexión caída: se intenta reconectar una vez y reenviar
            val host = lastHost
            if (host != null && handshake(host, lastPort, lastPin) == 0 && write(line)) {
                main.post {
                    connected = true
                    status = infoText
                }
                return@execute
            }
            closeQuietly()
            main.post {
                connected = false
                status = if (host == null) "Sin conexión. Elige un dispositivo." else "Conexión perdida. Revisa que el otro teléfono esté encendido y en la misma wifi."
            }
        }
    }

    override fun send(key: Key) = sendLine("K:${key.name}")
    override fun sendText(text: String) = sendLine("T:" + text.replace("\n", " "))
    override fun moveMouse(dx: Int, dy: Int) = sendLine("M:$dx,$dy")
    override fun click(right: Boolean) = sendLine(if (right) "C:R" else "C:L")
    override fun scroll(amount: Int) = sendLine("S:$amount")
    override fun drag(start: Boolean) = sendLine(if (start) "D:1" else "D:0")
}
