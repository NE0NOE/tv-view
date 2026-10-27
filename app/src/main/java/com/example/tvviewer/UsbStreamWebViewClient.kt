package com.example.tvviewer

import android.net.Uri
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
import java.util.zip.ZipFile

/**
 * Interceptor de Archivos USB y Paquetes .ZIP con Streaming de 8 KB en RAM.
 * Resuelve la carga de dependencias relativas (.css, .js, .svg, imágenes) en Android 6.0+
 * bajo el dominio virtual https://usb.tvlocal/ sin descomprimir el archivo .zip en memoria interna.
 */
class UsbStreamWebViewClient(
    private var activeRootDirectory: File? = null,
    private val onPageReadyCallback: ((String) -> Unit)? = null,
    private val onRequestInterceptedCallback: ((String, String, Boolean) -> Unit)? = null
) : WebViewClient() {

    companion object {
        const val LOCAL_HOST = "usb.tvlocal"
        private const val STREAM_BUFFER_BYTES = 8192 // 8 KB de buffer en RAM
    }

    private var activeZipArchive: File? = null

    fun setActiveRootDirectory(rootDir: File?) {
        this.activeRootDirectory = rootDir
        this.activeZipArchive = null
    }

    fun setActiveZipArchive(zipFile: File?) {
        this.activeZipArchive = zipFile
    }

    fun getActiveSourceDescription(): String {
        val zip = activeZipArchive
        if (zip != null && zip.exists()) {
            return "[ZIP STREAM] ${zip.name}"
        }
        val dir = activeRootDirectory
        if (dir != null) {
            return "[DIR STREAM] ${dir.absolutePath}"
        }
        return "Sin origen activo"
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val uri: Uri = request?.url ?: return super.shouldInterceptRequest(view, request)
        if (!LOCAL_HOST.equals(uri.host, ignoreCase = true)) {
            return super.shouldInterceptRequest(view, request)
        }

        var relPath = uri.path
        if (relPath.isNullOrEmpty() || relPath == "/") {
            relPath = "/index.html"
        }
        if (relPath.startsWith("/")) {
            relPath = relPath.substring(1)
        }

        val mimeType = resolveMimeType(relPath)

        try {
            val zipArchive = activeZipArchive
            if (zipArchive != null && zipArchive.exists()) {
                val zipFile = ZipFile(zipArchive)
                val entry = zipFile.getEntry(relPath)
                    ?: zipFile.entries().asSequence().firstOrNull {
                        !it.isDirectory && (it.name.equals(relPath, ignoreCase = true) ||
                                it.name.endsWith("/$relPath", ignoreCase = true))
                    }

                if (entry != null) {
                    val rawStream: InputStream = zipFile.getInputStream(entry)
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
                    onRequestInterceptedCallback?.invoke(relPath, mimeType, true)
                    return WebResourceResponse(mimeType, "UTF-8", buffered)
                }
                zipFile.close()
            }

            val rootDir = activeRootDirectory
            if (rootDir != null) {
                val targetFile = File(rootDir, relPath)
                val canonicalRoot = rootDir.canonicalPath
                val canonicalTarget = targetFile.canonicalPath
                if (canonicalTarget.startsWith(canonicalRoot) && targetFile.exists() && targetFile.isFile) {
                    val stream: InputStream = BufferedInputStream(
                        FileInputStream(targetFile),
                        STREAM_BUFFER_BYTES
                    )
                    onRequestInterceptedCallback?.invoke(relPath, mimeType, true)
                    return WebResourceResponse(mimeType, "UTF-8", stream)
                }
            }
        } catch (_: Exception) {
        }

        onRequestInterceptedCallback?.invoke(relPath, mimeType, false)
        val notFoundHtml = "<html><body style='background:#0B0F19;color:#F87171;font-family:sans-serif;padding:32px;'>" +
                "<h2>Archivo no encontrado en USB / ZIP</h2><p>Ruta solicitada: $relPath</p></body></html>"
        return WebResourceResponse(
            "text/html",
            "UTF-8",
            ByteArrayInputStream(notFoundHtml.toByteArray(Charsets.UTF_8))
        )
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view != null) {
            DpadSpatialInjector.inject(view)
        }
        onPageReadyCallback?.invoke(url ?: "")
    }

    fun resolveMimeType(path: String): String {
        val lower = path.lowercase()
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html"
        if (lower.endsWith(".css")) return "text/css"
        if (lower.endsWith(".js")) return "application/javascript"
        if (lower.endsWith(".svg")) return "image/svg+xml"
        if (lower.endsWith(".png")) return "image/png"
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg"
        if (lower.endsWith(".webp")) return "image/webp"
        if (lower.endsWith(".json")) return "application/json"
        if (lower.endsWith(".woff2")) return "font/woff2"
        if (lower.endsWith(".ttf")) return "font/ttf"
        val ext = MimeTypeMap.getFileExtensionFromUrl(path)
        if (!ext.isNullOrEmpty()) {
            val guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (guessed != null) return guessed
        }
        return "application/octet-stream"
    }
}
