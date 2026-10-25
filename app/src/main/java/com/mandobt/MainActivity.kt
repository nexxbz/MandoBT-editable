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
import android.widget.*

@SuppressLint("MissingPermission", "ClickableViewAccessibility")
class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var hid: HidController
    private lateinit var status: TextView
    private lateinit var pad: PadView
    private lateinit var touch: TouchPad
    private lateinit var kb: ScrollView
    private lateinit var editBar: LinearLayout
    private var cur = "xbox"
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("mando", MODE_PRIVATE)
        cur = prefs.getString("profile", "xbox") ?: "xbox"
        status = TextView(this).apply {
            setTextColor(0xFFB8C0D0.toInt()); textSize = 12f; setPadding(dp(8), 0, dp(8), 0); text = "Iniciando…"; maxLines = 1
        }
        hid = HidController(this) { s -> runOnUiThread { status.text = s } }
        pad = PadView(this)
        touch = TouchPad(this)
        pad.onReport = { hid.send(1, it) }
        touch.onMouse = { bt, dx, dy, w -> sendMouse(bt, dx, dy, w) }
        loadProfile(cur)

        val menuBtn = Button(this).apply {
            text = "☰"; textSize = 20f; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0); alpha = .85f; setOnClickListener { menu() }
        }
        editBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; visibility = View.GONE
            addView(small("Más grande") { pad.selected?.let { it.size = (it.size * 1.1f).coerceAtMost(.35f); pad.invalidate() } }, LinearLayout.LayoutParams(-2, dp(40)))
            addView(small("Más pequeño") { pad.selected?.let { it.size = (it.size / 1.1f).coerceAtLeast(.03f); pad.invalidate() } }, LinearLayout.LayoutParams(-2, dp(40)))
            addView(small("Guardar") { prefs.edit().putString("layout_$cur", pad.toJson()).apply(); toast("Distribución guardada") }, LinearLayout.LayoutParams(-2, dp(40)))
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

        kb = ScrollView(this).apply { addView(buildKeyboard()); visibility = View.GONE }
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

    override fun onDestroy() { hid.stop(); super.onDestroy() }

    private fun ensurePerm() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE), 1)
        else hid.start()
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        if (g.isNotEmpty() && g.all { it == PackageManager.PERMISSION_GRANTED }) hid.start()
        else status.text = "Falta el permiso de Bluetooth"
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

    private fun menu() {
        val consola = Templates.names.first { it.first == cur }.second
        val items = arrayOf(
            "Mando", "Ratón", "Teclado", "Consola: $consola", "Sensibilidad y ajustes",
            if (pad.edit) "Terminar de editar" else "Editar botones",
            "Hacerse visible para emparejar", "Conectar a un equipo"
        )
        AlertDialog.Builder(this).setTitle(status.text.toString()).setItems(items) { _, i ->
            when (i) {
                0 -> show(0); 1 -> show(1); 2 -> show(2)
                3 -> profileDialog(); 4 -> settings()
                5 -> { show(0); toggleEdit() }
                6 -> visible(); 7 -> connectDialog()
            }
        }.show()
    }

    private fun profileDialog() {
        val n = Templates.names
        AlertDialog.Builder(this).setTitle("Consola").setItems(n.map { it.second }.toTypedArray()) { _, i ->
            loadProfile(n[i].first)
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
        val l = hid.bonded
        if (l.isEmpty()) { toast("Primero empareja el otro equipo desde los ajustes de Bluetooth, o pulsa Visible y búscalo desde él."); return }
        val names = l.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Conectar a…").setItems(names) { _, i -> hid.connect(l[i]) }.show()
    }

    private fun sendMouse(bt: Int, dx: Int, dy: Int, w: Int) {
        var x = dx; var y = dy; var ww = w
        do {
            val cx = x.coerceIn(-127, 127); val cy = y.coerceIn(-127, 127); val cw = ww.coerceIn(-127, 127)
            hid.send(2, byteArrayOf(bt.toByte(), cx.toByte(), cy.toByte(), cw.toByte()))
            x -= cx; y -= cy; ww -= cw
        } while (x != 0 || y != 0 || ww != 0)
    }

    private fun applyPrefs() {
        pad.gain = prefs.getInt("gain", 100) / 100f
        pad.dead = prefs.getInt("dead", 8) / 100f
        pad.pcMode = prefs.getBoolean("pcmode", false)
        touch.speed = prefs.getInt("mouse", 150) / 100f
        touch.scroll = .05f * prefs.getInt("scroll", 100) / 100f
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

    // ---------------- Teclado ----------------
    private val rows = listOf(
        listOf("Esc", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "⌫"),
        listOf("Tab", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "[", "]", "\\"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", ";", "'", "Enter"),
        listOf("Shift", "z", "x", "c", "v", "b", "n", "m", ",", ".", "/", "↑"),
        listOf("Ctrl", "Alt", "Win", "Espacio", "←", "↓", "→")
    )
    private val modBit = mapOf("Ctrl" to 1, "Shift" to 2, "Alt" to 4, "Win" to 8)
    private val special = mapOf(
        "Enter" to 0x28, "Esc" to 0x29, "⌫" to 0x2A, "Tab" to 0x2B, "Espacio" to 0x2C, "-" to 0x2D, "=" to 0x2E,
        "[" to 0x2F, "]" to 0x30, "\\" to 0x31, ";" to 0x33, "'" to 0x34, "," to 0x36, "." to 0x37, "/" to 0x38,
        "→" to 0x4F, "←" to 0x50, "↓" to 0x51, "↑" to 0x52
    )

    private fun usage(k: String): Int = when {
        k.length == 1 && k[0] in 'a'..'z' -> 4 + (k[0] - 'a')
        k.length == 1 && k[0] in '1'..'9' -> 0x1E + (k[0] - '1')
        k == "0" -> 0x27
        else -> special[k] ?: 0
    }

    private fun sendKb() {
        val r = ByteArray(8)
        r[0] = mods.toByte()
        keysDown.take(6).forEachIndexed { i, k -> r[2 + i] = k.toByte() }
        hid.send(3, r)
    }

    private fun buildKeyboard(): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(52), dp(4), dp(4)) }
        for (r in rows) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in r) {
                val b = Button(this).apply {
                    text = k; isAllCaps = false; textSize = 14f; minWidth = 0; minimumWidth = 0
                    setPadding(0, 0, 0, 0)
                }
                val mb = modBit[k]
                if (mb != null) modBtns[mb] = b
                val u = usage(k)
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
                line.addView(b, LinearLayout.LayoutParams(0, dp(46), if (k == "Espacio") 4f else 1f))
            }
            col.addView(line)
        }
        return col
    }
}
