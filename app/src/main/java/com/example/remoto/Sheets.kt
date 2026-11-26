package com.example.remoto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Teclado: campo de texto + enviar, y fila de teclas especiales (espacio, flechas, borrar, intro). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSheet(controller: RemoteController, clearAfterSend: Boolean, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val send = {
        if (text.isNotEmpty()) {
            controller.sendText(text)
            if (clearAfterSend) text = ""
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF111216)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() })
                )
                IconButton(onClick = { send() }) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Ico) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyBtn(Modifier.weight(3f), Icons.Filled.SpaceBar) { controller.send(Key.SPACE) }
                KeyBtn(Modifier.weight(1f), Icons.Filled.KeyboardArrowUp) { controller.send(Key.UP) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyBtn(Modifier.weight(1f), Icons.AutoMirrored.Filled.Backspace) { controller.send(Key.BACKSPACE) }
                KeyBtn(Modifier.weight(1f), Icons.AutoMirrored.Filled.KeyboardReturn) { controller.send(Key.ENTER) }
                KeyBtn(Modifier.weight(1f), Icons.Filled.KeyboardArrowLeft) { controller.send(Key.LEFT) }
                KeyBtn(Modifier.weight(1f), Icons.Filled.KeyboardArrowDown) { controller.send(Key.DOWN) }
                KeyBtn(Modifier.weight(1f), Icons.Filled.KeyboardArrowRight) { controller.send(Key.RIGHT) }
            }
            Spacer(Modifier.height(8.dp))
        }
        LaunchedEffect(Unit) {
            delay(300)
            try {
                focus.requestFocus()
            } catch (e: Exception) {
            }
        }
    }
}

@Composable
private fun KeyBtn(modifier: Modifier, icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(10.dp)).background(Btn).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Ico) }
}

/** Panel numérico. */
@Composable
fun NumberPadDialog(controller: RemoteController, onDismiss: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("-", "0", "+")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Teclado numérico") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        r.forEach { label ->
                            Box(
                                Modifier.weight(1f).height(60.dp).clip(CircleShape).background(Btn).clickable {
                                    when (label) {
                                        "-", "+" -> controller.sendText(label)
                                        else -> controller.send(Key.valueOf("N$label"))
                                    }
                                },
                                contentAlignment = Alignment.Center
                            ) { Text(label, color = Ico, fontSize = 24.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

private data class MoreItem(val label: String, val icon: ImageVector?, val tint: Color?, val key: Key)

/** "Más botones": reproducción, pistas, captura, brillo y colores. */
@Composable
fun MoreButtonsDialog(controller: RemoteController, onDismiss: () -> Unit) {
    val items = listOf(
        MoreItem("Reproducción", Icons.Filled.PlayArrow, null, Key.PLAY),
        MoreItem("Pausar", Icons.Filled.Pause, null, Key.PAUSE),
        MoreItem("Detener", Icons.Filled.Stop, null, Key.STOP),
        MoreItem("Repetir", Icons.Filled.Repeat, null, Key.REPEAT),
        MoreItem("Retroceder", Icons.Filled.FastRewind, null, Key.REWIND),
        MoreItem("Adelantar", Icons.Filled.FastForward, null, Key.FORWARD),
        MoreItem("Pista anterior", Icons.Filled.SkipPrevious, null, Key.PREV),
        MoreItem("Pista siguiente", Icons.Filled.SkipNext, null, Key.NEXT),
        MoreItem("Subtítulos", Icons.Filled.ClosedCaption, null, Key.CAPTIONS),
        MoreItem("Captura de pantalla", Icons.Filled.Screenshot, null, Key.SCREENSHOT),
        MoreItem("Bajar brillo", Icons.Filled.BrightnessLow, null, Key.BRIGHT_DOWN),
        MoreItem("Subir brillo", Icons.Filled.BrightnessHigh, null, Key.BRIGHT_UP),
        MoreItem("Rojo", null, Color(0xFFB3261E), Key.RED),
        MoreItem("Verde", null, Color(0xFF2E7D32), Key.GREEN),
        MoreItem("Azul", null, Color(0xFF1565C0), Key.BLUE),
        MoreItem("Amarillo", null, Color(0xFFC9A400), Key.YELLOW)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Más botones") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { item ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { controller.send(item.key) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).background(Btn),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.icon != null) {
                                        Icon(item.icon, null, tint = Ico, modifier = Modifier.size(22.dp))
                                    } else {
                                        Box(Modifier.size(20.dp).clip(CircleShape).background(item.tint ?: Color.Gray))
                                    }
                                }
                                Text(item.label, color = Ico, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
