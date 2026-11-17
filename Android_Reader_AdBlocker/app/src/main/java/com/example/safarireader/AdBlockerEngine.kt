package com.example.safarireader

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * 廣告攔截引擎：
 * 1. 網路層攔截 (shouldInterceptRequest)：阻擋常見廣告網域、彈窗腳本與追蹤器
 * 2. DOM 層注入 CSS/JS：隱藏殘留的滿版蓋板廣告、浮動廣告與禁止 window.open 彈出視窗
 */
object AdBlockerEngine {

    // 常見彈窗廣告、聯播網與追蹤器黑名單網域關鍵字
    private val blockedDomains = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.",
        "pagead2.googlesyndication.com",
        "adnxs.com",
        "taboola.com",
        "outbrain.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adsterra.com",
        "exoclick.com",
        "juicyads.com",
        "criteo.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "smartadserver.com",
        "adsrvr.org",
        "advertising.com",
        "2mdn.net",
        "moatads.com",
        "quantserve.com",
        "scorecardresearch.com",
        "zedo.com",
        "yengo.com",
        "mgid.com",
        "revcontent.com",
        "adform.net",
        "bidgear.com",
        "teads.tv",
        "vidoomy.com",
        "onclickads.net",
        "trafficjunky.net",
        "SCupio.com",
        "tenmax.io",
        "onead.com.tw",
        "popin.cc",
        "dable.io",
        "innity.com",
        "bridges.com.tw"
    )

    // 網址路徑特徵攔截
    private val blockedPathPatterns = listOf(
        "/ads/",
        "/adserver/",
        "/popunder",
        "/popup.js",
        "pagead/js/adsbygoogle.js",
        "gpt.js"
    )

    /**
     * 檢查請求是否為廣告
     */
    fun isAdRequest(request: WebResourceRequest): Boolean {
        val url = request.url.toString().lowercase()
        val host = request.url.host?.lowercase() ?: ""

        if (blockedDomains.any { host == it || host.endsWith(".$it") || host.contains(it) }) {
            return true
        }
        if (blockedPathPatterns.any { url.contains(it) }) {
            return true
        }
        return false
    }

    /**
     * 回傳空的 Response 來阻斷廣告載入
     */
    fun createEmptyResource(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream("".toByteArray())
        )
    }

    /**
     * 檢查跨網域惡意跳轉 (防止點擊畫面時強制跳到蝦皮、娛樂城或垃圾廣告網站)
     */
    fun isSuspiciousRedirect(currentUrl: String?, targetUrl: String): Boolean {
        val targetUri = Uri.parse(targetUrl)
        val scheme = targetUri.scheme?.lowercase() ?: return true

        // 阻擋非 http/https 的強制喚起 App 跳轉 (如 intent://, market://, shopee://)
        if (scheme != "http" && scheme != "https") {
            return true
        }

        val targetHost = targetUri.host?.lowercase() ?: return false
        if (blockedDomains.any { targetHost.contains(it) }) {
            return true
        }
        return false
    }

    /**
     * 注入於原網頁模式的防彈窗與去廣告腳本 (攔截 window.open 與清除蓋板廣告)
     */
    val antiPopupAndCosmeticJs = """
        (function() {
            // 1. 徹底封鎖 window.open 彈出新視窗廣告
            window.open = function() {
                console.log('Blocked popup window.open');
                return null;
            };

            // 2. 注入去廣告 CSS，隱藏常見蓋板廣告、底部懸浮橫幅與 iframe 廣告
            var style = document.createElement('style');
            style.innerHTML = `
                ins.adsbygoogle,
                [id^="google_ads_"],
                [id^="div-gpt-ad"],
                [class*="popup-ad"],
                [class*="modal-ad"],
                [id*="popup-ad"],
                [class*="overlay-ad"],
                [class*="floating-ad"],
                [class*="sticky-ad"],
                iframe[src*="ads"],
                iframe[src*="doubleclick"],
                iframe[src*="googlesyndication"],
                .ad-banner, .ad-container, .ad-wrapper, .advertisement {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0 !important;
                    width: 0 !important;
                    pointer-events: none !important;
                }
                body, html {
                    overflow: auto !important;
                    position: static !important;
                }
            `;
            document.head.appendChild(style);

            // 3. 移除透明點擊劫持層 (Clickjacking overlays)
            function cleanOverlays() {
                var elements = document.querySelectorAll('div, section, aside, ins');
                for (var i = 0; i < elements.length; i++) {
                    var el = elements[i];
                    var computed = window.getComputedStyle(el);
                    var zIndex = parseInt(computed.zIndex, 10);
                    if ((computed.position === 'fixed' || computed.position === 'absolute') && zIndex > 999) {
                        var idAndClass = (el.id + ' ' + el.className).toLowerCase();
                        if (idAndClass.includes('ad') || idAndClass.includes('pop') || idAndClass.includes('modal') || idAndClass.includes('banner')) {
                            el.remove();
                        }
                    }
                }
            }
            cleanOverlays();
            setInterval(cleanOverlays, 1500);
        })();
    """.trimIndent()
}
