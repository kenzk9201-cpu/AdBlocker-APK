package com.example.safarireader

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var sharedUrlState = mutableStateOf<String?>(null)
    private var forceReaderModeOnLaunch = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF007AFF), // iOS Safari Blue
                    surface = Color(0xFFF2F2F7)
                )
            ) {
                SafariReaderApp(
                    externalSharedUrl = sharedUrlState.value,
                    openDirectlyInReader = forceReaderModeOnLaunch.value,
                    onExternalUrlConsumed = {
                        sharedUrlState.value = null
                        forceReaderModeOnLaunch.value = false
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * 處理從 Chrome「分享…」或選取文字傳過來的網址
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                extractFirstUrl(text)?.let { url ->
                    sharedUrlState.value = url
                    // 從 Chrome 分享過來時，預設直接進入像 Safari 一樣的無廣告閱讀模式！
                    forceReaderModeOnLaunch.value = true
                }
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                extractFirstUrl(text)?.let { url ->
                    sharedUrlState.value = url
                    forceReaderModeOnLaunch.value = true
                }
            }
            Intent.ACTION_VIEW -> {
                intent.dataString?.let { url ->
                    sharedUrlState.value = url
                    forceReaderModeOnLaunch.value = false
                }
            }
        }
    }

    private fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val regex = Regex("""https?://[^\s"<>]+""")
        return regex.find(text)?.value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SafariReaderApp(
    externalSharedUrl: String?,
    openDirectlyInReader: Boolean,
    onExternalUrlConsumed: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // 狀態管理
    var currentUrl by remember { mutableStateOf("https://zh.m.wikipedia.org/zh-tw/Safari") }
    var addressBarInput by remember { mutableStateOf(currentUrl) }
    var isReaderMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var blockedAdsCount by remember { mutableIntStateOf(0) }
    var blockedPopupsCount by remember { mutableIntStateOf(0) }

    // 閱讀模式外觀設定 (仿 iOS Safari "大小" 選單)
    var showAppearanceMenu by remember { mutableStateOf(false) }
    var readerTheme by remember { mutableStateOf(ReaderTheme.SEPIA) }
    var readerFontSize by remember { mutableIntStateOf(19) }
    var useSerifFont by remember { mutableStateOf(false) }
    var autoReaderMode by remember { mutableStateOf(false) }

    // 萃取後的文章快取
    var currentArticle by remember { mutableStateOf<ReaderArticle?>(null) }
    var isExtractingArticle by remember { mutableStateOf(false) }

    // 剪貼簿網址快速偵測 (當使用者在 Chrome 複製網址切回本 App 時提示)
    var clipboardDetectedUrl by remember { mutableStateOf<String?>(null) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // 切換或重新渲染 Safari 閱讀模式
    fun loadReaderModeForUrl(targetUrl: String) {
        isReaderMode = true
        isExtractingArticle = true
        coroutineScope.launch {
            val result = SafariReaderExtractor.extractArticle(targetUrl)
            isExtractingArticle = false
            result.onSuccess { article ->
                currentArticle = article
            }.onFailure { e ->
                Toast.makeText(context, "無法解析閱讀模式：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                isReaderMode = false
            }
        }
    }

    // 接收來自 Chrome「分享」的網址
    LaunchedEffect(externalSharedUrl) {
        if (!externalSharedUrl.isNullOrBlank()) {
            currentUrl = externalSharedUrl
            addressBarInput = externalSharedUrl
            if (openDirectlyInReader) {
                loadReaderModeForUrl(externalSharedUrl)
            } else {
                isReaderMode = false
                webViewRef?.loadUrl(externalSharedUrl)
            }
            onExternalUrlConsumed()
        }
    }

    // 處理輸入網址或關鍵字搜尋
    fun navigateToInput(rawInput: String) {
        focusManager.clearFocus()
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        val finalUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode(trimmed)}"
        }
        currentUrl = finalUrl
        addressBarInput = finalUrl
        if (isReaderMode) {
            loadReaderModeForUrl(finalUrl)
        } else {
            webViewRef?.loadUrl(finalUrl)
        }
    }

    // 返回鍵處理
    BackHandler(enabled = isReaderMode || (webViewRef?.canGoBack() == true)) {
        if (isReaderMode) {
            isReaderMode = false
        } else {
            webViewRef?.goBack()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF9F9FB))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // 頂部仿 iOS Safari 網址列與「閱讀模式」切換按鈕
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 左側：一鍵切換「Safari 閱讀模式」按鈕 (核心功能)
                    FilledTonalButton(
                        onClick = {
                            if (isReaderMode) {
                                isReaderMode = false
                            } else {
                                loadReaderModeForUrl(currentUrl)
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isReaderMode) Color(0xFF007AFF) else Color(0xFFE5E5EA),
                            contentColor = if (isReaderMode) Color.White else Color(0xFF1C1C1E)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Article,
                            contentDescription = "閱讀模式",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isReaderMode) "閱讀中" else "閱讀器",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 中間：網址與搜尋輸入框
                    OutlinedTextField(
                        value = addressBarInput,
                        onValueChange = { addressBarInput = it },
                        singleLine = true,
                        placeholder = { Text("輸入網址、搜尋，或從 Chrome 分享", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigateToInput(addressBarInput) }),
                        trailingIcon = {
                            if (addressBarInput.isNotEmpty()) {
                                IconButton(onClick = { addressBarInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清除", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color(0xFFEEEEF0),
                            unfocusedBorderColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // 右側：外觀與防廣告統計設定按鈕 ("大小")
                    IconButton(onClick = { showAppearanceMenu = !showAppearanceMenu }) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "閱讀設定與廣告統計",
                            tint = Color(0xFF007AFF)
                        )
                    }
                }

                // 廣告與彈窗攔截即時狀態列 + Chrome 剪貼簿快速導入按鈕
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "已攔截廣告: $blockedAdsCount · 阻擋彈窗: $blockedPopupsCount",
                            fontSize = 12.sp,
                            color = Color(0xFF6E6E73)
                        )
                    }

                    // 一鍵貼上 Chrome 複製的網址並進入無廣告閱讀模式
                    Text(
                        text = "📋 貼上網址直接閱讀",
                        fontSize = 12.sp,
                        color = Color(0xFF007AFF),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            val urlRegex = Regex("""https?://[^\s"<>]+""")
                            val matchedUrl = clipText?.let { urlRegex.find(it)?.value }
                            if (matchedUrl != null) {
                                currentUrl = matchedUrl
                                addressBarInput = matchedUrl
                                loadReaderModeForUrl(matchedUrl)
                            } else {
                                Toast.makeText(context, "剪貼簿中沒有偵測到 http/https 網址", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // 載入進度條
                if (isLoading || isExtractingArticle) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(2.dp)
                    )
                }

                // 展開式：Safari 閱讀器外觀面板 (字體大小、背景色、自動進入閱讀模式開關)
                AnimatedVisibility(visible = showAppearanceMenu) {
                    SafariAppearancePanel(
                        currentTheme = readerTheme,
                        onThemeSelected = { readerTheme = it },
                        fontSize = readerFontSize,
                        onFontSizeChange = { readerFontSize = it.coerceIn(14, 30) },
                        useSerif = useSerifFont,
                        onFontFamilyChange = { useSerifFont = it },
                        autoReaderMode = autoReaderMode,
                        onAutoReaderModeChange = { autoReaderMode = it }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. 底層：內建防彈窗、去廣告 WebView (原網頁模式)
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            // 關鍵防彈窗設定：禁止 JS 自動開啟彈出視窗與禁止多視窗廣告跳轉
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        }

                        webChromeClient = object : WebChromeClient() {
                            // 攔截所有試圖開啟新分頁/彈出視窗的惡意廣告行為
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                blockedPopupsCount++
                                return false
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            // 網路層攔截：阻擋廣告網域與追蹤腳本
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                if (request != null && AdBlockerEngine.isAdRequest(request)) {
                                    blockedAdsCount++
                                    return AdBlockerEngine.createEmptyResource()
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            // 導航層攔截：阻擋惡意跨網域跳轉與喚起外部購物/垃圾 App
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUrl = request?.url?.toString() ?: return false
                                if (AdBlockerEngine.isSuspiciousRedirect(view?.url, targetUrl)) {
                                    blockedPopupsCount++
                                    return true // 阻斷跳轉
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                if (!url.isNullOrBlank() && url != "about:blank") {
                                    currentUrl = url
                                    addressBarInput = url
                                }
                                // 頁面一開始載入就注入 window.open 封鎖與去廣告樣式
                                view?.evaluateJavascript(AdBlockerEngine.antiPopupAndCosmeticJs, null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                view?.evaluateJavascript(AdBlockerEngine.antiPopupAndCosmeticJs, null)
                                if (autoReaderMode && !url.isNullOrBlank() && url.startsWith("http")) {
                                    loadReaderModeForUrl(url)
                                }
                            }
                        }

                        loadUrl(currentUrl)
                    }
                }
            )

            // 2. 上層：iOS Safari 級純淨閱讀模式視圖 (100% 無廣告、無腳本、支援主題與字級切換)
            if (isReaderMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(android.graphics.Color.parseColor(readerTheme.bgHex))
                ) {
                    if (isExtractingArticle) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF007AFF))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "正在為您準備無廣告純淨閱讀模式…",
                                    color = Color(android.graphics.Color.parseColor(readerTheme.textHex)),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else if (currentArticle != null) {
                        val renderedHtml = remember(currentArticle, readerTheme, readerFontSize, useSerifFont) {
                            SafariReaderExtractor.buildSafariStyleHtml(
                                article = currentArticle!!,
                                theme = readerTheme,
                                fontSizeSp = readerFontSize,
                                useSerifFont = useSerifFont
                            )
                        }

                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    // 閱讀模式下完全關閉 JavaScript，保證 100% 不可能跳出任何廣告或彈窗！
                                    settings.javaScriptEnabled = false
                                    settings.loadsImagesAutomatically = true
                                }
                            },
                            update = { readerWebView ->
                                readerWebView.settings.javaScriptEnabled = false
                                readerWebView.loadDataWithBaseURL(
                                    currentArticle!!.url,
                                    renderedHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 仿 iOS Safari 閱讀器的「大小」與背景色控制面板
 */
@Composable
fun SafariAppearancePanel(
    currentTheme: ReaderTheme,
    onThemeSelected: (ReaderTheme) -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    useSerif: Boolean,
    onFontFamilyChange: (Boolean) -> Unit,
    autoReaderMode: Boolean,
    onAutoReaderModeChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 1. 字體大小調整 (A- / A+) 與字體選擇
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onFontSizeChange(fontSize - 2) }) {
                        Text("A-", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "${fontSize}pt",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    OutlinedButton(onClick = { onFontSizeChange(fontSize + 2) }) {
                        Text("A+", fontWeight = FontWeight.Bold)
                    }
                }

                FilterChip(
                    selected = useSerif,
                    onClick = { onFontFamilyChange(!useSerif) },
                    label = { Text(if (useSerif) "襯線體 (宋/明)" else "黑體 (預設)") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. 四種 Safari 經典閱讀背景主題 (明亮 / 護眼羊皮紙 / 夜間灰 / 純黑)
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                ReaderTheme.entries.forEach { theme ->
                    val isSelected = theme == currentTheme
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onThemeSelected(theme) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(theme.bgHex)))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF007AFF) else Color.LightGray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = theme.displayName,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isSelected) Color(0xFF007AFF) else Color.DarkGray
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            // 3. 自動開啟閱讀模式開關 (如同 iOS Safari 的「自動使用閱讀器」)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("自動進入無廣告閱讀模式", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("載入網頁後自動轉為純文字與圖片排版", fontSize = 11.sp, color = Color.Gray)
                }
                Switch(
                    checked = autoReaderMode,
                    onCheckedChange = onAutoReaderModeChange
                )
            }
        }
    }
}
