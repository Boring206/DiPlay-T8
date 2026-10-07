# DiPlay T8 — DiPlay for Android 8.1 head units

> **Unofficial fork** of [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay) 0.2.12. Upstream needs Android 9; this build runs on **Android 8.1 (API 27)** and explains on screen why a connection fails. If your head unit runs Android 9 or newer, use upstream instead.
>
> **非官方修改版。** 原版 DiPlay 需要 Android 9；這個版本可以裝在 **Android 8.1** 的車機上，並且會在畫面上說明連不上的原因。車機是 Android 9 以上的話，請直接用原版。

**[下載 / Download](https://github.com/Boring206/DiPlay-T8/releases/latest)** · [繁體中文](#繁體中文) · [English](#english)

---

## 繁體中文

### 先看這裡：你的車機能不能用

| 需要 | 說明 |
|---|---|
| Android 8.1 | 8.0 以下不能安裝。 |
| **真的** Android 系統藍牙 | 最常見的失敗原因。很多副廠車機的電話和音樂走廠商自己的藍牙模組，Android 這邊只是外殼，App 連不到 iPhone。這種車機**無法**使用無線 CarPlay，App 會在連線時判定並顯示出來。 |
| 能開熱點的 Wi-Fi | 只有 2.4 GHz 也可以，但畫面和聲音可能較卡。 |
| iPhone | 需支援無線 CarPlay。 |

### 目前狀態

- 在 Android 8.1 模擬器上用系統的熱點功能加一支模擬手機測過：覆蓋更新、熱點的兩種模式、服務廣播、連線埠、各種失敗情況的提示。
- 在一台實車（Allwinner T8／`sun8iw6p1`／Android 8.1.0）上：熱點成功啟動，但該車機的藍牙在 4–17 毫秒內就回報「已連線」，iPhone 從未回應，所以無法使用。
- **還沒有人回報在 Android 8.1 實機上完整進入 CarPlay。** 請把它當成實驗品。

### 安裝

1. 先安裝 [DiPlay-Legacy-Android v0.2.7](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/releases)（套件名稱 `com.shihab.diplay`）。本專案的安裝檔**不含** CarPlay 認證資料，第一次啟動時會從這個已安裝的 App 複製。
2. 從 [Releases](https://github.com/Boring206/DiPlay-T8/releases/latest) 下載 `DiPlay-T8.apk` 安裝。它會以「DiPlay T8」的名稱和原本的 App 並存。
3. 在車機的**系統藍牙設定**和 iPhone 配對。只在車機廠商的電話 App 裡配對是不夠的。
4. 關閉 iPhone 的個人熱點，開啟 DiPlay T8，按「連線手機」。

CarPlay 不需要車機有網路：車機開一個沒有網路的 Wi-Fi 給 iPhone 連，只用來傳畫面和聲音；地圖和音樂用的是 iPhone 自己的行動網路。

### 畫面訊息對照

| 畫面顯示 | 意思 | 怎麼辦 |
|---|---|---|
| 這台車機沒有可供 App 使用的 Android 系統藍牙 | 車機沒有 Android 藍牙 | 無線不可行 |
| 系統藍牙回報“已連線”，但實際上沒有連到 iPhone | 藍牙是廠商模組的外殼 | 無線不可行，只能找車機的 USB 線試有線 |
| 車機的藍牙是關閉的，DiPlay 無法自動開啟它 | 藍牙關著 | 按「開啟藍牙設定」開啟後返回 |
| 系統藍牙還沒有和 iPhone 配對 | 沒配對或配對失效 | 按「開啟藍牙設定」配對後返回 |
| 藍牙連上了，但 iPhone 沒有回應 | iPhone 在等你確認，或關閉了 CarPlay | 看 iPhone 的提示；檢查 設定 › 一般 › CarPlay |
| 車機自帶的熱點正開著 | 車機自己的熱點擋住 App 的熱點 | 按「選擇處理方式」 |
| 車機目前連線著 iPhone 的個人熱點 | 這時不會自動連線 | 關閉個人熱點後按「連線手機」 |

### 還是連不上

- **設定 → 診斷 → 檢視報告**，不需要儲存空間就能看。
- 車機和電腦或手機在同一個 Wi-Fi 時，用瀏覽器開 `http://<車機 IP>:8765/` 可以讀到同一份報告，內含最近 8 次連線紀錄。報告只在 App 開著時提供，並已遮蔽位址。

### 和原版的差異

- 可在 Android 8.1 編譯與執行。
- Android 8／9 提供 App 自建熱點（不用輸入名稱和密碼），並在只有一個 Wi-Fi 天線的車機上正確找到熱點介面。
- 車機自帶的熱點開著時提供三種處理方式，包含由 App 自動關閉它。
- Android 8／9 預設用 IPv4：那裡的熱點在 IPv6 link-local 上時有時無。
- 先準備藍牙再啟動熱點；Android 11 以下會自動開啟藍牙；判定「連線是假的」的車機。
- 首頁的車機相容性檢查、連線畫面的明確訊息與紀錄、區域網路診斷報告。
- 繁體中文介面。
- 套件名稱為 `com.shihab.diplay.t8`。有線 USB 模式的程式沒有更動，未在 Android 8.1 上測試。

### 自行編譯

需要 JDK 與 Android SDK／NDK，細節見 [docs/BUILD.md](docs/BUILD.md)。

```sh
ANDROID_KEYSTORE_PATH=... ANDROID_KEY_ALIAS=... ANDROID_KEYSTORE_PASSWORD=... ANDROID_KEY_PASSWORD=... \
  ./gradlew :mobile:assembleRelease
```

不設定 `DIPLAY_AUTH_ASSETS_DIR` 時，產出的安裝檔不含認證資料，和 Releases 裡的相同。單元測試：`./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest`。

---

## English

### First: can your head unit run it?

| Needed | Notes |
|---|---|
| Android 8.1 | Will not install on 8.0 or older. |
| A **real** Android Bluetooth stack | The usual reason for failure. Many aftermarket units route calls and music through the maker's own Bluetooth module and leave Android an adapter that pairs and "connects" without reaching the phone. Wireless CarPlay **cannot** work there; the app tests for this while connecting and says so. |
| Wi-Fi that can run a hotspot | 2.4 GHz only is accepted; picture and sound may stutter. |
| An iPhone | With wireless CarPlay. |

### Status

- Tested on an Android 8.1 emulator with real tethering and a simulated phone: in-place update, both hotspot modes, service discovery, the listener port, and the message for each failure.
- On one real unit (Allwinner T8, `sun8iw6p1`, Android 8.1.0) the hotspot came up, but its Bluetooth reported RFCOMM "connected" within 4–17 ms and the iPhone never answered, so it cannot be used there.
- **Nobody has reported a complete CarPlay session on Android 8.1 hardware yet.** Treat it as experimental.

### Install

1. Install [DiPlay-Legacy-Android v0.2.7](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/releases) (`com.shihab.diplay`) first. This project's APK carries **no** CarPlay identity; on first launch it copies the identity from that installed app.
2. Install `DiPlay-T8.apk` from [Releases](https://github.com/Boring206/DiPlay-T8/releases/latest). It appears as "DiPlay T8" beside the other app.
3. Pair the iPhone in the head unit's **system Bluetooth settings**. Pairing only in the maker's phone app is not enough.
4. Turn Personal Hotspot off on the iPhone, open DiPlay T8 and tap Connect phone.

The head unit needs no internet for CarPlay: it opens a Wi-Fi network without internet that only carries picture and sound, while maps and music use the iPhone's own mobile data.

### When it does not connect

The connecting screen names the cause: no Android Bluetooth, Bluetooth that reports connections which are not real, Bluetooth off or unpaired, an iPhone that does not answer, the car's own hotspot in the way, or the unit still joined to the iPhone's Personal Hotspot. Each comes with the next step where there is one.

**Settings → Diagnostics → View report** needs no storage. With the head unit and another device on the same Wi-Fi, `http://<head unit IP>:8765/` serves the same redacted report, including the last eight connection logs, while the app is open.

### What differs from upstream

- Builds and runs on Android 8.1.
- An app-owned hotspot on Android 8 and 9 (no name or password to type), found correctly on single-radio head units.
- Three ways forward when the car's own hotspot is on, including letting the app turn it off.
- IPv4 first on Android 8 and 9, where a hotspot's IPv6 link-local route comes and goes.
- Bluetooth is prepared before the hotspot, switched on by the app up to Android 11, and tested for links that are not real.
- A head unit check on the home screen, exact messages and log lines while connecting, and the LAN report.
- Traditional Chinese.
- Package `com.shihab.diplay.t8`. The wired USB path is unchanged and untested on Android 8.1.

### Build

See [docs/BUILD.md](docs/BUILD.md) for the toolchain. `./gradlew :mobile:assembleRelease` with the `ANDROID_KEYSTORE_*` variables signs a release; without `DIPLAY_AUTH_ASSETS_DIR` the APK carries no identity, like the published one. Unit tests: `./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest`.

### License and credits

Same licenses as upstream (GPL-3.0, with AGPL-3.0 parts noted below). All credit for DiPlay goes to its authors; this fork only adapts it. Not affiliated with Apple or with the upstream project.

The original README follows.

---

# DiPlay

**CarPlay for compatible BYD Android head units.** Wired and wireless, with the familiar DiAuto interface. Independent app: `com.shihab.diplay`.

> **BYD support scope:** These projects focus on BYD cars. They may work on other brands, but other brands are unsupported and there are no plans to add support or fix brand-specific incompatibilities.

[Download & website](https://shihabal3amri.github.io/DiPlay/) · [Release](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.12) · [Report a problem](https://github.com/shihabal3amri/DiPlay/issues/new/choose)

![DiPlay home](site/assets/home.png)

## 0.2.12 — public preview

Install on the **car**, not the iPhone. No jailbreak, dongle, Mac, account or authentication server is required for use. Core CarPlay does not require ADB; optional dashboard, battery, wheel-speed and parked-video features do. Your head unit must permit APK installation. Wireless supports Wi-Fi Direct, the car’s existing hotspot or Existing Wi-Fi / Same LAN; Wi-Fi Direct requires Android 10+; the APK supports Android 9+ for wired use.

- Wired USB and wireless CarPlay with local authentication.
- BYD HUD navigation with arrows, distance and street names on verified firmware.
- Car hotspot support, improved audio buffering and saved receive diagnostics.
- Automatic address discovery, fixed-channel Wi-Fi fallbacks and successful-configuration memory.
- Icon/text size, resolution and frame rate; applying a display change reconnects CarPlay.
- Local diagnostic export. Reports are sent only if you choose to share them.
- Separate installation alongside DiAuto. Run one projection app at a time.

This is **not an Apple-certified product**. The APK bundles an experimental accessory identity recovered from public Carlinkit firmware, not a newly provisioned MFi identity for DiPlay. A bundled private key is extractable. Acceptance after future iOS updates, reliability across head units and suitability of that identity for general distribution are unresolved. This release invites community testing; it is not a guarantee of universal compatibility.

Earlier releases were tested on the development DiLink5.1 car: live windshield guidance and street names work, Car hotspot now starts CarPlay, and Wi-Fi Direct performance is substantially improved. Occasional audio cutouts remain and are deferred to a later update. The floating-map test build was installed on the development DiLink 5.1 car; feedback led to the pinch corrections in 0.2.9. Earlier wheel-speed and video contributions were tested on a BYD Tang with DiLink 5.0 and an iPhone 15 Pro on iOS 27; wheel-speed dead reckoning in tunnels remains unverified. Broader head-unit and iOS compatibility is not guaranteed. The HUD firmware scope and cleanup limits are documented in [BYD navigation](docs/BYD_NAVIGATION.md).

## What’s new in 0.2.12

- Add Existing Wi-Fi / Same LAN wireless CarPlay with scoped IPv4/IPv6 discovery and network-change cleanup (#223).
- Wait for a stable car-hotspot interface and recover bounded wireless attempts when no AirPlay TCP follows StartSession (#229); add observed-state, authorized-ADB hotspot fallback on firmware exposing supported commands (#235).
- Improve Apple USB attach matching and narrowly scoped optional USB-prompt assistance (#170, #224).
- Pause Android 10 station scans during eligible hotspot/P2P sessions, preserving Same LAN, with controller leases and durable retryable restoration (#225).
- Improve split-screen, launcher cards, short-screen preparation and virtual cluster/floating-map geometry (#171, #172, #181).
- Add independent system-bar controls and correct in-session save/cancel and Local/USB-CH341 authentication selection (#191, #194).
- Add system, light-sensor, day and night CarPlay appearance modes, richer custom turn cards, and live main-video picture controls (#178, #193, #211).
- Offer custom integer resolution from 30% to 160%, with shared limits, correct 30%/160% labels and decoder/canvas capability fallback; refresh connection settings on resume (#179, #230, #196).
- Reconcile opt-in DiLink 4 cluster routing/calibration into one decoder owner, retain verified HUD gates, and journal exact stock-map holds and recovery (#213, #187).
- Add DiLink 3 guidance text and projection-display support with committed recovery before mutation, partial-setup compensation and retryable stock restoration (#182).
- Add opt-in wheel map zoom and main-screen joystick while preserving press/release and call behavior; reject stale queued work across phone/screen changes (#214, #231).
- Switch supported dashboard contents live using actual delivery and safely retained paused choices; preserve selection across stream/phone replacement (#232).
- Add a five-second dashboard-song-on-change window with timer invalidation, and retain album art while the next transfer is pending (#215, #228).
- Export reports through Downloads, document picker, app-external or private fallback storage, with explicit View/Share actions (#185, #219).

See [0.2.12 release notes](docs/RELEASE-NOTES-0.2.12.md) and [validation](docs/VALIDATION.md) for the full reviewed changes, contributor evidence and remaining hardware checks. Higher resolution costs more decoder/GPU work; above 100% is not a recommended default. Supported firmware and authorization are still required for optional BYD paths. General stutter, calls/Siri, iOS 15 startup and model-specific reports remain under investigation.

If a problem remains, reproduce it on **0.2.12**, then use **Settings → Diagnostics → Save diagnostic report**. Android 10+ normally saves to **Downloads/DiPlay**; Android 9 uses the document picker. If unavailable, use **View report** or **Share** from the confirmation, which identifies external/private fallback storage. Review the `.txt` and add it to a matching [existing issue](https://github.com/shihabal3amri/DiPlay/issues), or [create one](https://github.com/shihabal3amri/DiPlay/issues/new/choose). Include vehicle/head-unit model, exact firmware and Android/DiLink, phone/iOS, connection backend, relevant settings, steps and failure time. Reports are shared only when you choose; never post your hotspot password.

## Documentation

[Existing Wi-Fi / Same LAN](docs/EXISTING_WIFI.md) keeps the iPhone and head unit
on an external router. See the guide for setup, build requirements and the
BYD DiLink 4.0 / Android 10 clean-install validation result.

- [Install and connect](docs/INSTALL.md)
- [Compatibility and troubleshooting](docs/COMPATIBILITY.md)
- [Privacy and diagnostic reports](docs/PRIVACY.md)
- [Build from source](docs/BUILD.md)
- [Validation](docs/VALIDATION.md)
- [Release notes](CHANGELOG.md)
- [Credits and licenses](docs/THIRD_PARTY_NOTICES.md)

The website is available in English, Arabic, Russian, Ukrainian, Spanish and Simplified Chinese. The app interface supports those same six languages. Choose the app language in Settings; on Android 13+, it stays synchronized with Android’s per-app language setting.

## Source and credits

Based on [xcertplay](https://github.com/shilapi/xcertplay), GPL-3.0. The home/settings UI and website adapt [DiAuto](https://github.com/shihabal3amri/DiAuto), AGPL-3.0; that license is included in `docs/licenses`. Preserve those notices when distributing modifications. CarPlay and its icon belong to Apple Inc.; no Apple or BYD affiliation or endorsement is implied.

This repository starts with a clean public source snapshot. Local research, tester reports and release-signing secrets are excluded. The complete source corresponding to the APK is provided with every release; experimental runtime identity assets are described separately in the build instructions and notices.

## Local release packaging

The release APK intentionally contains the experimental accessory identity. The Git repository and source archive exclude all accessory and Android signing keys; tests generate synthetic identities at runtime. Source/CI builds omit runtime identity assets by default. Local release builds explicitly select an external asset directory. Publishing the APK makes its bundled identity extractable; building locally does not preserve that identity's confidentiality.
