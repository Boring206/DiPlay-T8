# 車機平台整理 / Head unit platforms

整理日期：2026-10-09。Compiled on 2026-10-09.

這份文件回答「我的車機能不能用無線 CarPlay」。它不是支援清單：除了標明「實測」的那一列，內容都是從公開的論壇、韌體檔和 issue 整理出來的，沒有用 DiPlay T8 測過。

This document is about one question: can my head unit do wireless CarPlay. It is not a support list. Apart from the rows marked as tested, everything here was put together from public forums, firmware images and issues, and was not tested with DiPlay T8.

[繁體中文](#繁體中文) · [English](#english)

---

## 繁體中文

### 為什麼不能列「支援的型號」

無線 CarPlay 的第一步，是 App 透過 Android 系統藍牙連到 iPhone。能不能用，取決於車機的藍牙是誰在管：

- **Android 系統藍牙**直接控制藍牙晶片：App 可以連到 iPhone，有機會用。
- **廠商的藍牙模組**：電話和音樂走另一顆模組，由廠商的程式和「藍牙」App 控制。Android 這一側不是沒有藍牙，就是有一個連不到手機的空殼。這種車機用任何一個 DiPlay 版本，無線都不會成功。

同一個品牌會賣好幾種主機板，同一顆晶片配不同韌體也會有兩種結果，所以只能按「平台」（主機板加韌體）來看。品牌名稱幾乎沒有參考價值。

### 怎麼認出自己的平台

看車機「設定 → 關於」頁面上的兩行：**系統版本**和 **MCU 版本**。例如 `V9.3.1_20190906.113920_TW2-FD` 加上 `T8.3.19-…` 是 TopWay 的 Allwinner T8；MCU 以 `MTCE` 開頭的是 Microntek／HCT；出現 `8227L_demo` 的是 AutoChips AC8227L。

同一頁寫的 Android 版本常常不是真的。DiPlay T8 首頁檢查卡片上、結論下面那一行小字，是系統自己回報的版本和 API 等級；以 API 數字為準。

### Android 8.1 的平台

| 平台 | 怎麼認 | 藍牙 | 無線 CarPlay 的展望 |
|---|---|---|---|
| Allwinner T8，TopWay 韌體 | 系統版本 `V9.3.1_…_TW2-FD`（字尾也可能是 AKW1、KED、JP、ZHC），MCU `T8.3.19-…` 或 `T5.3.19-…` | 廠商模組 | **不行**（本專案作者的車機實測） |
| Allwinner「T8」或 T3-P1，STM32 MCU 加 IVT 藍牙（Bosion、Eunavi、Hizpo 等） | MCU 版本以 `STM32-` 開頭；系統版本像 `KC1D01F1-O01-…-IVT-…` | 廠商模組 | 多半不行 |
| Allwinner T3L，TopWay 韌體 | 系統版本 `V8.1.1_…_TW2-X` 或 `_THEME1` | 廠商模組 | 多半不行 |
| TopWay TS9（Spreadtrum SC9853i；Ownice K6、Isudar H53 等） | MCU `Ts9.4.3-…`，系統版本 `V11.1.x_…` | 廠商模組 | 多半不行 |
| TopWay TS7（Unisoc UIS8141E／SL8141E） | MCU `Ts7.…`，系統版本 `V12.1.1_…_THEME1` | Android 系統藍牙 | **有成功紀錄**，用的是別的 DiPlay 版本（[原版 #315](https://github.com/shihabal3amri/DiPlay/issues/315)） |
| Unisoc SC7731E／UIS8141E 的其他車機 | 裝置資訊 App 顯示主機板 `sp7731e_1h10` | Android 系統藍牙 | **有成功紀錄**，用的是別的 DiPlay 版本（[原版 #274](https://github.com/shihabal3amri/DiPlay/issues/274)） |
| AutoChips AC8227L | 型號 `8227L_demo`；版本號含 `JCAC10003`、`ZXDZ` 或 `YT92xx`。畫面常寫 Android 9 到 12，實際是 API 27 | Jancar、ZXDZ 韌體是 Android 系統藍牙；XY Auto 韌體查不到 | 有可能，沒有人回報過無線 |
| Spreadtrum SC9853i，FYT 韌體（Joying、Teyes SPRO、Junsun V1） | 關於頁顯示 Intel Airmont 或 SC9853；內建 App 的套件名稱以 `com.syu.` 開頭 | 手機走廠商模組，系統的藍牙設定頁被拿掉 | 多半不行 |
| Rockchip PX30、PX6，Microntek／HCT 韌體 | MCU 版本以 `MTCE` 或 `MTCH` 開頭 | 廠商模組，只做手機免持和 OBD | 多半不行（這一列沒有經過第二輪查核） |
| Rockchip PX6，Klyde 直立螢幕，或 ZXW 的 BMW／Benz 專用螢幕 | MCU 版本含 `RL7Ac`、`RL8Ac`，或版本字串含 `eng.zxw` | 廠商模組 | 多半不行（這一列沒有經過第二輪查核） |
| MediaTek MT6737M（`k37mv1_bsp`） | 裝置資訊 App 顯示主機板 `k37mv1_bsp` | Android 藍牙開不起來 | **不行**，用的是別的版本（[Legacy #8](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/issues/8)） |
| NWD K2001O（Allwinner T3） | 版本號 `K2001O_…` | 查不到 | 不知道 |
| Android 8.1 的一般手機或平板 | 不是車機 | Android 系統藍牙 | 有可能，沒有人回報 |

「展望」的用詞：**不行**＝有實測失敗的紀錄；**多半不行**＝資料顯示是廠商模組，但沒有人實測；**有可能**＝資料顯示是 Android 系統藍牙，但沒有人回報過；**有成功紀錄**＝有人用某個 DiPlay 版本連上過，連結在英文段落裡。

### 不是 Android 8.1 的常見平台

- **Android 8.0（API 26）**：很常見的「PX5 Android 8」（Microntek MTCD／MTCE、Joying PX5）是 8.0.0，DiPlay T8 裝不上去；原版 0.2.15 以上可以安裝，但這些平台的藍牙同樣是廠商模組。
- **Android 9 以上**：請用原版。其中 BYD DiLink、AutoChips AC8257／AC8259、部分車廠原廠車機用的是 Android 系統藍牙；KSW／Witstek 的 Qualcomm 螢幕、Microntek／HCT 系列則是廠商模組。
- **Android 7.1 以下**：BYD DiLink 2.0（Android 7.1）用原版 0.2.15 以上；更舊的請看 DiPlay-Legacy-Android。

### 已經有人回報的車機

見英文段落的 [Reports that name a unit](#reports-that-name-a-unit)。要新增一筆，請到 [Issues](https://github.com/Boring206/DiPlay-T8/issues/new/choose) 選「車機回報」。

---

## English

### How this was put together

- Five researchers each took one platform family and read forum threads (mostly 4PDA and XDA), firmware dumps, GitHub issues and maker FAQs. A second, independent pass then opened the cited sources again and looked for evidence of the opposite. Both passes were done by AI agents on 2026-10-09 and read through before publishing; the wording of each entry is theirs, and "I" in an entry is the researcher. The second pass did not finish for the Rockchip family, so those entries say "not cross-checked".
- Some sites refused direct access. Where a thread was read through an archive copy or a search excerpt, the entry says so.
- "Outlook" is about wireless CarPlay with any DiPlay build on a unit running real Android 8.1. It is a reading of other people's reports, not a test.
- The deciding question for every entry is the same: can an ordinary app open a Bluetooth connection to a phone through Android's own API. Calls and music working in the maker's Bluetooth app do not answer it.

### What the outlook words mean

| Word | Meaning |
|---|---|
| Cannot work | A real attempt failed at Bluetooth on this platform. |
| Unlikely | Sources describe a maker's Bluetooth module that apps cannot use; nobody has tried CarPlay. |
| Possible, nobody has tried | Sources describe Android's own Bluetooth stack; no report either way. |
| Likely works | Someone connected with a DiPlay-derived build on this platform. |
| Not an Android 8.1 platform | Runs another Android version; the entry says which, and what was found about its Bluetooth. |

### Reports that name a unit

Each row is one report as written in the linked issue. "Build" matters: only the first row used DiPlay T8.

| Unit as reported | Real Android | Build | Result | Where it stops | Source |
|---|---|---|---|---|---|
| Allwinner T8 `sun8iw6p1`, TopWay firmware `V9.3.1_…_TW2-FD-AHD` | 8.1.0 (API 27) | DiPlay T8 t8.2 | Does not work | Bluetooth: "connected" within 4–17 ms, the iPhone never answers | this project's maintainer, 2026-10-07 |
| `sprd sp7731e_1h10_native`, aftermarket | 8.1.0 (API 27) | an unofficial build labelled 0.2.12 | Wireless works; wired does not | Wired: `USBMUX read failed` | [upstream #274](https://github.com/shihabal3amri/DiPlay/issues/274) |
| "Quad-SL8141E", MCU `Ts7.4.6-…`, system `V12.1.1_…_THEME1` (TopWay TS7) | 8.1.0 | Android 7 port ([PR #22](https://github.com/shihabal3amri/DiPlay/pull/22)), then hiscatwang's ARMv7 build | Works; audio problems on the port | — | [upstream #315](https://github.com/shihabal3amri/DiPlay/issues/315) |
| 2024 ORA Good Cat, Harman unit, Intel x86 | 8.1 | [hiscatwang/DiPlay](https://github.com/hiscatwang/DiPlay) | Works over the car hotspot, confirmed by that fork's owner | — | that fork's README |
| MediaTek MT6737M `k37mv1_bsp` | 8.1.0 (API 27) | DiPlay-Legacy-Android 0.2.7 | Does not work | Android Bluetooth cannot be switched on, while the maker's phone function pairs | [Legacy #8](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/issues/8) |
| `8227L_demo` (AC8227L, YT9216CJ), shows "Android 9.1" | API 27 | upstream 0.2.15 | Wired does not work; wireless not reported | The kernel's `cdc_ncm` driver holds the iPhone's network interface | [upstream #518](https://github.com/shihabal3amri/DiPlay/issues/518) |
| Allwinner T3L | 8.1 (API 27) | a build with the USB read fix | Wired works | — | [upstream PR #495](https://github.com/shihabal3amri/DiPlay/pull/495) |
| Nakamichi-badged aftermarket screen, `msm8953` | 9 | upstream 0.2.13 | Wireless does not work; wired works | Bluetooth: "connected" within 8–13 ms, then the reader fails | [upstream #354](https://github.com/shihabal3amri/DiPlay/issues/354) |
| NVD_K2501 (Allwinner), in a Honda CR-V | 9 | upstream | Wireless does not work; wired works | The maker's Bluetooth app owns the radio | [upstream #227](https://github.com/shihabal3amri/DiPlay/issues/227) |
| AutoChips AC8x demo board | 9 | upstream | Wireless does not work | Bluetooth: connection refused within 2–9 ms | [upstream #435](https://github.com/shihabal3amri/DiPlay/issues/435) |
| `auto_rk_t21` (RK3326), in a 2019 Geely Binrui | 11 | upstream | Wireless does not work | Bluetooth: connection times out | [upstream #98](https://github.com/shihabal3amri/DiPlay/issues/98) |

### Four ways Bluetooth fails on these units

1. **"Connected" at once, then silence.** The connection reports success within milliseconds and the iPhone never sends a byte. DiPlay T8 tests for this and says so.
2. **Refused at once.** The connection fails within milliseconds although calls and music work.
3. **Times out.** Nothing answers for 15 seconds.
4. **Android Bluetooth will not switch on,** or has no settings page, while the maker's phone app pairs normally.

All four mean the same thing for wireless CarPlay. A cable is then the only way, and it needs a USB port that carries data.

## Platforms by family

### Allwinner boards and TopWay firmware

This family splits into three firmware makers: TopWay (Allwinner R16/T3/T3L/T8 boards, then the Unisoc-based TS7/TS9/TS10/TS18 generations), an older "IVT/STM32" lineage sold by Bosion/Eunavi/Hizpo, and NWD (the K2001/K2401/K2501 boards). Genuine Android 8.1 is common here: TopWay Allwinner T8 firmware V9.3.1, TopWay T3L firmware V8.1.1, TopWay TS7 and TS9, the STM32 "T8" units, and NWD "K2001 O" are all 8.x, and several of them display a fake 9 or 10 in the About screen. On every Android 8.1 platform in this family the evidence points the same way: hands-free Bluetooth is a separate module run by the vendor firmware (on TopWay Allwinner units a "gocsdk" daemon selected by BC6/BC8 tick boxes), pairing happens in the vendor Bluetooth/Phone app, and the only non-phone device the firmware supports is an OBD adapter, often only when it is literally named "OBDII" and sometimes only with the bundled Torque. That matches the maintainer's failed Allwinner T8 unit, so that platform is rated cannot-work with high confidence; the other 8.1 platforms are rated unlikely on proxy evidence only, because I found no direct RFCOMM test by anyone else. The one clear counter-example is TopWay TS10 (UIS7862): it has a second Bluetooth adapter that pairs through standard Android settings, and tethering, file transfer, keyboards and OBD apps work on it, but it runs Android 10 or 12, so its owners belong with upstream DiPlay rather than this fork. Brand names are unreliable for identification (Ownice C500 is MediaTek, Ownice K1 is AC8227L, Ownice K6 is TopWay TS9, Teyes SPRO/CC2 is SC9853i); the firmware version string and MCU string in the About screen are what identify a platform. Wi-Fi hotspot and 5 GHz capability are poorly documented for the 8.1 units; I found only one spec line claiming an access-point mode on an Allwinner T8 unit. Direct fetches of XDA were blocked, so XDA threads were read from Internet Archive copies; 4PDA pages were read directly.

#### Allwinner T8 with TopWay firmware (system version V9.x.x, MCU T8.3.19 / T5.3.19) - the maintainer's platform

- **Recognise it by:** Settings > About: system version like V9.3.1_20190906.xxxxxx_TW2-FD (V9.3.1 means Android 8.1); MCU version like T8.3.19-24-946201-... or T5.3.19-...; T8.3.20 on DSP units. Firmware update files have GUID names. A red 'UI unauthorized, please contact the supplier' banner appears after flashing another seller's build. build.prop on the Android 9 build shows ro.fota.oem=TOPWAY, ro.fota.platform=T8, ro.tw.version=V9.4.1_..._TW2.
- **Android:** Android 6 (V9.1.1), 7.1 (V9.2.2), 8.1 (V9.3.1), 9 (V9.4.1). The V9.4.1 build.prop posted on XDA shows ro.build.version.sdk=28, so that Android 9 is genuine. No fake version reporting found for this platform.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: reported not working. Sources state: Bluetooth is a separate module whose type is chosen with BC4/BC6/BC8 tick boxes in Extra settings > Config info; choosing BC6 or BC8 links /system/bin/gocsdk to /system/etc/goc/gocsdk6 or gocsdk8 (4PDA). Some units carry an IVT BlueSoleil i140 module instead (GitHub BLP-990A; firmware names ending -IVT or -i140). The 4PDA FAQ says Bluetooth on all these units is cut down compared with normal Android, and a FAQ author describes it as a module external to Android. Phones and OBD adapters are paired in the vendor Bluetooth app with a PIN set there. Which Android package implements Bluetooth was not found.
- **Outlook for wireless CarPlay on Android 8.1:** Cannot work (confidence: high; sources checked, claim stands).
- **For owners:** If Settings > About shows a system version like V9.3.1_2019xxxx.xxxxxx_TW2-FD (or AKW1, KED, JP, ZHC...) and an MCU version starting T8.3.19 or T5.3.19, this app will not connect: calls and music run on a separate Bluetooth module operated by the vendor Bluetooth app, and Android's own Bluetooth cannot reach an iPhone. This was confirmed on the maintainer's own unit, so do not spend an afternoon on it.
- **Also read in the second pass:** [1](https://4pda.to/forum/index.php?showtopic=875407&view=findpost&p=68615518)
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=875407), [2](https://4pda.to/forum/index.php?showtopic=792456&view=findpost&p=68056136), [3](https://4pda.to/forum/index.php?showtopic=806442&view=findpost&p=62379122), [4](https://4pda.to/forum/index.php?showtopic=875407&view=findpost&p=68615518), [5](https://4pda.to/forum/index.php?showtopic=875407&view=findpost&p=87032180), [6](https://4pda.to/forum/index.php?showtopic=837879&view=findpost&p=66563288), [7](https://github.com/Ligthguard/BLP990A_osd_fix), [8](https://web.archive.org/web/20251019090443/https://xdaforums.com/t/solved-how-to-update-android-version-6-7-8-9-on-hu-with-allwinner-t8-firmware-v9-1-1-v9-2-2-v9-3-1-v9-4-1.4274577/), [9](https://web.archive.org/web/2024/https://forum.xda-developers.com/t/allwinner-t8-octa-core-android-9.3969183/), [10](https://4pda.to/forum/index.php?showtopic=875407&view=findpost&p=81783229), [11](https://www.ixbt.com/live/digs/xtrons-tr771l-universalnaya-avtomobilnaya-2din-magnitola-na-android-81.html), [12](https://4pda.to/forum/index.php?showtopic=875407&view=findpost&p=69331555)

#### Allwinner 'T8' / T3-P1 with STM32 MCU and IVT Bluetooth (Bosion, Eunavi, Hizpo lineage)

- **Recognise it by:** MCU version starts with STM32, for example STM32-20190318-11-kc2-20. OS version looks like KC1D01F1-O01-3.0.4.3.5-IVT-20190425 or XWQC01D1-O55-1.04.3.1 - BLINK_NETWORK - 20190923, not V9.x.x. TopWay T8 firmware cannot be flashed on it.
- **Android:** Android 8.1.0. 4PDA: there is no Android 9 on it, the displayed 9 is fake. An XDA owner also reports 9.0.1 shown while apps confirm Android 8.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: reported not working. 4PDA platform description lists the Bluetooth module as IVT, and the firmware name itself includes the Bluetooth module type (example KC1D01F1 - O01 - 3.0.4.3.5 - IVT - 20190425). The platform descends from the older Allwinner A20/T3 'original' line. No package names found.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: medium; sources checked, claim stands).
- **For owners:** If the MCU version starts with STM32- and the OS version looks like KC1D01F1-O01-...-IVT-... or XWQC01D1-..., expect this app not to work: owners report that Android settings have no Bluetooth page, that the unit pairs with nothing but a phone, and that only the seller's bundled Torque can use Bluetooth. These units really run Android 8.1 even when the About screen says 9 or 10, and some were sold as 'T8' but contain a T3/T3L.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=964334), [2](https://web.archive.org/web/20251019090443/https://xdaforums.com/t/solved-how-to-update-android-version-6-7-8-9-on-hu-with-allwinner-t8-firmware-v9-1-1-v9-2-2-v9-3-1-v9-4-1.4274577/), [3](https://web.archive.org/web/2024/https://forum.xda-developers.com/t/allwinner-t8-octa-core-android-9.3969183/)

#### Allwinner T3L (T3-P1) with TopWay firmware V8.1.1

- **Recognise it by:** System version V8.1.1_2021xxxx.xxxxxx_TW2-X or _THEME1; MCU on STM32, MM32F031 or STM8;
- **Android:** Android 8.1 (4PDA header: 'Android version: 8.1'; topic title says 6/7/8). No fake-version statement found for the TopWay V8.1.1 builds.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: no report found. 4PDA header: 'Bluetooth cut down (only for connecting a smartphone)'. The answer to 'Bluetooth does not work' is to toggle the module tick box (written BX6 in the post) in the extended settings' Bluetooth configuration and reboot, the same mechanism as the BC6/BC8 switch on T3/T8. Chip model not found.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: medium; sources checked, claim stands).
- **For owners:** If the system version looks like V8.1.1_2021xxxx.xxxxxx_TW2-X or _THEME1 on a 4-core Allwinner T3L unit (some Junsun V1 units are this), it is the smaller sibling of the unit that failed: same maker, same BC6 Bluetooth module switch, Bluetooth meant only for a phone in the vendor app. Nobody has tested it, but expect it not to work.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=1036647), [2](https://4pda.to/forum/index.php?showtopic=1036647&view=findpost&p=112404304), [3](https://4pda.to/forum/index.php?showtopic=1036647&view=findpost&p=123903470)

#### Allwinner T3 with TopWay firmware V8.2.x / V8.3.2 ('new variant')

- **Recognise it by:** Android settings are black text on white; system version like V8.2.1_20170419.173320_JYZC1; MCU like T5.3.19-24-10-C06101-170418; GUID-named update files.
- **Android:** Android 4.4, 6.0.1 (V8.2.x) and 7.1 (V8.3.2). The topic title also says 8, but the header lists no Android 8 firmware.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: reported not working. Same design as the T8: BC6/BC8 tick boxes, /system/etc/goc folder to back up before flashing. OBD adapters are paired in the vendor Bluetooth app by long-pressing the device and tapping the middle 'OBD' button; the adapter must be named OBDII; Torque 1.6.28 is the version that connects, newer ones do not.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** Units whose system version starts V8.2 or V8.3 run Android 6 or 7 and are below this app's minimum. If your 'T3' unit shows V8.1.1 instead, it is a T3L on Android 8.1: see that entry.
- **Also read in the second pass:** [1](https://4pda.to/forum/index.php?showtopic=837879&st=17380)
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=837879), [2](https://4pda.to/forum/index.php?showtopic=837879&view=findpost&p=68133871), [3](https://4pda.to/forum/index.php?showtopic=837879&st=17380), [4](https://web.archive.org/web/2024/https://xdaforums.com/t/dev-new-navradio-app-for-topway-based-units-t3-and-t8allwinner-ts9-intel-ts10-uis7862-and-ts18-uis8151.4024701/)

#### Allwinner T3 'original variant' with IVT BlueSoleil i140 (firmware named BSP1.x-IVT-date)

- **Recognise it by:** Android settings are white text on black; versions under 'About phone' like BSP1.3-IVT-170228, MCU like 1.13.5.00-20170225-77867719T; engineering menu by tapping Kernel version and OS version three times; firmware files os_update.zip and mcu.bin.
- **Android:** Android 6.0.1, upgraded from 4.4 on older boards; Android 7 builds exist.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: reported not working. Header: Bluetooth module IVT BlueSoleil i140 (hands-free and ELM327 work at the same time); Wi-Fi module Realtek RTL8188ETV. Older boards used a GOC module. The stock Android Bluetooth settings page exists but is hidden and has to be opened with a shortcut app.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** These units (versions like BSP1.3-IVT-170228) run Android 6 or 7 and are below this app's minimum.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=806442), [2](https://4pda.to/forum/index.php?showtopic=806442&view=findpost&p=66066517), [3](https://4pda.to/forum/index.php?showtopic=806442&view=findpost&p=60738634)

#### NWD K2001 / K2101 (Allwinner T3, board t3_p2)

- **Recognise it by:** Settings menu with a tree of items always visible on the left; build number K2001x_<code>_Sxxxxxx; separate Car Setting app;
- **Android:** K2001 = Android 4.4, K2001M = 6, K2001N = 7, K2001O = 8, K2001Q = 10. 4PDA warns that the Android version shown in device info is most likely fake; read the letter in the build name or the API level.
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: no report found. OBD adapters are bound through the vendor menu Tools > OBD pairing, not through Bluetooth search. On Android 7 builds OBD over Bluetooth does not work, with exceptions; one owner fixed it with the root app Bluetooth+, which patches the Android Bluetooth system. An Android 4.4 owner had no Bluetooth entry in the menu and could pair only a phone. Chip and driving service not found.
- **Outlook for wireless CarPlay on Android 8.1:** Unknown (confidence: low; could not be cross-checked).
- **For owners:** On NWD boards (build number starting K2001) only builds with the letter O are Android 8, and nothing I found says whether that is 8.0 or 8.1 (this app needs 8.1). Nobody has reported how Bluetooth on those builds behaves with ordinary apps, so this is untested: try it and please report.
- **Also read in the second pass:** [1](https://4pda.to/forum/index.php?showtopic=840515&view=findpost&p=67267221), [2](https://4pda.to/forum/index.php?showtopic=840515&view=findpost&p=99468273)
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=840515), [2](https://4pda.to/forum/index.php?showtopic=840515&view=findpost&p=66182037), [3](https://4pda.to/forum/index.php?showtopic=840515&view=findpost&p=99468273)

#### NWD K2501 (Allwinner T507 / H616) and K2401 (Allwinner A133)

- **Recognise it by:** Build number K2501_... or K2401_...; Toolbox app with OBD Match.
- **Android:** Android 10, API 29, on both (4PDA headers).
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: no report found. K2501 header: radio module (FM + Bluetooth) RDA5876, Wi-Fi XR819. OBD adapters are bound in a vendor Toolbox app 'OBD Match', which only lists devices with OBD in the Bluetooth name. On K2401 the head-unit pairing PIN was 6789, the adapter name should be OBD, and the engineering menu has a 'Bluetooth transmission' tick box.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** K2501 and K2401 units run Android 10, so use upstream DiPlay rather than this fork. Whether their Bluetooth is usable by ordinary apps is not known; OBD adapters are bound in a vendor Toolbox tool.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=1033389), [2](https://4pda.to/forum/index.php?showtopic=1084167), [3](https://4pda.to/forum/index.php?showtopic=1084167&view=findpost&p=139474147), [4](https://web.archive.org/web/20250114184000/https://xdaforums.com/t/k2501-thread.4326123/)

#### TopWay TS7 (Unisoc UIS8141E)

- **Recognise it by:** System version V12.1.1_2021xxxx.xxxxxx_THEME1 or _TW11; CPU shown as uis8141e;
- **Android:** Android 8.1.0, API 27. One owner of the same unit reports Android 10 shown; 4PDA does not say whether that is fake.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: no report found. No teardown of the Bluetooth part found. An owner paired an OBD adapter in the vendor Bluetooth dialler after changing its PIN from 0000 to the adapter's PIN, and a third-party app then picked the adapter up. Another owner could not get the unit to see an adapter with PINs 0000, 1234, 1111 or 6789.
- **Outlook for wireless CarPlay on Android 8.1:** Possible, nobody has tried (confidence: low; sources checked, claim weakened).
- **For owners:** TopWay TS7 (system version V12.1.1_..., CPU uis8141e; really Android 8.1 even when the screen says 10 or 12) is untested but is the most promising unit in this family: an owner shows the normal Android Bluetooth pairing page on it and others run wireless CarPlay through the seller's TLink. Even if it connects, expect choppy video, because its Wi-Fi is 2.4 GHz only and the unit is slow; please report your result.
- **Also read in the second pass:** [1](https://4pda.to/forum/index.php?showtopic=1028320&st=8100), [2](https://4pda.to/forum/index.php?showtopic=1028320&st=8100), [3](https://4pda.to/forum/index.php?showtopic=1028320&view=findpost&p=141790806), [4](https://4pda.to/forum/index.php?showtopic=1028320&st=8040)
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=1028320), [2](https://4pda.to/forum/index.php?showtopic=814148&view=findpost&p=98255095), [3](https://4pda.to/forum/index.php?showtopic=1028320&st=1500), [4](https://4pda.to/forum/index.php?showtopic=1028320&st=4160), [5](https://4pda.to/forum/index.php?showtopic=1028320&st=7060)

#### TopWay TS9 (Spreadtrum SC9853i)

- **Recognise it by:** MCU version Ts9.4.3-xxx-xx-xxxxxx-xxxxxx; system version V11.1.1_... or V11.1.2_...; processor type Octa-sp9853i;
- **Android:** Android 8.1. Some units display 9 or 10; XDA owners state that all TS9 firmware is 8.1 whatever the info screen shows.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: no report found. The 4PDA TS9 header reuses the Allwinner T8 FAQ ('part applies from the T8 topic'): BC4/BC6/BC8 module tick boxes, adapter name OBDII, pairing in the vendor Bluetooth app. Hands-free and A2DP run in the original Bluetooth app. A third-party hidden-settings app only opened the standard location settings on this firmware. Chip model not found.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: low; sources checked, claim stands).
- **For owners:** TopWay TS9 (MCU version Ts9.4.3-..., system V11.1.x, processor Octa-sp9853i; most are Android 8.1 even when the screen says 9 or 10) will probably not work: owners confirm it uses the same BC6/BC8 hands-free module setting as the Allwinner T8 unit that failed. Nobody has actually tried this app on one, so a test report would be valuable.
- **Also read in the second pass:** [1](https://web.archive.org/web/20250319112132/https://xdaforums.com/t/ts9-spreadtrum-sc9853i-4-64g-android-9-discussion-thread.4115425/)
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=976371), [2](https://web.archive.org/web/20250319112132/https://xdaforums.com/t/ts9-spreadtrum-sc9853i-4-64g-android-9-discussion-thread.4115425/), [3](https://web.archive.org/web/20251212002427/https://xdaforums.com/t/ts9-sp9853i-fact-finding-mission.4053785/), [4](https://web.archive.org/web/20220117032935/https://forum.xda-developers.com/t/help-thread-guides-ownice-k6-c960-android-head-unit-with-digital-audio-output.4085239/), [5](https://web.archive.org/web/20210416203330/https://forum.xda-developers.com/t/help-thread-guides-ownice-k6-c960-android-head-unit-with-digital-audio-output.4085239/page-8), [6](https://4pda.to/forum/index.php?showtopic=976371&st=2300), [7](https://4pda.to/forum/index.php?showtopic=976371&st=2420), [8](https://4pda.to/forum/index.php?showtopic=976371&st=1740)

#### TopWay TS10 / TS10S (UIS7862) and TS18 (UIS8581A / SC9863A)

- **Recognise it by:** MCU family TS10 or TS18; CPU UIS7862(S) or UIS8581A; Bluetooth app settings contain 'Bluetooth data service'; a phone sees two names, carkit... and Topway.
- **Android:** Android 10 on TS10 and TS18, Android 12 on TS10S. 4PDA: versions 11, 13 and 14 shown on these are drawn by the seller.
- **Bluetooth:** both kinds exist; apps reaching a phone or device through the Android API: mixed reports. 4PDA: Realtek 8761BTV, Bluetooth 5.0. TS10 has two Bluetooth modules. The built-in one appears to a phone as 'carkit ...' and serves hands-free and A2DP through the vendor Bluetooth app. The second is switched on under Bluetooth > Settings > Bluetooth data service > Go (or the unit's 'More' settings), appears to a phone as 'Topway', and opens the menu for ordinary Bluetooth devices. TS18 sometimes has only one module. A 4PDA buying FAQ says a single built-in module can only connect a phone as a headset and OBD2 adapters.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** TS10 and TS18 units run Android 10 or 12 (versions 11, 13, 14 on the screen are painted on), so use upstream DiPlay, not this fork. On a TS10 the place to pair the iPhone is the standard Android Bluetooth page opened by Bluetooth > Settings > 'Bluetooth data service', not the vendor phone app; TS18 units with a single module probably cannot do it.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=1015856), [2](https://4pda.to/forum/index.php?showtopic=1015856&view=findpost&p=119259456), [3](https://4pda.to/forum/index.php?showtopic=1023106&view=findpost&p=107611396), [4](https://4pda.to/forum/index.php?showtopic=1023106&view=findpost&p=107661219), [5](https://4pda.to/forum/index.php?showtopic=946708&st=11920), [6](https://web.archive.org/web/20211026013126/https://forum.xda-developers.com/t/ts10-topway-oknavi-ekiy-etc-uis7862-hardware-pics.4298329/)

#### Older TopWay / Allwinner generations below Android 8 (R16, T7, TS8)

- **Recognise it by:** R16 TopWay firmware version 7.3.x; TS8 firmware V10.4.5_...; Ownice K5/C900 with a 360-degree camera app.
- **Android:** R16: Android 4.4 and 6.0. Ownice K5/C900 (T7): Android 6.0 per its topic, 7.1 per the section index. TS8: Android 6.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: no report found. Ownice K5/C900 header names the Bluetooth chip BC-06 with A2DP and OBDII. R16 owners describe the same vendor-app PIN pairing for OBD. The T3 TopWay firmware is described on 4PDA as a recompiled R16 firmware.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** Allwinner R16 and T7 units and the TopWay TS8 run Android 4 to 7 and are below this app's minimum. Note that Ownice C500, K1 and K3 are not Allwinner units at all; go by the version strings in About, not the brand.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=792456), [2](https://4pda.to/forum/index.php?showtopic=983995), [3](https://4pda.to/forum/index.php?showtopic=954230), [4](https://4pda.to/forum/index.php?showtopic=779092), [5](https://4pda.to/forum/index.php?showtopic=902632)

### Rockchip (Microntek/HCT MTC, Klyde, ZXW, Joying PX5)

Across the Rockchip family I found no platform where an ordinary third-party app is shown to reach a phone over Bluetooth through the Android API, and a lot of evidence that it cannot. On the Microntek/HCT line (MCU string MTCB/MTCC/MTCD/MTCE/MTCP/MTCH/HCTG), Bluetooth is a vendor design: a hands-free module or a Realtek chip run by vendor binaries (gocsdk, sdsdk, sdsdk968) plus the vendor Bluetooth app, and the firmware sorts every remote device into "phone" (calls, music, phonebook) or "OBD adapter". The 4PDA FAQs for PX5, PX6 and PX30 all say the module works only with those two things; no source reports a test of an RFCOMM socket to a phone, so the verdict is "unlikely", not "proven impossible". Genuine Android 8.1 exists mainly on MTCE PX30 (build IDs OPM6/OPM8), on first-generation MTCE PX6, on Klyde/XYStar vertical-screen PX6 units and on ZXW PX6 screens for BMW (OPM5); these are the fork's real Rockchip audience and all of them show the same restricted Bluetooth. The very common "PX5 Android 8" units run 8.0.0 (API 26, build OPR5.170623.007), so the T8 build (min API 27) will not install on them; this applies to MTCD/E PX5, Klyde CSN2 PX5 and Joying FYT PX5. Joying "QD" PX5 units display "8.1.0" next to an 8.0.0 build ID, which looks like a relabel (my inference from Google's build-number table). Wi-Fi hotspot exists on the HCT units (the factory Zlink wireless CarPlay uses it together with Bluetooth), but I found no source for 5 GHz. Two things the maintainer should know: upstream's README now says its APK supports Android 7.1+ (API 25) with 7.1-8.1 "not yet confirmed on a vehicle", and it calls non-BYD brands unsupported. Method caveat: XDA blocks direct fetching, so I read XDA threads through a reader proxy and the quotes are close paraphrases with some usernames and dates missing; 4PDA pages were read directly and the translations are mine.

#### Microntek MTCB / MTCC (RK3066, RK3188)

- **Recognise it by:** Settings > About: MCU version starts with MTCB or MTCC (example in a source: MTCB-GS-V1.91). Android 4.x or 5.1.1.
- **Android:** Android 4.1/4.2/4.4.4 on RK3066 and 5.1.1 on RK3188 according to the sources. No 8.x firmware found.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: mixed reports. Sources state: the factory menu offers hands-free modules WQ_BC5, WQ_BC6, WQ_BC6B, WQ_BC8, SD_BC5, SD_BC8, Parrot_FC6000T and IVT_BT. XDA developer agentdr8 says the OEM routes "pretty much all BT interactions through the MCU, via serial AT commands", Bluetooth audio does not go through Android, and MTCManager filters which devices may pair for OBD by checking that the name starts with OBD. With a Parrot module the firmware's OBD extension stops working.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: high; not cross-checked).
- **For owners:** Firmware tops out at Android 5.1.1, far below API 27. The Bluetooth design would also block it.
- **Sources:** [1](https://xdaforums.com/t/mtcc-units-mtcb-units-with-renamed-updated-mcu-software.3443767/page-3), [2](https://xdaforums.com/t/bluetooth-does-not-pair-with-obd-adapter.3246283/), [3](https://xdaforums.com/t/parrot-bluetooth-board-on-new-units.3487997/page-3), [4](https://4pda.to/forum/index.php?showtopic=918418&st=40), [5](https://github.com/petrows/RK3066-Headunit-service), [6](https://xdaforums.com/t/obd2-bluetooth-paired-in-settings-but-list-empty-in-torque.3378305/)

#### Microntek/HCT MTCD / MTCE with PX3 (RK3188)

- **Recognise it by:** MCU version starts with MTCD or MTCE and the build number starts with rk3188 (Hal9k's stated requirement for his RK3188 MTCD mod); Android 5.1.1 or 7.1.x.
- **Android:** Android 5.1.1 (MTCD) and 7.1.1/7.1.2 (MTCD/MTCE) according to 4PDA. No 8.x firmware found; one 4PDA buyer who ordered a PX3 with 7.1.2 received a PX30 with 8.1 instead.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: mixed reports. Same HCT design as the PX5 entry. The Hal9k Mod changelog for RK3188 MTCD says stock firmware made diagnostic apps report "Bluetooth is off", that the mod fixed this, made paired device names visible in third-party programs and added OBD adapter detection by configurable name (needed "if your adapter does not have the OBD letters in the name").
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: high; not cross-checked).
- **For owners:** Android 5.1.1 or 7.1.x only, below API 27.
- **Sources:** [1](https://xdaforums.com/t/rom-rk3188-5-1-1-hal9k-mod-for-mtcd-head-units.3623982/), [2](https://4pda.to/forum/index.php?showtopic=918418&st=40), [3](https://4pda.to/forum/index.php?showtopic=933188)

#### Microntek/HCT MTCD / MTCE / MTCP with PX5 (RK3368)

- **Recognise it by:** Settings > About machine: MCU version starts with MTCD, MTCE or MTCP followed by a maker code (examples from sources: MTCE_MX2_V2.75_1, MTCE_XRC2_V2.84_1, MTCD_LM_V1.90_1); model starts with px5; Android 6.0.x, 8.0.x, 9 or 10. Terminal: getprop bt.md725.type. System app /system/priv-app/MTCManager (Android 6-9) or HCTManagerService (Android 10+).
- **Android:** Android 6.0.1, 8.0.0, 9 and 10. The Oreo firmware is 8.0.0 (API 26), not 8.1: Hal9k's requirement reads "The Android version starts with 8.0" and an owner page shows "Android: 8.0.0" with MCU MTCE_XRC2_V2.84_1. I found no MTCD/E PX5 build string with 8.1.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: mixed reports. later firmware adds WQ_RF210, BARROT-i145, SD-816/916, FSC-BW124 and SDIO-AUTO. MD725 is a Realtek RTL8723BU Wi-Fi+BT combo on USB, driven by vendor binaries in /system/bin: gocsdk when property bt.md725.type=1, sdsdk when it is 2, started from init.hct.rc. SD-968 is driven by /system/bin/sdsdk968. Serial hands-free modules send analog audio to the audio chip the MCU controls. Hal9k (ROM developer, 4PDA 11.11.19): a remote device whose Major Device Class is 0x1F is treated as an OBD adapter, anything else as a phone; OBD PINs are hard-coded 1234 then 0000. Earlier firmware used the name prefix OBD and the HCTBlueToothManager app. My inference: Android's own Bluetooth stack does not drive the radio on any of these modules.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: medium; not cross-checked).
- **For owners:** Two blockers. On Android 8.0.0 the T8 build cannot be installed at all (API 26 is below its minimum of 27). On Android 9/10 it installs, but the phone is bonded to a vendor Bluetooth stack that only offers calls, music and phonebook, and the firmware gives apps a data path only to devices it classifies as OBD adapters, so there is no visible route for an outgoing RFCOMM connection to the iPhone's iAP2 service. Not proven by a direct test.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=883219), [2](https://4pda.to/forum/index.php?s=&showtopic=891403&view=findpost&p=90732325), [3](https://xdaforums.com/t/px5-android-8-0-md725-fix-for-md725-type-2-bluetooth-on-oreo.3865670/), [4](https://xdaforums.com/t/px5-md725-solution-for-the-bluetooth-echo-v2.3661284/), [5](https://xdaforums.com/t/help-understanding-bluetooth-mcu-and-android-interaction.3752363/), [6](https://xdaforums.com/t/kia-mtce-px5-unit-bluetooth-issues.3732698/), [7](https://xdaforums.com/t/road-to-a-working-bluetooth-obd2-on-mtcd-px5.3716530/), [8](https://xdaforums.com/t/mtcd-bluetooth.3671839/), [9](https://xdaforums.com/t/bluetooth-low-energy-ble-on-mtce-px5-works.3839489/), [10](https://xdaforums.com/t/mtcd-px5-headunits-repository-amp-information-stock-amp-custom.3619906/page-2), [11](https://xdaforums.com/t/rom-px5-oreo-8-0-0-hal9k-rom-3-for-mtcd-e-head-units-with-android-8-6.3847477/), [12](https://sites.google.com/site/fr3db3rt/home/categories/android/px5-android-car-radio), [13](https://xdaforums.com/t/editing-the-stock-mtc-manager.3611184/), [14](https://xdaforums.com/t/external-usb-dongle-on-px5.4296461/), [15](https://xdaforums.com/t/px5-mtcd-head-unit-discussion-thread-rockchip-px5-a53-android-6-0-2gb-ram.3573881/), [16](https://4pda.to/forum/index.php?showtopic=1030051)

#### Microntek/HCT MTCE with PX30 (RK3326)

- **Recognise it by:** Settings > About machine: MCU version starts with MTCE (examples: MTCE_LM_V2.94_1, MTCE_HXD_V2.94_1, MTCE_GS_V2.94_3); model starts with px30 or the build with rk3326_mid; Android 8.1.0, 9 or 10. 2 GB RAM is typical.
- **Android:** Android 8.1.0 at launch (late 2018), later 9 and 10. The 8.1 is genuine: owners report "rk3326_mid-userdebug 8.1.0 OPM6.171019.030.B1 eng.hct.20180912" and "Px30 8.1.0 OPM8.181105.002", and Google lists OPM8.181105.002 as android-8.1.0_r51.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: mixed reports. Same HCT firmware line as PX5/PX6. The 4PDA PX30 topic says the module type is shown in Factory settings, first tab, and warns against changing it; a known problem is A2DP noise fixed by switching the BT type there. HCT Bluetooth apps for this platform circulate as HCTBluetooth4HCT4 and HCT6BlueTooth (file names, 4PDA). I found no PX30-specific teardown naming the chip.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: medium; not cross-checked).
- **For owners:** This is the Rockchip platform that most often has real Android 8.1, so the T8 build installs. But the phone is paired to HCT's vendor Bluetooth, which the platform's own FAQ says serves only phone hands-free and OBD adapters, so an outgoing RFCOMM link from a third-party app to the iPhone has no visible path. Nobody has reported trying DiPlay on one.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=933188), [2](https://4pda.to/forum/index.php?showtopic=933188&st=20), [3](https://xdaforums.com/t/px30-rk3326-successor-of-px5.3871416/page-4), [4](https://xdaforums.com/t/px30-rk3326-successor-of-px5.3871416/), [5](https://source.android.com/docs/setup/reference/build-numbers), [6](https://4pda.to/forum/index.php?showtopic=918418&st=40), [7](https://www.cnx-software.com/2018/10/15/rockchip-px30-processor-car-infotainment-system/)

#### Microntek/HCT MTCE / MTCH with PX6 (RK3399)

- **Recognise it by:** Settings > About machine: MCU version starts with MTCE or MTCH (examples: MTCE_HA_V3.71_1, MTCE_GS_V3.73, MTCH_MX_V3.84_1, MTCH_GS_V3.80_3); model PX6, build starts with rk3399; an Android 10 example is "rk3399-userdebug 10 ... eng.hct2" style.
- **Android:** Android 8.1 on the first boards (HCT-RK3399-REV03, late 2018), then 9, 10 and 11. I did not find a build string for the 8.1 MTCE PX6 firmware, so its genuineness is not checked against a build ID.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: mixed reports. Sources state: factory BT options on Dasaita PX6 are FSC-BW124, WQ RF210, WQ-BC6 and SDIO-AUTO; Android 11 supports WQ_GT, Parrot_FC6000T, SD-968, BARROT-i145, SD-916, WQ_RF210, SD-816, FSC-BW124, SDIO-AUTO, BARROT-i1107e and FSC-BT1031P. MD725 does not work on Android 10. An XDA poster identifies the RF210 chip as probably Realtek RTL8761 on UART, and a gocsdk_8761 update exists for RF210 on a sister platform. A Dasaita PX6 Android 10 owner (MTCE_HA_V3.71_1): "there is no slider to enable/disable" Bluetooth in Android. Dasaita support (7/26/24, thread started by a Scout owner): the unit "mainly support to connect phone/OBD via Bluetooth". What SDIO-AUTO is, and whether it uses Android's own stack, I could not find.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: medium; not cross-checked).
- **For owners:** Android version is fine (8.1 on early units; 9+ owners belong to upstream), but Bluetooth is the same HCT vendor design limited to phone hands-free and OBD, with no Bluetooth toggle in Android on at least one reported unit. The factory Zlink reaches the iPhone over Bluetooth through the vendor's own path, which a third-party app does not have.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=918418), [2](https://4pda.to/forum/index.php?showtopic=918418&st=40), [3](https://www.dasaita.com/community/forum/topic/60408/px6-android-10-no-bluetooth-support), [4](https://www.dasaita.com/community/forum/topic/125634/bluetooth-not-connecting-to-anything-but-a-phone), [5](https://xdaforums.com/t/dasaita-px6-zlink-apple-carplay-not-working-wired-or-wireless.4132271/), [6](https://www.dasaita.com/community/forum/topic/131548/problems-with-zlink-on-px6), [7](https://4pda.to/forum/index.php?showtopic=1086611), [8](https://xdaforums.com/t/can-a-bluetooth-device-not-smartphone-not-obd-works-with-android-px5-px6-device.4113177/), [9](https://xdaforums.com/t/rom-px5-px6-px30-q-10-hal9k-mod-v5-for-mtcx-hctx-head-units-with-android-10-9-8-6.4314163/page-45), [10](https://xdaforums.com/t/android-10-upgrade-md725-to-rf210-bt-module-swap.4221421/), [11](https://xdaforums.com/t/android-4-1-headunit-reloaded-for-android-auto-with-wifi.3432348/page-352), [12](https://hal9k.ru/?page_id=10)

#### HCT 'PX4' RK3566 (MTCE / MTCH, Android 11)

- **Recognise it by:** Settings > About: build starts with rk3566_r, Android 11, build tag contains eng.hct2; MCU starts with MTCE or MTCH.
- **Android:** Android 11 only in the sources ("rk3566_r-userdebug 11 RQ3A.210705.001 eng.hct2.20211018").
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: no report found. The build tag eng.hct2 and the MTCH MCU show it is the same HCT firmware line, and the Malay ROM author treats PX4 RK3566 and PX6 RK3399 Android 11 together. I found no RK3566-specific description of the Bluetooth module or driver. Inference only: likely the same vendor design as PX6.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: low; not cross-checked).
- **For owners:** Android 11 units are upstream's audience, not this fork's. If the Bluetooth follows the rest of the HCT line, a third-party app cannot reach the phone; that is an inference from the shared firmware lineage, not a documented fact for RK3566.
- **Sources:** [1](https://xdaforums.com/t/android-11-for-headunits.4370867/), [2](https://4pda.to/forum/index.php?showtopic=1086611), [3](https://xdaforums.com/t/eonon-r03-10-1-android-11-2gb-ram-quadcore-a55-possible-root-via-magdisk-rk3566-latest-update-from-eonon.4412291/)

#### Klyde CSN2 / CSN2_D / CSN2_8600_D (PX5, PX6; sold under several brands)

- **Recognise it by:** Settings > About: MCU version starts with CSN2_, CSN2_D_ or CSN2_8600_D_ followed by a date-time number (example CSN2_D_1152020_114303).
- **Android:** Android 8.0.0, 9.0.0 and 10.0.0 per 4PDA. A "Fake Android 10" build is "the same android 9 firmware, but with the changed launcher, radio and Bluetooth app" (XDA thread author).
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: mixed reports. Sources state: BT module set under Factory Settings > FUNC > BT Mode; modules IVT140/IVT145, RF210, KD6 and Parrot. On IVT145, Bluetooth OBDII and hands-free calls cannot run at the same time. KD6 did not work on Android 10 until a firmware fix. A Bluetooth library update is distributed as gocsdk_8761.zip (not for Parrot, which has its own firmware). On Android 9 the head unit can take internet from the phone over Bluetooth, guaranteed only with RF210. Not an MTC platform.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: low; not cross-checked).
- **For owners:** PX5 units on Android 8.0.0 cannot install the T8 build (API 26). On 9/10 the Bluetooth is a vendor stack (gocsdk) or self-contained module with phone and OBD roles; no evidence that apps get a general RFCOMM path. Thin evidence, so low confidence.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=928321), [2](https://4pda.to/forum/index.php?showtopic=928321&view=findpost&p=91977566), [3](https://xdaforums.com/t/klyde-px4-px5-px6-head-units-with-mcu-csn2-csn2_d-csn2_8600_d-android-8-9-10.3912413/)

#### Klyde / XYStar vertical-screen 'Tesla-style' PX6 (MCU RL7Ac, RL78c, RL8Ac, RL9Ac)

- **Recognise it by:** Settings > system information: MCUVer contains RL7Ac_KLD or RL7A_KLYDE (also RL78c, RL8Ac, RL9Ac). Hold the screen 5 seconds on that page and enter 7890 for factory mode; 8861 for extended settings.
- **Android:** Android 8.1 and 9.0 (topic title also says 10.0). A 12.1-inch Android 8.1 firmware is listed; owners who received 8.1 are pointed to a 9.0 firmware. I found no build string to confirm the 8.1 is genuine.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: mixed reports. 4PDA FAQ for this platform (my translation): "The head unit's BT module is cut down and can work ONLY with two things: an OBD adapter and hands-free for the phone (calls, music over A2DP)." The Zlink build offered is named zlink-wla-a-zhuoxw-release and the voice adapter TXZSmartAdapter-ZXW, which suggests ZXW-derived firmware (my inference).
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: low; not cross-checked).
- **For owners:** Some units do run Android 8.1, but the platform's FAQ says Bluetooth serves only phone hands-free/A2DP and OBD adapters. Single source, so low confidence.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=1002877)

#### ZXW PX6 OEM-screen units for BMW / Mercedes (build tag eng.zxw, MCU ...-CIC-HW7 / ...-NBT-HW7)

- **Recognise it by:** About screen shows AndroidVer 8.1.0, a Framework/build line containing eng.zxw, an MCU line like 615043bALS-CIC-HW7-181217[B1280_170325] or 023042bGS-CIC-HW7-190118, and an AppVer like 2019030801_1280x480_970.
- **Android:** Android 8.1.0, genuine by build ID: "rk3399-userdebug 8.1.0 OPM5.171019.019 eng.zxw.20181228.162012" and "...eng.zxw.20190520.182151". Owners ask for 9/10 firmware and get no answer.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: reported not working. One owner (BMW CIC PX6, 2 GB) reports the configuration has the "WenqiangBC6" BT module enabled, a self-contained hands-free module. He states there is no Bluetooth option in Android settings; pairing exists only in the vendor's BMW-style menu, which finds only mobile phones.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: low; not cross-checked).
- **For owners:** Real Android 8.1, so it installs, but on the reported unit the phone cannot even be bonded in Android's own Bluetooth settings because that page is absent, and the BC6 module only does calls and music. Based on a single owner's report, so I stop short of cannot-work.
- **Sources:** [1](https://xdaforums.com/t/bmw-e8x-e9x-cic-px6-android-10-25-trouble-with-bluetooth-idrive-display-ect.3934953/), [2](https://xdaforums.com/t/help-px6-rk3399-bmw-android-head-unit-als-cic-hw7-mcu-need-stock-or-updated-firmware.4763157/), [3](https://xdaforums.com/t/android-8-1-update-firmware.4384613/), [4](https://source.android.com/docs/setup/reference/build-numbers)

#### Joying PX5 on FYT firmware (MCU 'date time JY_...'; also KYD, KEQ, HHQ, GMI)

- **Recognise it by:** Settings > About: MCU Version is a date and time followed by JY_ (example "2018-01-23 10:59:33 JY_(R68)_R-86C-096I-NC-NP-1.0-P12S") or KYD_, KEQ_, HHQ_, GMI_; build px5-userdebug 8.0.0. Files in /oem/app or a com.fyt process indicate FYT firmware.
- **Android:** Android 8.0.0 only: "px5-userdebug 8.0.0 OPR5.170623.007". Joying did not release Android 9 for PX5 (surfer63).
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: no report found. Sources state: Joying routes all Bluetooth through a vendor service (described as btlink; an init entry "/vendor/bin/blink" talking to a /dev/tty port) to Joying's Bluetooth app. A Bluetooth LE test app was "denied every time" when it tried to turn Bluetooth on. Joying support: "Our head unit can not support Bluetooth tethering". 4PDA names the chip as RTL8723BU.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: high; not cross-checked).
- **For owners:** Only ever shipped Android 8.0.0 (API 26), so the T8 build (min API 27) cannot be installed. Bluetooth is also vendor-routed and an app could not even enable it.
- **Sources:** [1](https://xdaforums.com/t/joying-head-unit-4gb-px5-octa-core-bluetooth-tethering.3818271/), [2](https://xdaforums.com/t/new-joying-px5-based-android-8-0-units-rockchip-px5-a53-android-8-0-4gb-ram.3786831/), [3](https://4pda.to/forum/index.php?showtopic=936888), [4](https://xdaforums.com/t/zlink-function-on-joying-unit-android-auto-car-play-on-every-px5-radio-possible.3791200/), [5](https://gist.github.com/andrecuellar/3c39b4f1bdc03a5c4e97336217f13c38)

#### Joying 'QD' PX5 (MCU TP6735) and sister TP67G1D PX6 / PX30 units

- **Recognise it by:** Settings > About: "APP Version TP6735 0.0.65" and "MCU Version TP6735 4.40" (or 0.0.125 / 4.51), kernel 4.4.103; or MCU starting TP67G1D. A Bluetooth version line (SD/V16.2 or GOC_v1.2) appears in About. Not MTCD/E.
- **Android:** QD PX5: shipped 8.0.0; later firmware shows "ANDROID VERSION 8.1.0" together with "BUILD NUMBER OPR5.170623.007". Google lists OPR5.170623.007 as Android 8.0.0, so the 8.1.0 label is probably a relabel (my inference; the real API level is unverified). TP67G1D PX6 and PX30 units: Android 9.0.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: no report found. The About screen shows a Bluetooth firmware string: "BLUETOOTH V. SD/V16.2-2018" on a QD PX5, and "GOC_v1.2" or "SD/v16.2" on TP67G1D units. MCU firmware files are specific to the Bluetooth version fitted (for example "TP67G1Dv0.18 for radio module 6686 and Bluetooth version GOC_v1.2"), and flashing the wrong one can break Bluetooth. From that I infer a separate Bluetooth module tied to the MCU, not Android's stack; no source says so outright.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: low; not cross-checked).
- **For owners:** If the 8.1.0 label hides an 8.0.0 base, the T8 build will not install; owners should check the real SDK level. Independently, the Bluetooth looks like an MCU-coupled vendor module. Both points are inferences, so confidence is low.
- **Sources:** [1](https://xdaforums.com/t/joying-px5-android-8-1-to-9-0.4037425/), [2](https://xdaforums.com/t/new-joying-px5-based-android-8-0-units-rockchip-px5-a53-android-8-0-4gb-ram.3786831/), [3](https://xdaforums.com/t/px30-rk3326-successor-of-px5.3871416/), [4](https://xdaforums.com/t/update-joying-px5-android-8-1-to-android-9.3926103/), [5](https://source.android.com/docs/setup/reference/build-numbers), [6](https://4pda.to/forum/index.php?showtopic=910575), [7](https://4pda.to/forum/index.php?showtopic=976939), [8](https://4pda.to/forum/index.php?showtopic=978692)

#### Other non-MTC Rockchip PX5 / PX6 (XinRC / LingYun / Leima S32F0, MF1; KC6 STM32)

- **Recognise it by:** Settings > About: MCU version contains S32F0_XinRC, S32F0_LingYun, S32F0_Leima or MF1; or reads like "STM 32 - 20190622 - 11 - KC6 - 26". Displayed Android 10/11/12 may be a relabelled 9 or 10.
- **Android:** S32F0/MF1 PX5: Android 9 and 10, with fake labels: "Android 9 (10 fake)" and "Android 10 (11, 12 fake)" per 4PDA. KC6 PX6: version not captured.
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: no report found. 4PDA specs list built-in Bluetooth and Wi-Fi and Bluetooth OBDII adapter support. The topic curator says there is essentially no information or modified firmware for the S32F0 platform.
- **Outlook for wireless CarPlay on Android 8.1:** Unknown (confidence: low; not cross-checked).
- **For owners:** Android 9+ units are upstream's audience, and I found no evidence either way about their Bluetooth.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=973677), [2](https://4pda.to/forum/index.php?showtopic=963715)

### FYT / "syu" firmware (Unisoc, Spreadtrum, Intel SoFIA)

FYT ("syu") head units do not have one Bluetooth answer. FYT builds most boards with two Bluetooth radios: a separate UART module run by FYT's own daemon (blink, earlier gocsdk) and the com.syu.bt app for calls and music, plus the SoC's built-in radio under the normal Android stack; the config value sys.fyt.bluetooth_type (0 = built-in SoC radio, 1 = Realtek/blink module, 7 = Qualcomm module, 8 = built-in on SC9863A) decides which one the phone pairs with. Where the phone pairs with the module (the default on SC9853i and UIS7862-class units), Android cannot see that phone, so DiPlay would find no bonded iPhone unless the iPhone is also paired with Android's own radio (shown as 'Bluetooth 2' on ATOTO, hidden on other brands) or the unit is switched to type 0; I found no report of a CarPlay/iAP2 session done that way, so those platforms are 'possible, untested'. The best evidence is for the cheapest Android 8.1 boards: on SC7731E/UIS8141E ('sprd sp7731e_1h10_native', API 27) two independent reports show the default Android adapter exchanging RFCOMM with a phone, one from an Open Headunit user whose firmware exposes FYT's 'syu' service, and one upstream DiPlay issue whose attached log shows iPhone iAP2 over RFCOMM connecting in 407 ms, MFi authentication, a hotspot on wlan0 and video through OMX.sprd.h264.decoder; these are likely to work, on 2.4 GHz only. The other common Android 8.1 FYT platform, SC9853i (Joying, Teyes SPRO, Junsun V1), is an Intel x86-64 chip that advertises only 32-bit ARM compatibility, uses the external module for phones and hides Android's Bluetooth page, and nobody reports a phone-RFCOMM app on it either way: low-confidence 'possible'. UIS7862/UMS512/UIS7862S/UIS8581A units are really Android 10 (API 29) whatever number the About screen shows and UIS7870 is Android 13, so those owners belong with upstream DiPlay; SoFIA 3GR (Android 5.1/6.0, x86) and FYT PX5 (Android 8.0.0) are below the minimum version. Android version labels in this family are often faked upward (API 27 boards sold as Android 9/10/12), so owners must check the API/SDK level, and the same SoCs are used by non-FYT makers (TopWay TS7/TS9/TS10, K706) where the Bluetooth answer can differ. Limits of this research: xdaforums.com returns 403 to direct fetching, so its threads were read through the r.jina.ai text-reader proxy; 4PDA and drive2 pages could not be opened at all (bot checks), so no Russian-language source is cited; and the FYT identity of the unit in the DiPlay success log is inferred from its CarLink setting and the reporter's wording, not proven.

#### FYT SC7731E / UIS8141E (board 'sp7731e_1h10_native'), Android 8.1

- **Recognise it by:** About screen or a device-info app shows board/device 'sp7731e_1h10' or 'sprd sp7731e_1h10_native', CPU UIS8141E or SC7731E quad-core 1.3 GHz, 1-2 GB RAM, Android 8.1.0 (or a higher label with API 27). Warning: the same board string is used by non-FYT makers (an XDA moderator called one such unit 'Not FYT'; TopWay TS7 units show an MCU like 'Ts7.4.6-...' and system 'V12.1.1_..._THEME1'), so the board name alone does not prove FYT.
- **Android:** Android 8.1.0 / API 27 (build OPM2.171019.012, kernel 4.4.83). Frequently displayed as Android 10, 11 or 12 by sellers; the SDK level is what counts.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: reported working. On the units reported, the phone-facing radio is the default Android adapter (service bluetooth_manager, adapter name 'CAR BT F3' on one unit) and the stock stack holds the hands-free link on that same adapter. The firmware also exposes an FYT 'syu' binder service, which is not a second Bluetooth adapter. I found no source showing an external module on an FYT SC7731E board, and no source giving its sys.fyt.bluetooth_type value, so 'android-stack' rests on the reports below, not on a teardown.
- **Outlook for wireless CarPlay on Android 8.1:** Likely works (confidence: medium; sources checked, claim stands).
- **For owners:** If a device-info app shows board 'sp7731e_1h10' (SC7731E / UIS8141E) and SDK 27, this is the best-supported Android 8.1 case: one such unit logged full wireless CarPlay with a modified DiPlay 0.2.12 build (iPhone 16 Pro, hardware H.264, about 12-26 fps) and another did Android Auto over the normal Android Bluetooth adapter. That is two unbranded units and not this fork's own build, so treat it as promising rather than proven; expect 2.4 GHz Wi-Fi, occasional Bluetooth retries, and wired USB failed on the reporting unit.
- **Also read in the second pass:** [1](https://github.com/andreknieriem/open-headunit/issues/992), [2](https://github.com/user-attachments/files/33051018/DiPlay-20261005-145730-021-7754111309005933644.txt), [3](https://xdaforums.com/t/how-can-i-get-carplay-to-work-on-my-headunit-ive-tried-everything.4668840/)
- **Sources:** [1](https://github.com/andreknieriem/open-headunit/issues/992), [2](https://github.com/shihabal3amri/DiPlay/issues/274), [3](https://github.com/user-attachments/files/33051018/DiPlay-20261005-145730-021-7754111309005933644.txt), [4](https://github.com/andreknieriem/open-headunit/issues/967), [5](https://github.com/shihabal3amri/DiPlay/issues/315), [6](https://xdaforums.com/t/how-can-i-get-carplay-to-work-on-my-headunit-ive-tried-everything.4668840/), [7](https://xdaforums.com/t/carlink-2-0-app-working-intermittently.4620015/), [8](https://xdaforums.com/t/question-rooting-uis8141e-sp7731e-head-unit-android-8-1-0.4796786/), [9](https://teyes.com.ua/faq.html)

#### FYT SC9853i (Intel Airmont x86-64), Android 8.1

- **Recognise it by:** About screen: 'Intel Airmont' / 'Intel X86 Octa-Core 1.8GHz SC9853', Android 8.1.0 (or 9/10 with API 27). A device-info app shows CPU 'Spreadtrum SC9853I-IA', board sp9853i_1h10 and ABIs 'x86_64, x86, armeabi-v7a, armeabi'. Firmware update sticks contain lsec6521update, 6521_1.zip, Allapp.pkg, config.txt, Stm32ud.bin. /oem/app holds fyt.prop (ro.build.fytmanufacturer, ro.fyt.uiid) and config.txt (sys.fyt.bluetooth_type). The Bluetooth app's version line starts BLINK_ or GOC_. a yellow 'ui and mcu does not match' bar means mismatched firmware. TopWay TS9 units also use SC9853i and are not FYT.
- **Android:** Android 8.1.0 / API 27 (fingerprint SPRD/sp9853i_1h10_vmmTos/sp9853i_1h10:8.1.0/OPM2.171019.012). Some are labelled Android 9 or 10 while still API 27 ('It shows Android 9, but it's really API 27'; 'branded as Android 10, but the API level is 27').
- **Bluetooth:** both kinds exist; apps reaching a phone or device through the Android API: mixed reports. Phone side: a UART Bluetooth module driven by a vendor daemon, not by Android. An FYT engineer's notes for sp9853 show 'blink /dev/ttyS0 500000&' and AT commands written to /dev/BT_serial, and list 'type 0 default / type 1 blink / type 2 brlinkd' with sys.fyt.bluetooth_type=1 as the setting. Owners see Bluetooth version strings 'BLINK_2161_RELEASE0/..._blink' (Junsun V1) or 'GOC_V1.0/..._gocsdk2' (Ullgo). The UI is com.syu.bt, which pairs only phones and dongles whose name contains OBD (plus a few TPMS devices); the Bluetooth page was removed from Android Settings ('the bluetooth setting in the android menu will disappear'). Joying's FAQ: the unit 'can only support 3 kinds of Bluetooth devices: Android phone, iOS phone and Joying OBD2'. Android side: an Android stack is still present and usable for accessories (notes carry 'sys.fyt.systemobd=true'; setting bluetooth_type=0 moves to the built-in radio and 'gives you more possibilities'). Chip part numbers were not found.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: low; sources checked, claim weakened).
- **For owners:** Not expected to work as shipped: these Intel (x86) units pair phones in FYT's own Bluetooth app on a separate module, Android's Bluetooth settings page has been removed, and nobody has reported any third-party app reaching a phone over Android Bluetooth on them. It is untested rather than disproved; only owners prepared to reflash config.txt (sys.fyt.bluetooth_type=0) and report back should try.
- **Also read in the second pass:** [1](https://xdaforums.com/t/joying-stereo-wont-link-with-hondata-kpro-v4-via-bluetooth.3964327/), [2](https://github.com/andreknieriem/open-headunit/pull/328#issuecomment-4209592652), [3](https://github.com/andreknieriem/open-headunit/blob/main/app/src/main/java/com/andrerinas/openheadunit/utils/ExternalBtPolicy.kt)
- **Sources:** [1](https://github.com/trywent/note/blob/master/device/sp9853/9853), [2](https://github.com/trywent/note/blob/master/prop), [3](https://xdaforums.com/t/rooting-upgrading-sp9853i-based-chinese-headunit.4154183/), [4](https://xdaforums.com/t/q-a-tips-info-roll-up-joying-fyt-sc9853i.3979377/), [5](https://xdaforums.com/t/joying-android-8-1-intel-airmont-eight-core-1-8ghz-sc9853i-4gb-ram-32gb-flash.3897206/), [6](https://xdaforums.com/t/joying-android-8-1-intel-airmont-eight-core-1-8ghz-sc9853i-4gb-ram-32gb-flash.3897206/page-59), [7](https://xdaforums.com/t/joying-android-8-1-intel-airmont-eight-core-1-8ghz-sc9853i-4gb-ram-32gb-flash.3897206/page-58), [8](https://xdaforums.com/t/joying-stereo-wont-link-with-hondata-kpro-v4-via-bluetooth.3964327/), [9](https://xdaforums.com/t/modding-your-joying-fyt-sc9853i-unit-without-root.3974357/page-30), [10](https://xdaforums.com/t/junsun-android-8-1-intel-airmont-eight-core-1-8ghz-sc9853i-4gb-ram-64gb-flash.4129019/), [11](https://www.joyingauto.eu/faq), [12](https://xdaforums.com/t/joying-android-8-1-intel-airmont-eight-core-1-8ghz-sc9853i-4gb-ram-32gb-flash.3897206/page-47), [13](https://xdaforums.com/t/modding-your-joying-fyt-sc9853i-unit-without-root.3974357/)

#### FYT UIS7862 / UMS512 / UIS7862S / UIS8581A (SC9863A), Android 10

- **Recognise it by:** About screen CPU 'UIS7862' / 'UMS512' / 'UIS8581A' with Android 10 or a higher label; check SDK = 29. Firmware sticks contain lsec6315update + 6315_1.zip (UIS7862) or lsec6316update + 6316_1.zip (UIS8581A), AllAppUpdate.bin, config.txt. /oem/app has fyt.prop (ro.build.fytmanufacturer, ro.fyt.uiid) and config.txt (sys.fyt.bluetooth_type). surfer63's HWGet_Info app shows these properties. Stock apps are com.syu.*, CarPlay app is Car Link. Same-SoC units that are not FYT: TopWay TS10/TS18, and QF001 / ROCO K706 (Zlink, Bluetooth names 'CAR8032' / 'K706').
- **Android:** Android 10 / API 29. The displayed version is routinely faked: 'These Android 11, 12 and 13 versions for the current FYT uis7862(S)/uis8581 units are fake!'; one repo documents a build string faked to '16' with SDK 29.
- **Bluetooth:** both kinds exist; apps reaching a phone or device through the Android API: mixed reports. Two radios. 'BT1': an add-on module (Realtek on older CC3/Joying/Mekede, Qualcomm on UIS7862S from about Sept 2022) wired to an AP UART and driven by FYT's blink daemon (/oem/bin/blink); UI is com.syu.bt; version strings look like 'CQ131_1.4_..._blink131'. Phones pair here by default and 'Android cannot see or dial phones bonded to the external module'. 'BT2': the SoC's built-in radio under the standard Android stack, with a different MAC address. sys.fyt.bluetooth_type in /oem/app/config.txt selects the phone radio: 'type 0 switches device to built-in spreadtrum SoC module available in every device, type 1 for realtek modules, type 7 for qualcomm modules, type 8 for built-in spreadtrum SoC modules for SC9863 (UIS8581) devices'. With 0, 'anything that I pair in the BT1 app is already paired in the BT2 app'. Stock Settings has no Bluetooth page on most brands; ATOTO exposes it as 'Bluetooth 2'; elsewhere it is reached with a shortcut app or the FET tool (root). Whether type 8 units run the SoC radio through Android's stack or through blink is not stated anywhere I found.
- **Outlook for wireless CarPlay on Android 8.1:** Possible, nobody has tried (confidence: medium; sources checked, claim stands).
- **For owners:** These are Android 10 units whatever the About screen says, so use upstream DiPlay, not this fork. As shipped the iPhone is paired with FYT's separate Bluetooth module, which no ordinary app can reach; it can only work if you also pair the iPhone with Android's own Bluetooth (ATOTO 'Bluetooth 2', the Android Bluetooth page on DUDUOS, a settings shortcut on other brands, or sys.fyt.bluetooth_type=0). One owner got Android Auto going that way once; nobody has reported CarPlay.
- **Also read in the second pass:** [1](https://github.com/andreknieriem/open-headunit/pull/328#issuecomment-4209843218), [2](https://github.com/andreknieriem/open-headunit/blob/main/app/src/main/java/com/andrerinas/openheadunit/utils/ExternalBtPolicy.kt), [3](https://xdaforums.com/t/does-bluetooth-only-reconnect-to-the-last-device-used.4560381/page-2), [4](https://github.com/shihabal3amri/DiPlay/issues/100)
- **Sources:** [1](https://xdaforums.com/t/general-fyt-based-spreadtrum-uis7862-s-unisoc-ums512-uis8581a-sc9863-q-a-mods-tips-firmware.4396339/), [2](https://xdaforums.com/t/general-fyt-based-spreadtrum-uis7862-s-unisoc-ums512-uis8581a-sc9863-q-a-mods-tips-firmware.4396339/page-200), [3](https://xdaforums.com/t/does-bluetooth-only-reconnect-to-the-last-device-used.4560381/), [4](https://xdaforums.com/t/does-bluetooth-only-reconnect-to-the-last-device-used.4560381/page-2), [5](https://xdaforums.com/t/atoto-s8-general-discussion.4114493/page-68), [6](https://github.com/andreknieriem/open-headunit/pull/1027), [7](https://github.com/andreknieriem/open-headunit), [8](https://github.com/Pacjonek/fyt-dialer), [9](https://github.com/trywent/note/blob/master/device/sp7862/build), [10](https://xdaforums.com/t/how-to-permanently-disable-bluetooth-on-uis7862-s-head-unit.4564923/), [11](https://xdaforums.com/t/carlink-2-and-or-tlink-help.4620175/), [12](https://xdaforums.com/t/fet-fyt-extra-tool.4653315/), [13](https://github.com/PimpinPumpkin/fyt7862-mods), [14](https://teyes.com.ua/faq.html), [15](https://xdaforums.com/t/about-s7862-and-carplay-android-auto.4746798/)

#### FYT UIS7870, Android 13

- **Recognise it by:** System info: 'SoC: UIS7870SC', 'Android version: 13', 'Build number: TP1A.220624.014', MCU like 'SHTU_53_L7870_N32P48F64_E60_V:1.0 (2024.08.30)', 'System Info: APP 2000x1200 2025-03-31'.
- **Android:** Android 13 (build TP1A.220624.014; firmware file AllAppUpdateA13.bin). The API level is not stated in the sources opened; the build ID is a genuine Android 13 one.
- **Bluetooth:** both kinds exist; apps reaching a phone or device through the Android API: no report found. A dialer project for 'FYT-series head units with UIS7870/UIS7862S SoC (like DUDU Auto)' states there are two Bluetooth modules, the non-Android one handling HFP through com.syu.bt. No source opened gives the module type, the sys.fyt.bluetooth_type value or the state of Android's own radio on UIS7870 specifically.
- **Outlook for wireless CarPlay on Android 8.1:** Unknown (confidence: low; could not be cross-checked).
- **For owners:** Android 13, so use upstream DiPlay. There is no evidence either way: one developer's README says these units also have two Bluetooth radios with phones on FYT's own module, and nobody has reported whether Android's own radio can reach a phone.
- **Sources:** [1](https://xdaforums.com/t/wdfl-fyt-7870-firmware-help-thread.4734579/), [2](https://xdaforums.com/t/wdfl-fyt-7870-firmware-help-thread.4734579/page-4), [3](https://github.com/Pacjonek/fyt-dialer), [4](https://xdaforums.com/t/fet-fyt-extra-tool.4653315/)

#### FYT Intel SoFIA 3GR (Joying 'Intel' units, 2016-2018), Android 5.1.1 / 6.0.1

- **Recognise it by:** About screen shows an Intel quad-core, 2 GB RAM, Android 5.1.1 or 6.0.1, MCU like '2016-10-31 14:30:43 JY_(NOR)_90_C9_7706_5009_CAN(GX)_Newlap'; firmware archives contain 5009_60.zip.
- **Android:** Android 5.1.1, later 6.0.1. No Android 8.x for this hardware was found in the sources opened.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: reported working. 'the SOM has the BT/Wi-Fi chip on it so the android has full access to the BT and WI-FI module'; 'The bluetooth stack on these units appears to be ENTIRELY functional'. Joying hid the Bluetooth page in Settings and handled pairing/PIN in its own Bluetooth app; on Android 5.1 the hidden page could be opened with a settings-shortcut widget or the BlueBalls app, on Android 6.0 it closes after a second.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** Cannot be installed: these older Joying Intel units run Android 5.1.1 or 6.0.1, below the Android 8.1 minimum, and use an x86 processor.
- **Sources:** [1](https://xdaforums.com/t/bluetooth-on-intel.3504526/), [2](https://xdaforums.com/t/bluetooth-on-intel.3504526/page-5), [3](https://xdaforums.com/t/joying-intel-sofia-all-you-need-to-know-micro-tutorials-q-a.3616074/), [4](https://xdaforums.com/t/protip-access-android-bluetooth-settings-on-joying-intel.3538245/)

#### FYT Rockchip PX5 (Joying 2018), Android 8.0.0

- **Android:** Android 8.0.0 (API 26). Owners may read this as 'Android 8' and assume it qualifies; it does not.
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: no report found. Not established for the PX5 FYT board. One Sofia-era post says 'The Bluetooth was a separate module on the RK boards'; FYT engineering notes mention a 'px3' Bluetooth config with gocsdk5. Neither names the PX5 unit's hardware.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** Cannot be installed: the FYT-built Joying PX5 runs Android 8.0.0 (SDK 26), one level below the minimum of 8.1 (SDK 27). Check the SDK number, not the '8' on the About screen.
- **Sources:** [1](https://xdaforums.com/t/review-joying-px5-2gb-32gb-on-android-version-8-0-0-fyt-unit.3789422/), [2](https://xdaforums.com/t/bluetooth-on-intel.3504526/page-5), [3](https://github.com/trywent/note/blob/master/prop)

### MediaTek / AutoChips

This family splits into several different situations, and only one of them is this fork's audience. The cheap "AC8227L / 8227L_demo" units are the important ones: four firmware images I opened (one Jancar "JCAC10003" build and three ZXDZ builds) are really Android 8.1 (API 27) underneath even when the screen says "Android 12", and an upstream DiPlay issue shows an XY Auto YT9216CJ unit reporting "Android 9.1 / API 27". In those images Bluetooth is NOT a separate hands-free module: the only Bluetooth software present is Android's own stack (MtkBluetooth.apk, bluetooth.default.so, MediaTek HCI HAL on the SoC's built-in radio) with a vendor phone app (BTSuite or ivi-bt) on top, and no gocsdk/BlueAngel binaries. That is the favourable architecture, but it is not proof: I found no report of any third-party app opening RFCOMM to an iPhone on an AC8227L, the ZXDZ images carry an undocumented flag ro.atc.disable_android_bt=1, and the owner forums that would settle it (XDA, 4PDA, android-headunits, drive2) refused to load (HTTP 403), so their content reached me only as search-engine excerpts that I could not verify. The honest verdict for AC8227L on real Android 8.1 is therefore "possible, unproven, needs a tester"; the single upstream report from such a unit was wired-only and failed because the kernel's cdc_ncm driver grabs the iPhone's USB network interface. Some units sold as "8227L / Android 8.1 Go" are claimed by another DiPlay fork to be Android 6.0 (API 23) and cannot install the app at all. The newer AC8257/AC8259 units (Podofo, Hikity, SIXWIN, Junsun V1 Pro, XY Auto YT7260B) are Android 9 (API 28) under labels of 10/12/13, also use Android's own Bluetooth stack, and belong with upstream; the same goes for car-maker factory units on MediaTek MT8666 and AutoChips "ac8x", where DiPlay's Bluetooth step has worked on some cars and failed on one board. Displayed Android versions on this family are routinely fake, and XY Auto firmware even ships a list naming CPU-Z, AIDA64, AnTuTu and similar apps (my inference: to feed them doctored values), so the dependable test is whether the APK installs or what "adb shell getprop ro.build.version.sdk" returns. One thing the maintainer should know: upstream's README now says its own APK supports Android 7.1+ (API 25) since 0.2.15, "new and not yet confirmed on a vehicle", which overlaps this fork's Android 8.1 audience.

#### AutoChips AC8227L with Jancar 'ivi' firmware (build JCAC10003-...), real Android 8.1 displayed as 'Android 12'

- **Recognise it by:** About screen: model '8227L_demo', build number starting 'JCAC10003-'. Apps named with the 'ivi-' style (Jancar launcher, ivi settings). Over adb: getprop ro.board.platform -> ac8227l; ro.mediatek.version.branch -> alps-mp-o1.mp5; ro.build.version.sdk -> 27 even though ro.build.version.release says 12. If the DiPlay T8 APK installs, the unit is at least API 27.
- **Android:** Real Android 8.1 (ro.build.version.sdk=27, MediaTek branch alps-mp-o1.mp5). The same build.prop sets ro.build.version.release=12, the system fingerprint is spoofed as alps/full_evb3561sv_w_65_m0/evb3561sv_w_65_m0:6.0/MRA58K and the vendor fingerprint claims 12/SP1A, so the About screen cannot be trusted. Build dated Oct 2023.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: no report found. Source states (firmware file list and props): system/app/MtkBluetooth/MtkBluetooth.apk, system/lib/hw/bluetooth.default.so, system/etc/bluetooth/bt_stack.conf and mtk_bt_stack.conf, vendor/bin/hw/android.hardware.bluetooth@1.0-service-mediatek, vendor/lib/modules/bt_drv.ko, libbt-vendor.so; vendor phone UI and service are system/priv-app/ivi-bt and ivi-btservice. Props: ro.atc.disable_android_bt=0, ro.btstack=blueangel, ro.mtk_bt_support=1, persist.bluetooth.atc.carplay=enable. No gocsdk, mtkbt or other separate-module daemon appears in the list. My inference: Android's own Bluedroid/Fluoride (MediaTek flavour) drives the SoC's built-in radio and the Jancar apps sit on top of it; 'blueangel' is only a leftover property value. Not established: whether Android Settings exposes a Bluetooth page.
- **Outlook for wireless CarPlay on Android 8.1:** Possible, nobody has tried (confidence: low; sources checked, claim stands).
- **For owners:** If the build number starts with JCAC10003 the unit is really Android 8.1 (API 27) whatever the About screen says, and its firmware contains Android's own Bluetooth stack, so the app installs and might work, but nobody has tested it. The same firmware carries the chip maker's own wired/wireless CarPlay, so check first whether the unit already offers CarPlay, and if you try DiPlay T8 please send a diagnostic report.
- **Also read in the second pass:** [1](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/JCAC10003-OC2-V1.0.97R3-231023_1114/system/etc/ivi-config.ini), [2](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/JCAC10003-OC2-V1.0.97R3-231023_1114/system/etc/ivi-settings.ini)
- **Sources:** [1](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/JCAC10003-OC2-V1.0.97R3-231023_1114/system/build.prop), [2](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/JCAC10003-OC2-V1.0.97R3-231023_1114/vendor/build.prop), [3](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/JCAC10003-OC2-V1.0.97R3-231023_1114/all_files.txt), [4](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/JCAC10003-OC2-V1.0.97R3-231023_1114/vendor/etc/iAP2FeatureConfiguration.xml), [5](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/JCAC10003-OC2-V1.0.97R3-231023_1114/bootimg/ramdisk/init.jancar.rc), [6](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/JCAC10003-OC2-V1.0.97R3-231023_1114/vendor/etc/media_codecs_mediatek_video.xml), [7](https://github.com/twrpdtgen/android_device_alps_8227L_demo)

#### AutoChips AC8227L with ZXDZ / 'hcn' firmware (fota device CVD1660-WJ, CVD1396-XT; version strings ZXDZ_YYYY.MM.DD), genuine Android 8.1.0

- **Recognise it by:** Version/build text containing 'ZXDZ' (e.g. ZXDZ_2020.11.19.14), model CVD1660-WJ / CVD1396-XT or '8227L_demo'; system apps named Auto* (AutoRadio, AutoSetting, AutoCan, AutoMcuUpgrade) and a Bluetooth app from BTSuite. Over adb: ro.board.platform=ac8227l, ro.build.version.sdk=27, ro.fota.oem=hcn8167_8.1, ro.atc.disable_android_bt=1. Package names above come from the firmware's quick-boot whitelist (qb_list.xml); being whitelisted does not prove each one is installed.
- **Android:** Real Android 8.1.0, API 27 in all three builds opened (Jul 2019, Sep 2020, Nov 2020); ro.build.version.release=8.1.0, security patch 2018-07-05, branch alps-mp-o1.mp5. These particular images do not fake the version in build.prop.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: no report found. Source states (file list and props): system/app/MtkBluetooth, system/app/BTSuite, system/app/BtTool, system/lib/hw/bluetooth.default.so, libmtkbluetooth_jni.so, system/etc/bluetooth/bt_stack.conf, vendor android.hardware.bluetooth@1.0-service-mediatek, bt_drv.ko, WMT_SOC.cfg, pppd_btdun. Vendor props: ro.atc.disable_android_bt=1, ro.btstack=blueangel, ro.atc.aosp_enhancement=1, persist.atc.bt.pan=enable. The list contains no gocsdk, mtkbt or other Bluetooth-module daemon (there is an 'mcud' MCU daemon and libserialport.so). My inference: Android's stack is the only Bluetooth host stack in the image and BTSuite is the AutoChips phone UI on top of it. Unknown: what ro.atc.disable_android_bt=1 actually disables (hiding the stock Bluetooth UI is one possibility, blocking normal use is another); I found no documentation.
- **Outlook for wireless CarPlay on Android 8.1:** Possible, nobody has tried (confidence: low; sources checked, claim stands).
- **For owners:** Units whose version text contains 'ZXDZ' are genuine Android 8.1 and their firmware contains Android's own Bluetooth stack, so the app installs, but the firmware also sets an undocumented switch (ro.atc.disable_android_bt=1) and nobody has reported a wireless CarPlay attempt. Treat it as untested: the Bluetooth step may or may not get an answer from the iPhone.
- **Sources:** [1](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/full_8227L_demo-user-8.1.0-O11019-1564460321-test-keys/system/build.prop), [2](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/full_8227L_demo-user-8.1.0-O11019-1564460321-test-keys/all_files.txt), [3](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/full_8227L_demo-user-8.1.0-O11019-1605768519-test-keys/vendor/build.prop), [4](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/full_8227L_demo-user-8.1.0-O11019-1605768519-test-keys/system/build.prop), [5](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/full_8227L_demo-user-8.1.0-O11019-1605768519-test-keys/system/etc/qb_list.xml), [6](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/full_8227L_demo-user-8.1.0-O11019-1605768519-test-keys/all_files.txt), [7](https://raw.githubusercontent.com/AndroidBlobs/device_alps_8227L_demo/full_8227L_demo-user-8.1.0-O11019-1564460321-test-keys/vendor_prop.mk), [8](https://raw.githubusercontent.com/AndroidBlobs/device_alps_8227L_demo/full_8227L_demo-user-8.1.0-O11019-1564460321-test-keys/manifest.xml)

#### AutoChips AC8227L with XY Auto firmware on YT9213 / YT9216 / YT9217 / YT9218 boards (the common '8227L_demo' units labelled Android 8.1 Go, 9.1, 10 and up)

- **Recognise it by:** About screen shows model '8227L_demo' and a build number beginning YT92xx (YT9213AJ, YT9216BJ/CJ, YT9217, YT9218) and often 'XY AUTO'. Do not trust the Android version shown, and be careful with info apps: XY Auto's AC8257 firmware ships a file andsdk_third_app_white_list_xyauto.conf that names CPU-Z, AIDA64, AnTuTu, DevCheck, Device Info HW and 'mark.fakedevicetest' (my inference: these apps are shown doctored values; the file does not state its purpose, and I did not see it in an 8227L image). Dependable checks: 'adb shell getprop ro.build.version.sdk' (27 = Android 8.1) and 'getprop ro.board.platform' (ac8227l), or simply whether the DiPlay T8 APK installs; 'There was a problem parsing the package' means the real API level is below the app's minimum.
- **Android:** One upstream DiPlay report from a YT9216CJ_00012_V009 unit gives 'Android 9.1 / API 27', i.e. real Android 8.1 under a made-up label; DiPlay builds before 0.2.14 refused to install there. A GitHub author calls the 'Android 8.1 Go' label on YT9216B a 'chinese scam because it isn't 8.1' without giving the real level. Another DiPlay fork claims some '8227L-class' units are really API 23 (see the next entry). No firmware image of this exact family was found to settle the spread.
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: no report found. No opened source describes the Bluetooth implementation of XY Auto firmware on AC8227L. For orientation only: XY Auto's own AC8257 firmware (next platform generation) ships MtkBluetooth.apk plus BTSuite.apk and no separate-module daemon, and the two other AC8227L firmware families I opened also use Android's stack. That makes the Android stack the likely answer here, but it is my inference, not a finding.
- **Outlook for wireless CarPlay on Android 8.1:** Unknown (confidence: low; could not be cross-checked).
- **For owners:** On YT92xx / 'XY AUTO' 8227L units the Android version on screen cannot be trusted; one YT9216CJ unit turned out to be API 27 and the app installs there, but its wired attempt failed and nobody has reported whether the Bluetooth step works. We do not know how Bluetooth is built on these units, so try it only if you are happy to experiment, and please report what you see.
- **Sources:** [1](https://github.com/shihabal3amri/DiPlay/issues/518), [2](https://github.com/Matejz90/Chinese-HU-with-buttons-YT9216B-1-16-or-8227L-Upgrade-firmware), [3](https://github.com/matomo-org/device-detector/pull/7388), [4](https://www.hovatek.com/forum/archive/index.php/thread-48861.html), [5](https://dumps.tadiphone.dev/dumps/alps/ac8257_demo_1g_32/-/raw/YT7260B_00007_V001/system/system/etc/xy/andsdk_third_app_white_list_xyauto.conf), [6](https://github.com/shihabal3amri/DiPlay)

#### Units sold as '8227L' or 'Android 8.1/9.1' that are really Android 6.0 (API 23), including MediaTek MT3561 'evb3561sv' boards

- **Recognise it by:** The DiPlay T8 APK (minimum API 27) fails to install with 'There was a problem parsing the package'. Over adb, getprop ro.build.version.sdk returns 23. A build fingerprint containing 'evb3561sv' and ':6.0/MRA58K' is a hint but not proof, because newer firmwares copy that string.
- **Android:** Claimed real Android 6.0 (SDK_INT=23) under an 'Android 8.1 Go' label, per the description of a pull request in another DiPlay fork, which says it was verified with a probe APK after an API-27 build failed to parse-install. The fingerprint alps/full_evb3561sv_w_65_m0/evb3561sv_w_65_m0:6.0/MRA58K exists as a real identity: later AC8227L and AC8257 firmwares reuse it as a spoofed fingerprint.
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: no report found. No opened source describes Bluetooth on MT3561/MT3562 or on Android 6.0 AC8227L builds. Unverified search excerpt: Bluetooth is missing from Android Settings on evb3561sv units and pairing is done in a vendor Bluetooth program.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: low; could not be cross-checked).
- **For owners:** Some units sold as '8227L, Android 8.1/9.1/10' may really run Android 6.0; if the APK refuses to install with 'There was a problem parsing the package', the unit is below Android 8.1 and this fork cannot run on it. This rests on one developer's report, not on firmware we examined.
- **Also read in the second pass:** [1](https://github.com/shihabal3amri/DiPlay/issues/518), [2](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/full_8227L_demo-user-8.1.0-O11019-1605768519-test-keys/system/build.prop)
- **Sources:** [1](https://github.com/jijiamoer/DiPlay/pull/2), [2](https://github.com/shihabal3amri/DiPlay/issues/392), [3](https://github.com/LibreHU/android_device_alps_ac8257_demo), [4](https://dumps.tadiphone.dev/dumps/alps/8227l_demo/-/raw/JCAC10003-OC2-V1.0.97R3-231023_1114/system/build.prop)

#### AutoChips AC8257 / AC8259 aftermarket units (XY Auto YT7260B, YT5760L; Jancar UJC201 sold as SIXWIN, Podofo, Hikity; Junsun V1 Pro '825x_pro'), real Android 9 displayed as 10 / 12 / 13

- **Recognise it by:** Build number YT7260B_... / YT5760L_... (XY Auto, apps named Gala*, XyautoSettings) or UJC201-V1.x (Jancar, apps named ivi-*, com.jancar.services). Over adb: ro.board.platform=ac8257, ro.build.version.sdk=28 whatever the release string says. Units are often advertised as 8-core Android 10/12/13.
- **Android:** Real Android 9, API 28. XY Auto builds: ro.build.version.sdk=28 with ro.build.version.release=10.0, build tag PPR1.180610.011, ro.product.first_api_level=28, ro.config.low_ram=true. Jancar UJC201: 'Le firmware annonce ro.build.version.release=12 (SDK 28 = Android 9)'. A Junsun 825x_pro owner reports 'Android 13' (displayed value). No genuine 8.1 seen.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: reported working. Source states: XY Auto image has system/app/MtkBluetooth, BTSuite, libbluetooth.so, bt_stack.conf, vendor android.hardware.bluetooth@1.0-service-mediatek, bt_drv.ko, mediatek.wlan.chip=CONSYS_AC8257, firmware soc1_0_ram_bt_1_1_hdr.bin. On Jancar UJC201 the LibreHU project says calls are 'des appels Telecom crees par le service HFP client d'Android (com.android.bluetooth)' while the stock ROM's engine is Jancar ivi-btservice (binder com.jancar.btservice.bluetooth.IBluetooth); its kernel notes say stock wmt_drv and bt_drv modules load and 'phone paired, Android Auto starts over it'. So Android's own stack drives the SoC radio, with a vendor phone app on top.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** These units are really Android 9 (API 28) even when the screen says 10, 12 or 13, so use upstream DiPlay rather than this fork. Their Bluetooth runs on Android's own stack, which is the favourable kind, and one AC8257 owner got CarPlay with upstream only in 'Existing Wi-Fi / Same LAN' mode while hotspot and USB failed, so results vary by unit.
- **Also read in the second pass:** [1](https://github.com/andreknieriem/open-headunit), [2](https://github.com/shihabal3amri/DiPlay/issues/270)
- **Sources:** [1](https://dumps.tadiphone.dev/dumps/alps/ac8257_demo_1g_32/-/raw/YT7260B_00007_V001/system/system/build.prop), [2](https://dumps.tadiphone.dev/dumps/alps/ac8257_demo_1g_32/-/raw/YT7260B_00007_V001/vendor/build.prop), [3](https://dumps.tadiphone.dev/dumps/alps/ac8257_demo_1g_32/-/raw/YT7260B_00007_V001/all_files.txt), [4](https://dumps.tadiphone.dev/dumps/alps/ac8257_demo_1g_32/-/raw/YT7260B_00007_V001/system/system/bin/zlink5.sh), [5](https://dumps.tadiphone.dev/dumps/alps/ac8257_demo_1g_32/-/raw/YT5760L_00001_V001/system/system/build.prop), [6](https://github.com/LibreHU/android_device_alps_ac8257_demo), [7](https://github.com/LibreHU/android_kernel_autochips_ac8257_4.9), [8](https://github.com/LibreHU/LibreHU-dialer-app), [9](https://github.com/andreknieriem/open-headunit/issues/914), [10](https://github.com/andreknieriem/open-headunit/issues/992), [11](https://github.com/initialChris/junsun-v1pro-recovery)

#### MediaTek MT8666 car-maker factory head units (Wuling Ling OS, Changan) on Android 9

- **Recognise it by:** Factory screen of a Wuling or Changan car; build/product strings containing spm8666 (seen in DiPlay diagnostic reports); Android 9.
- **Android:** Android 9 (API 28) in every report opened. These are factory-fitted units, not aftermarket; no Android 8.1 report seen.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: reported working. A Changan owner with root states the head unit's Bluetooth is com.android.bluetooth, 'including the AVRCP controller and A2DP sink', which stays active during CarPlay and forwards play/pause keys to the iPhone; disabling com.android.bluetooth stopped that.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** Factory Wuling and Changan screens on MediaTek MT8666 run Android 9, so use upstream DiPlay, not this fork. One Changan owner got wireless CarPlay running over the car's own hotspot after an IPv4 workaround; on Wuling Ling OS the USB path is blocked by the system.
- **Sources:** [1](https://github.com/shihabal3amri/DiPlay/issues/415), [2](https://github.com/shihabal3amri/DiPlay/issues/442)

#### AutoChips 'ac8x_demo' car-maker factory head units (e.g. Geely Geometry E) on Android 9

- **Recognise it by:** Build fingerprint beginning autochips/full_ac8x_demo/ac8x_demo:9/; 1920x720 screen in both reports; factory unit of a car brand rather than an aftermarket radio.
- **Android:** Android 9 (API 28) in both reports. No Android 8.1 report seen.
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: mixed reports. Not described beyond behaviour: on both units the system's own A2DP/HFP (music and calls) work. Which stack and which chip are not stated.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** Factory units that identify as 'ac8x_demo' run Android 9, so use upstream DiPlay. The Bluetooth step worked on a Geely Geometry E but was refused within milliseconds on another board with the same platform name, so the chip name does not tell you the outcome.
- **Sources:** [1](https://github.com/shihabal3amri/DiPlay/issues/253), [2](https://github.com/shihabal3amri/DiPlay/issues/435)

#### MediaTek MT8163 aftermarket units

- **Recognise it by:** Seller lists MTK8163 / MT8163 quad-core; over adb, getprop ro.board.platform and ro.build.version.sdk would give the truth. No About-screen strings were found in opened sources.
- **Android:** Unknown. The one opened report does not give the Android version; seller claims seen only in search excerpts range from 6.0 to 8.0 and could not be verified.
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: no report found. Not described. The app maintainer in the opened thread only says that on many devices Bluetooth and Wi-Fi are handled by the same 2.4 GHz chip.
- **Outlook for wireless CarPlay on Android 8.1:** Unknown (confidence: low; could not be cross-checked).
- **For owners:** We have no reliable information about the real Android version or the Bluetooth design of MT8163 units. If the APK installs, the unit is at least Android 8.1 and you can try it, but expect 2.4 GHz-only Wi-Fi and please report the result.
- **Sources:** [1](https://github.com/andreknieriem/open-headunit/issues/964)

### Qualcomm, car makers' own units, phones and tablets

In this family I found no Qualcomm-based head unit that ships genuine Android 8.1: BYD DiLink 1.0/2.0 is Android 7.1, KSW/Witstek M501 and the other MSM8953 aftermarket units are Android 9/10, and everything newer is Android 10 to 14. The fork's Android 8.1 audience here is therefore close to empty, and upstream DiPlay 0.2.15 now states "The APK supports Android 7.1+ (API 25)" (unconfirmed on a vehicle for 7.1-8.1), which overlaps the fork's reason to exist. BYD DiLink (Qualcomm, factory) uses Android's own Bluetooth stack and is the platform upstream was built for; a DiLink 2.0 / Snapdragon 625 / Android 7.1.2 owner reports wireless connecting over the car hotspot. KSW/Witstek Qualcomm units are the opposite case, with high confidence: the vendor service's decompiled source turns the Android adapter on only to read its address, then sets vendor.disable.bt=1 and starts the Goodocom "gocsdk" daemon on the same Qualcomm radio, and XDA owners confirm that native Android Bluetooth is "disabled and blocked". A second Qualcomm data point matches the maintainer's Allwinner T8 symptom exactly: a Nakamichi-badged MSM8953 Android 9 unit where RFCOMM "connects" in 8-13 ms, no byte ever arrives, and wired USB CarPlay works. ZXW GT6/GT7, Joying Snapdragon 665, ATOTO, NXP i.MX, Intel Apollo Lake and other factory cockpits are low-confidence entries: the few reports show Android-API Bluetooth working on NXP i.MX8 and Intel Gordon Peak (both Android 9), and nothing conclusive for the rest. Stock phones and tablets use the normal Android stack, but upstream reports are mixed (Lenovo tablet on Android 13 completes the Bluetooth iAP2 step, Huawei P40 Pro times out) and nobody has tested an Android 8.1 device. Two practical points for the compatibility text: every wireless mode, including "Existing Wi-Fi / Same LAN", reuses the Bluetooth iAP2 step, while wired USB needs no Bluetooth and worked on the MSM8953 unit where wireless failed. Outside this family, upstream issue #274 has a reporter saying wireless CarPlay works on an Android 8.1 Unisoc SP7731E unit with an unidentified modified build. Method limits: XDA blocks direct fetches so its threads were read through the r.jina.ai text proxy, GitHub issues and source were read through the GitHub API and raw URLs, and 4PDA and Reddit could not be reached at all. Downloaded evidence is in /tmp/claude-1000/-mnt-d-User-Desktop-diplay/08160165-c183-4cf2-aa0d-6f9a8e600547/scratchpad/web.

#### BYD DiLink 1.0 / 2.0 (factory head unit, Snapdragon 625)

- **Recognise it by:** BYD factory screen; DiLink 1.0/2.0, firmware branch numbers starting 2.1.x (e.g. 2.1.12, 2.1.21, 2.1.31); Android 7 in About.
- **Android:** Android 7 (an owner reports 7.1.2, 32-bit armeabi-v7a). The community DiLink table lists Android 7 for controllers 1 and 2. No fake version reporting is mentioned.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: reported working. No teardown found. Inferred from behaviour: the owner pairs the iPhone with the car and DiPlay 0.2.15 connects wirelessly over the built-in hotspot, which needs the Android-API RFCOMM/iAP2 step. The chip and the Bluetooth package names are not stated by any source.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** BYD DiLink 1.0/2.0 screens run Android 7.1, so this fork (Android 8.1 and up) will not install; use upstream DiPlay 0.2.15 or newer instead. One 2020 Han owner reports wireless CarPlay running there once the car hotspot is switched on by hand.
- **Sources:** [1](https://github.com/shihabal3amri/DiPlay/issues/519), [2](https://byd.forum/dilink-versions.html), [3](https://www.xchuxing.com/article/17778), [4](https://raw.githubusercontent.com/shihabal3amri/DiPlay/main/README.md)

#### BYD DiLink 3.0 / 4.0 / 5.x and later (factory head unit, upstream DiPlay's target)

- **Recognise it by:** BYD factory screen. Firmware branch starts with the controller number (13.1.x = DiLink 3.0 on Qualcomm 665, 16/17/21.1.x = DiLink 4.0, 23.1.x = DiLink 100, 34.1.x = DiLink 150). Android 9, 10 or newer in About.
- **Android:** Android 9 (controller 4), Android 10 (DiLink 3.0 and DiLink 4.0), Android 12 (DiLink 100), Android 13 (DiLink 150), Android 14 on the newest. No row of the community table lists Android 8.x; the Android cell for controller 8 (branch 8.1.11 / 8.1.21, where 8.1 is the branch number) is blank.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: reported working. Inferred from upstream's own testing: its setup guide says to pair the iPhone with the car's Bluetooth and "the app sends its details over Bluetooth". No source names the chip or the Bluetooth packages.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: high; sources checked, claim stands).
- **For owners:** BYD DiLink 3.0 and later run Android 9 to 14 and are the cars upstream DiPlay is written and tested for. Use upstream, not this fork.
- **Sources:** [1](https://byd.forum/dilink-versions.html), [2](https://github.com/wheregoes/byd-dolphin-hacking), [3](https://raw.githubusercontent.com/shihabal3amri/DiPlay/master/docs/COMPATIBILITY.md), [4](https://github.com/shihabal3amri/DiPlay/issues/15), [5](https://github.com/shihabal3amri/DiPlay/issues/131), [6](https://raw.githubusercontent.com/shihabal3amri/DiPlay/main/docs/INSTALL.md)

#### KSW / Witstek M501 (Snapdragon 625 / 450 "Android screens" for BMW, Mercedes, Audi), including Joying's MSM8953 "Snapdragon" units

- **Recognise it by:** Settings → About: build number starts with "M501"; display ID like "Ksw-Q-Userdebug_OS_v4.2.9"; ro.board.platform = msm8953. A Factory Menu with "Bluetooth Selection" (BT_Type) and Zlink switches; a separate vendor Bluetooth app instead of a normal Android Bluetooth page.
- **Android:** Android 9 (early units, firmware 1.2.0) and Android 10 (firmware 3.x/4.x; fingerprint field ":10:", tag QKQ1.191008.001). No Android 8.1 firmware is listed for this platform.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: reported not working. The radio is the Qualcomm one on the SoC board, but it is driven by the vendor's Goodocom "gocsdk" daemon (with libGbtsTask.so, config in /data/goc/bt_conf.ini), not by Android's stack. The decompiled vendor service com.wits.pms (BtController.fixBt, called at boot in the master branch) enables BluetoothAdapter ("gocsdk start qcom bt"), reads the address on STATE_ON, then sets vendor.disable.bt=1 (and calls BluetoothAdapter.disable() on Android 9), and on STATE_OFF sets vendor.init.ksw.bt_address=1 with the log "start gocsdk bt". Phone and music are operated in the vendor app com.wits.ksw.bt. A factory setting BT_Type chooses "Original Car Bluetooth" or "Additional Bluetooth".
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: high; sources checked, claim stands).
- **For owners:** KSW / Wits Snapdragon 625 or 450 screens (build number starting M501, Android 9 or 10) hand the Bluetooth radio to the vendor's own software and turn Android Bluetooth off after boot, so DiPlay's wireless mode cannot reach the iPhone on stock firmware. They are Android 9/10 anyway, so this fork is not for them; only wired USB with upstream is worth a try, and that is untested.
- **Also read in the second pass:** [1](https://xdaforums.com/t/android-10-snapdragon-625-hu-kswcarproject.4279143/), [2](https://4pda.to/forum/index.php?showtopic=976331), [3](https://4pda.to/forum/index.php?showtopic=1022202)
- **Sources:** [1](https://raw.githubusercontent.com/KswCarProject/KswCenterService/S/sources/com/wits/pms/custom/BtController.java), [2](https://raw.githubusercontent.com/KswCarProject/KswCenterService/master/sources/com/wits/pms/core/PowerManagerAppService.java), [3](https://raw.githubusercontent.com/KswCarProject/KswCenterService/S/sources/com/wits/pms/core/CenterControlImpl.java), [4](https://xdaforums.com/t/android-10-snapdragon-625-hu-kswcarproject.4279143/), [5](https://xdaforums.com/t/guide-ksw-witstek-zlink-wireless-carplay-never-connects-fixed-its-gocsdk-the-bt-daemon.4798989/), [6](https://xdaforums.com/t/calling-all-joying-snapdragon-and-android-10-head-unit-users-seeking-experience-stories-and-answers-to-questions-prospective-buyer.4426699/), [7](https://xdaforums.com/t/new-snapdragon-joying-headunit.4356399/), [8](https://kamilbrk.github.io/headunits/platforms/ksw/m501), [9](https://kamilbrk.github.io/headunits/upgrade-path/ksw), [10](https://kamilbrk.github.io/headunits/factory-settings/ksw/), [11](https://github.com/KswCarProject/KswRooting)

#### KSW / Witstek M600, M606, M700, M785 (Snapdragon 662 / 460 / 680 / 685)

- **Recognise it by:** Build number starts with "M600" or "M700"; display ID like "Ksw-T-M600_OS_v1.6.1" or "Witstek-T-M600_OS_v1.8.7"; ro.board.platform = bengal (shared with ZXW GT7); soc_id 444 = SM6115, 417 = SM4250.
- **Android:** Android 11, then 12, then 13 (M600); Android 13 (M700). The M600 system fingerprint says 13 and the vendor fingerprint says 11, which is the normal split for a Qualcomm system-only upgrade.
- **Bluetooth:** maker's module; apps reaching a phone or device through the Android API: no report found. The vendor service branches for these generations (S and T/1.4.0) still contain BtController.rebootBt(), which logs "reboot gocsdk" and toggles vendor.disable.gocsdk, so the Goodocom daemon is still the Bluetooth stack. The boot-time fixBt() call seen in the older branch is no longer made from this service; where the hand-over now happens is not visible in the sources I read.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium; sources checked, claim stands).
- **For owners:** KSW / Wits M600 and M700 screens run Android 11 to 13, so use upstream DiPlay rather than this fork. Their firmware still refers to the vendor's own Bluetooth daemon, so wireless may be blocked as on the older M501, but nobody has tested it.
- **Sources:** [1](https://kamilbrk.github.io/headunits/platforms/ksw/m600), [2](https://kamilbrk.github.io/headunits/upgrade-path/ksw), [3](https://raw.githubusercontent.com/KswCarProject/KswCenterService/S/sources/com/wits/pms/custom/BtController.java)

#### ZXW GT6 / GT7 ("GT6-CAR", "GT7-CAR"; Snapdragon 665 / 680 / 685), including at least one Mekede-sold Qualcomm unit

- **Recognise it by:** Build number starts with "GT6" or "GT7" (e.g. GT6-EAU-T16.00.050-userdebug-20240627); model "GT6-CAR" / "GT7-CAR"; app version like "20240724GT_KSW_G_GL (Qcom 680)"; ro.board.platform = trinket (GT6) or bengal (GT7).
- **Android:** Recent firmware: system fingerprint Android 13, vendor fingerprint Android 11. One 2023 GT6 owner says the unit "tricks everything into displaying it is Android 11" but "it is really version 10". No 8.1.
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: no report found. Mixed signals. A GT7 About screen shows "BT VER BLINK_6225_A13_R_2024/07/24", a vendor-stack style version string, and that owner wants a different Bluetooth chip so BLE gear can connect. The decompiled ZXW event service lists many module types (BTTYPE_SUDING_BC5/BC8, WENQIANG_BC6/BC9, FEIYITONG, IVT_BC5, Qualcomm, WENQIANG_Qualcomm, SUDING_816, CHENGQIAN822) and a vendor phone app com.szchoiceway.btsuite, yet it also turns the Android BluetoothAdapter on and off at ACC events. The factory config has "Bluetooth selection" (additional vs original car) and a "Dual Bluetooth machine" transmit switch.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: low; sources checked, claim stands).
- **For owners:** ZXW GT6/GT7 Snapdragon screens run Android 10 or newer (some display a higher version than they really have), so this fork is not for them. Whether any third-party app can use their Bluetooth is not known; owners only report phones connecting.
- **Also read in the second pass:** [1](https://xdaforums.com/t/help-identify-head-unit.4610731/), [2](https://raw.githubusercontent.com/KswCarProject/ZxwEventCenter/main/sources/com/szchoiceway/eventcenter/AccEvent/Utils.java)
- **Sources:** [1](https://kamilbrk.github.io/headunits/platforms/zxw/gt6), [2](https://xdaforums.com/t/bluetooth-options.4743426/), [3](https://www.tahoeyukonforum.com/threads/aftermarket-head-units-question.144837/post-1844026), [4](https://raw.githubusercontent.com/KswCarProject/ZxwEventCenter/main/sources/com/szchoiceway/eventcenter/SysProviderOpt.java), [5](https://raw.githubusercontent.com/KswCarProject/ZxwEventCenter/main/sources/com/szchoiceway/eventcenter/EventUtils.java), [6](https://kamilbrk.github.io/headunits/factory-settings/zxw)

#### Unbranded MSM8953 Android 9 aftermarket screen (Nakamichi-badged; build msm8953_64:9/PKQ1.181105.001)

- **Recognise it by:** About screen / build fingerprint contains msm8953_64:9/PKQ1.181105.001; 1280x720; Nakamichi branding.
- **Android:** Android 9 / API 28 on the one unit reported.
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: reported not working. The owner describes it as "Qualcomm default stack (QCOM-BTD, uncustomized)". The diagnostic contradicts a normal bonded setup: bondState=10, which is Android's BOND_NONE, although the phone was paired with code 0000. Inference: a fixed 0000 PIN and no Android bond suggest the pairing lives in a vendor hands-free stack rather than Android's.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: low; sources checked, claim stands).
- **For owners:** One Android 9 Snapdragon 625 screen shows the same failure as the maintainer's Allwinner T8: the Bluetooth socket reports 'connected' within milliseconds and the iPhone never answers, while wired USB CarPlay works on that unit. If your unit behaves like this, wireless will not work and a cable is the thing to try.
- **Sources:** [1](https://github.com/shihabal3amri/DiPlay/issues/354), [2](https://github.com/shihabal3amri/DiPlay/issues/330), [3](https://raw.githubusercontent.com/shihabal3amri/DiPlay/main/docs/CONNECTION_RELIABILITY.md)

#### Joying 2023 Snapdragon 665 units and other unidentified QCM6125 "trinket" head units

- **Recognise it by:** AIDA64/CPU-Z shows manufacturer QUALCOMM, model/board "trinket", /proc/cpuinfo "QCM6125"; userdebug test-keys build. The stock Settings shortcut is removed on the Joying unit.
- **Android:** Disputed. One owner: "real Android 13 comes with Android 12 OTA update to A13". Another: "These Snapdragon Joying units are all running Android 10 spoofed to Android 12." An AIDA64 dump from a similar unit shows Android 12 / API 31. No 8.1.
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: no report found. Not described. Owners only complain: "The Bluetooth thing just really screwed it for me"; another wants "a BT dongle that gives me BT BLE" so an OBD2 device can connect. The board maker is not identified in the thread.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: low; sources checked, claim stands).
- **For owners:** Aftermarket Snapdragon 665 units (Joying 2023, Dasaita, Xtrons and unbranded ones) run Android 10 or newer, often showing a higher number than the real one, so this fork is not for them. Owners disagree on whether anything other than a phone can use their Bluetooth, so wireless DiPlay there is an open question for upstream.
- **Also read in the second pass:** [1](https://xdaforums.com/t/custom-rom-development-2023-joying-qualcomm-snapdragon-665-headunits.4661801/), [2](https://xdaforums.com/t/help-identify-head-unit.4610731/), [3](https://4pda.to/forum/index.php?showtopic=1046729), [4](https://4pda.to/forum/index.php?showtopic=1083376)
- **Sources:** [1](https://xdaforums.com/t/custom-rom-development-2023-joying-qualcomm-snapdragon-665-headunits.4661801/), [2](https://xdaforums.com/t/help-identify-head-unit.4610731/)

#### ATOTO A6 / S8 (dual-Bluetooth units)

- **Recognise it by:** ATOTO branding, model codes such as S8G2A71S / S8G2A75P; AICE UI.
- **Android:** Android 10 on the S8 Ultra (review). I found no ATOTO model documented as Android 8.1 and could not confirm the Android version of older A6 units.
- **Bluetooth:** both kinds exist; apps reaching a phone or device through the Android API: reported working. Two Bluetooth chips (review: "dual Bluetooth chips"). Retail listings seen only in search results describe BT1 for hands-free and music and BT2 for tethering and other devices; I could not open an official ATOTO page, and no source says which chip the Android API drives.
- **Outlook for wireless CarPlay on Android 8.1:** Possible, nobody has tried (confidence: low; sources checked, claim weakened).
- **For owners:** ATOTO S8 units have two Bluetooth radios: one for calls and music, and a second one that ordinary apps such as Torque can use and that a phone can be paired to. Early S8 units were described by owners as Android 8 and later ones ship Android 10; nobody has tried DiPlay on either, so it is an untested maybe (check that the unit really reports Android 8.1 / API 27 before trying this fork, and use upstream on Android 10).
- **Also read in the second pass:** [1](https://xdaforums.com/t/atoto-s8-general-discussion.4114493/page-2), [2](https://xdaforums.com/t/atoto-s8-general-discussion.4114493/page-20), [3](https://xdaforums.com/t/atoto-s8-general-discussion.4114493/page-10), [4](https://xdaforums.com/t/atoto-s8-general-discussion.4114493/page-35), [5](https://xdaforums.com/t/atoto-s8-general-discussion.4114493/page-70)
- **Sources:** [1](https://www.cgmagonline.com/review/hardware/atoto-s8-ultra-car-stereo), [2](https://forum.mx5oc.co.uk/t/tech-gurus-help-please/146684), [3](https://xdaforums.com/t/atoto-s8-general-discussion.4114493/)

#### NXP / Freescale i.MX6 and i.MX8 factory units (Pioneer China "tamago", Beijing X7, Venucia D60 Plus)

- **Recognise it by:** Build fingerprint or board strings: "freescale", "SABRESD-MX6DQ", "mek_8q", "C3005H-MX8Q".
- **Android:** Android 4.3 (one i.MX6 unit), Android 7.1.2 (Pioneer tamago), Android 9 (both i.MX8Q units). No Android 8.1 unit found.
- **Bluetooth:** both kinds exist; apps reaching a phone or device through the Android API: mixed reports. Inferred from behaviour only; no chip or package is named. On the Beijing X7 the Bluetooth iAP2 identification and MFi authentication complete through the Android API. On the Android 4.3 i.MX6 unit a separate API-18 adaptation of DiPlay reached a full wireless session.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: low; sources checked, claim weakened).
- **For owners:** The NXP i.MX factory units reported so far run Android 4.3, 7.1 or 9, not 8.1, so this fork does not apply to them. Bluetooth differs by car maker even on the same chip family: one Android 9 unit completed the Bluetooth step with upstream DiPlay and another could not see the phone at all.
- **Also read in the second pass:** [1](https://github.com/shihabal3amri/DiPlay/issues/334)
- **Sources:** [1](https://github.com/shihabal3amri/DiPlay/issues/316), [2](https://github.com/shihabal3amri/DiPlay/issues/443), [3](https://github.com/shihabal3amri/DiPlay/issues/112), [4](https://github.com/shihabal3amri/DiPlay/issues/334)

#### Intel Atom Apollo Lake factory units (x86-64: Gordon Peak reference board, Great Wall WEY)

- **Recognise it by:** Board "gr_mrb" / Gordon Peak, or a CPU app showing an Intel Atom x86-64 processor.
- **Android:** Android 9 on the Gordon Peak unit reported upstream. Great Wall and Intel announced in April 2019 an Apollo Lake terminal on "Android 8.0 O-MR1" (that is, 8.1) for WEY; I found no confirmation of what shipped.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: reported working. Inferred from behaviour on the Android 9 Gordon Peak unit: "authentication and CarPlay StartSession completed" over Bluetooth, and wireless CarPlay works with upstream 0.2.13. Nothing is known about the WEY unit's Bluetooth.
- **Outlook for wireless CarPlay on Android 8.1:** Unknown (confidence: low; sources checked, claim weakened).
- **For owners:** No Intel-based head unit on Android 8.1 has been tested. One Android 9 Intel unit works wirelessly with upstream DiPlay; whether an 8.1 Intel unit is actually in any car, and whether this fork's APK runs on an Intel CPU, is not known.
- **Also read in the second pass:** [1](https://github.com/shihabal3amri/DiPlay/issues/242)
- **Sources:** [1](https://github.com/shihabal3amri/DiPlay/issues/242), [2](https://autonews.gasgoo.com/articles/icv/intel-great-wall-motor-team-up-to-pave-the-way-for-automobile-intelligence-development-70015887), [3](https://github.com/shihabal3amri/DiPlay/issues/330)

#### Other non-BYD factory Android cockpits (Ford, Haval/Renesas, Geely, Leapmotor, XPeng, Hongqi)

- **Recognise it by:** Factory screen of the named car makers; Android 9 to 12 in About.
- **Android:** Android 9 (Ford Escort, Haval Chitu, Hongqi HS5), Android 11 (a Geely unit), Android 12 (Geely Galaxy E8, Leapmotor C11). No 8.1 seen.
- **Bluetooth:** both kinds exist; apps reaching a phone or device through the Android API: mixed reports. Varies by maker. Ford Escort: Android's stack is in use and the iPhone is bonded (bondState=12, A2DP/HFP/AVRCP/PBAP connect) but the iAP2 service is not cached and the RFCOMM connect throws IOException. Geely Flyme Auto: third-party apps run in a sandbox that cannot reach USB or Bluetooth. XPeng: firmware blocks sideloading outright.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: low; sources checked, claim stands).
- **For owners:** Factory screens from other car makers are all Android 9 or newer in the reports found, so they belong to upstream DiPlay, not this fork. Results there vary by maker: some connect wirelessly, some pair but refuse the CarPlay Bluetooth service, and some block installing apps altogether.
- **Also read in the second pass:** [1](https://github.com/shihabal3amri/DiPlay/issues/361)
- **Sources:** [1](https://github.com/shihabal3amri/DiPlay/issues/330), [2](https://github.com/shihabal3amri/DiPlay/issues/361), [3](https://github.com/shihabal3amri/DiPlay/issues/275), [4](https://github.com/shihabal3amri/DiPlay/issues/43), [5](https://github.com/shihabal3amri/DiPlay/issues/380), [6](https://github.com/shihabal3amri/DiPlay/issues/446)

#### Stock Android phones and tablets used as the receiver

- **Recognise it by:** A normal phone or tablet with Google-style Settings → Bluetooth where any device can be paired.
- **Android:** Upstream reports cover Android 9 to 16 devices. Android 8.1 was a mainstream phone and tablet release (general knowledge, not from a source opened here), but I found no DiPlay test on any 8.1 phone or tablet.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: mixed reports. Ordinary Android Bluetooth with a normal Settings pairing page; no vendor hands-free module. Huawei's HarmonyOS stack behaves differently from AOSP in one report.
- **Outlook for wireless CarPlay on Android 8.1:** Possible, nobody has tried (confidence: low; sources checked, claim stands).
- **For owners:** An ordinary Android 8.1 phone or tablet has the standard Bluetooth that DiPlay needs, so it is the most plausible place for this fork to work, but nobody has reported trying one. On newer tablets the Bluetooth step usually succeeds and the trouble comes later (Wi-Fi Direct, black screen with iOS 27); Huawei HarmonyOS devices fail at the Bluetooth step.
- **Also read in the second pass:** [1](https://github.com/shihabal3amri/DiPlay/issues/372), [2](https://github.com/shihabal3amri/DiPlay/issues/100), [3](https://github.com/shihabal3amri/DiPlay/issues/200)
- **Sources:** [1](https://github.com/shihabal3amri/DiPlay/issues/249), [2](https://github.com/shihabal3amri/DiPlay/issues/142), [3](https://github.com/shihabal3amri/DiPlay/issues/290), [4](https://github.com/shihabal3amri/DiPlay/issues/299), [5](https://raw.githubusercontent.com/shihabal3amri/DiPlay/main/docs/ANDROID9_WIFI_DIRECT.md), [6](https://raw.githubusercontent.com/shihabal3amri/DiPlay/main/docs/EXISTING_WIFI.md)

#### Brand-name receivers on Telechips (Kenwood DMX958XR)

- **Recognise it by:** Kenwood-branded receiver; no Android settings or app installer visible to the user is described anywhere I looked.
- **Android:** Not established. The teardown found a Linux login prompt on the debug UART and does not say Android runs on it.
- **Bluetooth:** not established; apps reaching a phone or device through the Android API: no report found. A separate Murata radio module (LBEE6ZZ1WD-334) handles Wi-Fi and Bluetooth. The software stack is not described.
- **Outlook for wireless CarPlay on Android 8.1:** Unlikely (confidence: low; sources checked, claim stands).
- **For owners:** Brand-name receivers such as the Kenwood DMX958XR are not Android units you can install apps on as far as any source shows, and they already include CarPlay. DiPlay is not meant for them.
- **Sources:** [1](https://www.thezdi.com/blog/2024/11/18/looking-at-the-internals-of-the-kenwood-dmx958xr-ivi)

#### Teyes K3001 / K3201 (Snapdragon 212 / 425, firmware 'TZY') - a Qualcomm unit that displays a fake Android 8.1

- Added in the second pass.
- **Bluetooth:** Android's own stack; apps reaching a phone or device through the Android API: reported working.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: low).
- **For owners:** Teyes K3001/K3201 units may show Android 7.1 or 8.1 in Settings but are really Android 5.1.1 (API 22) or 7.0 (API 24), so this fork will not install on them. Any owner should check the real API level with a third-party device-info app before trusting the number in Settings.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=927325)

#### other aftermarket Snapdragon families documented on 4PDA (Snapdragon 450 Android 10 units, Wits M510 2-DIN, QCM6125 with MCU MTCH / HCTGQ / RL9A / GB)

- Added in the second pass.
- **Bluetooth:** both kinds exist; apps reaching a phone or device through the Android API: mixed reports.
- **Outlook for wireless CarPlay on Android 8.1:** Not an Android 8.1 platform (confidence: medium).
- **For owners:** The aftermarket Snapdragon 450, 625, 662 and 665 units catalogued on 4PDA all run Android 9 or newer, often really Android 10 while showing 11 to 13, so they are upstream's audience and not this fork's. Their Bluetooth is generally built for phone calls, music and at most an ELM327 adapter, which makes wireless DiPlay doubtful on them.
- **Sources:** [1](https://4pda.to/forum/index.php?showtopic=946708&st=11920), [2](https://4pda.to/forum/index.php?showtopic=1010372), [3](https://4pda.to/forum/index.php?showtopic=1054152), [4](https://4pda.to/forum/index.php?showtopic=1061371)

