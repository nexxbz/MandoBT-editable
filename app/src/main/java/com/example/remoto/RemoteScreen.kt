package com.example.remoto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Teclas del control. Se envían por wifi como texto con su nombre (por ejemplo "K:VOL_UP"). */
enum class Key {
    UP, DOWN, LEFT, RIGHT, OK,
    HOME, BACK, MENU, GUIDE, POWER, SETTINGS,
    VOL_UP, VOL_DOWN, MUTE, CAPTIONS,
    BRIGHT_UP, BRIGHT_DOWN,
    REWIND, PLAY, PAUSE, FORWARD, STOP, PREV, NEXT, REPEAT, SCREENSHOT,
    RED, GREEN, BLUE, YELLOW,
    SPACE, BACKSPACE, ENTER,
    N0, N1, N2, N3, N4, N5, N6, N7, N8, N9
}

interface RemoteController {
    fun send(key: Key)
    fun sendText(text: String)
    fun moveMouse(dx: Int, dy: Int)
    fun click(right: Boolean)
    fun scroll(amount: Int)
    fun drag(start: Boolean)
}

internal val Bg = Color(0xFF000000)
internal val Btn = Color(0xFF0F1014)
internal val Ico = Color(0xFFE4E4EA)

/**
 * Control remoto. Se adapta al tamaño de la ventana: en pantalla completa usa la cruceta grande y,
 * si la ventana es pequeña (modo ventana), encoge botones y cruceta para que todo quepa.
 */
