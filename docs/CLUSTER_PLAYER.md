# Local cluster player (experimental)

Settings → Advanced → CarPlay map on dashboard (experimental) → View → Player (experimental). Map remains the default. The selector is shown only for a physical presentation display, an enabled ADB cluster route, or DiLink 3 (whose display can appear after projection starts).

The player shows artwork, title, artist, album and elapsed/duration from CarPlay metadata. Missing timing data stays unknown. The foreground can fit between native strips; its fitted bounds are proportions, not detected insets. Speed and optional gear/battery/range readings use existing read-only vehicle getters and expire when stale. This feature does not change firmware, native warning visibility or native speed geometry.

## Projection and navigation

Player and Map both respect the saved dashboard map shape, including Half screen on DiLink 3. Selecting Player pauses map stream 111 and its center-screen/launcher copies. The main CarPlay display and audio continue normally. Cluster turn-by-turn guidance is unavailable in Player mode; this is stated in the setting description. Returning to Map restores the retained TextureView surface immediately, including when CarPlay stays in the background. Genuine disconnect still hands the cluster back to the vehicle.

## Rendering

The background is static by default. Moving background is a separate off-by-default toggle. With movement off, playing progress refreshes once a second; repeated timing metadata does not redraw the page. Optional drift runs at up to 20 fps while playing and visible. Both modes keep 400 ms track-change crossfades synchronized to display frames, matching the map artwork fade cadence. Window-alpha hiding explicitly stops player animation callbacks.

The player caches title and footer layouts and font-dependent geometry instead of rebuilding them on each animation frame. Artwork sampling happens only when the cover changes. Small accent buckets are filtered by visible area, and absent artwork uses neutral colors. Brightness caps keep even the dimmest metadata text above 4.5:1 against white/yellow artwork backgrounds in rendering tests.

The existing map speedometer retains its solid album-color setting and automatic black/white contrast selection. Animated map speedometer backgrounds and caption-shade refinements are proposed separately.

## Validation and vehicle acceptance

Automated checks cover encrypted stream pause/resume commands, settings route gating, saved Half/Full projection shape, retained TextureView restoration, map handback, stale readings, cached layouts, static defaults, artwork transitions and contrast. Settings renders cover compact/full screens, large fonts and Arabic.

User-reported test vehicle: BYD Song Pro 2025, DiLink 3. Prior diagnostics identified the DiLink 3 projection path. The updated build still needs a fresh vehicle report showing Player → Map with CarPlay in the background, and confirmation that Half screen retains native speed/status readouts. Desktop tests do not substitute for that in-car check.
