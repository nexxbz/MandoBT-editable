package com.mandobt

import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class PadView(ctx: Context) : View(ctx) {
    var ctls: MutableList<Ctl> = Templates.build("xbox")
    var edit = false
        private set
    var selected: Ctl? = null
    var gain = 1f
    var dead = .08f
    // false = orden estándar de Android y Linux; true = orden genérico para PC
    var pcMode = false
    var onReport: ((ByteArray) -> Unit)? = null

    private val down = HashMap<Int, Ctl>()
    private var lastRep = ByteArray(9)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    // id del botón en la pantalla -> bit del reporte HID
    private val androidMap = intArrayOf(0, 1, 3, 4, 6, 7, 8, 9, 10, 11, 13, 14, 12)
    private val pcMap = intArrayOf(0, 1, 2, 3, 4, 5, 11, 12, 6, 7, 8, 9, 10)
    private fun bitOf(id: Int) = (if (pcMode) pcMap else androidMap)[id]

    private fun rad(c: Ctl) = c.size * height

    private fun hit(c: Ctl, x: Float, y: Float): Boolean {
        val cx = c.cx * width
        val cy = c.cy * height
        val r = rad(c)
        return if (c.kind == 'r') abs(x - cx) <= r * 1.2f && abs(y - cy) <= r * .65f
        else hypot(x - cx, y - cy) <= r * (if (c.kind == 's') 1.2f else 1f)
    }

    fun setEdit(v: Boolean) {
        edit = v
        down.clear()
        ctls.forEach { it.vx = 0f; it.vy = 0f }
        if (!v) selected = null
        sendReport()
        invalidate()
    }

    override fun onDraw(cv: Canvas) {
        cv.drawColor(0xFF12151C.toInt())
        p.textAlign = Paint.Align.CENTER
        for (c in ctls) {
            val cx = c.cx * width
            val cy = c.cy * height
            val r = rad(c)
            val on = !edit && down.values.contains(c)
            p.style = Paint.Style.FILL
            p.color = if (on) 0xFF4C7DFF.toInt() else 0xFF2C3447.toInt()
            when (c.kind) {
                'r' -> cv.drawRoundRect(cx - r * 1.2f, cy - r * .65f, cx + r * 1.2f, cy + r * .65f, r * .4f, r * .4f, p)
                's' -> {
                    p.color = 0xFF1E2431.toInt(); cv.drawCircle(cx, cy, r, p)
                    p.color = if (on) 0xFF4C7DFF.toInt() else 0xFF46506A.toInt()
                    cv.drawCircle(cx + c.vx * r * .45f, cy + c.vy * r * .45f, r * .55f, p)
                }
                else -> cv.drawCircle(cx, cy, r, p)
            }
            if (edit && c === selected) {
                p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = 0xFFFFD83B.toInt()
                if (c.kind == 'r') cv.drawRoundRect(cx - r * 1.2f, cy - r * .65f, cx + r * 1.2f, cy + r * .65f, r * .4f, r * .4f, p)
                else cv.drawCircle(cx, cy, r, p)
            }
            if (c.label.isNotEmpty()) {
                p.style = Paint.Style.FILL
                p.color = 0xFFEDF1FF.toInt()
                p.textSize = if (c.kind == 'r') r * .7f else r * .8f
                cv.drawText(c.label, cx, cy + p.textSize * .35f, p)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val c = ctls.lastOrNull { hit(it, e.getX(i), e.getY(i)) }
                if (c != null) {
                    down[e.getPointerId(i)] = c
                    if (edit) selected = c else performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                } else if (edit) selected = null
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val c = down[e.getPointerId(i)] ?: continue
                move(c, e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                down.remove(e.getPointerId(e.actionIndex))?.let { it.vx = 0f; it.vy = 0f }
            }
            MotionEvent.ACTION_CANCEL -> { down.clear(); ctls.forEach { it.vx = 0f; it.vy = 0f } }
        }
        sendReport()
        invalidate()
        return true
    }

    private fun move(c: Ctl, x: Float, y: Float) {
        if (edit) {
            c.cx = (x / width).coerceIn(.03f, .97f)
            c.cy = (y / height).coerceIn(.05f, .95f)
        } else if (c.kind == 's') {
            var dx = (x - c.cx * width) / rad(c)
            var dy = (y - c.cy * height) / rad(c)
            val m = hypot(dx, dy)
            if (m > 1f) { dx /= m; dy /= m }
            c.vx = dx; c.vy = dy
        }
    }

    private fun sendReport() {
        var b = 0
        var mask = 0
        val ax = IntArray(4)
        var gas = 0
        var brk = 0
        if (!edit) for (c in down.values) when (c.kind) {
            'b', 'r' -> {
                b = b or (1 shl bitOf(c.id))
                if (c.id == 7) gas = 255
                if (c.id == 6) brk = 255
            }
            'd' -> mask = mask or c.id
            's' -> {
                val m = hypot(c.vx, c.vy)
                if (m > dead) {
                    val k = (((m - dead) / (1f - dead)) * gain).coerceAtMost(1f) / m
                    ax[c.id * 2] = (c.vx * k * 127).roundToInt().coerceIn(-127, 127)
                    ax[c.id * 2 + 1] = (c.vy * k * 127).roundToInt().coerceIn(-127, 127)
                }
            }
        }
        val hat = when (mask) { 1 -> 1; 3 -> 2; 2 -> 3; 6 -> 4; 4 -> 5; 12 -> 6; 8 -> 7; 9 -> 8; else -> 0 }
        val r = byteArrayOf(b.toByte(), (b shr 8).toByte(), hat.toByte(),
            ax[0].toByte(), ax[1].toByte(), ax[2].toByte(), ax[3].toByte(),
            gas.toByte(), brk.toByte())
        if (!r.contentEquals(lastRep)) { lastRep = r; onReport?.invoke(r) }
    }

    fun toJson(): String {
        val a = JSONArray()
        for (c in ctls) a.put(JSONObject().put("k", c.kind.toString()).put("i", c.id)
            .put("x", c.cx.toDouble()).put("y", c.cy.toDouble()).put("s", c.size.toDouble()).put("l", c.label))
        return a.toString()
    }

    fun load(json: String) {
        val a = JSONArray(json)
        ctls = MutableList(a.length()) {
            val o = a.getJSONObject(it)
            Ctl(o.getString("k")[0], o.getInt("i"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                o.getDouble("s").toFloat(), o.getString("l"))
        }
        selected = null
        invalidate()
    }
}
