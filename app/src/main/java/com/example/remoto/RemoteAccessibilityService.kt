package com.example.remoto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.nsd.NsdManager
import android.net.wifi.WifiManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlin.math.abs

/** PIN de 6 dígitos que protege este teléfono; el otro teléfono debe escribirlo para controlarlo. */
object ReceiverPin {
    private fun prefs(context: Context) = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    fun get(context: Context): String {
        var pin = prefs(context).getString("pin", null)
        if (pin == null) {
            pin = newPin()
            prefs(context).edit().putString("pin", pin).apply()
        }
        return pin
    }

    fun renew(context: Context): String {
        val pin = newPin()
        prefs(context).edit().putString("pin", pin).apply()
        return pin
    }

    private fun newPin(): String = (100000 + SecureRandom().nextInt(900000)).toString()
}

/**
 * Servicio de accesibilidad: abre un puerto por wifi, se anuncia en la red y ejecuta las órdenes
 * (atrás, inicio, volumen, toques, deslizamientos, texto) de quien conozca el PIN.
 */
class RemoteAccessibilityService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var server: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var cursor: CursorView? = null
    private var cursorLp: WindowManager.LayoutParams? = null
    private var cx = 0f
    private var cy = 0f
    private var cursorActive = false
    private var ring: RingView? = null
    private var ringLp: WindowManager.LayoutParams? = null
    private var ringBounds: Rect? = null
    private val hideRing = Runnable { ring?.visibility = View.GONE }
    private class Cand(val node: AccessibilityNodeInfo, val rect: Rect)
    private val hideCursor = Runnable {
        cursor?.visibility = View.GONE
        cursorActive = false
    }
    private var cpuLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val releaseAwake = Runnable { releaseAwakeLocks() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val m = resources.displayMetrics
        cx = m.widthPixels / 2f
        cy = m.heightPixels / 2f
        startServer()
        registerNsd()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        releaseAwakeLocks()
        try {
            server?.close()
        } catch (e: Exception) {
        }
        server = null
        try {
            regListener?.let { nsd?.unregisterService(it) }
        } catch (e: Exception) {
        }
        regListener = null
        main.post {
            removeCursor()
            removeRing()
        }
    }

    // ---------- Red ----------

    private fun startServer() {
        if (server != null) return
        Thread {
            try {
                val ss = ServerSocket(CONTROL_PORT)
                server = ss
                while (!ss.isClosed) {
                    val client = ss.accept()
                    Thread { handleClient(client) }.start()
                }
            } catch (e: Exception) {
                server = null
            }
        }.start()
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val out = client.getOutputStream()
            val hello = reader.readLine() ?: return
            if (hello != "HELLO ${ReceiverPin.get(this)}") {
                Thread.sleep(1000)
                out.write("NO\n".toByteArray())
                out.flush()
                return
            }
            out.write("OK ${Build.MODEL}|${Build.VERSION.SDK_INT}\n".toByteArray())
            out.flush()
            client.soTimeout = 0
            while (true) {
                val line = reader.readLine() ?: break
                main.post { execute(line) }
            }
        } catch (e: Exception) {
        } finally {
            try {
                client.close()
            } catch (e: Exception) {
            }
        }
    }

    private fun registerNsd() {
        val manager = getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = manager
        val info = NsdServiceInfo()
        info.serviceName = Build.MODEL
        info.serviceType = SERVICE_TYPE
        info.port = CONTROL_PORT
        val l = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
        }
        regListener = l
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            regListener = null
        }
    }

    // ---------- Órdenes ----------

    private fun execute(line: String) {
        val idx = line.indexOf(':')
        if (idx < 0) return
        val cmd = line.substring(0, idx)
        val arg = line.substring(idx + 1)
        when (cmd) {
            "K" -> key(arg)
            "T" -> typeText(arg)
            "M" -> {
                val p = arg.split(",")
                moveCursor(p.getOrNull(0)?.toIntOrNull() ?: 0, p.getOrNull(1)?.toIntOrNull() ?: 0)
            }
            "C" -> clickAtCursor(arg == "R")
            "S" -> scrollAtCursor(arg.toIntOrNull() ?: 0)
            "D" -> if (arg == "1") dragStart() else dragEnd()
        }
    }

    private fun key(name: String) {
        when (name) {
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "MENU" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "SETTINGS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "POWER" -> power()
            "VOL_UP" -> volume(AudioManager.ADJUST_RAISE)
            "VOL_DOWN" -> volume(AudioManager.ADJUST_LOWER)
            "MUTE" -> volume(AudioManager.ADJUST_TOGGLE_MUTE)
            "PLAY" -> media(KeyEvent.KEYCODE_MEDIA_PLAY)
            "PAUSE" -> media(KeyEvent.KEYCODE_MEDIA_PAUSE)
            "REWIND" -> media(KeyEvent.KEYCODE_MEDIA_REWIND)
            "FORWARD" -> media(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            "BRIGHT_UP" -> brightness(25)
            "BRIGHT_DOWN" -> brightness(-25)
            "OK" -> dpad(4)
            "UP" -> dpad(0)
            "DOWN" -> dpad(1)
            "LEFT" -> dpad(2)
            "RIGHT" -> dpad(3)
            "STOP" -> media(KeyEvent.KEYCODE_MEDIA_STOP)
            "PREV" -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "NEXT" -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
            "SCREENSHOT" -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            "SPACE" -> typeText(" ")
            "BACKSPACE" -> backspace()
            "ENTER" -> enter()
            else -> if (name.length == 2 && name[0] == 'N' && name[1].isDigit()) typeText(name.substring(1))
        }
    }

    private fun volume(direction: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun media(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun brightness(delta: Int) {
        if (!Settings.System.canWrite(this)) return
        val cr = contentResolver
        val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (current + delta).coerceIn(1, 255))
    }

    /**
     * Cruceta real. En Android 13+ el sistema ejecuta las teclas de dirección y OK como un control de TV
     * (mueve el foco en cualquier app). En versiones anteriores se mueve el foco desde accesibilidad.
     * dir: 0 arriba, 1 abajo, 2 izquierda, 3 derecha, 4 centro (OK).
     */
    private fun dpad(dir: Int) {
        if (Build.VERSION.SDK_INT >= 33) {
            val action = when (dir) {
                0 -> GLOBAL_ACTION_DPAD_UP
                1 -> GLOBAL_ACTION_DPAD_DOWN
                2 -> GLOBAL_ACTION_DPAD_LEFT
                3 -> GLOBAL_ACTION_DPAD_RIGHT
                else -> GLOBAL_ACTION_DPAD_CENTER
            }
            if (performGlobalAction(action)) return
        }
        if (dir == 4) okPress() else if (!moveNative(dir)) moveVirtual(dir, true)
    }

    // ---------- Cruceta por pantalla (Android 12 o menor, o si el sistema no la ejecuta) ----------
    // Se dibuja un recuadro sobre el elemento seleccionado; las flechas lo mueven al elemento más cercano
    // en esa dirección y OK lo pulsa. Funciona aunque la app no soporte navegación con teclado.

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<Cand>, depth: Int) {
        if (node == null || depth > 40 || out.size > 500) return
        if (!node.isVisibleToUser) return
        val r = Rect()
        node.getBoundsInScreen(r)
        if ((node.isClickable || node.isFocusable) && node.isEnabled && r.width() > 8 && r.height() > 8) {
            out.add(Cand(node, r))
        }
        for (i in 0 until node.childCount) collect(node.getChild(i), out, depth + 1)
    }

    /**
     * Si la app ya tiene un elemento con foco (como las apps de TV), se usa la navegación nativa del sistema:
     * el foco se mueve con el mismo buscador de foco que usan las flechas de un teclado y la app dibuja su
     * propio resaltado. Devuelve false si no hay foco y entonces se usa el recuadro.
     */
    private fun moveNative(dir: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val direction = when (dir) {
            0 -> View.FOCUS_UP
            1 -> View.FOCUS_DOWN
            2 -> View.FOCUS_LEFT
            else -> View.FOCUS_RIGHT
        }
        val next = current.focusSearch(direction)
        if (next != null && next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
            removeRing()
            return true
        }
        // Borde de la lista: se desplaza como lo haría la app
        val r = Rect()
        current.getBoundsInScreen(r)
        scrollForDirection(root, r, dir)
        return true
    }

    private fun moveVirtual(dir: Int, retry: Boolean) {
        val root = rootInActiveWindow ?: return
        val all = ArrayList<Cand>()
        collect(root, all, 0)
        val m = resources.displayMetrics
        val screenArea = m.widthPixels.toFloat() * m.heightPixels
        var list: List<Cand> = all.filter { it.rect.width().toFloat() * it.rect.height() < screenArea * 0.7f }
        val focusables = list.filter { it.node.isFocusable }
        if (focusables.size >= 2) list = focusables
        if (list.isEmpty()) return

        var cur: Rect? = ringBounds?.let { r -> list.firstOrNull { it.rect == r }?.rect }
        if (cur == null) {
            val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (f != null) {
                val fr = Rect()
                f.getBoundsInScreen(fr)
                if (list.any { it.rect == fr }) cur = fr
            }
        }
        if (cur == null) {
            // Primera pulsación: se marca el elemento más arriba y a la izquierda
            val first = list.minWithOrNull(compareBy<Cand>({ it.rect.top / 40 }, { it.rect.left }))
            if (first != null) showRing(first.rect)
            return
        }

        val cx = cur.exactCenterX()
        val cy = cur.exactCenterY()
        var best: Cand? = null
        var bestScore = Float.MAX_VALUE
        for (c in list) {
            if (c.rect == cur) continue
            val dx = c.rect.exactCenterX() - cx
            val dy = c.rect.exactCenterY() - cy
            val primary = when (dir) {
                0 -> -dy
                1 -> dy
                2 -> -dx
                else -> dx
            }
            if (primary <= 4f) continue
            val perp = if (dir < 2) abs(dx) else abs(dy)
            val score = primary + 2f * perp
            if (score < bestScore) {
                bestScore = score
                best = c
            }
        }
        val target = best
        if (target != null) {
            showRing(target.rect)
            return
        }
        // No hay más elementos en esa dirección: se desplaza la lista y se vuelve a intentar una vez
        if (retry && scrollForDirection(root, cur, dir)) {
            main.postDelayed({ moveVirtual(dir, false) }, 450)
        }
    }

    private fun scrollForDirection(root: AccessibilityNodeInfo, cur: Rect, dir: Int): Boolean {
        val action = when (dir) {
            0 -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
            1 -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
            2 -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
            else -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
        }
        var best: AccessibilityNodeInfo? = null
        var bestArea = Long.MAX_VALUE
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40) return
            if (n.isVisibleToUser && n.isScrollable) {
                val r = Rect()
                n.getBoundsInScreen(r)
                if (r.contains(cur.centerX(), cur.centerY())) {
                    val area = r.width().toLong() * r.height()
                    if (area < bestArea) {
                        bestArea = area
                        best = n
                    }
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return best?.performAction(action) ?: false
    }

    /** OK: pulsa el elemento marcado; si no se puede, toca en su centro. */
    private fun okPress() {
        val r = ringBounds
        if (r != null && ring?.visibility == View.VISIBLE) {
            val root = rootInActiveWindow
            if (root != null) {
                val all = ArrayList<Cand>()
                collect(root, all, 0)
                val match = all.firstOrNull { it.rect == r }
                var n: AccessibilityNodeInfo? = match?.node
                while (n != null) {
                    if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                    n = n.parent
                }
            }
            val path = Path()
            path.moveTo(r.exactCenterX(), r.exactCenterY())
            gesture(path, 60)
            return
        }
        // Sin recuadro: se pulsa el elemento con foco, o el cursor si se acaba de usar el panel táctil
        val root = rootInActiveWindow
        var n: AccessibilityNodeInfo? = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            n = n.parent
        }
        if (cursorActive) clickAtCursor(false)
    }

    private fun showRing(r: Rect) {
        ringBounds = r
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val existing = ring
        val lp = ringLp
        if (existing == null || lp == null) {
            val view = RingView(this)
            val params = WindowManager.LayoutParams(
                r.width(), r.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = r.left
            params.y = r.top
            try {
                wm.addView(view, params)
                ring = view
                ringLp = params
            } catch (e: Exception) {
                return
            }
        } else {
            lp.width = r.width()
            lp.height = r.height()
            lp.x = r.left
            lp.y = r.top
            try {
                wm.updateViewLayout(existing, lp)
            } catch (e: Exception) {
            }
        }
        ring?.visibility = View.VISIBLE
        ring?.invalidate()
        main.removeCallbacks(hideRing)
        main.postDelayed(hideRing, 10000)
    }

    private fun removeRing() {
        val view = ring ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (e: Exception) {
        }
        ring = null
        ringLp = null
        ringBounds = null
    }

    // ---------- Encender / apagar ----------

    /** Un solo botón: si la pantalla está encendida la apaga; si está apagada la enciende. */
    private fun power() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) {
            holdAwakeForWake()
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            wakeScreen()
        }
    }

    /** Con la pantalla apagada se mantiene la conexión viva un rato para poder volver a encenderla. */
    @Suppress("DEPRECATION")
    private fun holdAwakeForWake() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (cpuLock == null) cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "controlremoto:cpu")
            cpuLock?.acquire(30 * 60 * 1000L)
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiLock == null) wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "controlremoto:wifi")
            if (wifiLock?.isHeld == false) wifiLock?.acquire()
            main.removeCallbacks(releaseAwake)
            main.postDelayed(releaseAwake, 30 * 60 * 1000L)
        } catch (e: Exception) {
        }
    }

    private fun releaseAwakeLocks() {
        try {
            if (cpuLock?.isHeld == true) cpuLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "controlremoto:wake"
            )
            wl.acquire(5000)
        } catch (e: Exception) {
        }
        main.removeCallbacks(releaseAwake)
        releaseAwakeLocks()
    }

    private fun backspace() {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val current = if (node.isShowingHintText) "" else (node.text?.toString() ?: "")
        if (current.isEmpty()) return
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current.dropLast(1))
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun enter() {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val ok = node?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id) ?: false
        if (!ok) dpad(4)
    }

    private fun typeText(text: String) {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val current = if (node.isShowingHintText) "" else (node.text?.toString() ?: "")
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current + text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ---------- Gestos y cursor ----------

    private fun gesture(path: Path, duration: Long) {
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(g, null, null)
    }

    private fun swipe(fx1: Float, fy1: Float, fx2: Float, fy2: Float) {
        val m = resources.displayMetrics
        val path = Path()
        path.moveTo(fx1 * m.widthPixels, fy1 * m.heightPixels)
        path.lineTo(fx2 * m.widthPixels, fy2 * m.heightPixels)
        gesture(path, 250)
    }

    private fun moveCursor(dx: Int, dy: Int) {
        val m = resources.displayMetrics
        cx = (cx + dx).coerceIn(0f, m.widthPixels.toFloat())
        cy = (cy + dy).coerceIn(0f, m.heightPixels.toFloat())
        showCursor()
        if (dragStroke != null) dragPump()
    }

    // ---------- Apretar y deslizar (arrastrar) ----------
    // Se mantiene el dedo "apretado" en el cursor con un gesto continuo y se va moviendo con el panel táctil.

    private var dragStroke: GestureDescription.StrokeDescription? = null
    private var dragBusy = false
    private var dragEndRequested = false
    private var dragLastX = 0f
    private var dragLastY = 0f

    private fun dragStart() {
        if (dragStroke != null) return
        dragLastX = cx
        dragLastY = cy
        dragEndRequested = false
        val path = Path()
        path.moveTo(cx, cy)
        val stroke = GestureDescription.StrokeDescription(path, 0L, 1L, true)
        dragStroke = stroke
        dragBusy = true
        val ok = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    main.post { dragBusy = false; dragPump() }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    main.post { dragBusy = false; dragStroke = null }
                }
            },
            null
        )
        if (!ok) {
            dragBusy = false
            dragStroke = null
        }
        showCursor()
    }

    private fun dragEnd() {
        if (dragStroke == null) return
        dragEndRequested = true
        dragPump()
    }

    private fun dragPump() {
        val stroke = dragStroke ?: return
        if (dragBusy) return
        val end = dragEndRequested
        if (!end && cx == dragLastX && cy == dragLastY) return
        val path = Path()
        path.moveTo(dragLastX, dragLastY)
        path.lineTo(cx, cy)
        val next = stroke.continueStroke(path, 0L, 20L, !end)
        dragLastX = cx
        dragLastY = cy
        if (end) {
            dragStroke = null
            dragEndRequested = false
        } else {
            dragStroke = next
        }
        dragBusy = true
        val ok = dispatchGesture(
            GestureDescription.Builder().addStroke(next).build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    main.post { dragBusy = false; dragPump() }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    main.post { dragBusy = false; dragStroke = null; dragEndRequested = false }
                }
            },
            null
        )
        if (!ok) {
            dragBusy = false
            dragStroke = null
            dragEndRequested = false
        }
    }

    private fun clickAtCursor(hold: Boolean) {
        val path = Path()
        path.moveTo(cx, cy)
        gesture(path, if (hold) 700L else 60L)
        showCursor()
    }

    private fun scrollAtCursor(amount: Int) {
        if (amount == 0) return
        val m = resources.displayMetrics
        val dist = 150f * amount
        val y2 = (cy + dist).coerceIn(0f, m.heightPixels.toFloat())
        val path = Path()
        path.moveTo(cx, cy)
        path.lineTo(cx, y2)
        gesture(path, 250)
        showCursor()
    }

    private fun showCursor() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val curW = (22 * density).toInt()
        val curH = (34 * density).toInt()
        val x = cx.toInt()
        val y = cy.toInt()
        val existing = cursor
        val lp = cursorLp
        if (existing == null || lp == null) {
            val view = CursorView(this)
            val params = WindowManager.LayoutParams(
                curW, curH,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = x
            params.y = y
            try {
                wm.addView(view, params)
                cursor = view
                cursorLp = params
            } catch (e: Exception) {
                return
            }
        } else {
            lp.x = x
            lp.y = y
            try {
                wm.updateViewLayout(existing, lp)
            } catch (e: Exception) {
            }
        }
        cursor?.visibility = View.VISIBLE
        cursorActive = true
        main.removeCallbacks(hideCursor)
        main.postDelayed(hideCursor, 4000)
    }

    private fun removeCursor() {
        val view = cursor ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (e: Exception) {
        }
        cursor = null
        cursorLp = null
    }
}

/** Flecha de ratón: la punta (arriba a la izquierda) marca dónde está el cursor. */
class CursorView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = Path()
        p.moveTo(2f, 2f)
        p.lineTo(2f, h * 0.82f)
        p.lineTo(w * 0.27f, h * 0.64f)
        p.lineTo(w * 0.45f, h * 0.97f)
        p.lineTo(w * 0.62f, h * 0.89f)
        p.lineTo(w * 0.45f, h * 0.57f)
        p.lineTo(w * 0.82f, h * 0.57f)
        p.close()
        canvas.drawPath(p, fill)
        canvas.drawPath(p, edge)
    }
}

/** Recuadro que marca el elemento seleccionado con la cruceta. */
class RingView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF
        style = Paint.Style.FILL
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E88E5.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 12f
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    override fun onDraw(canvas: Canvas) {
        val rect = RectF(6f, 6f, width - 6f, height - 6f)
        canvas.drawRoundRect(rect, 16f, 16f, fill)
        canvas.drawRoundRect(rect, 16f, 16f, outline)
        canvas.drawRoundRect(rect, 16f, 16f, stroke)
    }
}
