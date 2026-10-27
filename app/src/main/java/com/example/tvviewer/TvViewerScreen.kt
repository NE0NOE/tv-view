package com.example.tvviewer

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R

@Composable
fun TvLocalViewerApp(
    uiState: TvViewerUiState,
    webView: WebView,
    onEntryClick: (Int) -> Unit,
    onScanUsbClick: () -> Unit,
    onDemoWorkspaceClick: () -> Unit,
    onImportFileClick: () -> Unit,
    onToggleRemoteOverlay: () -> Unit,
    onToggleGuideDialog: (Boolean) -> Unit,
    onDpadUp: () -> Unit,
    onDpadDown: () -> Unit,
    onDpadLeft: () -> Unit,
    onDpadRight: () -> Unit,
    onDpadOk: () -> Unit,
    onDpadBack: () -> Unit
) {
    // Manejo del botón BACK del sistema / control remoto cuando el visor WebView está abierto
    if (uiState.isViewerOpen) {
        BackHandler {
            onDpadBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // 1. Capa de fondo: WebView ultraligero con interceptor 8 KB (UsbStreamWebViewClient)
        AndroidView(
            factory = { webView },
            update = { view ->
                val targetUrl = uiState.activeWebUrl
                if (uiState.isViewerOpen && targetUrl.isNotEmpty() && view.url != targetUrl) {
                    view.loadUrl(targetUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("tv_webview_container")
        )

        // 2. Barra superior flotante cuando se está visualizando un .HTML o .ZIP
        AnimatedVisibility(
            visible = uiState.isViewerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ViewerTopStatusHud(
                activeSourceBadge = uiState.activeSourceBadge,
                recentStreams = uiState.recentStreams,
                showRemoteOverlay = uiState.showRemoteOverlay,
                onBackToMenu = onDpadBack,
                onToggleRemote = onToggleRemoteOverlay
            )
        }

        // 3. Capa principal: Menú limpio de Carpetas / USB (.html y .zip)
        AnimatedVisibility(
            visible = !uiState.isViewerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            CleanUsbExplorerScreen(
                uiState = uiState,
                onEntryClick = onEntryClick,
                onScanUsbClick = onScanUsbClick,
                onDemoWorkspaceClick = onDemoWorkspaceClick,
                onImportFileClick = onImportFileClick,
                onToggleRemoteOverlay = onToggleRemoteOverlay,
                onShowGuide = { onToggleGuideDialog(true) }
            )
        }

        // 4. Control Remoto TV D-Pad Flotante (para probar en emulador / pantalla táctil o TV)
        if (uiState.showRemoteOverlay) {
            FloatingTvRemoteDpad(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                onUp = onDpadUp,
                onDown = onDpadDown,
                onLeft = onDpadLeft,
                onRight = onDpadRight,
                onOk = onDpadOk,
                onBack = onDpadBack
            )
        }

        if (uiState.showGuideDialog) {
            ArchitectureInfoDialog(onDismiss = { onToggleGuideDialog(false) })
        }
    }
}

@Composable
private fun CleanUsbExplorerScreen(
    uiState: TvViewerUiState,
    onEntryClick: (Int) -> Unit,
    onScanUsbClick: () -> Unit,
    onDemoWorkspaceClick: () -> Unit,
    onImportFileClick: () -> Unit,
    onToggleRemoteOverlay: () -> Unit,
    onShowGuide: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF0B0F19)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        // Cabecera TV Local USB
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = stringResource(R.string.app_name),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.header_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.status_streaming),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onToggleRemoteOverlay,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_remote_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = stringResource(R.string.btn_remote_toggle),
                        tint = if (uiState.showRemoteOverlay) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(
                    onClick = onShowGuide,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("info_architecture_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Arquitectura API 23",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Ruta activa (igual a pathHeaderView en MainActivity.java de la guía)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.path_prefix) + uiState.currentDirectoryPath,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("current_path_header")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.statusBanner,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Barra de acciones rápidas (Escanear USB real, Espacio Demo MEMORIA_USB, Importar .ZIP/.HTML)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilledTonalButton(
                    onClick = onScanUsbClick,
                    modifier = Modifier.testTag("scan_usb_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_usb_scan))
                }
            }
            item {
                OutlinedButton(
                    onClick = onDemoWorkspaceClick,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("demo_workspace_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_demo_workspace))
                }
            }
            item {
                OutlinedButton(
                    onClick = onImportFileClick,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.testTag("import_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_import_file))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Lista limpia de carpetas, .HTML y paquetes .ZIP (con foco D-Pad resaltado en #38BDF8)
        if (uiState.entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.empty_folder_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("usb_file_list")
            ) {
                itemsIndexed(
                    items = uiState.entries,
                    key = { _, item -> item.file.absolutePath + "_" + item.type.name }
                ) { index, entry ->
                    val isFocused = index == uiState.selectedIndex
                    ExplorerEntryRow(
                        entry = entry,
                        isFocused = isFocused,
                        onClick = { onEntryClick(index) },
                        index = index
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplorerEntryRow(
    entry: ExplorerEntry,
    isFocused: Boolean,
    onClick: () -> Unit,
    index: Int
) {
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary // #38BDF8
    } else {
        MaterialTheme.colorScheme.outline
    }
    val containerColor = if (isFocused) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    val badgeColor = when (entry.type) {
        ExplorerEntryType.PARENT_DIR -> MaterialTheme.colorScheme.tertiary
        ExplorerEntryType.DIRECTORY -> MaterialTheme.colorScheme.primary
        ExplorerEntryType.ZIP_PACKAGE -> MaterialTheme.colorScheme.secondary
        ExplorerEntryType.HTML_PAGE -> Color(0xFF38BDF8)
    }

    val icon = when (entry.type) {
        ExplorerEntryType.PARENT_DIR,
        ExplorerEntryType.DIRECTORY -> Icons.Default.Folder
        ExplorerEntryType.ZIP_PACKAGE -> Icons.Default.FolderZip
        ExplorerEntryType.HTML_PAGE -> Icons.Default.Code
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isFocused) 2.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("explorer_entry_$index")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = entry.tagLabel,
                    tint = badgeColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = badgeColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = entry.tagLabel,
                                color = badgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = entry.displayTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.file.absolutePath,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.sizeLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (isFocused) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "OK ↵",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerTopStatusHud(
    activeSourceBadge: String,
    recentStreams: List<StreamLogItem>,
    showRemoteOverlay: Boolean,
    onBackToMenu: () -> Unit,
    onToggleRemote: () -> Unit
) {
    Surface(
        color = Color(0xE60B0F19),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onBackToMenu,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("viewer_back_to_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.btn_back_menu),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_back_menu),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = activeSourceBadge,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                IconButton(
                    onClick = onToggleRemote,
                    modifier = Modifier.testTag("viewer_toggle_remote_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = stringResource(R.string.btn_remote_toggle),
                        tint = if (showRemoteOverlay) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (recentStreams.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    items(recentStreams) { item ->
                        Surface(
                            color = if (item.success) {
                                Color(0x2610B981)
                            } else {
                                Color(0x26EF4444)
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "8KB Stream: ${item.relativePath} (${item.mimeType})",
                                color = if (item.success) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingTvRemoteDpad(
    modifier: Modifier = Modifier,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        color = Color(0xEB131B2E),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
        tonalElevation = 8.dp,
        modifier = modifier.testTag("floating_tv_remote")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "CONTROL TV (D-PAD)",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Flecha Arriba
            RemoteCircleButton(
                onClick = onUp,
                tag = "dpad_up_button",
                contentDescription = stringResource(R.string.dpad_up)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
            }

            // Izquierda - OK - Derecha
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                RemoteCircleButton(
                    onClick = onLeft,
                    tag = "dpad_left_button",
                    contentDescription = stringResource(R.string.dpad_left)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                }

                Button(
                    onClick = onOk,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dpad_ok_button")
                ) {
                    Text(
                        text = stringResource(R.string.dpad_ok),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }

                RemoteCircleButton(
                    onClick = onRight,
                    tag = "dpad_right_button",
                    contentDescription = stringResource(R.string.dpad_right)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }

            // Flecha Abajo
            RemoteCircleButton(
                onClick = onDown,
                tag = "dpad_down_button",
                contentDescription = stringResource(R.string.dpad_down)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("dpad_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.dpad_back),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RemoteCircleButton(
    onClick: () -> Unit,
    tag: String,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}

@Composable
private fun ArchitectureInfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Arquitectura TVLocalWeb (Android 6.0+)",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 480.dp)
            ) {
                Text(
                    "• UsbStreamWebViewClient: Intercepta peticiones en https://usb.tvlocal/ y sirve HTML, CSS, JS, SVG y paquetes .ZIP mediante un BufferedInputStream de 8 KB sin descomprimir en la memoria flash del TV.",
                    fontSize = 13.sp
                )
                Text(
                    "• DpadSpatialInjector: Inyecta navegación espacial D-Pad ([data-tv-focus] con borde #38BDF8) y 0% de uso de CPU en reposo.",
                    fontSize = 13.sp
                )
                Text(
                    "• UsbMountReceiver + LEANBACK_LAUNCHER: Detecta memorias USB en /storage/usb1, usbotg, udisk0 y libera RAM automáticamente en onTrimMemory().",
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido")
            }
        }
    )
}
