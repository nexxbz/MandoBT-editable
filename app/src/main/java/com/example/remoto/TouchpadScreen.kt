package com.example.remoto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Panel táctil como el del control de referencia: panel grande, franja de desplazamiento (↑ ↓)
 * y tres botones sin texto: clic izquierdo (se puede mantener apretado para arrastrar),
 * clic central y clic derecho (mantener presionado = abrir las opciones, como al presionar la pantalla).
 *
 * En el panel: un dedo que se desliza mueve el cursor; un toque hace clic; mantener el dedo quieto
 * un momento y luego deslizar = apretar y deslizar (arrastrar).
 */
@Composable
fun TouchpadArea(controller: RemoteController, settings: AppSettings, modifier: Modifier) {
    fun scrollBy(direction: Int) {
        val amount = (3f * settings.scrollSpeed).roundToInt().coerceAtLeast(1)
        controller.scroll(if (settings.invertScroll) -direction * amount else direction * amount)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Panel
            Box(
                Modifier.weight(6f).fillMaxHeight().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                    .background(Btn)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val slop = viewConfiguration.touchSlop
                            var total = Offset.Zero

                            // Esperar: ¿soltó (clic), se movió (cursor) o se quedó quieto (arrastrar)?
                            val phase: Int? = withTimeoutOrNull(350L) {
                                var result = 0
                                while (result == 0) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull() ?: break
                                    if (!ch.pressed) {
                                        result = 1
                                    } else {
                                        total += ch.positionChange()
                                        if (total.getDistance() > slop) result = 2
                                    }
                                }
                                result
                            }

                            when (phase) {
                                1 -> controller.click(false)
                                else -> {
                                    val dragging = phase == null
                                    if (dragging) controller.drag(true)
                                    if (phase == 2) {
                                        controller.moveMouse(
                                            (total.x * 2.2f * settings.pointerSpeed).toInt(),
                                            (total.y * 2.2f * settings.pointerSpeed).toInt()
                                        )
                                    }
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull() ?: break
                                        if (!ch.pressed) break
                                        val d = ch.positionChange()
                                        if (d != Offset.Zero) {
                                            controller.moveMouse(
                                                (d.x * 2.2f * settings.pointerSpeed).toInt(),
                                                (d.y * 2.2f * settings.pointerSpeed).toInt()
                                            )
                                            ch.consume()
                                        }
                                    }
                                    if (dragging) controller.drag(false)
                                }
                            }
                        }
                    }
            )
            // Franja de desplazamiento
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 20.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                        .background(Btn).clickable { scrollBy(1) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.ArrowUpward, null, tint = Ico) }
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(Btn).clickable { scrollBy(-1) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.ArrowDownward, null, tint = Ico) }
            }
        }

        // Tres botones sin texto: izquierdo, central y derecho
        Row(Modifier.weight(0.24f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                Modifier.weight(37f).fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 20.dp, bottomEnd = 8.dp))
                    .background(Btn)
                    .pointerInput(Unit) {
                        // Izquierdo: un toque = clic; mantenerlo apretado y deslizar en el panel = arrastrar
                        detectTapGestures(onPress = {
                            controller.drag(true)
                            tryAwaitRelease()
                            controller.drag(false)
                        })
                    }
            )
            Box(
                Modifier.weight(23f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                    .background(Btn).clickable { controller.click(false) }
            )
            Box(
                Modifier.weight(37f).fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 20.dp))
                    .background(Btn).clickable { controller.click(true) }
            )
        }
    }
}
