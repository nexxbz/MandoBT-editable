package com.example.remoto

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionName: String, val apkUrl: String)

/** update != null: hay versión nueva. Si es null, message explica por qué (ya estás al día, repositorio privado, sin internet...). */
data class CheckResult(val update: UpdateInfo?, val message: String)

/** Busca el último Release del repositorio de GitHub y, si es más nuevo, lo descarga e instala. */
object Updater {

    /**
     * Pregunta a github.com cuál es el último Release SIN usar la API (la API da error 403 por límite de
     * consultas). La página /releases/latest redirige a /releases/tag/build-N; de ahí sale el número.
     */
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val repo = BuildConfig.GITHUB_REPO
            val conn = URL("https://github.com/$repo/releases/latest").openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "ControlRemote")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            if (code == 404) {
                return@withContext CheckResult(null, "No hay Releases publicados o el repositorio es privado. Hazlo público.")
            }
            val location = conn.getHeaderField("Location")
            if (code !in 300..399 || location == null) {
                return@withContext CheckResult(null, "GitHub respondió con el código $code.")
            }

            // Los releases se llaman "build-<número>"; el número es el versionCode de la app.
            val tag = location.substringAfter("/tag/", "").substringBefore('?').substringBefore('/')
            val number = tag.substringAfterLast("-").toIntOrNull()
                ?: return@withContext CheckResult(null, "El Release \"$tag\" no tiene un número de versión válido.")
            if (number <= BuildConfig.VERSION_CODE) {
                return@withContext CheckResult(
                    null,
                    "Ya tienes la última versión (instalada 1.0.${BuildConfig.VERSION_CODE}, último Release 1.0.$number)."
                )
            }
            CheckResult(UpdateInfo("1.0.$number", "https://github.com/$repo/releases/download/$tag/app-debug.apk"), "")
        } catch (e: Exception) {
            CheckResult(null, "No se pudo consultar GitHub. ¿Hay internet?")
        }
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "update.apk")
            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read * 100 / total).toInt())
                    }
                }
            }
            if (out.length() < 100_000) throw java.io.IOException("Descarga incompleta")
            out
        }

    /** Devuelve false si primero hay que permitir "instalar apps desconocidas" para esta app. */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
