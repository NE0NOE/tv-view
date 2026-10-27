package com.example.tvviewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Receptor de eventos de montaje y extracción de memorias USB en Android TV (API 23+).
 * Notifica inmediatamente a la actividad principal para refrescar el listado de carpetas y .ZIP.
 */
class UsbMountReceiver : BroadcastReceiver() {

    companion object {
        private val _usbEvents = MutableSharedFlow<UsbMountEvent>(extraBufferCapacity = 4)
        val usbEvents: SharedFlow<UsbMountEvent> = _usbEvents.asSharedFlow()
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val path = intent.data?.path
        when (action) {
            Intent.ACTION_MEDIA_MOUNTED -> {
                _usbEvents.tryEmit(UsbMountEvent(mounted = true, mountPath = path))
            }
            Intent.ACTION_MEDIA_REMOVED,
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT -> {
                _usbEvents.tryEmit(UsbMountEvent(mounted = false, mountPath = path))
            }
        }
    }
}

data class UsbMountEvent(
    val mounted: Boolean,
    val mountPath: String?
)
