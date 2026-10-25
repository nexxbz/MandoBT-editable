package com.mandobt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Guarda el control Bluetooth en un solo lugar para que sobreviva a la pantalla. */
object HidHolder {
    var hid: HidController? = null
    var lastStatus: String = "Iniciando…"
    var onStatus: ((String) -> Unit)? = null

    fun status(s: String) {
        lastStatus = s
        onStatus?.invoke(s)
    }
}

/**
 * Servicio en primer plano: mantiene viva la conexión Bluetooth aunque salgas de la app.
 * Muestra una notificación fija mientras el control está activo.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (HidHolder.hid == null) {
            val h = HidController(applicationContext) { HidHolder.status(it) }
            HidHolder.hid = h
            h.start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mando", "Mando BT", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "mando")
            .setContentTitle("Mando BT")
            .setContentText("Control Bluetooth activo")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(1, n)
        return START_STICKY
    }

    override fun onDestroy() {
        HidHolder.hid?.stop()
        HidHolder.hid = null
        super.onDestroy()
    }
}
