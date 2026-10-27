package com.example.tvviewer

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Prepara la carpeta simulada MEMORIA_USB con sitios de negocio reales (.html + dependencias .css/.js/.svg
 * y paquetes comprimidos .zip con index.html adentro) y genera también el paquete descargable
 * TVLocalWeb-Android6-API23-Project.zip con el código fuente exacto de 64 KB.
 */
object DemoUsbWorkspaceBuilder {

    fun ensureDemoUsbWorkspace(context: Context): File {
        val usbRoot = File(context.filesDir, "MEMORIA_USB")
        if (!usbRoot.exists()) {
            usbRoot.mkdirs()
        }

        val cssDir = File(usbRoot, "css").apply { mkdirs() }
        val jsDir = File(usbRoot, "js").apply { mkdirs() }
        val imgDir = File(usbRoot, "img").apply { mkdirs() }
        val subFolder = File(usbRoot, "sucursal-centro").apply { mkdirs() }

        // 1. Dependencia CSS compartida (css/negocio.css)
        File(cssDir, "negocio.css").writeText(
            """
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
                background: radial-gradient(circle at top right, #1E293B 0%, #0B0F19 75%);
                color: #F8FAFC;
                font-family: sans-serif;
                padding: 28px 36px;
                min-height: 100vh;
            }
            header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                border-bottom: 2px solid rgba(56, 189, 248, 0.3);
                padding-bottom: 18px;
                margin-bottom: 24px;
            }
            .brand {
                display: flex;
                align-items: center;
                gap: 16px;
            }
            .badge {
                background: rgba(56, 189, 248, 0.15);
                color: #38BDF8;
                border: 1px solid #38BDF8;
                padding: 6px 14px;
                border-radius: 999px;
                font-size: 13px;
                font-weight: bold;
            }
            .tabs {
                display: flex;
                gap: 12px;
                margin-bottom: 24px;
                flex-wrap: wrap;
            }
            .tab-btn {
                background: #1E293B;
                color: #E2E8F0;
                border: 1px solid #334155;
                padding: 12px 22px;
                border-radius: 12px;
                font-size: 16px;
                font-weight: 600;
                cursor: pointer;
            }
            .tab-btn.active {
                background: #0284C7;
                color: #FFFFFF;
                border-color: #38BDF8;
            }
            .grid {
                display: grid;
                grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
                gap: 18px;
            }
            .card {
                background: rgba(30, 41, 59, 0.88);
                border: 1px solid #334155;
                border-radius: 16px;
                padding: 20px;
                display: flex;
                flex-direction: column;
                justify-content: space-between;
                gap: 12px;
            }
            .card h3 {
                font-size: 20px;
                color: #F8FAFC;
            }
            .card p {
                font-size: 14px;
                color: #94A3B8;
                line-height: 1.4;
            }
            .price-row {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-top: 8px;
            }
            .price {
                font-size: 24px;
                font-weight: 800;
                color: #38BDF8;
            }
            .action-btn {
                background: #0F172A;
                color: #38BDF8;
                border: 1px solid #38BDF8;
                border-radius: 10px;
                padding: 8px 14px;
                font-weight: bold;
                cursor: pointer;
            }
            #tv-toast {
                margin-top: 22px;
                padding: 14px 20px;
                border-radius: 12px;
                background: rgba(16, 185, 129, 0.16);
                border: 1px solid #10B981;
                color: #A7F3D0;
                font-size: 15px;
                display: none;
            }
            """.trimIndent(),
            StandardCharsets.UTF_8
        )

        // 2. Dependencia JS compartida (js/negocio.js)
        File(jsDir, "negocio.js").writeText(
            """
            function seleccionarPlato(nombre, precio) {
                var toast = document.getElementById('tv-toast');
                if (toast) {
                    toast.style.display = 'block';
                    toast.innerHTML = '<strong>Seleccionado con Control Remoto (OK):</strong> ' + nombre + ' — <strong>' + precio + '</strong>';
                }
            }
            function filtrarCategoria(cat, btn) {
                var cards = document.querySelectorAll('.card');
                for (var i = 0; i < cards.length; i++) {
                    var itemCat = cards[i].getAttribute('data-cat');
                    cards[i].style.display = (cat === 'todos' || itemCat === cat) ? 'flex' : 'none';
                }
                var botones = document.querySelectorAll('.tab-btn');
                for (var j = 0; j < botones.length; j++) {
                    botones[j].classList.remove('active');
                }
                if (btn) btn.classList.add('active');
            }
            """.trimIndent(),
            StandardCharsets.UTF_8
        )

        // 3. Dependencia SVG (img/logo-cafe.svg)
        File(imgDir, "logo-cafe.svg").writeText(
            """
            <svg xmlns="http://www.w3.org/2000/svg" width="52" height="52" viewBox="0 0 24 24" fill="none" stroke="#38BDF8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17 8h1a4 4 0 1 1 0 8h-1"/>
                <path d="M3 8h14v9a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4Z"/>
                <line x1="6" y1="2" x2="6" y2="4"/>
                <line x1="10" y1="2" x2="10" y2="4"/>
                <line x1="14" y1="2" x2="14" y2="4"/>
            </svg>
            """.trimIndent(),
            StandardCharsets.UTF_8
        )

        // 4. Página HTML Principal: menu-restaurante.html (usa css/negocio.css, js/negocio.js, img/logo-cafe.svg)
        File(usbRoot, "menu-restaurante.html").writeText(
            """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Menú Digital Restaurante</title>
                <link rel="stylesheet" href="css/negocio.css">
            </head>
            <body>
                <header>
                    <div class="brand">
                        <img src="img/logo-cafe.svg" alt="Logo Restaurante">
                        <div>
                            <h1 style="font-size:28px;">Asador & Café Del Valle</h1>
                            <p style="color:#94A3B8;font-size:14px;">Menú Digital TV • Cargado por Streaming 8 KB desde USB</p>
                        </div>
                    </div>
                    <span class="badge">ANDROID TV 6.0 READY</span>
                </header>

                <div class="tabs">
                    <button class="tab-btn active" onclick="filtrarCategoria('todos', this)">Todo el Menú</button>
                    <button class="tab-btn" onclick="filtrarCategoria('parrilla', this)">Parrilla & Cortes</button>
                    <button class="tab-btn" onclick="filtrarCategoria('bebidas', this)">Cafetería & Bebidas</button>
                    <a class="tab-btn" href="catalogo-ofertas.html" style="text-decoration:none;">Ir a Catálogo de Ofertas ➔</a>
                </div>

                <div class="grid">
                    <div class="card" data-cat="parrilla">
                        <div>
                            <h3>Bife de Chorizo Angus</h3>
                            <p>400g al carbón acompañado de papas rústicas al romero y chimichurri artesanal.</p>
                        </div>
                        <div class="price-row">
                            <span class="price">$18.50</span>
                            <button class="action-btn" onclick="seleccionarPlato('Bife de Chorizo Angus', '$18.50')">Destacar en TV</button>
                        </div>
                    </div>

                    <div class="card" data-cat="parrilla">
                        <div>
                            <h3>Hamburguesa Doble Brisket</h3>
                            <p>Pan brioche tostado, doble carne ahumada, cheddar fundido y cebolla caramelizada.</p>
                        </div>
                        <div class="price-row">
                            <span class="price">$12.90</span>
                            <button class="action-btn" onclick="seleccionarPlato('Hamburguesa Doble Brisket', '$12.90')">Destacar en TV</button>
                        </div>
                    </div>

                    <div class="card" data-cat="bebidas">
                        <div>
                            <h3>Cold Brew Naranja & Canela</h3>
                            <p>Extracción en frío por 18 horas con infusión cítrica natural de temporada.</p>
                        </div>
                        <div class="price-row">
                            <span class="price">$4.80</span>
                            <button class="action-btn" onclick="seleccionarPlato('Cold Brew Naranja & Canela', '$4.80')">Destacar en TV</button>
                        </div>
                    </div>

                    <div class="card" data-cat="bebidas">
                        <div>
                            <h3>Combo Ejecutivo Mediodía</h3>
                            <p>Plato fuerte a elección + bebida natural + café espresso o postre de la casa.</p>
                        </div>
                        <div class="price-row">
                            <span class="price">$14.00</span>
                            <button class="action-btn" onclick="seleccionarPlato('Combo Ejecutivo Mediodía', '$14.00')">Destacar en TV</button>
                        </div>
                    </div>
                </div>

                <div id="tv-toast"></div>
                <script src="js/negocio.js"></script>
            </body>
            </html>
            """.trimIndent(),
            StandardCharsets.UTF_8
        )

        // 5. Segunda página HTML: catalogo-ofertas.html
        File(usbRoot, "catalogo-ofertas.html").writeText(
            """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Catálogo de Ofertas</title>
                <link rel="stylesheet" href="css/negocio.css">
            </head>
            <body>
                <header>
                    <div class="brand">
                        <img src="img/logo-cafe.svg" alt="Icono Ofertas">
                        <div>
                            <h1 style="font-size:28px;">Catálogo de Ofertas Semanales</h1>
                            <p style="color:#94A3B8;font-size:14px;">Navega con las flechas del Control Remoto y pulsa OK</p>
                        </div>
                    </div>
                    <a class="tab-btn" href="menu-restaurante.html" style="text-decoration:none;">⬅ Volver al Menú Principal</a>
                </header>

                <div class="grid">
                    <div class="card">
                        <div>
                            <span class="badge">2x1 JUEVES</span>
                            <h3 style="margin-top:10px;">Pizzas Artesanales a la Piedra</h3>
                            <p>Todas las variedades grandes cocinadas al horno de leña de 18:00 a 22:00 hs.</p>
                        </div>
                        <div class="price-row">
                            <span class="price">$15.99</span>
                            <button class="action-btn" onclick="seleccionarPlato('Promo 2x1 Pizzas', '$15.99')">Ver Detalle</button>
                        </div>
                    </div>

                    <div class="card">
                        <div>
                            <span class="badge">-30% DESCUENTO</span>
                            <h3 style="margin-top:10px;">Pack Familiar Desayuno</h3>
                            <p>4 cafés con leche + 8 medialunas de manteca recién horneadas + jugo exprimido.</p>
                        </div>
                        <div class="price-row">
                            <span class="price">$11.50</span>
                            <button class="action-btn" onclick="seleccionarPlato('Pack Familiar Desayuno', '$11.50')">Ver Detalle</button>
                        </div>
                    </div>
                </div>

                <div id="tv-toast"></div>
                <script src="js/negocio.js"></script>
            </body>
            </html>
            """.trimIndent(),
            StandardCharsets.UTF_8
        )

        // 6. Subcarpeta de ejemplo con cartelera de sucursal
        File(subFolder, "cartelera-turno.html").writeText(
            """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <title>Cartelera de Turnos</title>
                <style>
                    body { background:#0B0F19; color:#F8FAFC; font-family:sans-serif; padding:36px; }
                    .turn-box { background:#1E293B; border:2px solid #38BDF8; border-radius:18px; padding:28px; margin-bottom:20px; display:flex; justify-content:space-between; align-items:center; }
                    .turn-num { font-size:48px; font-weight:900; color:#38BDF8; }
                    button { background:#0284C7; color:white; border:none; padding:14px 24px; border-radius:12px; font-size:18px; cursor:pointer; }
                </style>
            </head>
            <body>
                <h1 style="margin-bottom:20px;">Sucursal Centro — Llamador de Pedidos TV</h1>
                <div class="turn-box">
                    <div>
                        <p style="color:#94A3B8;">PEDIDO LISTO PARA RETIRAR</p>
                        <div class="turn-num" id="turno-actual">A-042</div>
                    </div>
                    <button onclick="document.getElementById('turno-actual').innerText='A-04' + Math.floor(Math.random()*9+3)">Siguiente Turno (OK)</button>
                </div>
            </body>
            </html>
            """.trimIndent(),
            StandardCharsets.UTF_8
        )

        // 7. Paquete comprimido .ZIP de negocio (menu-negocio.zip con index.html + estilos + script internos)
        val zipFile = File(usbRoot, "menu-negocio.zip")
        createSampleBusinessZip(zipFile)

        return usbRoot
    }

