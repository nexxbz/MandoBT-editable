package com.mandobt

/** Un elemento del mando. kind: 'b' botón redondo, 'r' botón ancho, 'd' dirección de cruceta, 's' palanca. */
class Ctl(val kind: Char, val id: Int, var cx: Float, var cy: Float, var size: Float, val label: String) {
    var vx = 0f
    var vy = 0f
}

object Templates {
    val names = listOf("xbox" to "Xbox", "ps" to "PlayStation", "sw" to "Nintendo Switch", "gen" to "Genérico")

    // Bits del reporte: 0 abajo,1 derecha,2 izquierda,3 arriba,4 L1,5 R1,6 L2,7 R2,8 select,9 start,10 L3,11 R3,12 home
    fun build(p: String): MutableList<Ctl> {
        val ps = p == "ps"
        val face = when (p) { "ps" -> listOf("✕", "○", "□", "△"); "sw" -> listOf("B", "A", "Y", "X"); else -> listOf("A", "B", "X", "Y") }
        val sh = when (p) { "ps" -> listOf("L1", "R1", "L2", "R2"); "sw" -> listOf("L", "R", "ZL", "ZR"); "xbox" -> listOf("LB", "RB", "LT", "RT"); else -> listOf("L1", "R1", "L2", "R2") }
        val sel = when (p) { "ps" -> "Create"; "sw" -> "−"; "xbox" -> "View"; else -> "Select" }
        val st = when (p) { "ps" -> "Options"; "sw" -> "+"; "xbox" -> "Menu"; else -> "Start" }
        val home = when (p) { "ps" -> "PS"; "xbox" -> "Guía"; else -> "Home" }
        val dx = if (ps) .17f else .34f
        val dy = if (ps) .50f else .78f
        val sx = if (ps) .34f else .17f
        val sy = if (ps) .78f else .50f
        return mutableListOf(
            Ctl('r', 6, .12f, .09f, .10f, sh[2]), Ctl('r', 4, .12f, .22f, .085f, sh[0]),
            Ctl('r', 7, .88f, .09f, .10f, sh[3]), Ctl('r', 5, .88f, .22f, .085f, sh[1]),
            Ctl('r', 8, .40f, .30f, .07f, sel), Ctl('r', 9, .60f, .30f, .07f, st),
            Ctl('b', 12, .50f, .50f, .06f, home),
            Ctl('s', 0, sx, sy, .17f, ""), Ctl('s', 1, .66f, .78f, .17f, ""),
            Ctl('d', 1, dx, dy - .12f, .07f, "▲"), Ctl('d', 2, dx + .06f, dy, .07f, "▶"),
            Ctl('d', 4, dx, dy + .12f, .07f, "▼"), Ctl('d', 8, dx - .06f, dy, .07f, "◀"),
            Ctl('b', 0, .83f, .71f, .075f, face[0]), Ctl('b', 1, .91f, .57f, .075f, face[1]),
            Ctl('b', 2, .75f, .57f, .075f, face[2]), Ctl('b', 3, .83f, .43f, .075f, face[3]),
            Ctl('b', 10, .05f, .88f, .045f, "L3"), Ctl('b', 11, .95f, .88f, .045f, "R3"),
            Ctl('g', 20, .50f, .80f, .05f, "GIRO")
        )
    }
}
