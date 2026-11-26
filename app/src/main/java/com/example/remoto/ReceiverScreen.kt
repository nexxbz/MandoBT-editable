package com.example.remoto

import android.content.Context
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun isServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return enabled.contains("RemoteAccessibilityService")
}

@Composable
fun ReceiverScreen(
    enabled: Boolean,
    pin: String,
    ip: String?,
    canWriteSettings: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onNewPin: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Ico) }
            Text("Este teléfono", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }

        InfoCard("Servicio") {
            Text(
                if (enabled) "Activado. Este teléfono ya aparece en la red para controlarlo."
                else "Desactivado. Actívalo en Accesibilidad: busca \"Control Remoto\" y enciéndelo.",
                color = Ico, fontSize = 16.sp
            )
            if (!enabled) {
                Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Abrir Accesibilidad") }
                Text(
                    "Si Android dice \"Configuración restringida\" o no deja encenderlo: abre la info de la app, " +
                        "toca el menú ⋮ y elige \"Permitir ajustes restringidos\". Luego vuelve a intentarlo.",
                    color = Color.Gray, fontSize = 13.sp
                )
                OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) { Text("Abrir info de la app") }
            }
        }

        InfoCard("PIN para conectarse") {
            Text(pin, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text("Escríbelo en el otro teléfono la primera vez que se conecte.", color = Color.Gray, fontSize = 13.sp)
            OutlinedButton(onClick = onNewPin) { Text("Generar otro PIN") }
        }

        InfoCard("Dirección en la red") {
            Text(ip ?: "Sin wifi", color = Ico, fontSize = 20.sp)
            Text("Solo funciona si ambos teléfonos están en la misma wifi.", color = Color.Gray, fontSize = 13.sp)
        }

        InfoCard("Brillo") {
            Text(
                if (canWriteSettings) "Permiso concedido: los botones de brillo funcionan."
                else "Para que los botones de brillo funcionen, permite modificar los ajustes del sistema.",
                color = Ico, fontSize = 16.sp
            )
            if (!canWriteSettings) OutlinedButton(onClick = onOpenWriteSettings) { Text("Permitir cambiar brillo") }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Btn).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = Ico, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        content()
    }
}
