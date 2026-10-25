package com.mandobt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Ratón táctil: un dedo mueve, toque = clic, dos dedos desplazan, toque con dos dedos = clic derecho. */
class TouchPad(ctx: Context) : View(ctx) {
    var speed = 1.5f
    var scroll = .05f
    var onMouse: ((Int, Int, Int, Int) -> Unit)? = null

    private var btns = 0
    private val zone = HashMap<Int, Int>()
    private val last = HashMap<Int, PointF>()
    private var t0 = 0L
    private var moved = 0f
    private var maxPtr = 0
    private var ax = 0f
    private var ay = 0f
    private var sc = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF1A1F2B.toInt())
        val top = height * .78f
        p.color = 0xFF2C3447.toInt()
        c.drawRect(2f, top, width / 2f - 2f, height - 2f, p)
        c.drawRect(width / 2f + 2f, top, width - 2f, height - 2f, p)
        p.color = 0xFFEDF1FF.toInt(); p.textAlign = Paint.Align.CENTER; p.textSize = height * .06f
        c.drawText("Clic izquierdo", width / 4f, (top + height) / 2f, p)
        c.drawText("Clic derecho", width * .75f, (top + height) / 2f, p)
        p.color = 0xFF8590A6.toInt()
        c.drawText("Desliza para mover. Dos dedos para desplazar.", width / 2f, top / 2f, p)
    }

    private fun emit(dx: Int, dy: Int, w: Int) { onMouse?.invoke(btns, dx, dy, w) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val barTop = height * .78f
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                if (e.getY(i) > barTop) {
                    val bit = if (e.getX(i) < width / 2f) 1 else 2
                    zone[id] = bit
                    btns = btns or bit
                    emit(0, 0, 0)
                } else {
                    last[id] = PointF(e.getX(i), e.getY(i))
                    if (last.size == 1) { t0 = e.eventTime; moved = 0f; maxPtr = 1 } else maxPtr = maxOf(maxPtr, last.size)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                var sdx = 0f
                var sdy = 0f
                for (i in 0 until e.pointerCount) {
                    val l = last[e.getPointerId(i)] ?: continue
                    val dx = e.getX(i) - l.x
                    val dy = e.getY(i) - l.y
                    l.set(e.getX(i), e.getY(i))
                    sdx += dx; sdy += dy
                    moved += abs(dx) + abs(dy)
                }
                val n = last.size
                if (n == 1) {
                    ax += sdx * speed; ay += sdy * speed
                    val ix = ax.toInt(); val iy = ay.toInt()
                    ax -= ix; ay -= iy
                    if (ix != 0 || iy != 0) emit(ix, iy, 0)
                } else if (n >= 2) {
                    sc += -sdy / n * scroll
                    val w = sc.toInt()
                    if (w != 0) { sc -= w; emit(0, 0, w) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                if (zone.remove(id) != null) {
                    btns = zone.values.fold(0) { a, b -> a or b }
                    emit(0, 0, 0)
                } else if (last.remove(id) != null && last.isEmpty() && e.eventTime - t0 < 220 && moved < 20f) {
                    val b = if (maxPtr >= 2) 2 else 1
                    onMouse?.invoke(btns or b, 0, 0, 0)
                    onMouse?.invoke(btns, 0, 0, 0)
                }
            }
            MotionEvent.ACTION_CANCEL -> { zone.clear(); last.clear(); btns = 0; emit(0, 0, 0) }
        }
        return true
    }
}
