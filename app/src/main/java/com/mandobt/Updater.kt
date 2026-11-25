package com.mandobt

import java.net.HttpURLConnection
import java.net.URL

/** Consulta la última versión publicada en GitHub Releases. */
object Updater {
    class Info(val tag: String, val num: Int, val url: String)

    /** Devuelve la versión más reciente o null si no se pudo consultar. Llamar fuera del hilo principal. */
    fun latest(repo: String): Info? {
        return try {
            val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return null
            val j = org.json.JSONObject(c.inputStream.bufferedReader().readText())
            val tag = j.getString("tag_name")
            val assets = j.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break }
            }
            val n = tag.filter { it.isDigit() }.toIntOrNull()
            if (url == null || n == null) null else Info(tag, n, url)
        } catch (_: Exception) {
            null
        }
    }
}
