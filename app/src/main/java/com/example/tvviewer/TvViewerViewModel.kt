package com.example.tvviewer

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.URLEncoder

data class TvViewerUiState(
    val currentDirectoryPath: String = "",
    val entries: List<ExplorerEntry> = emptyList(),
    val selectedIndex: Int = 0,
    val isViewerOpen: Boolean = false,
    val activeWebUrl: String = "",
    val activeSourceBadge: String = "",
    val showRemoteOverlay: Boolean = true,
    val statusBanner: String = "Esperando memoria USB...",
    val recentStreams: List<StreamLogItem> = emptyList(),
    val showGuideDialog: Boolean = false,
    val loadRequestTick: Long = 0L
)

class TvViewerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TvViewerUiState())
    val uiState: StateFlow<TvViewerUiState> = _uiState.asStateFlow()

    private var currentDir: File? = null

    companion object {
        private val CANDIDATE_USB_MOUNTS = arrayOf(
            "/storage/usb1",
            "/storage/usb2",
            "/storage/usbotg",
            "/storage/udisk0",
            "/storage/udisk1",
            "/mnt/media_rw",
            "/mnt/usb_storage",
            "/mnt/usb",
            "/storage"
        )
    }

    fun initializeWorkspace(context: Context) {
        scanForUsbOrExternal(context)
    }

    /**
     * Busca activamente la memoria USB conectada al televisor Android 6.0+.
     * También inspecciona subcarpetas de /storage y /mnt/media_rw donde muchos TV montan las USB por UUID (ej. /storage/1A2B-3C4D).
     */
    fun findMountedUsbDirectory(): File? {
        // 1. Puntos de montaje directos clásicos en Android TV 6.0
        val directMounts = arrayOf(
            "/storage/usb1",
            "/storage/usb2",
            "/storage/usbotg",
            "/storage/udisk0",
            "/storage/udisk1",
            "/mnt/usb_storage"
        )
        for (path in directMounts) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                val children = dir.listFiles()
                if (!children.isNullOrEmpty()) return dir
            }
        }

        // 2. Volúmenes montados por UUID en /storage/<UUID> o /mnt/media_rw/<UUID>
        val volumeParents = arrayOf("/storage", "/mnt/media_rw")
        for (parentPath in volumeParents) {
            val parent = File(parentPath)
            if (parent.exists() && parent.isDirectory && parent.canRead()) {
                val subDirs = parent.listFiles() ?: continue
                for (sub in subDirs) {
                    val name = sub.name.lowercase()
                    if (name != "emulated" && name != "self" && sub.isDirectory && sub.canRead()) {
                        val children = sub.listFiles()
                        if (!children.isNullOrEmpty()) {
                            return sub
                        }
                    }
                }
            }
        }
        return null
    }

    fun scanForUsbOrExternal(context: Context) {
        val usb = findMountedUsbDirectory()
        if (usb != null) {
            openDirectory(usb, "Memoria USB detectada: ${usb.absolutePath}")
            return
        }

        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists() && ext.canRead()) {
            openDirectory(ext, "Sin USB detectada • Mostrando almacenamiento del TV: ${ext.absolutePath}")
        } else {
            val storageRoot = File("/storage")
            openDirectory(storageRoot, "Inserta tu memoria USB y pulsa «Refrescar USB»")
        }
    }

    fun openInternalStorage() {
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists()) {
            openDirectory(ext, "Almacenamiento interno del TV: ${ext.absolutePath}")
        }
    }

    fun openDirectory(directory: File, customStatus: String? = null) {
        currentDir = directory
        val builtEntries = mutableListOf<ExplorerEntry>()

        val parent = directory.parentFile
        if (parent != null && parent.exists() && parent.canRead()) {
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
                if (lower.startsWith(".")) continue

                if (f.isDirectory) {
                    builtEntries.add(
                        ExplorerEntry(
                            file = f,
                            type = ExplorerEntryType.DIRECTORY,
                            displayTitle = f.name,
                            tagLabel = "[CARPETA]",
                            sizeLabel = "Carpeta"
                        )
                    )
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
                statusBanner = customStatus ?: "Explorando: ${directory.absolutePath}"
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
                        statusBanner = "Flujo directo 8 KB desde ${entry.file.name}",
                        loadRequestTick = System.currentTimeMillis()
                    )
                }
            }
            ExplorerEntryType.HTML_PAGE -> {
                streamClient.setActiveRootDirectory(entry.file.parentFile)
                val encodedName = URLEncoder.encode(entry.file.name, "UTF-8").replace("+", "%20")
                val url = "https://${UsbStreamWebViewClient.LOCAL_HOST}/$encodedName"
                _uiState.update {
                    it.copy(
                        isViewerOpen = true,
                        activeWebUrl = url,
                        activeSourceBadge = "[PÁGINA HTML] ${entry.file.name}",
                        recentStreams = emptyList(),
                        statusBanner = "Sirviendo ${entry.file.name} + dependencias desde USB",
                        loadRequestTick = System.currentTimeMillis()
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

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        return String.format("%.1f KB", kb)
    }
}
