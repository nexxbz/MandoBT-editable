package com.mandobt

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.SharedPreferences
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.Surface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import android.widget.*

@SuppressLint("MissingPermission", "ClickableViewAccessibility")
class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private val hid: HidController? get() = HidHolder.hid
    private var sensorMgr: SensorManager? = null
    private lateinit var status: TextView
    private lateinit var pad: PadView
    private lateinit var touch: TouchPad
    private lateinit var kb: LinearLayout
    private lateinit var touch2: TouchPad
    private lateinit var editBar: LinearLayout
    private var cur = "xbox"
    private var activo: String? = null
    private var mods = 0
    private val keysDown = mutableListOf<Int>()
    private val modBtns = mutableMapOf<Int, Button>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { f() }
    }

    private fun small(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; textSize = 12f; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(10), 0, dp(10), 0); alpha = .9f; setOnClickListener { f() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try {
            construir()
        } catch (e: Throwable) {
            val tv = TextView(this).apply {
                text = "Error al iniciar. Envía una captura de este texto:\n\n" + android.util.Log.getStackTraceString(e)
                setTextColor(Color.WHITE); textSize = 11f; setPadding(dp(12), dp(12), dp(12), dp(12))
            }
            setContentView(ScrollView(this).apply { setBackgroundColor(0xFF12151C.toInt()); addView(tv) })
        }
    }

    private fun construir() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("mando", MODE_PRIVATE)
        cur = prefs.getString("profile", "xbox") ?: "xbox"
        status = TextView(this).apply {
            setTextColor(0xFFB8C0D0.toInt()); textSize = 12f; setPadding(dp(8), 0, dp(8), 0); text = HidHolder.lastStatus; maxLines = 1
        }
        HidHolder.onStatus = { s -> runOnUiThread { status.text = s } }
        sensorMgr = getSystemService(SensorManager::class.java)
        pad = PadView(this)
        touch = TouchPad(this)
        touch2 = TouchPad(this)
        pad.onReport = { hid?.send(1, it) }
        pad.onToggleGyro = { setGyro(!pad.gyroOn) }
        touch.onMouse = { bt, dx, dy, w -> sendMouse(bt, dx, dy, w) }
        touch2.onMouse = touch.onMouse
        loadProfile(cur)
        activo = prefs.getString("perfil_activo", null)
        activo?.let { if (!cargarPerfil(it)) activo = null }

        val menuBtn = Button(this).apply {
            text = "☰"; textSize = 20f; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0); alpha = .85f; setOnClickListener { menu() }
        }
        editBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; visibility = View.GONE
            addView(small("Más grande") { pad.selected?.let { it.size = (it.size * 1.1f).coerceAtMost(.35f); pad.invalidate() } }, LinearLayout.LayoutParams(-2, dp(40)))
            addView(small("Más pequeño") { pad.selected?.let { it.size = (it.size / 1.1f).coerceAtLeast(.03f); pad.invalidate() } }, LinearLayout.LayoutParams(-2, dp(40)))
            addView(small("Guardar") { guardarActual() }, LinearLayout.LayoutParams(-2, dp(40)))
            addView(small("Restablecer") {
                prefs.edit().remove("layout_$cur").apply()
                pad.ctls = Templates.build(cur); pad.selected = null; pad.invalidate()
            }, LinearLayout.LayoutParams(-2, dp(40)))
            addView(small("Listo") { toggleEdit() }, LinearLayout.LayoutParams(-2, dp(40)))
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(menuBtn, LinearLayout.LayoutParams(dp(48), dp(40)))
            addView(status)
            addView(editBar)
        }

        kb = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; visibility = View.GONE
            setPadding(dp(4), dp(48), dp(4), dp(4))
            addView(buildKeyboard(), LinearLayout.LayoutParams(0, -1, 7f))
            addView(touch2, LinearLayout.LayoutParams(0, -1, 3f).apply { leftMargin = dp(6) })
        }
        touch.visibility = View.GONE
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF12151C.toInt())
            addView(pad, FrameLayout.LayoutParams(mp, mp))
            addView(touch, FrameLayout.LayoutParams(mp, mp))
            addView(kb, FrameLayout.LayoutParams(mp, mp))
            addView(topRow, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(4) })
        }
        setContentView(root)
        immersive()
        applyPrefs()
        ensurePerm()
        if (prefs.getBoolean("gyro_on", false)) setGyro(true)

        val splash = ImageView(this).apply {
            setImageResource(R.drawable.portada)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(resources.getColor(R.color.portada_fondo, theme))
            isClickable = true
        }
        root.addView(splash, FrameLayout.LayoutParams(mp, mp))
        splash.postDelayed({
            splash.animate().alpha(0f).setDuration(400).withEndAction { root.removeView(splash) }.start()
        }, 1400)
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onDestroy() {
        HidHolder.onStatus = null
        sensorMgr?.unregisterListener(gyroListener)
        super.onDestroy()
    }

    private fun ensurePerm() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1) else iniciarServicio()
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) iniciarServicio()
        else status.text = "Falta el permiso de Bluetooth"
    }

    private fun iniciarServicio() {
        startForegroundService(Intent(this, KeepAliveService::class.java))
    }

    private fun show(m: Int) {
        pad.visibility = if (m == 0) View.VISIBLE else View.GONE
        touch.visibility = if (m == 1) View.VISIBLE else View.GONE
        kb.visibility = if (m == 2) View.VISIBLE else View.GONE
        if (m != 0 && pad.edit) toggleEdit()
    }

    private fun toggleEdit() {
        pad.setEdit(!pad.edit)
        editBar.visibility = if (pad.edit) View.VISIBLE else View.GONE
        if (pad.edit) toast("Arrastra los botones. Toca uno para cambiar su tamaño.")
    }

    // ---------------- Giroscopio ----------------
    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            @Suppress("DEPRECATION") val rot = windowManager.defaultDisplay.rotation
            val wx = e.values[0]
            val wy = e.values[1]
            val right: Float
            val up: Float
            when (rot) {
                Surface.ROTATION_90 -> { right = wy; up = -wx }
                Surface.ROTATION_270 -> { right = -wy; up = wx }
                Surface.ROTATION_180 -> { right = -wx; up = -wy }
                else -> { right = wx; up = wy }
            }
            val k = prefs.getInt("gyro", 100) / 100f / 3f
            val inv = if (prefs.getBoolean("gyroinv", false)) -1f else 1f
            pad.setGyro(-up * k, right * k * inv)
        }

        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private fun setGyro(on: Boolean) {
        val sm = sensorMgr
        sm?.unregisterListener(gyroListener)
        var activo = on
        if (on) {
            val s = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            if (s == null) { toast("Este teléfono no tiene giroscopio"); activo = false }
            else sm.registerListener(gyroListener, s, SensorManager.SENSOR_DELAY_GAME)
        }
        pad.gyroOn = activo
        pad.setGyro(0f, 0f)
        prefs.edit().putBoolean("gyro_on", activo).apply()
        pad.invalidate()
    }

    private fun menu() {
        val consola = Templates.names.first { it.first == cur }.second
        val items = arrayOf(
            "Mando", "Ratón", "Teclado", "Consola: $consola",
            "Mis perfiles" + (activo?.let { " ($it)" } ?: ""),
            "Giroscopio: " + if (pad.gyroOn) "activado" else "desactivado",
            "Sensibilidad y ajustes",
            if (pad.edit) "Terminar de editar" else "Editar botones",
            "Hacerse visible para emparejar", "Conectar a un equipo",
            "Batería: mantener conexión en segundo plano",
            "Salir y desconectar"
        )
        AlertDialog.Builder(this).setTitle(status.text.toString()).setItems(items) { _, i ->
            when (i) {
                0 -> show(0); 1 -> show(1); 2 -> show(2)
                3 -> profileDialog(); 4 -> perfilesDialog()
                5 -> setGyro(!pad.gyroOn)
                6 -> settings()
                7 -> { show(0); toggleEdit() }
                8 -> visible(); 9 -> connectDialog()
                10 -> startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                11 -> { stopService(Intent(this, KeepAliveService::class.java)); finish() }
            }
        }.show()
    }

    // ---------------- Perfiles de distribución ----------------
    private fun perfiles(): MutableList<String> {
        val a = org.json.JSONArray(prefs.getString("perfiles", "[]") ?: "[]")
        return MutableList(a.length()) { a.getString(it) }
    }

    private fun guardarPerfil(name: String) {
        val l = perfiles()
        if (name !in l) l.add(name)
        prefs.edit().putString("perfiles", org.json.JSONArray(l).toString())
            .putString("pf_$name", pad.toJson()).putString("perfil_activo", name).apply()
        activo = name
        toast("Perfil «$name» guardado")
    }

    private fun cargarPerfil(name: String): Boolean {
        val s = prefs.getString("pf_$name", null) ?: return false
        return try {
            pad.load(s); activo = name
            prefs.edit().putString("perfil_activo", name).apply(); true
        } catch (_: Exception) { false }
    }

    private fun borrarPerfil(name: String) {
        val l = perfiles(); l.remove(name)
        prefs.edit().putString("perfiles", org.json.JSONArray(l).toString()).remove("pf_$name").apply()
        if (activo == name) { activo = null; prefs.edit().remove("perfil_activo").apply() }
    }

    private fun pedirNombre(f: (String) -> Unit) {
        val et = EditText(this).apply { hint = "Nombre del perfil"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Guardar como perfil").setView(et)
            .setPositiveButton("Guardar") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) f(n) else toast("Escribe un nombre")
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun guardarActual() {
        val a = activo
        if (a != null) guardarPerfil(a) else pedirNombre { guardarPerfil(it) }
    }

    private fun perfilesDialog() {
        val l = perfiles()
        val items = (l.map { if (it == activo) "✓ $it" else it } + "＋ Guardar la distribución actual como perfil nuevo").toTypedArray()
        AlertDialog.Builder(this).setTitle("Mis perfiles").setItems(items) { _, i ->
            if (i == l.size) pedirNombre { guardarPerfil(it) }
            else {
                val n = l[i]
                AlertDialog.Builder(this).setTitle(n)
                    .setItems(arrayOf("Usar este perfil", "Guardar la distribución actual aquí", "Borrar perfil")) { _, j ->
                        when (j) {
                            0 -> if (cargarPerfil(n)) show(0) else toast("No se pudo cargar")
                            1 -> guardarPerfil(n)
                            2 -> { borrarPerfil(n); toast("Perfil borrado") }
                        }
                    }.show()
            }
        }.show()
    }

    private fun profileDialog() {
        val n = Templates.names
        AlertDialog.Builder(this).setTitle("Consola").setItems(n.map { it.second }.toTypedArray()) { _, i ->
            loadProfile(n[i].first)
            activo = null
            prefs.edit().remove("perfil_activo").apply()
            prefs.edit().putString("profile", cur).apply()
            show(0)
        }.show()
    }

    private fun loadProfile(key: String) {
        cur = key
        val s = prefs.getString("layout_$key", null)
        try {
            if (s != null) pad.load(s) else { pad.ctls = Templates.build(key); pad.selected = null }
        } catch (_: Exception) { pad.ctls = Templates.build(key) }
        pad.invalidate()
    }

    private fun visible() {
        startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300))
    }

    private fun connectDialog() {
        val l = hid?.bonded ?: emptyList()
        if (l.isEmpty()) { toast("Primero empareja el otro equipo desde los ajustes de Bluetooth, o pulsa Visible y búscalo desde él."); return }
        val names = l.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Conectar a…").setItems(names) { _, i -> hid?.connect(l[i]) }.show()
    }

    private fun sendMouse(bt: Int, dx: Int, dy: Int, w: Int) {
        var x = dx; var y = dy; var ww = w
        do {
            val cx = x.coerceIn(-127, 127); val cy = y.coerceIn(-127, 127); val cw = ww.coerceIn(-127, 127)
            hid?.send(2, byteArrayOf(bt.toByte(), cx.toByte(), cy.toByte(), cw.toByte()))
            x -= cx; y -= cy; ww -= cw
        } while (x != 0 || y != 0 || ww != 0)
    }

    private fun applyPrefs() {
        pad.gain = prefs.getInt("gain", 100) / 100f
        pad.dead = prefs.getInt("dead", 8) / 100f
        pad.pcMode = prefs.getBoolean("pcmode", false)
        touch.speed = prefs.getInt("mouse", 150) / 100f
        touch.scroll = .05f * prefs.getInt("scroll", 100) / 100f
        touch2.speed = touch.speed
        touch2.scroll = touch.scroll
    }

    private fun settings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(10), dp(20), 0) }
        fun seek(label: String, key: String, def: Int, min: Int, max: Int) {
            val tv = TextView(this); val sb = SeekBar(this)
            tv.text = "$label: ${prefs.getInt(key, def)}%"
            sb.max = max - min; sb.progress = prefs.getInt(key, def) - min
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    tv.text = "$label: ${p + min}%"
                    prefs.edit().putInt(key, p + min).apply(); applyPrefs()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            box.addView(tv); box.addView(sb)
        }
        seek("Sensibilidad de palancas", "gain", 100, 50, 200)
        seek("Zona muerta de palancas", "dead", 8, 0, 30)
        seek("Velocidad del ratón", "mouse", 150, 50, 400)
        seek("Velocidad de desplazamiento", "scroll", 100, 30, 300)
        seek("Sensibilidad del giroscopio", "gyro", 100, 30, 300)
        box.addView(Switch(this).apply {
            text = "Invertir giro vertical"
            isChecked = prefs.getBoolean("gyroinv", false)
            setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("gyroinv", v).apply() }
        })
        box.addView(Switch(this).apply {
            text = "Orden de botones para PC"
            isChecked = prefs.getBoolean("pcmode", false)
            setPadding(0, dp(12), 0, 0)
            setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("pcmode", v).apply(); applyPrefs() }
        })
        box.addView(TextView(this).apply {
            text = "Apagado: orden estándar de Android y Linux (otro teléfono). Encendido: orden genérico para Windows."
            textSize = 12f
        })
        AlertDialog.Builder(this).setTitle("Ajustes").setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Cerrar", null).show()
    }

    // ---------------- Teclado completo ----------------
    private class K(val l: String, val w: Float = 1f)

    private val rows: List<List<K>> = listOf(
        listOf(K("Esc", 1.5f)) + (1..12).map { K("F$it") },
        listOf("`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=").map { K(it) } + K("⌫", 1.8f),
        listOf(K("Tab", 1.5f)) + listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "[", "]").map { K(it) } + K("\\", 1.3f),
        listOf(K("Caps", 1.8f)) + listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ", "'").map { K(it) } + K("Enter", 2.2f),
        listOf(K("Shift", 2.3f)) + listOf("z", "x", "c", "v", "b", "n", "m", ",", ".", "/").map { K(it) } + K("↑") + K("Shift#", 1.4f),
        listOf(K("Ctrl", 1.3f), K("Win", 1.3f), K("Alt", 1.3f), K("Espacio", 5.5f), K("AltGr", 1.3f), K("←"), K("↓"), K("→"), K("Ctrl#", 1.3f))
    )
    private val modBit = mapOf("Ctrl" to 0x01, "Shift" to 0x02, "Alt" to 0x04, "Win" to 0x08,
        "Ctrl#" to 0x10, "Shift#" to 0x20, "AltGr" to 0x40)
    private val special = mapOf(
        "Enter" to 0x28, "Esc" to 0x29, "⌫" to 0x2A, "Tab" to 0x2B, "Espacio" to 0x2C, "-" to 0x2D, "=" to 0x2E,
        "[" to 0x2F, "]" to 0x30, "\\" to 0x31, "ñ" to 0x33, "'" to 0x34, "`" to 0x35, "," to 0x36, "." to 0x37,
        "/" to 0x38, "Caps" to 0x39, "→" to 0x4F, "←" to 0x50, "↓" to 0x51, "↑" to 0x52
    )

    private fun usage(k: String): Int = when {
        k.length == 1 && k[0] in 'a'..'z' -> 4 + (k[0] - 'a')
        k.length == 1 && k[0] in '1'..'9' -> 0x1E + (k[0] - '1')
        k == "0" -> 0x27
        k.length in 2..3 && k[0] == 'F' && (k.drop(1).toIntOrNull() ?: 0) in 1..12 -> 0x3A + k.drop(1).toInt() - 1
        else -> special[k] ?: 0
    }

    private fun shown(l: String) = if (l.length == 1 && l[0].isLetter()) l.uppercase() else l.replace("#", "")

    private fun sendKb() {
        val r = ByteArray(8)
        r[0] = mods.toByte()
        keysDown.take(6).forEachIndexed { i, k -> r[2 + i] = k.toByte() }
        hid?.send(3, r)
    }

    private fun buildKeyboard(): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in rows) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in r) {
                val b = Button(this).apply {
                    text = shown(k.l); isAllCaps = false; textSize = 11f
                    minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                    setPadding(0, 0, 0, 0)
                }
                val mb = modBit[k.l]
                if (mb != null) modBtns[mb] = b
                val u = usage(k.l)
                b.setOnTouchListener { v, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (mb != null) {
                                mods = mods xor mb
                                b.setTextColor(if (mods and mb != 0) Color.YELLOW else Color.WHITE)
                                sendKb()
                            } else if (u != 0) {
                                if (u !in keysDown) keysDown.add(u)
                                sendKb()
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (mb == null && u != 0) {
                            keysDown.remove(u)
                            mods = 0
                            modBtns.values.forEach { it.setTextColor(Color.WHITE) }
                            sendKb()
                        }
                    }
                    true
                }
                line.addView(b, LinearLayout.LayoutParams(0, -1, k.w).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
            }
            col.addView(line, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        return col
    }
}
