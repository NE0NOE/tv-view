package com.example.tvviewer

import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * Interceptor de Archivos USB y Paquetes .ZIP con Streaming de 8 KB en RAM.
 * Soporta tanto la sobrecarga moderna (WebResourceRequest) como la heredada (String url)
 * de Android 6.0 (API 23), decodificando espacios/acentos y detectando carpetas anidadas en .ZIP.
 */
class UsbStreamWebViewClient(
    @Volatile private var activeRootDirectory: File? = null,
    private val onPageReadyCallback: ((String) -> Unit)? = null,
    private val onRequestInterceptedCallback: ((String, String, Boolean) -> Unit)? = null
) : WebViewClient() {

    companion object {
        const val LOCAL_HOST = "usb.tvlocal"
        private const val STREAM_BUFFER_BYTES = 8192 // 8 KB de buffer en RAM
    }

    @Volatile
    private var activeZipArchive: File? = null

    fun setActiveRootDirectory(rootDir: File?) {
        this.activeRootDirectory = rootDir
        this.activeZipArchive = null
    }

    fun setActiveZipArchive(zipFile: File?) {
        this.activeZipArchive = zipFile
        this.activeRootDirectory = null
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val uri = request?.url ?: return null
        return interceptUri(uri) ?: super.shouldInterceptRequest(view, request)
    }

    @Suppress("DEPRECATION")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        if (url == null) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        return interceptUri(uri) ?: super.shouldInterceptRequest(view, url)
    }

    private fun interceptUri(uri: Uri): WebResourceResponse? {
        if (!LOCAL_HOST.equals(uri.host, ignoreCase = true)) {
            return null
        }

        var rawPath = uri.path ?: "/index.html"
        rawPath = try {
            URLDecoder.decode(rawPath, "UTF-8")
        } catch (_: Exception) {
            rawPath
        }

        var relPath = rawPath.trim()
        if (relPath.isEmpty() || relPath == "/") {
            relPath = "index.html"
        }
        while (relPath.startsWith("/")) {
            relPath = relPath.substring(1)
        }
        while (relPath.startsWith("./")) {
            relPath = relPath.substring(2)
        }

        try {
            // 1. Si se seleccionó un paquete .ZIP desde la USB
            val zipArchive = activeZipArchive
            if (zipArchive != null && zipArchive.exists()) {
                val zipFile = ZipFile(zipArchive)
                val matchedEntry = findZipEntrySmart(zipFile, relPath)
                if (matchedEntry != null) {
                    val actualMime = resolveMimeType(matchedEntry.name)
                    val encoding = if (isTextMime(actualMime)) "UTF-8" else null
                    val rawStream: InputStream = zipFile.getInputStream(matchedEntry)
                    val buffered = object : BufferedInputStream(rawStream, STREAM_BUFFER_BYTES) {
                        override fun close() {
                            try {
                                super.close()
                            } finally {
                                try {
                                    zipFile.close()
                                } catch (_: IOException) {
                                }
                            }
                        }
                    }
                    onRequestInterceptedCallback?.invoke(matchedEntry.name, actualMime, true)
                    return createCorsResponse(actualMime, encoding, buffered)
                }
                zipFile.close()
            }

            // 2. Si se seleccionó un archivo .HTML dentro de una carpeta de la USB
            val rootDir = activeRootDirectory
            if (rootDir != null && rootDir.exists()) {
                val targetFile = resolveFileInDirectorySmart(rootDir, relPath)
                if (targetFile != null && targetFile.exists() && targetFile.isFile) {
                    val actualMime = resolveMimeType(targetFile.name)
                    val encoding = if (isTextMime(actualMime)) "UTF-8" else null
                    val stream: InputStream = BufferedInputStream(
                        FileInputStream(targetFile),
                        STREAM_BUFFER_BYTES
                    )
                    onRequestInterceptedCallback?.invoke(relPath, actualMime, true)
                    return createCorsResponse(actualMime, encoding, stream)
                }
            }
        } catch (_: Exception) {
        }

        val requestedMime = resolveMimeType(relPath)
        onRequestInterceptedCallback?.invoke(relPath, requestedMime, false)

        // Si es la página HTML principal y no se encontró, mostrar diagnóstico claro en el TV
        if (requestedMime == "text/html") {
            val notFoundHtml = "<!DOCTYPE html><html><body style='background:#0B0F19;color:#F8FAFC;font-family:sans-serif;padding:36px;'>" +
                    "<h2 style='color:#38BDF8;'>No se pudo abrir el archivo HTML en la USB</h2>" +
                    "<p style='color:#94A3B8;margin-top:10px;'>Ruta solicitada: <b>$relPath</b></p>" +
                    "<p style='color:#94A3B8;margin-top:6px;'>Verifica que el archivo o paquete .ZIP contenga un archivo .html válido y que el permiso de almacenamiento esté concedido.</p>" +
                    "</body></html>"
            return createCorsResponse(
                "text/html",
                "UTF-8",
                ByteArrayInputStream(notFoundHtml.toByteArray(Charsets.UTF_8))
            )
        }

        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    /**
     * Busca la entrada dentro del .ZIP incluso si el .ZIP fue comprimido con una carpeta raíz adentro
     * (por ejemplo, mi-negocio/index.html o mi-negocio/css/estilos.css).
     */
    private fun findZipEntrySmart(zipFile: ZipFile, relPath: String): java.util.zip.ZipEntry? {
        val normalized = relPath.replace('\\', '/').trimStart('/')
        val direct = zipFile.getEntry(normalized)
        if (direct != null && !direct.isDirectory) return direct

        val entries = zipFile.entries().asSequence().filter { !it.isDirectory }.toList()

        // Coincidencia insensible a mayúsculas/minúsculas o dentro de subcarpeta raíz del ZIP
        val match = entries.firstOrNull { entry ->
            val entryNorm = entry.name.replace('\\', '/').trimStart('/')
            entryNorm.equals(normalized, ignoreCase = true) ||
                    entryNorm.endsWith("/$normalized", ignoreCase = true)
        }
        if (match != null) return match

        // Si pidió index.html y el ZIP tiene otro nombre de archivo .html principal, abrir el primer .html disponible
        if (normalized.equals("index.html", ignoreCase = true)) {
            return entries.firstOrNull {
                val lower = it.name.lowercase()
                lower.endsWith(".html") || lower.endsWith(".htm")
            }
        }
        return null
    }

    /**
     * Resuelve archivos en la memoria USB tolerando diferencias de mayúsculas/minúsculas
     * (muy común en memorias USB FAT32 creadas en Windows).
     */
    private fun resolveFileInDirectorySmart(rootDir: File, relPath: String): File? {
        val direct = File(rootDir, relPath)
        val canonicalRoot = runCatching { rootDir.canonicalPath }.getOrDefault(rootDir.absolutePath)
        val canonicalDirect = runCatching { direct.canonicalPath }.getOrDefault(direct.absolutePath)

        if (canonicalDirect.startsWith(canonicalRoot) && direct.exists() && direct.isFile) {
            return direct
        }

        // Búsqueda insensible a mayúsculas/minúsculas segmento por segmento
        val segments = relPath.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        var current: File = rootDir
        for (segment in segments) {
            if (segment == "..") {
                val parent = current.parentFile ?: return null
                current = parent
                continue
            }
            val exactChild = File(current, segment)
            current = if (exactChild.exists()) {
                exactChild
            } else {
                current.listFiles()?.firstOrNull { it.name.equals(segment, ignoreCase = true) }
                    ?: return null
            }
        }
        return if (current.exists() && current.isFile) current else null
    }

    private fun createCorsResponse(
        mimeType: String,
        encoding: String?,
        stream: InputStream
    ): WebResourceResponse {
        val response = WebResourceResponse(mimeType, encoding, stream)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            response.responseHeaders = mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "no-store, no-cache"
            )
        }
        return response
    }

    private fun isTextMime(mimeType: String): Boolean {
        return mimeType.startsWith("text/") ||
                mimeType == "application/javascript" ||
                mimeType == "application/json" ||
                mimeType == "image/svg+xml"
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view != null) {
            DpadSpatialInjector.inject(view)
        }
        onPageReadyCallback?.invoke(url ?: "")
    }

    fun resolveMimeType(path: String): String {
        val clean = path.substringBefore('?').substringBefore('#').lowercase()
        if (clean.endsWith(".html") || clean.endsWith(".htm")) return "text/html"
        if (clean.endsWith(".css")) return "text/css"
        if (clean.endsWith(".js") || clean.endsWith(".mjs")) return "application/javascript"
        if (clean.endsWith(".svg")) return "image/svg+xml"
        if (clean.endsWith(".png")) return "image/png"
        if (clean.endsWith(".jpg") || clean.endsWith(".jpeg")) return "image/jpeg"
        if (clean.endsWith(".webp")) return "image/webp"
        if (clean.endsWith(".gif")) return "image/gif"
        if (clean.endsWith(".ico")) return "image/x-icon"
        if (clean.endsWith(".json")) return "application/json"
        if (clean.endsWith(".woff2")) return "font/woff2"
        if (clean.endsWith(".woff")) return "font/woff"
        if (clean.endsWith(".ttf")) return "font/ttf"
        if (clean.endsWith(".otf")) return "font/otf"
        if (clean.endsWith(".mp4")) return "video/mp4"
        if (clean.endsWith(".webm")) return "video/webm"
        if (clean.endsWith(".mp3")) return "audio/mpeg"
        val ext = MimeTypeMap.getFileExtensionFromUrl(clean)
        if (!ext.isNullOrEmpty()) {
            val guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (guessed != null) return guessed
        }
        return "application/octet-stream"
    }
}
