# Allwinner T8 with TopWay firmware: Bluetooth test on the real unit

2026-10-10, DiPlay T8 0.2.12-t8.8, on the maintainer's own aftermarket head unit, with an iPhone that Android listed as paired.

The lines below are copied from the app's diagnostic report, fetched from `http://<head unit>:8765/` twelve seconds after the attempt stopped. The report redacts addresses itself. Lines that do not bear on Bluetooth are left out; nothing is reworded.

## What happened

1. The app-owned hotspot, the AirPlay listener and Bonjour came up normally.
2. The RFCOMM connection to the iPhone's iAP2 service reported success after **6 ms**.
3. In the next **12 s** the app sent 72 bytes and received **0**.
4. A connection to a service UUID that no phone offers then also "succeeded", after **12 ms**.
5. The app concluded that the connections are not real, stopped without retrying, and the home page has shown "cannot use wireless CarPlay" since.

A real Bluetooth stack has to page the phone and look the service up before it can connect, which takes hundreds of milliseconds, and it cannot connect to a service that does not exist.

## The attempt

```
15:45:45.322  LocalOnlyHotspot starting with Wi-Fi client enabled=true
15:45:48.973  wireless hotspot backend=LocalOnlyHotspot iface=wlan0 family=IPv4 identitySource=interface host=[ip] band=2.4 GHz channel=0 frequency=unknownMHz
15:45:48.987  CONNECTION_DIAGNOSTIC attempt=75 run=1 phase=WIRELESS stage=HotspotReady
15:45:49.070  airplay listener ready family=IPv4 port=7000
15:45:49.222  wireless Bonjour services started mode=interface iface=wlan0
15:45:49.233  STEP bt/rfcomm: connecting to the iPhone iAP2 RFCOMM service
15:45:49.243  CONNECTION_DIAGNOSTIC attempt=75 run=1 phase=WIRELESS Bluetooth snapshot point=before-connect enabled=true bondState=11 cachedServiceCount=unknown cachedIap2Service=unknown
15:45:49.262  CONNECTION_DIAGNOSTIC attempt=75 run=1 phase=WIRELESS Bluetooth connect completed elapsedMs=6
15:45:49.314  wireless RFCOMM connected address=[address]
15:45:49.323  wireless iAP2 CSM channel opened over RFCOMM
15:45:49.359  wireless Bluetooth iAP2 bootstrap starting location=false vehicleStatus=false
15:45:59.330  wireless startup elapsedMs=10358 authenticated=false wifiConfigs=0 startRequests=0 tcpAccepted=0 sessionActive=false waitingFor=Bluetooth_iAP2_authentication startRequestAgeMs=none firstTcpAfterStartMs=none
15:46:01.298  Bluetooth check: connected in 6ms, then rx=0 tx=72 after 12000ms; connect to a service no phone offers succeeded in 12ms unreal=true
15:46:01.316  ERROR Android Bluetooth on this head unit reports connections that are not real: a connection to a service no phone offers also succeeded in 12ms
15:46:01.386  wireless startup recovery stopped generation=74 reason=BLUETOOTH_NOT_READY retries=0
```

## The head unit as it describes itself

```
DiPlay 0.2.12-t8.8 · private beta diagnostic report
Android 8.1.0 / API 27
Head unit: Allwinner T8
Head-unit board: exdroid; hardware: sun8iw6p1; build: t8_p1-eng 8.1.0 OPM1.171019.013 20190117 test-keys
Head unit check: verdict=CANNOT bluetoothUnreal=true wirelessWorked=false carPlayWorked=false
ABIs: armeabi-v7a, armeabi
Processor: ARMv7 Processor rev 5 (v7l); sun8iw6; cores=8
Memory: 2000 MB total, 881 MB free
Display: 1024x600 density=160
Wi-Fi: enabled=true fiveGhz=false p2p=false joinedPhoneHotspot=true
Bluetooth: enabled=true state=12 bonded=1 bondedIPhones=0 phoneSelected=true
Bluetooth adapter: state=12 scanMode=0 discovering=false nameLength=null addressPrefix=hidden
Bluetooth devices: bond=11 type=0 class=7936 services=none iap2=unknown iPhone=false selected=true
Bluetooth service: hosts=[com.android.bluetooth/AdapterService] stockApp=8.1.0 stockLibs=libbluetooth_jni.so
Bluetooth kernel: hci=[unreadable] rfkill=[phy0:wlan, phy1:wlan] wifiDriver=rtl8188eu
Bluetooth files: /system/lib/hw: bluetooth.default.so | /system/lib: android.hardware.bluetooth@1.0.so,libbluelet.so,libbluetooth_jni.so,libbt_platform.so,libldacBT_abr.so,libldacBT_enc.so | /system/etc/bluetooth: bt_did.conf,bt_stack.conf
Kernel modules: 8188fu,8188eu,uvcvideo,goodix_touch,hdmi,pvrsrvkm
Video decoders: OMX.allwinner.video.decoder.avc[avc], OMX.google.h264.decoder[avc], OMX.allwinner.video.decoder.hevc[hevc], OMX.google.hevc.decoder[hevc]
Installed vendor packages: com.tw.auxin com.tw.bootanimation com.tw.bt com.tw.color com.tw.eq com.tw.music com.tw.radio com.tw.service com.tw.video
```

Read together: Bluetooth is switched on (`state=12`), yet the adapter has no name, the kernel lists no Bluetooth radio (`rfkill` holds two Wi-Fi entries only, and the only radio modules loaded are the RTL8188 Wi-Fi drivers), and the one device Android lists as paired has no type, no services and a bond state of "bonding" (11), which it never leaves. Calls and music on this unit are paired in the maker's own app (`com.tw.bt`).
