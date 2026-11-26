package com.example.remoto

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var controller: WifiController
    private lateinit var settings: AppSettings
    private var remoteVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = WifiController()
        settings = AppSettings(this)
        val prefs = getSharedPreferences("ajustes", Context.MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                App(prefs, controller, settings) { remoteVisible = it }
            }
        }
    }

    // Botones de volumen del teléfono -> volumen del otro teléfono (si se activó en Ajustes)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (remoteVisible && settings.volumeKeys) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                controller.send(Key.VOL_UP)
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                controller.send(Key.VOL_DOWN)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        controller.stop()
        super.onDestroy()
    }
}

private enum class Screen { DEVICES, REMOTE, RECEIVER, SETTINGS }

@Composable
fun App(
    prefs: SharedPreferences,
    controller: WifiController,
    settings: AppSettings,
    onRemoteVisible: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { PeerStore(context) }

    var screen by remember { mutableStateOf(Screen.DEVICES) }
    var saved by remember { mutableStateOf(store.load()) }
    val discovered = remember { mutableStateListOf<Peer>() }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var askPin by remember { mutableStateOf<Peer?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var pinText by remember { mutableStateOf(ReceiverPin.get(context)) }
    var showHelp by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var autoTried by remember { mutableStateOf(false) }

    LaunchedEffect(screen) { onRemoteVisible(screen == Screen.REMOTE) }

    // Al volver a la app (por ejemplo de Accesibilidad) se actualiza el estado
    DisposableEffect(Unit) {
        val lifecycle = (context as ComponentActivity).lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val ownIp = remember(tick) { localIpv4(context) }

    // Búsqueda de teléfonos con la app en la red
    DisposableEffect(tick) {
        val d = WifiDiscovery(
            context,
            onFound = { p ->
                if (p.host != ownIp) {
                    val i = discovered.indexOfFirst { it.name == p.name }
                    if (i >= 0) discovered[i] = p else discovered.add(p)
                }
            },
            onLost = { name -> discovered.removeAll { it.name == name } }
        )
        d.start()
        onDispose { d.stop() }
    }

    val peers = mergePeers(saved, discovered.toList())
    val online = discovered.map { it.name }.toSet()

    fun savePeer(p: Peer) {
        saved = saved.filter { it.name != p.name } + p
        store.save(saved)
    }

    fun connectPeer(p: Peer, pin: String) {
        controller.connect(p.host, p.port, pin) { ok, pinWrong ->
            if (ok) {
                savePeer(Peer(p.name, p.host, p.port, pin, p.fav, p.auto))
                selectedName = p.name
                screen = Screen.REMOTE
            } else if (pinWrong) {
                askPin = p
            }
        }
    }

    // Conexión automática: solo para el dispositivo que se marcó con "Conexión automática"
    LaunchedEffect(discovered.size) {
        if (!autoTried && screen == Screen.DEVICES) {
            val p = peers.firstOrNull { it.auto && it.pin != null && it.name in online }
            val pin = p?.pin
            if (p != null && pin != null) {
                autoTried = true
                connectPeer(p, pin)
            }
        }
    }

    // Al abrir la app: ¿hay una versión más nueva en GitHub?
    LaunchedEffect(Unit) { update = Updater.check().update }

    when (screen) {
        Screen.DEVICES -> DevicesScreen(
            status = controller.status,
            favorites = peers.filter { it.fav },
            others = peers.filter { !it.fav },
            online = online,
            onPick = { p ->
                val pin = p.pin
                if (pin != null) connectPeer(p, pin) else askPin = p
            },
            onToggleFav = { p -> savePeer(p.copy(fav = !p.fav)) },
            onToggleAuto = { p ->
                // solo un dispositivo puede tener conexión automática
                saved = saved.map { it.copy(auto = false) }
                savePeer(p.copy(auto = !p.auto))
            },
            onForget = { p ->
                saved = saved.filter { it.name != p.name }
                store.save(saved)
            },
            onAdd = { p -> savePeer(p) },
            onReceiver = { screen = Screen.RECEIVER },
            onHelp = { showHelp = true },
            onSettings = { screen = Screen.SETTINGS }
        )
        Screen.REMOTE -> {
            BackHandler { screen = Screen.DEVICES }
            RemoteScreen(
                deviceName = selectedName ?: "Control",
                status = controller.status,
                controller = controller,
                settings = settings,
                onKeyboard = { showKeyboard = true },
                onDevices = { screen = Screen.DEVICES },
                onDisconnect = { controller.disconnect(); screen = Screen.DEVICES }
            )
        }
        Screen.RECEIVER -> {
            val enabled = remember(tick) { isServiceEnabled(context) }
            val canWrite = remember(tick) { Settings.System.canWrite(context) }
            ReceiverScreen(
                enabled = enabled,
                pin = pinText,
                ip = ownIp,
                canWriteSettings = canWrite,
                onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                onOpenAppInfo = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                },
                onOpenWriteSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                },
                onNewPin = { pinText = ReceiverPin.renew(context) },
                onBack = { screen = Screen.DEVICES }
            )
        }
        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            version = BuildConfig.VERSION_NAME,
            onCheckUpdate = {
                scope.launch {
                    val r = Updater.check()
                    if (r.update != null) update = r.update
                    else Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                }
            },
            onBack = { screen = Screen.DEVICES }
        )
    }

    val ap = askPin
    if (ap != null) {
        var pin by remember(ap) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askPin = null },
            title = { Text("PIN de ${ap.name}") },
            text = {
                Column {
                    Text("Escribe el PIN de 6 dígitos que aparece en el otro teléfono, en \"Este teléfono\".")
                    OutlinedTextField(
                        pin, { pin = it.filter(Char::isDigit).take(6) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = pin.length == 6, onClick = { askPin = null; connectPeer(ap, pin) }) { Text("Conectar") }
            },
            dismissButton = { TextButton(onClick = { askPin = null }) { Text("Cancelar") } }
        )
    }

    if (showKeyboard) KeyboardSheet(controller, settings.clearAfterSend) { showKeyboard = false }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Ayuda") },
            text = {
                Text(
                    "Instala esta misma app en los dos teléfonos y conéctalos a la misma wifi.\n\n" +
                        "En el teléfono que quieres controlar: toca \"Este teléfono\", activa el servicio en " +
                        "Accesibilidad y anota el PIN.\n\n" +
                        "En este teléfono: espera a que aparezca en la lista, tócalo y escribe el PIN. " +
                        "Si no aparece, agrégalo por su IP.\n\n" +
                        "Menú ⋮ de cada dispositivo: favoritos y conexión automática.\n\n" +
                        "Si el servicio no se deja activar, permite los ajustes restringidos en la info de la app."
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Entendido") } }
        )
    }

    val u = update
    if (u != null) {
        val p = progress
        if (p == null) {
            AlertDialog(
                onDismissRequest = { update = null },
                title = { Text("Actualización disponible") },
                text = { Text("Hay una nueva versión (${u.versionName}). ¿Desea descargar esta actualización?") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            progress = 0
                            try {
                                val apk = Updater.download(context, u) { progress = it }
                                progress = null
                                update = null
                                if (!Updater.install(context, apk)) {
                                    Toast.makeText(
                                        context,
                                        "Permite instalar desde esta app y vuelve a abrirla para actualizar.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (e: Exception) {
                                progress = null
                                Toast.makeText(context, "No se pudo descargar la actualización.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) { Text("Aceptar") }
                },
                dismissButton = { TextButton(onClick = { update = null }) { Text("Rechazar") } }
            )
        } else {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Descargando actualización…") },
                text = { LinearProgressIndicator(progress = { p / 100f }) },
                confirmButton = {}
            )
        }
    }
}