    private fun createSampleBusinessZip(targetZip: File) {
        ZipOutputStream(FileOutputStream(targetZip)).use { zos ->
            val indexHtml = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <title>Paquete ZIP Streaming — Pantalla Promocional</title>
                    <link rel="stylesheet" href="estilos.css">
                </head>
                <body>
                    <div class="hero">
                        <span class="pill">STREAMING DIRECTO DESDE .ZIP (SIN DESCOMPRIMIR EN FLASH)</span>
                        <h1>Catálogo Interactivo en Paquete .ZIP</h1>
                        <p>Este sitio completo (index.html + estilos.css + app.js) se lee al vuelo mediante ZipFile + BufferedInputStream de 8 KB en Android 6.0.</p>
                        <div class="btn-row">
                            <button onclick="cambiarPromo(1)">Promo Combo Burger ($9.99)</button>
                            <button onclick="cambiarPromo(2)">Promo Sushi 30 Piezas ($21.50)</button>
                            <button onclick="cambiarPromo(3)">Promo Postre Helado ($4.20)</button>
                        </div>
                        <div id="promo-panel" class="panel">
                            <h2>Selecciona una promoción con las flechas del control remoto y presiona OK</h2>
                        </div>
                    </div>
                    <script src="app.js"></script>
                </body>
                </html>
            """.trimIndent()

            val estilosCss = """
                body {
                    background: linear-gradient(135deg, #090D16 0%, #172554 100%);
                    color: #FFFFFF;
                    font-family: sans-serif;
                    padding: 40px;
                    margin: 0;
                }
                .hero {
                    max-width: 900px;
                    margin: 0 auto;
                }
                .pill {
                    display: inline-block;
                    background: rgba(56, 189, 248, 0.2);
                    border: 1px solid #38BDF8;
                    color: #38BDF8;
                    padding: 6px 14px;
                    border-radius: 99px;
                    font-size: 12px;
                    font-weight: bold;
                }
                h1 { font-size: 34px; margin: 16px 0 8px 0; }
                p { color: #CBD5E1; font-size: 16px; margin-bottom: 28px; }
                .btn-row { display: flex; gap: 14px; flex-wrap: wrap; margin-bottom: 28px; }
                button {
                    background: #1E293B;
                    color: #F8FAFC;
                    border: 2px solid #38BDF8;
                    border-radius: 12px;
                    padding: 14px 20px;
                    font-size: 16px;
                    font-weight: bold;
                    cursor: pointer;
                }
                .panel {
                    background: rgba(15, 23, 42, 0.85);
                    border: 1px solid #334155;
                    border-radius: 16px;
                    padding: 24px;
                }
            """.trimIndent()

            val appJs = """
                function cambiarPromo(num) {
                    var p = document.getElementById('promo-panel');
                    if (num === 1) {
                        p.innerHTML = '<h2 style="color:#38BDF8;">Combo Burger Doble Queso — $9.99</h2><p>Incluye papas fritas grandes y bebida refil en salón.</p>';
                    } else if (num === 2) {
                        p.innerHTML = '<h2 style="color:#38BDF8;">Barco Sushi 30 Piezas — $21.50</h2><p>Salmón rosado, philadelphia, langostinos apanados y salsa teriyaki.</p>';
                    } else {
                        p.innerHTML = '<h2 style="color:#38BDF8;">Copa Helado Artesanal — $4.20</h2><p>Dos bochas a elección con salsa de chocolate belga y almendras.</p>';
                    }
                }
            """.trimIndent()

            writeZipEntry(zos, "index.html", indexHtml)
            writeZipEntry(zos, "estilos.css", estilosCss)
            writeZipEntry(zos, "app.js", appJs)
        }
    }

    /**
     * Genera un archivo .ZIP con el proyecto Android Studio ligero (API 23, cero dependencias)
     * exactamente como se documenta en la guía paso a paso por si el usuario desea exportarlo.
     */
    fun generateStandaloneApi23ProjectZip(context: Context): File {
        val outFile = File(context.cacheDir, "TVLocalWeb-Android6-API23.zip")
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            writeZipEntry(
                zos,
                "TVLocalWeb/app/build.gradle",
                """
                apply plugin: 'com.android.application'

                android {
                    compileSdkVersion 23
                    buildToolsVersion "23.0.3"

                    defaultConfig {
                        applicationId "com.tvlocal.htmlviewer"
                        minSdkVersion 23
                        targetSdkVersion 23
                        versionCode 1
                        versionName "1.0.0"
                    }

                    buildTypes {
                        release {
                            minifyEnabled true
                            shrinkResources true
                            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                        }
                    }

                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_1_7
                        targetCompatibility JavaVersion.VERSION_1_7
                    }
                }

                dependencies {
                    // Vacío intencionalmente: usa solo clases nativas de Android 6.0 (API 23)
                }
                """.trimIndent()
            )

            writeZipEntry(
                zos,
                "TVLocalWeb/app/proguard-rules.pro",
                """
                -optimizationpasses 5
                -dontusemixedcaseclassnames
                -dontskipnonpubliclibraryclasses
                -verbose
                -keepattributes *Annotation*,JavascriptInterface
                -keepclassmembers class * {
                    @android.webkit.JavascriptInterface <methods>;
                }
                """.trimIndent()
            )
        }
        return outFile
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        val entry = ZipEntry(name)
        zos.putNextEntry(entry)
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        zos.write(bytes)
        zos.closeEntry()
    }
}
