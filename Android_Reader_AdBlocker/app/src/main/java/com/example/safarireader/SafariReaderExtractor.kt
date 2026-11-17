package com.example.safarireader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist

/**
 * 閱讀模式主題設定 (仿 iOS Safari Reader 四種經典配色)
 */
enum class ReaderTheme(
    val displayName: String,
    val bgHex: String,
    val textHex: String,
    val subTextHex: String,
    val borderHex: String
) {
    WHITE("明亮", "#FFFFFF", "#1C1C1E", "#6E6E73", "#E5E5EA"),
    SEPIA("護眼羊皮紙", "#F8F1E3", "#3C2F23", "#7A6855", "#E4D7C5"),
    GRAY("夜間灰", "#2C2C2E", "#E5E5EA", "#98989D", "#3A3A3C"),
    BLACK("純黑 OLED", "#000000", "#D1D1D6", "#8E8E93", "#2C2C2E")
}

data class ReaderArticle(
    val url: String,
    val title: String,
    val byline: String?,
    val siteName: String?,
    val contentHtml: String,
    val textLength: Int
)

/**
 * 負責將充滿廣告的複雜網頁轉換成「像 iOS Safari 一樣」完全無廣告的純淨閱讀模式
 */
object SafariReaderExtractor {

    /**
     * 從指定網址抓取並解析出無廣告文章內容 (也可直接傳入已載入的 HTML)
     */
    suspend fun extractArticle(url: String, preloadedHtml: String? = null): Result<ReaderArticle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rawHtml = if (!preloadedHtml.isNullOrBlank()) {
                    preloadedHtml
                } else {
                    Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                        .timeout(12000)
                        .followRedirects(true)
                        .get()
                        .outerHtml()
                }

                // 步驟 1：先用 Jsoup 預先剔除所有廣告標籤、script、iframe、彈窗容器
                val cleanedDoc: Document = Jsoup.parse(rawHtml, url)
                cleanedDoc.select(
                    "script, style, noscript, iframe, object, embed, form, button, nav, footer, aside, " +
                    ".ad, .ads, .adsbygoogle, [id*=ad-], [class*=ad-], [id*=popup], [class*=popup], " +
                    "[class*=banner], [class*=sponsor], [class*=recommend], [class*=related], [class*=share]"
                ).remove()

                // 修正圖片懶載入 (把 data-src / data-original 轉為正式 src)
                cleanedDoc.select("img").forEach { img ->
                    val realSrc = img.attr("abs:data-src").ifBlank {
                        img.attr("abs:data-original").ifBlank {
                            img.attr("abs:data-lazy-src").ifBlank {
                                img.attr("abs:src")
                            }
                        }
                    }
                    if (realSrc.isNotBlank()) {
                        img.attr("src", realSrc)
                    }
                }

                // 步驟 2：使用 Mozilla Readability4J 萃取核心文章正文
                val readability = Readability4J(url, cleanedDoc.outerHtml())
                val article = readability.parse()

                val rawContent = article.contentWithUtf8Encoding ?: article.content ?: ""

                // 步驟 3：透過 Jsoup Safelist 進行白名單淨化，確保 100% 不會有任何 JS、事件或廣告殘留
                val safeList = Safelist.relaxed()
                    .addTags("figure", "figcaption", "section", "article", "hr")
                    .addAttributes("img", "src", "alt", "title")
                    .addAttributes("a", "href")

                val pureContentHtml = Jsoup.clean(rawContent, url, safeList)

                val fallbackText = if (pureContentHtml.isBlank()) {
                    // 若部分特殊網站無標準 article 結構，抓取所有段落 <p>
                    cleanedDoc.select("p")
                        .map { it.text().trim() }
                        .filter { it.length > 20 }
                        .joinToString("") { "<p>$it</p>" }
                } else {
                    pureContentHtml
                }

                val title = article.title?.takeIf { it.isNotBlank() }
                    ?: cleanedDoc.title().ifBlank { "無標題文章" }

