package com.example.remoto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DevicesScreen(
    status: String,
    favorites: List<Peer>,
    others: List<Peer>,
    online: Set<String>,
    onPick: (Peer) -> Unit,
    onToggleFav: (Peer) -> Unit,
    onToggleAuto: (Peer) -> Unit,
    onForget: (Peer) -> Unit,
    onAdd: (Peer) -> Unit,
    onReceiver: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Dispositivos", color = Ico, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = onHelp) { Icon(Icons.Filled.Info, null, tint = Ico, modifier = Modifier.size(28.dp)) }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, null, tint = Ico, modifier = Modifier.size(28.dp)) }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Btn).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Info, null, tint = Ico)
                Text("Información", color = Ico, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                if (status.isNotEmpty()) status
                else "Aquí solo aparecen los teléfonos que tienen esta app con el servicio activado, en tu misma wifi.",
                color = Ico, fontSize = 14.sp
            )
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (favorites.isNotEmpty()) {
                item { SectionLabel("Favoritos") }
                items(favorites, key = { "f" + it.name }) { p ->
                    PeerRow(p, online, onPick, onToggleFav, onToggleAuto, onForget)
                }
            }
            item { SectionLabel("Dispositivos con la app") }
            if (others.isEmpty()) {
                item {
                    Text(
                        "Todavía no se encontró ninguno. Abre la app en el otro teléfono, entra a \"Este teléfono\" y activa el servicio.",
                        color = Color.Gray, fontSize = 14.sp
                    )
                }
            }
            items(others, key = { "o" + it.name }) { p ->
                PeerRow(p, online, onPick, onToggleFav, onToggleAuto, onForget)
            }
        }

        Button(onClick = onReceiver, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Este teléfono: permitir que lo controlen") }
        OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Agregar por dirección IP") }
        Spacer(Modifier.height(4.dp))
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var host by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Agregar teléfono") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                    OutlinedTextField(host, { host = it }, label = { Text("IP (ej. 192.168.1.50)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = host.isNotBlank(),
                    onClick = {
                        onAdd(Peer(name.ifBlank { host.trim() }, host.trim(), CONTROL_PORT, null))
                        showAdd = false
                    }
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun PeerRow(
    p: Peer,
    online: Set<String>,
    onPick: (Peer) -> Unit,
    onToggleFav: (Peer) -> Unit,
    onToggleAuto: (Peer) -> Unit,
    onForget: (Peer) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFA8C7FA)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PhoneAndroid, null, tint = Color(0xFF062E6F), modifier = Modifier.size(26.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(p.name, color = Ico, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${p.host} · " + if (p.name in online) "en la red" else "guardado", color = Color.Gray, fontSize = 13.sp)
        }
        // "Auto" solo aparece si activaste la conexión automática en este dispositivo
        if (p.auto) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(Btn).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Auto", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, null, tint = Ico) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("Conectar") }, onClick = { open = false; onPick(p) })
                DropdownMenuItem(
                    text = { Text(if (p.fav) "Quitar de favoritos" else "Agregar a favoritos") },
                    onClick = { open = false; onToggleFav(p) }
                )
                DropdownMenuItem(
                    text = { Text(if (p.auto) "Desactivar conexión automática" else "Conexión automática") },
                    onClick = { open = false; onToggleAuto(p) }
                )
                DropdownMenuItem(text = { Text("Olvidar") }, onClick = { open = false; onForget(p) })
            }
        }
    }
}
