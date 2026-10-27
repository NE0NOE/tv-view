package com.example.tvviewer

import android.webkit.WebView

/**
 * Inyector de Control Remoto (D-Pad Spatial Navigation) para Android TV.
 * Permite que las flechas del control remoto resalten botones, tarjetas y enlaces dentro
 * de cualquier página HTML sin pantalla táctil y con 0% de uso de CPU en reposo.
 */
object DpadSpatialInjector {

    fun buildJsPayload(): String {
        return "(function(){" +
                "if(window.__tvDpadReady){window.__tvMove(0);return;}" +
                "window.__tvDpadReady=true;" +
                "var st=document.createElement('style');" +
                "st.textContent='[data-tv-focus]{outline:3px solid #38BDF8!important;outline-offset:4px!important;box-shadow:0 0 18px rgba(56,189,248,0.65)!important;transition:outline 0.12s ease;}';" +
                "document.head.appendChild(st);" +
                "var idx=0;" +
                "function nodes(){return document.querySelectorAll('a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),[tabindex],[data-tv-nav]');}" +
                "window.__tvMove=function(dir){" +
                "  var list=nodes();if(!list.length)return -1;" +
                "  for(var i=0;i<list.length;i++)list[i].removeAttribute('data-tv-focus');" +
                "  idx=(idx+dir+list.length)%list.length;" +
                "  var el=list[idx];el.setAttribute('data-tv-focus','1');" +
                "  el.focus();el.scrollIntoView({behavior:'smooth',block:'nearest'});" +
                "  return idx;" +
                "};" +
                "window.__tvClick=function(){" +
                "  var list=nodes();if(list[idx]){list[idx].click();return true;}return false;" +
                "};" +
                "window.__tvMove(0);" +
                "})();"
    }

    fun inject(webView: WebView) {
        webView.evaluateJavascript(buildJsPayload(), null)
    }

    fun moveFocus(webView: WebView, delta: Int) {
        webView.evaluateJavascript("window.__tvMove && window.__tvMove($delta);", null)
    }

    fun performClick(webView: WebView) {
        webView.evaluateJavascript("window.__tvClick && window.__tvClick();", null)
    }
}
