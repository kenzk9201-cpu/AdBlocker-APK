# Android 純淨閱讀器 (防彈出廣告 + iOS Safari 級閱讀模式)

這是一個使用 **Kotlin + Jetpack Compose** 開發的 Android 應用程式，專門解決手機逛網頁時不斷跳出蓋板廣告、惡意新分頁跳轉的問題，並提供與 **iOS Safari「顯示閱讀器 (Reader View)」** 相同體驗的純淨閱讀模式。

## 檔案結構
- [settings.gradle.kts](file:///c:/Users/kent_lin/Documents/Notes_使用_程式_語法/尚未備份文件/Chrome_自製_擴充功能/Android_Reader_AdBlocker/settings.gradle.kts) / [app/build.gradle.kts](file:///c:/Users/kent_lin/Documents/Notes_使用_程式_語法/尚未備份文件/Chrome_自製_擴充功能/Android_Reader_AdBlocker/app/build.gradle.kts)：整合 `Jsoup` 與 `Readability4J`（Mozilla 閱讀模式演算法）。
- [AndroidManifest.xml](file:///c:/Users/kent_lin/Documents/Notes_使用_程式_語法/尚未備份文件/Chrome_自製_擴充功能/Android_Reader_AdBlocker/app/src/main/AndroidManifest.xml)：註冊 `ACTION_SEND` 與 `ACTION_PROCESS_TEXT`，讓你在 Chrome 瀏覽器中可隨時一鍵切換至本 App 的閱讀模式。
- [AdBlockerEngine.kt](file:///c:/Users/kent_lin/Documents/Notes_使用_程式_語法/尚未備份文件/Chrome_自製_擴充功能/Android_Reader_AdBlocker/app/src/main/java/com/example/safarireader/AdBlockerEngine.kt)：三層防彈窗與廣告攔截引擎。
- [SafariReaderExtractor.kt](file:///c:/Users/kent_lin/Documents/Notes_使用_程式_語法/尚未備份文件/Chrome_自製_擴充功能/Android_Reader_AdBlocker/app/src/main/java/com/example/safarireader/SafariReaderExtractor.kt)：負責剝除網頁所有廣告節點、保留標題/正文/圖片，並產生 iOS Safari 四種主題的純淨排版。
- [MainActivity.kt](file:///c:/Users/kent_lin/Documents/Notes_使用_程式_語法/尚未備份文件/Chrome_自製_擴充功能/Android_Reader_AdBlocker/app/src/main/java/com/example/safarireader/MainActivity.kt)：主介面、網址列、廣告攔截計數器、Safari 閱讀模式切換與字級/配色面板。

## 如何在 Chrome 與本 App 中使用？

### 方法 1：在 Chrome 遇到滿版廣告時，一鍵切換 Safari 閱讀模式
1. 在 Chrome 瀏覽任何新聞、部落格或小說網站時，若遇到蓋板廣告或干擾排版：
2. 點擊 Chrome 右上角選單 **「分享... (Share)」** -> 選擇 **「純淨閱讀器 (防廣告)」**。
3. App 會自動啟動並**直接進入「Safari 閱讀模式」**，100% 剔除所有廣告與 JavaScript 彈窗！
4. （或者）在 Chrome 複製網址後，打開本 App 點擊頂部的 **「📋 貼上網址直接閱讀」**。

### 方法 2：直接使用內建防廣告瀏覽器 + 隨時切換「閱讀器」
1. 直接在上方網址列輸入網址或 Google 搜尋關鍵字。
2. 瀏覽時，[AdBlockerEngine.kt](file:///c:/Users/kent_lin/Documents/Notes_使用_程式_語法/尚未備份文件/Chrome_自製_擴充功能/Android_Reader_AdBlocker/app/src/main/java/com/example/safarireader/AdBlockerEngine.kt) 會自動：
   - 攔截常見廣告網域（DoubleClick, PopAds, Taboola, OneAD, PopIn 等）。
   - 封鎖 `window.open` 與 `onCreateWindow`，防止點擊畫面時亂跳新分頁或強迫開啟購物 App。
   - 注入去廣告 CSS 隱藏底部懸浮橫幅與蓋板廣告。
3. 隨時點擊左上角的 **「閱讀器」** 按鈕，即可在「原網頁（去廣告版）」與「iOS Safari 純淨閱讀模式」之間一鍵切換；點擊右上角字體圖示可切換 **明亮 / 護眼羊皮紙 / 夜間灰 / 純黑 OLED** 背景與字體大小。
