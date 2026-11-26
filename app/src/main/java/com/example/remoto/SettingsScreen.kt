package com.example.remoto

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(settings: AppSettings, version: String, onCheckUpdate: () -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Ico) }
            Text("Ajustes", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }

        Section("Remoto")
        Text("Modo de navegación por defecto", color = Ico, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !settings.touchpadDefault,
                onClick = { settings.updateTouchpadDefault(false) },
                label = { Text("Cruceta") }
            )
            FilterChip(
                selected = settings.touchpadDefault,
                onClick = { settings.updateTouchpadDefault(true) },
                label = { Text("Panel táctil") }
            )
        }
        SwitchRow(
            "Usar los botones de volumen del teléfono",
            "Suben y bajan el volumen del otro teléfono mientras estás en el control",
            settings.volumeKeys
        ) { settings.updateVolumeKeys(it) }

        Section("Mouse")
        SliderRow("Velocidad del puntero (x${"%.1f".format(settings.pointerSpeed)})", settings.pointerSpeed) {
            settings.updatePointerSpeed(it)
        }
        SliderRow("Velocidad del desplazamiento (x${"%.1f".format(settings.scrollSpeed)})", settings.scrollSpeed) {
            settings.updateScrollSpeed(it)
        }
        SwitchRow("Invertir la dirección de desplazamiento", null, settings.invertScroll) { settings.updateInvertScroll(it) }

        Section("Teclado y campo de entrada")
        SwitchRow("Borrar el campo de texto después de enviar", null, settings.clearAfterSend) {
            settings.updateClearAfterSend(it)
        }

        Section("Acerca de")
        Text("Versión $version", color = Ico, fontSize = 16.sp)
        OutlinedButton(onClick = onCheckUpdate) { Text("Buscar actualización") }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Color(0xFFA8C7FA), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ico, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Color.Gray, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = Ico, fontSize = 16.sp)
        Slider(value = value, onValueChange = onChange, valueRange = 0.5f..3f)
    }
}
