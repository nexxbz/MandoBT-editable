package com.example.remoto

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address

data class Peer(
    val name: String,
    val host: String,
    val port: Int,
    val pin: String?,
    val fav: Boolean = false,
    val auto: Boolean = false
)

/** Guarda los teléfonos a los que ya te conectaste (con su PIN). */
class PeerStore(context: Context) {
    private val prefs = context.getSharedPreferences("peers", Context.MODE_PRIVATE)

    fun load(): List<Peer> =
        (prefs.getString("lista", "") ?: "").lines().mapNotNull { line ->
            val p = line.split("\t")
            val port = p.getOrNull(2)?.toIntOrNull()
            if (p.size >= 3 && port != null) {
                Peer(p[0], p[1], port, p.getOrNull(3)?.ifBlank { null }, p.getOrNull(4) == "1", p.getOrNull(5) == "1")
            } else null
        }

    fun save(list: List<Peer>) {
        prefs.edit()
            .putString("lista", list.joinToString("\n") { "${it.name}\t${it.host}\t${it.port}\t${it.pin ?: ""}\t${if (it.fav) "1" else "0"}\t${if (it.auto) "1" else "0"}" })
            .apply()
    }
}

/** Une los guardados con los encontrados en la red (la IP nueva gana, el PIN guardado se conserva). */
fun mergePeers(saved: List<Peer>, found: List<Peer>): List<Peer> {
    val map = LinkedHashMap<String, Peer>()
    saved.forEach { map[it.name] = it }
    found.forEach { f ->
        val old = map[f.name]
        map[f.name] = Peer(f.name, f.host, f.port, old?.pin, old?.fav ?: false, old?.auto ?: false)
    }
    return map.values.toList()
}

/** Busca por la red (mDNS) los teléfonos que tienen activado el servicio. Los resultados llegan en el hilo principal. */
class WifiDiscovery(
    context: Context,
    private val onFound: (Peer) -> Unit,
    private val onLost: (String) -> Unit
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private var listener: NsdManager.DiscoveryListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start() {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                main.post { queue.addLast(serviceInfo); next() }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                main.post { onLost(serviceInfo.serviceName) }
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        listener = l
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
        }
    }

    private fun next() {
        if (resolving) return
        val info = queue.removeFirstOrNull() ?: return
        resolving = true
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = done()
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = (serviceInfo.host as? Inet4Address)?.hostAddress
                    if (host != null) {
                        val peer = Peer(serviceInfo.serviceName, host, serviceInfo.port, null)
                        main.post { onFound(peer) }
                    }
                    done()
                }
            })
        } catch (e: Exception) {
            done()
        }
    }

    private fun done() {
        main.post { resolving = false; next() }
    }

    fun stop() {
        listener?.let {
            try {
                nsd.stopServiceDiscovery(it)
            } catch (e: Exception) {
            }
        }
        listener = null
        queue.clear()
    }
}
