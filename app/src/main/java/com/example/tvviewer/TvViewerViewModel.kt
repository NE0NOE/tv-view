package com.example.tvviewer

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream

data class TvViewerUiState(
    val currentDirectoryPath: String = "",
    val entries: List<ExplorerEntry> = emptyList(),
    val selectedIndex: Int = 0,
    val isViewerOpen: Boolean = false,
    val activeWebUrl: String = "",
    val activeSourceBadge: String = "",
    val showRemoteOverlay: Boolean = true,
    val statusBanner: String = "Listo • Interceptor 8 KB usb.tvlocal activo",
    val recentStreams: List<StreamLogItem> = emptyList(),
    val showGuideDialog: Boolean = false
)

class TvViewerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TvViewerUiState())
    val uiState: StateFlow<TvViewerUiState> = _uiState.asStateFlow()

    private var demoRootDir: File? = null
    private var currentDir: File? = null

    companion object {
        private val TECHNICAL_DIR_NAMES = setOf("css", "js", "img", "assets", "fonts", "images")
        private val CANDIDATE_USB_MOUNTS = arrayOf(
            "/storage/usb1",
            "/storage/usb2",
            "/storage/usbotg",
            "/storage/udisk0",
            "/mnt/media_rw",
            "/mnt/usb_storage"
        )
    }

    fun initializeWorkspace(context: Context) {
        val demoDir = DemoUsbWorkspaceBuilder.ensureDemoUsbWorkspace(context)
        demoRootDir = demoDir

        val mountedUsb = findMountedUsbDirectory()
        if (mountedUsb != null) {
            openDirectory(mountedUsb, "Memoria USB Física detectada: ${mountedUsb.absolutePath}")
        } else {
            openDirectory(demoDir, "MEMORIA_USB Lista (Menús y .ZIP de demostración cargados)")
        }
    }

    fun findMountedUsbDirectory(): File? {
        for (path in CANDIDATE_USB_MOUNTS) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                val children = dir.listFiles()
                if (!children.isNullOrEmpty()) return dir
            }
        }
        return null
    }

    fun scanForUsbOrExternal(context: Context) {
        val usb = findMountedUsbDirectory()
        if (usb != null) {
            openDirectory(usb, "Memoria USB detectada en ${usb.absolutePath}")
            return
        }
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists() && ext.canRead() && !ext.listFiles().isNullOrEmpty()) {
            openDirectory(ext, "Almacenamiento Externo: ${ext.absolutePath}")
        } else {
            val demo = demoRootDir ?: DemoUsbWorkspaceBuilder.ensureDemoUsbWorkspace(context)
            openDirectory(
                demo,
                "Sin USB externo montado • Mostrando unidad virtual MEMORIA_USB"
            )
        }
    }

    fun openDemoWorkspace(context: Context) {
        val demo = DemoUsbWorkspaceBuilder.ensureDemoUsbWorkspace(context)
        demoRootDir = demo
        openDirectory(demo, "Espacio MEMORIA_USB para Negocios cargado")
    }

    fun openDirectory(directory: File, customStatus: String? = null) {
        currentDir = directory
        val builtEntries = mutableListOf<ExplorerEntry>()

        val parent = directory.parentFile
        val demoRootPath = demoRootDir?.canonicalPath
        val currentCanonical = runCatching { directory.canonicalPath }.getOrDefault(directory.absolutePath)

        // Permite subir a carpeta anterior si tiene permisos de lectura y no sale del espacio seguro
        if (parent != null && parent.canRead() && currentCanonical != demoRootPath) {
            builtEntries.add(
                ExplorerEntry(
                    file = parent,
                    type = ExplorerEntryType.PARENT_DIR,
                    displayTitle = "⬆ [..] Subir a carpeta anterior",
                    tagLabel = "[SUBIR]",
                    sizeLabel = parent.name.ifEmpty { "/" }
                )
            )
        }

        val files = directory.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        )

        if (files != null) {
            for (f in files) {
                val lower = f.name.lowercase()
                if (f.isDirectory) {
                    // Oculta carpetas técnicas (css, js, img) tal como indica la guía para no confundir en el TV
                    if (lower !in TECHNICAL_DIR_NAMES && !lower.startsWith(".")) {
                        builtEntries.add(
                            ExplorerEntry(
                                file = f,
                                type = ExplorerEntryType.DIRECTORY,
                                displayTitle = f.name,
                                tagLabel = "[CARPETA]",
                                sizeLabel = "Carpeta"
                            )
                        )
                    }
                } else if (lower.endsWith(".zip")) {
                    builtEntries.add(
                        ExplorerEntry(
                            file = f,
                            type = ExplorerEntryType.ZIP_PACKAGE,
                            displayTitle = f.name,
                            tagLabel = "[PAQUETE ZIP]",
                            sizeLabel = formatSize(f.length())
                        )
                    )
                } else if (lower.endsWith(".html") || lower.endsWith(".htm")) {
                    builtEntries.add(
                        ExplorerEntry(
                            file = f,
                            type = ExplorerEntryType.HTML_PAGE,
                            displayTitle = f.name,
                            tagLabel = "[PÁGINA HTML]",
                            sizeLabel = formatSize(f.length())
                        )
                    )
                }
            }
        }

        _uiState.update { state ->
            state.copy(
                currentDirectoryPath = directory.absolutePath,
                entries = builtEntries,
                selectedIndex = 0,
                isViewerOpen = false,
                statusBanner = customStatus ?: "Explorando: ${directory.name}"
            )
        }
    }

    fun moveMenuSelection(delta: Int) {
        _uiState.update { state ->
            val count = state.entries.size
            if (count == 0) state
            else {
                val next = (state.selectedIndex + delta + count) % count
                state.copy(selectedIndex = next)
            }
        }
    }

    fun selectEntryAt(
        index: Int,
        streamClient: UsbStreamWebViewClient
    ) {
        val list = _uiState.value.entries
        if (index !in list.indices) return
        val entry = list[index]
        _uiState.update { it.copy(selectedIndex = index) }

        when (entry.type) {
            ExplorerEntryType.PARENT_DIR,
            ExplorerEntryType.DIRECTORY -> {
                openDirectory(entry.file)
            }
            ExplorerEntryType.ZIP_PACKAGE -> {
                streamClient.setActiveZipArchive(entry.file)
                val url = "https://${UsbStreamWebViewClient.LOCAL_HOST}/index.html"
                _uiState.update {
                    it.copy(
                        isViewerOpen = true,
                        activeWebUrl = url,
                        activeSourceBadge = "[PAQUETE ZIP] ${entry.file.name}",
                        recentStreams = emptyList(),
                        statusBanner = "Flujo directo 8 KB desde ${entry.file.name}"
                    )
                }
            }
            ExplorerEntryType.HTML_PAGE -> {
                streamClient.setActiveRootDirectory(entry.file.parentFile)
                val url = "https://${UsbStreamWebViewClient.LOCAL_HOST}/${entry.file.name}"
                _uiState.update {
                    it.copy(
                        isViewerOpen = true,
                        activeWebUrl = url,
                        activeSourceBadge = "[PÁGINA HTML] ${entry.file.name}",
                        recentStreams = emptyList(),
                        statusBanner = "Sirviendo ${entry.file.name} + dependencias desde USB"
                    )
                }
            }
        }
    }

    fun activateHighlightedEntry(streamClient: UsbStreamWebViewClient) {
        selectEntryAt(_uiState.value.selectedIndex, streamClient)
    }

    fun returnToExplorerMenu() {
        _uiState.update {
            it.copy(
                isViewerOpen = false,
                statusBanner = "Menú Principal Carpeta / USB"
            )
        }
    }

    fun toggleRemoteOverlay() {
        _uiState.update { it.copy(showRemoteOverlay = !it.showRemoteOverlay) }
    }

    fun toggleGuideDialog(show: Boolean) {
        _uiState.update { it.copy(showGuideDialog = show) }
    }

    fun onResourceIntercepted(relPath: String, mimeType: String, success: Boolean) {
        _uiState.update { state ->
            val updated = (listOf(StreamLogItem(relPath, mimeType, success)) + state.recentStreams)
                .distinctBy { it.relativePath }
                .take(6)
            state.copy(recentStreams = updated)
        }
    }

    fun importExternalUriToUsbWorkspace(context: Context, uri: Uri, streamClient: UsbStreamWebViewClient) {
        try {
            val targetDir = currentDir ?: DemoUsbWorkspaceBuilder.ensureDemoUsbWorkspace(context)
            var fileName = queryDisplayName(context, uri) ?: "importado_${System.currentTimeMillis()}.html"
            val lower = fileName.lowercase()
            if (!lower.endsWith(".html") && !lower.endsWith(".htm") && !lower.endsWith(".zip")) {
                fileName += ".html"
            }

            val destFile = File(targetDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, 8192)
                }
            }

            openDirectory(targetDir, "Archivo importado: $fileName")
            val newIdx = _uiState.value.entries.indexOfFirst { it.file.absolutePath == destFile.absolutePath }
            if (newIdx >= 0) {
                selectEntryAt(newIdx, streamClient)
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(statusBanner = "Error al importar archivo: ${e.localizedMessage ?: "desconocido"}")
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        result = cursor.getString(idx)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        return String.format("%.1f KB", kb)
    }
}
