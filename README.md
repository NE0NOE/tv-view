# 📺 Visor Local USB (`TVLocalWeb`) — Android TV 6.0+ (API 23+)

Visor nativo ultra-ligero de páginas `.html` y paquetes comprimidos `.zip` desde memorias USB para televisores **Android TV** y pantallas digitales de negocios, sin requerir conexión a internet.

---

## 🚀 Cómo descargar el APK desde GitHub Releases

1. Cuando subas este proyecto a tu repositorio de GitHub (usando el botón **Exportar a GitHub** de AI Studio), el flujo automático **`.github/workflows/android-release.yml`** se ejecutará solo en la pestaña **Actions**.
2. Al finalizar, creará automáticamente una publicación en la sección **Releases** (en la columna derecha de tu repositorio en GitHub) con los archivos listos para descargar:
   - **`VisorLocalUSB-AndroidTV-release.apk`** (Versión firmada de producción para instalar en el TV)
   - **`VisorLocalUSB-AndroidTV-debug.apk`** (Versión de pruebas)

> **Tip:** También puedes descargar el APK o AAB directamente desde este entorno en **AI Studio** abriendo el menú de configuración (arriba a la derecha) y seleccionando **Download APK**, o exportando todo el proyecto como `.ZIP`.

---

## 🛠️ Características Técnicas

- **Interceptor Streaming de 8 KB (`UsbStreamWebViewClient`):** Sirve páginas `.html` junto con sus hojas `.css`, scripts `.js` e imágenes `.svg`/`.png`/`.jpg` relativas bajo el dominio virtual `https://usb.tvlocal/` usando un `BufferedInputStream` de 8 KB sin saturar la memoria RAM.
- **Lectura Directa de Paquetes `.ZIP`:** Abre archivos `menu-negocio.zip` en flujo continuo sin descomprimirlos en la memoria interna del televisor.
- **Inyector Espacial para Control Remoto (`DpadSpatialInjector`):** Permite recorrer enlaces y botones con las flechas del control remoto (`DPAD_UP/DOWN/LEFT/RIGHT`), hacer clic con `OK/ENTER` y regresar al menú con `BACK`, resaltando el elemento activo con borde `#38BDF8` y **0% de consumo de CPU en reposo**.
- **Detección Automática USB (`UsbMountReceiver`):** Escanea automáticamente `/storage/usb1`, `/storage/usb2`, `/storage/usbotg`, `/storage/udisk0`, `/mnt/media_rw` y `/mnt/usb_storage`.

---

## 📂 Estructura recomendada para la Memoria USB (FAT32)

```text
MEMORIA_USB/
├── menu-restaurante.html
├── catalogo-ofertas.html
├── menu-negocio.zip          <-- Paquete comprimido con index.html adentro
├── css/
│   └── negocio.css
├── js/
│   └── negocio.js
└── img/
    └── logo-cafe.svg
```