@Composable
fun RemoteScreen(
    deviceName: String,
    status: String,
    controller: RemoteController,
    settings: AppSettings,
    onKeyboard: () -> Unit,
    onDevices: () -> Unit,
    onDisconnect: () -> Unit
) {
    var touchpad by remember { mutableStateOf(settings.touchpadDefault) }
    var showNumbers by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        val gap = 12f
        val w = (maxWidth.value - 32f).coerceAtLeast(120f)
        val headerH = if (status.isNotEmpty()) 74f else 56f
        val availH = maxHeight.value - headerH - gap
        val cellFull = (w - gap * 3f) / 4f

        // Se encogen los botones hasta que la cruceta tenga un tamaño razonable
        var k = 1f
        var cell = cellFull
        var pad = availH - 4f * cell - 4f * gap
        while (k > 0.4f && pad < 2.6f * cell) {
            k -= 0.05f
            cell = cellFull * k
            pad = availH - 4f * cell - 4f * gap
        }
        val rowW = 4f * cell + 3f * gap
        pad = pad.coerceIn(90f, rowW)

        val c = cell.dp
        val g = gap.dp

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(g)
        ) {
            // Encabezado: nombre + mouse/cruceta, teclado, menú
            Row(Modifier.fillMaxWidth().height(headerH.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        deviceName, color = Color.White,
                        fontSize = if (w < 300f) 22.sp else 30.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                    if (status.isNotEmpty()) Text(status, color = Color.Gray, fontSize = 11.sp, maxLines = 2)
                }
                IconButton(onClick = { touchpad = !touchpad }) {
                    Icon(if (touchpad) Icons.Filled.OpenWith else Icons.Filled.Mouse, null, tint = Ico, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = onKeyboard) { Icon(Icons.Filled.Keyboard, null, tint = Ico, modifier = Modifier.size(28.dp)) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = Ico, modifier = Modifier.size(28.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Dispositivos") }, onClick = { menu = false; onDevices() })
                        DropdownMenuItem(text = { Text("Desconectar") }, onClick = { menu = false; onDisconnect() })
                    }
                }
            }

            // Reproducción
            Row(
                Modifier.width(rowW.dp).height(c).clip(RoundedCornerShape(50)).background(Btn),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                IconKey(Icons.Filled.FastRewind, c) { controller.send(Key.REWIND) }
                IconKey(Icons.Filled.PlayArrow, c) { controller.send(Key.PLAY) }
                IconKey(Icons.Filled.Pause, c) { controller.send(Key.PAUSE) }
                IconKey(Icons.Filled.FastForward, c) { controller.send(Key.FORWARD) }
            }

            // Volumen | mute, números | CC, más | brillo
            Row(horizontalArrangement = Arrangement.spacedBy(g)) {
                Pill(c, c * 2 + g, Icons.Filled.VolumeUp, Icons.Filled.VolumeDown,
                    { controller.send(Key.VOL_UP) }, { controller.send(Key.VOL_DOWN) })
                Column(verticalArrangement = Arrangement.spacedBy(g)) {
                    Round(c, Icons.Filled.VolumeOff) { controller.send(Key.MUTE) }
                    Round(c, Icons.Filled.Dialpad) { showNumbers = true }
                }
                Column(verticalArrangement = Arrangement.spacedBy(g)) {
                    Round(c, Icons.Filled.ClosedCaption) { controller.send(Key.CAPTIONS) }
                    Round(c, Icons.Filled.MoreHoriz) { showMore = true }
                }
                Pill(c, c * 2 + g, Icons.Filled.BrightnessHigh, Icons.Filled.BrightnessLow,
                    { controller.send(Key.BRIGHT_UP) }, { controller.send(Key.BRIGHT_DOWN) })
            }

            // Atrás, inicio, menú, encendido/apagado
            Row(horizontalArrangement = Arrangement.spacedBy(g)) {
                Round(c, Icons.AutoMirrored.Filled.ArrowBack) { controller.send(Key.BACK) }
                Round(c, Icons.Filled.Home) { controller.send(Key.HOME) }
                Round(c, Icons.AutoMirrored.Filled.List) { controller.send(Key.MENU) }
                Round(c, Icons.Filled.PowerSettingsNew) { controller.send(Key.POWER) }
            }

            // Cruceta o panel táctil (en el mismo lugar)
            if (touchpad) {
                TouchpadArea(controller, settings, Modifier.width(rowW.dp).height(pad.dp))
            } else {
                DPad(pad.dp, controller)
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showNumbers) NumberPadDialog(controller) { showNumbers = false }
    if (showMore) MoreButtonsDialog(controller) { showMore = false }
}

@Composable
private fun DPad(size: Dp, controller: RemoteController) {
    Box(Modifier.size(size).clip(CircleShape).background(Btn)) {
        ArrowBtn(Modifier.align(Alignment.TopCenter).padding(top = size * 0.05f), size, Icons.Filled.KeyboardArrowUp) { controller.send(Key.UP) }
        ArrowBtn(Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.05f), size, Icons.Filled.KeyboardArrowDown) { controller.send(Key.DOWN) }
        ArrowBtn(Modifier.align(Alignment.CenterStart).padding(start = size * 0.05f), size, Icons.Filled.KeyboardArrowLeft) { controller.send(Key.LEFT) }
        ArrowBtn(Modifier.align(Alignment.CenterEnd).padding(end = size * 0.05f), size, Icons.Filled.KeyboardArrowRight) { controller.send(Key.RIGHT) }
        Box(
            Modifier.align(Alignment.Center).size(size * 0.3f).clip(CircleShape)
                .background(Ico).padding(size * 0.025f).clip(CircleShape).background(Btn)
                .clickable { controller.send(Key.OK) }
        )
    }
}

@Composable
private fun ArrowBtn(modifier: Modifier, size: Dp, icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier.size(size * 0.28f).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Ico, modifier = Modifier.size(size * 0.22f)) }
}

@Composable
private fun IconKey(icon: ImageVector, cell: Dp, onClick: () -> Unit) =
    Icon(icon, null, tint = Ico, modifier = Modifier.size(cell * 0.5f).clickable { onClick() })

@Composable
private fun Round(size: Dp, icon: ImageVector, onClick: () -> Unit) =
    Box(
        Modifier.size(size).clip(CircleShape).background(Btn).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Ico, modifier = Modifier.size(size * 0.45f)) }

@Composable
private fun Pill(width: Dp, height: Dp, top: ImageVector, bottom: ImageVector, onTop: () -> Unit, onBottom: () -> Unit) =
    Column(Modifier.size(width, height).clip(RoundedCornerShape(50)).background(Btn)) {
        Box(Modifier.weight(1f).fillMaxWidth().clickable { onTop() }, contentAlignment = Alignment.Center) {
            Icon(top, null, tint = Ico, modifier = Modifier.size(width * 0.45f))
        }
        Box(Modifier.weight(1f).fillMaxWidth().clickable { onBottom() }, contentAlignment = Alignment.Center) {
            Icon(bottom, null, tint = Ico, modifier = Modifier.size(width * 0.45f))
        }
    }
