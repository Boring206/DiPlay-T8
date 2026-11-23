# Initial UI and map appearance

Author: 寒叙 (@Hanxu4131). Updated for #1159.

## Current behavior

By default both the main and the cluster display declare four fields in `/info`, all `0` (automatic): `uiAppearanceMode`, `uiAppearanceSetting`, `mapAppearanceMode` and `mapAppearanceSetting`.
Every release since 0.2.16 (#346) has done this for every car, and the default has not changed.
Runtime theme changes go only through `setNightMode`, with or without the fields.

A driver can turn the declaration off: Settings → Advanced → Display (experimental) → **Tell the iPhone that CarPlay appearance is automatic (experimental)**.
With it off, the display entries carry no appearance fields, as in 0.2.15.
Other `/info` fields (audio formats, safe areas, corner masks) have changed since 0.2.15, so only the appearance part matches that release.
The setting applies at the next connection.

## Why there is a setting

The fields came from #346 (0.2.16).
寒叙 tested the local BYD CarPlay implementation on a 2023 BYD Tang DM-i Champion Edition, platform/controller 21.
There, `setNightMode` alone did not make CarPlay follow the head unit's light/dark changes, and CarPlay followed them after the four fields were added.
That was the source implementation's vehicle result, not a vehicle test of the upstream port, and it is not a claim of CarPlay Ultra support.

Three later reports say that CarPlay did not follow, or followed only in part, while the fields were declared:

- #1159, 2024 BYD Yuan Plus (ATTO 3), DiLink 4.0, iPhone 16 Pro Max on iOS 18.7.2.
  The reporter says 0.2.15 followed the head unit's automatic theme changes, and also followed manual light/dark switches made in daylight, including CarPlay's navigation screen and map.
  0.2.16 did not follow, and 0.2.22 still does not.
  In a 0.2.22 diagnostic report, DiPlay wrote `setNightMode` dark and light (`THEME_DIAGNOSTIC send … commandWritten=true`), and only DiPlay's own screens changed. Nothing inside CarPlay did.
  Between 0.2.15 and 0.2.16, these four fields are the only change in what DiPlay sends the iPhone about appearance, and `setNightMode` itself did not change.
  The 0.2.15 and 0.2.22 results were not taken back to back, so this points at the fields but does not prove they are the cause.
- #1021, Harman head unit, iPhone on iOS 26.1, DiPlay 0.2.21.
  There `setNightMode` did darken the map, and the rest of CarPlay followed the iPhone's clock.
  #1021 was never tested without the fields, so it shows neither that the fields block `setNightMode` nor that the iOS version is irrelevant.
- #675, Xpeng P7 (SA8295), iOS 18, DiPlay 0.2.18: CarPlay did not follow the head unit's day/night mode.
  There is no diagnostic report, so the head unit may simply not report the change. This is weak evidence.

On the other side, a driver with a workaround in #779 said on 2026-10-16, after 0.2.16 was out, that DiPlay's day/night switching worked.
They named neither the car nor the DiPlay version, and may have meant DiPlay's own screens.

So the fields are known to matter on one car and are suspected of doing harm on another, and nobody has compared the two states on the same car.
DiPlay cannot tell which cars need them: the Tang's platform is not detected, and there is not enough data to say whether the iOS version matters.
The default therefore stays as it was, and the setting lets a driver compare.

## Still to test

None of this has been tested in a car.

- The Yuan Plus from #1159: with the setting on (the default), switch the head unit light → dark → light during a CarPlay session. Then turn the setting off, reconnect, and repeat. Note separately whether CarPlay's home screen, the navigation screen and the map follow. The `airplay /info displays=[…]` line of a diagnostic report shows whether the fields were sent.
- A Tang (platform 21): whether CarPlay still follows the head unit with the setting off.
- One car with iOS 26 (for example the #1021 setup): whether CarPlay follows with the setting off.
- Forced Day and Night modes and the instrument (cluster) display with the setting both on and off.

Changing the default needs at least the Yuan Plus and a Tang to agree.

The four fields describe the initial automatic UI/map mode.
They are not a substitute for runtime theme updates, and a written `setNightMode` is not evidence that the iPhone applied it.
