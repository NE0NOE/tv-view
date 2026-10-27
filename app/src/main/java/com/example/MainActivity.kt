package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tvviewer.DpadSpatialInjector
import com.example.tvviewer.TvLocalViewerApp
import com.example.tvviewer.TvViewerViewModel
import com.example.tvviewer.UsbMountReceiver
import com.example.tvviewer.UsbStreamWebViewClient
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TvViewerViewModel by viewModels()
    private lateinit var webView: WebView
    private lateinit var streamClient: UsbStreamWebViewClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setupLightweightWebView()
        viewModel.initializeWorkspace(applicationContext)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val storagePermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ ->
                viewModel.scanForUsbOrExternal(applicationContext)
            }

            val fileImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.importExternalUriToUsbWorkspace(
                        context = applicationContext,
                        uri = uri,
                        streamClient = streamClient
                    )
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            }

            LaunchedEffect(Unit) {
                UsbMountReceiver.usbEvents.collect {
                    viewModel.scanForUsbOrExternal(applicationContext)
                }
            }

            MyApplicationTheme {
                TvLocalViewerApp(
                    uiState = uiState,
                    webView = webView,
                    onEntryClick = { index ->
                        viewModel.selectEntryAt(index, streamClient)
                    },
                    onScanUsbClick = {
                        viewModel.scanForUsbOrExternal(applicationContext)
                    },
                    onDemoWorkspaceClick = {
                        viewModel.openDemoWorkspace(applicationContext)
                    },
                    onImportFileClick = {
                        fileImportLauncher.launch(
                            arrayOf(
                                "text/html",
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream"
                            )
                        )
                    },
                    onToggleRemoteOverlay = {
                        viewModel.toggleRemoteOverlay()
                    },
                    onToggleGuideDialog = { show ->
                        viewModel.toggleGuideDialog(show)
                    },
                    onDpadUp = { handleRemoteDpadMove(-1) },
                    onDpadDown = { handleRemoteDpadMove(1) },
                    onDpadLeft = { handleRemoteDpadMove(-1) },
                    onDpadRight = { handleRemoteDpadMove(1) },
                    onDpadOk = { handleRemoteDpadOk() },
                    onDpadBack = { handleRemoteDpadBack() }
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupLightweightWebView() {
        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F19"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                allowFileAccess = false
                allowContentAccess = false
            }
        }

        streamClient = UsbStreamWebViewClient(
            activeRootDirectory = null,
            onPageReadyCallback = {
                DpadSpatialInjector.inject(webView)
            },
            onRequestInterceptedCallback = { relPath, mimeType, ok ->
                viewModel.onResourceIntercepted(relPath, mimeType, ok)
            }
        )
        webView.webViewClient = streamClient
    }

    private fun handleRemoteDpadMove(delta: Int) {
        if (viewModel.uiState.value.isViewerOpen) {
            DpadSpatialInjector.moveFocus(webView, delta)
        } else {
            viewModel.moveMenuSelection(delta)
        }
    }

    private fun handleRemoteDpadOk() {
        if (viewModel.uiState.value.isViewerOpen) {
            DpadSpatialInjector.performClick(webView)
        } else {
            viewModel.activateHighlightedEntry(streamClient)
        }
    }

    private fun handleRemoteDpadBack() {
        if (viewModel.uiState.value.isViewerOpen) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                viewModel.returnToExplorerMenu()
            }
        } else {
            val entries = viewModel.uiState.value.entries
            val parentIdx = entries.indexOfFirst {
                it.type == com.example.tvviewer.ExplorerEntryType.PARENT_DIR
            }
            if (parentIdx >= 0) {
                viewModel.selectEntryAt(parentIdx, streamClient)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    handleRemoteDpadMove(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    handleRemoteDpadMove(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    handleRemoteDpadOk()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (viewModel.uiState.value.isViewerOpen) {
                        handleRemoteDpadBack()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::webView.isInitialized && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            webView.clearCache(false)
            webView.freeMemory()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