                ReaderArticle(
                    url = url,
                    title = title,
                    byline = article.byline,
                    siteName = UriHostHelper.getDomainName(url),
                    contentHtml = fallbackText.ifBlank { "<p>無法從此頁面萃取長文段落，此頁面可能是首頁或純圖片/影片頁。</p>" },
                    textLength = (article.textContent ?: fallbackText).length
                )
            }
        }

    /**
     * 產生 iOS Safari 風格的極簡閱讀器 HTML
     */
    fun buildSafariStyleHtml(
        article: ReaderArticle,
        theme: ReaderTheme,
        fontSizeSp: Int,
        useSerifFont: Boolean
    ): String {
        val fontFamily = if (useSerifFont) {
            "'Noto Serif TC', 'Songti TC', 'PMingLiU', Georgia, serif"
        } else {
            "-apple-system, BlinkMacSystemFont, 'Noto Sans TC', 'PingFang TC', Roboto, sans-serif"
        }

        val metaInfo = listOfNotNull(
            article.siteName?.takeIf { it.isNotBlank() },
            article.byline?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")

        return """
            <!DOCTYPE html>
            <html lang="zh-Hant">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0">
                <style>
                    :root {
                        --bg-color: ${theme.bgHex};
                        --text-color: ${theme.textHex};
                        --sub-text: ${theme.subTextHex};
                        --border-color: ${theme.borderHex};
                    }
                    * {
                        box-sizing: border-box;
                    }
                    body {
                        margin: 0;
                        padding: 24px 20px 80px 20px;
                        background-color: var(--bg-color);
                        color: var(--text-color);
                        font-family: $fontFamily;
                        font-size: ${fontSizeSp}px;
                        line-height: 1.78;
                        letter-spacing: 0.02em;
                        word-wrap: break-word;
                        -webkit-font-smoothing: antialiased;
                    }
                    .reader-container {
                        max-width: 680px;
                        margin: 0 auto;
                    }
                    .reader-meta {
                        font-size: 0.82em;
                        color: var(--sub-text);
                        text-transform: uppercase;
                        letter-spacing: 0.04em;
                        margin-bottom: 8px;
                    }
                    h1.reader-title {
                        font-size: 1.55em;
                        line-height: 1.35;
                        font-weight: 700;
                        margin: 0 0 18px 0;
                        padding-bottom: 16px;
                        border-bottom: 1px solid var(--border-color);
                    }
                    p {
                        margin: 0 0 1.25em 0;
                        text-align: justify;
                    }
                    img, figure {
                        max-width: 100% !important;
                        height: auto !important;
                        border-radius: 10px;
                        margin: 16px auto;
                        display: block;
                    }
                    figcaption {
                        font-size: 0.82em;
                        color: var(--sub-text);
                        text-align: center;
                        margin-top: 6px;
                    }
                    blockquote {
                        margin: 1.2em 0;
                        padding: 8px 16px;
                        border-left: 3px solid var(--sub-text);
                        color: var(--sub-text);
                        font-style: italic;
                    }
                    a {
                        color: #007AFF;
                        text-decoration: none;
                    }
                    pre, code {
                        background-color: var(--border-color);
                        border-radius: 6px;
                        padding: 2px 6px;
                        overflow-x: auto;
                        font-size: 0.9em;
                    }
                    pre {
                        padding: 12px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 16px 0;
                        font-size: 0.9em;
                    }
                    th, td {
                        border: 1px solid var(--border-color);
                        padding: 8px;
                    }
                </style>
            </head>
            <body>
                <article class="reader-container">
                    ${if (metaInfo.isNotBlank()) "<div class=\"reader-meta\">$metaInfo</div>" else ""}
                    <h1 class="reader-title">${article.title}</h1>
                    <div class="reader-content">
                        ${article.contentHtml}
                    </div>
                </article>
            </body>
            </html>
        """.trimIndent()
    }
}

private object UriHostHelper {
    fun getDomainName(url: String): String {
        return try {
            val host = java.net.URI(url).host ?: return ""
            host.removePrefix("www.")
        } catch (e: Exception) {
            ""
        }
    }
}
