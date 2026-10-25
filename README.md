# TooClose — 小孩看螢幕太近就蓋住畫面 / Covers the screen when a child holds it too close

> **狀態：alpha（實驗版）。** 歡迎試用回報。 **Status: alpha.** Issues welcome.

> ⭐ **好用的話，請按右上角的 Star 給個星星！** 這是我們知道有人在用、繼續維護下去的最大動力。
> ⭐ **If this app is useful to you, please give it a Star (top-right).** It's how we know people use it, and it keeps the project going.

小朋友看平板、手機常常越看越近。iPhone 有「螢幕距離」，Android 沒有內建；市面上的同類 App 多半收費、不開源，
還要把畫面交給雲端。TooClose 只用手機本機的前鏡頭和 Android 內建的人臉偵測，**沒有網路權限**。

Kids drift closer and closer to the screen. iPhone has *Screen Distance*; stock Android has nothing similar, and the
apps that do this are mostly paid and closed source. TooClose uses only the front camera and Android's built-in face
detector, on the device, and **has no internet permission**.

## 它怎麼做 / How it works
| 情況 Situation | 反應 Response |
|---|---|
| 臉比門檻（預設 30 cm）近，連續 3 秒 / Closer than the threshold (default 30 cm) for 3 s | 全螢幕蓋住＋（選）語音提醒＋（選）暫停正在播的影片 / Full-screen cover, optional spoken reminder, pause playing media |
| 退到門檻 +3 cm 以外，連續 2 秒 / 3 cm beyond the threshold for 2 s | 自動解除、影片繼續 / Cover goes away, media resumes |
| 鏡頭被遮住（畫面全黑）/ Camera covered (black frame) | 不解除，提示別遮鏡頭 / Stays covered, asks not to cover the camera |
| 10 秒沒看到臉（走開了）/ No face for 10 s (walked away) | 解除 / Cover goes away |
| 螢幕關 / Screen off | 鏡頭關（省電）/ Camera released (saves battery) |

距離怎麼算：Android 內建 `android.media.FaceDetector` 回報兩眼距離（像素），用 Camera2 的焦距與感光元件尺寸換成焦距像素，
距離 ≈ 焦距像素 × 真實眼距 ÷ 眼距像素。真實眼距依年齡組（3–5 歲 4.7 cm、6–12 歲 5.4 cm、大人 6.3 cm）；
在 30 cm 處按一次「校正」可以修掉個人差異與鏡頭變形。

How the distance is computed: Android's `android.media.FaceDetector` reports the eye distance in pixels; the Camera2
focal length and sensor size give the focal length in pixels; distance ≈ focal px × real eye distance ÷ eye distance px.
The real eye distance depends on the age group (3–5 y 4.7 cm, 6–12 y 5.4 cm, adult 6.3 cm); one optional calibration
at 30 cm removes the remaining personal and lens differences.

## 隱私 / Privacy
- 沒有 `INTERNET` 權限；畫面只在記憶體中分析，算完就丟，不存檔、不上傳 / No `INTERNET` permission; frames are analysed in memory and dropped.
- 螢幕關時鏡頭關；守門時 Android 會顯示綠色鏡頭小點 / Camera closed while the screen is off; Android shows the green camera dot while watching.
- 不用 Google ML Kit 或任何雲端服務 / No Google ML Kit, no cloud service.

## 安裝 / Install
1. 到 [Releases](../../releases) 下載 APK 安裝 / Download the APK from Releases.
2. 打開 App：① 允許相機與通知 ② 允許顯示在其他 App 上 ③ 選年齡組 ④ 開始守門。
   Open the app: ① allow camera and notifications ② allow display over other apps ③ pick the age group ④ start.
3. （選用）設家長密碼：停止守門、改設定都要密碼 / (Optional) set a parent PIN to protect stopping and settings.

## 已知極限 / Known limits
- 重開機後 Android 不准背景開鏡頭，TooClose 會跳通知，點一下就重新開始 / After a reboot Android blocks background camera access; tap the reminder notification once.
- 小孩若會進系統設定強制停止 App，就擋不住 / A child who can force-stop apps in system settings can still turn it off.
- 內建人臉偵測對側臉、很暗的環境比較弱；量不到臉時不會亂蓋 / The built-in detector is weaker with side faces and very dark rooms; no face means no cover.
- 一直開前鏡頭會多耗一些電 / Keeping the front camera on uses some extra battery.

## 從原始碼編譯 / Build from source
```
gradle assembleDebug        # Gradle 8.9, JDK 17, Android SDK 34
```
CI（GitHub Actions）每次 push 都會編一份 debug 與未簽章 release；正式版 APK 由開發者用自己的金鑰簽。
CI builds a debug and an unsigned release APK on every push; release APKs are signed with the developer's key.

## 授權 / License
MIT。本專案在 AI（Claude）協助下撰寫，由作者審閱與維護。 Written with AI assistance (Claude); reviewed and maintained by the author.
