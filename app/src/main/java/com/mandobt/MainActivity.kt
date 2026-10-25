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
    private lateinit var editBtn: Button
    private var cur = "xbox"
    private var mods = 0
    private val keysDown = mutableListOf<Int>()
    private val modBtns = mutableMapOf<Int, Button>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { f() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("mando", MODE_PRIVATE)
        cur = prefs.getString("profile", "xbox") ?: "xbox"
        status = TextView(this).apply {
            setTextColor(0xFFB8C0D0.toInt()); textSize = 13f; setPadding(dp(10), dp(4), dp(10), 0); text = "Iniciando…"
        }
        hid = HidController(this) { s -> runOnUiThread { status.text = s } }
        pad = PadView(this)
        touch = TouchPad(this)
        pad.onReport = { hid.send(1, it) }
        touch.onMouse = { bt, dx, dy, w -> sendMouse(bt, dx, dy, w) }

        val spin = Spinner(this)
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Templates.names.map { it.second })
        spin.setSelection(Templates.names.indexOfFirst { it.first == cur }.coerceAtLeast(0))
        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                loadProfile(Templates.names[pos].first)
                prefs.edit().putString("profile", cur).apply()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        editBtn = btn("Editar") { toggleEdit() }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btn("Visible") { visible() })
            addView(btn("Conectar") { connectDialog() })
            addView(btn("Ajustes") { settings() })
            addView(btn("Mando") { show(0) })
            addView(btn("Ratón") { show(1) })
            addView(btn("Teclado") { show(2) })
            addView(spin)
            addView(editBtn)
        }
        val bar = HorizontalScrollView(this).apply { addView(row) }

        editBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; visibility = View.GONE
            addView(btn("Más grande") { pad.selected?.let { it.size = (it.size * 1.1f).coerceAtMost(.35f); pad.invalidate() } })
            addView(btn("Más pequeño") { pad.selected?.let { it.size = (it.size / 1.1f).coerceAtLeast(.03f); pad.invalidate() } })
            addView(btn("Guardar") { prefs.edit().putString("layout_$cur", pad.toJson()).apply(); toast("Distribución guardada") })
            addView(btn("Restablecer") {
                prefs.edit().remove("layout_$cur").apply()
                pad.ctls = Templates.build(cur); pad.selected = null; pad.invalidate()
            })
        }

        kb = ScrollView(this).apply { addView(buildKeyboard()); visibility = View.GONE }
        touch.visibility = View.GONE
        val content = FrameLayout(this).apply {
            val mp = ViewGroup.LayoutParams.MATCH_PARENT
            addView(pad, FrameLayout.LayoutParams(mp, mp))
            addView(touch, FrameLayout.LayoutParams(mp, mp))
            addView(kb, FrameLayout.LayoutParams(mp, mp))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF12151C.toInt())
            addView(status); addView(bar); addView(editBar)
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        applyPrefs()
        ensurePerm()
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
        editBtn.visibility = if (m == 0) View.VISIBLE else View.GONE
        if (m != 0 && pad.edit) toggleEdit()
    }

    private fun toggleEdit() {
        pad.setEdit(!pad.edit)
        editBtn.text = if (pad.edit) "Listo" else "Editar"
        editBar.visibility = if (pad.edit) View.VISIBLE else View.GONE
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
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
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
