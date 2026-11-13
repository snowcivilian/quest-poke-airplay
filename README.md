# Quest AirPlay Receiver

A free, sideloaded AirPlay receiver for **Meta Quest 3**, built to play iPhone games (Delta) on the headset well: correct aspect ratio with no black bars, in-headset volume that sticks, low delay, an immersive theater mode, and a **Pokémon battle helper** that reads the battle off the screen and shows type matchups next to the game.

It's a fork of [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) (GPL-3.0), which does the AirPlay work through [UxPlay](https://github.com/FDH2/UxPlay). Full spec: [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md).

**Setup:** Delta runs on the iPhone with a Bluetooth controller paired to the phone. The phone screen-mirrors to the Quest. Saves stay in Delta.

## Features

| | What it does |
| --- | --- |
| **Quest panel** | Launches as a 16:9 panel named **Quest AirPlay**, starts listening automatically, shows *Waiting for iPhone*, receiver name **Quest**, no PIN, keeps the headset awake while streaming. |
| **Volume** | Volume bar over the video (mute, 0–100 %) and in Settings. Saved across restarts. *Ignore iPhone volume* (on by default) stops the phone's buttons from changing the sound. |
| **System presets** | One per Delta system: NES (and 8:7), SNES, N64, GB/GBC, GBA, DS top screen, DS both screens, Genesis, plus Full frame. Crops Delta's black bars and shows the game at the right shape (e.g. SNES at 4:3). Defined in [`presets.json`](app/src/main/assets/presets.json). |
| **Panel fits the game** | Horizon OS can't resize a panel from code, so the panel reopens at the preset's shape (4:3, 3:2, 10:9, 8:7, 2:3 or 16:9). The stream keeps running. Can be turned off. |
| **Auto-detect** | Measures the black bars about every 0.4 s and picks the matching system after 3 agreeing readings. Fades and dark scenes are ignored. Picking a preset by hand turns it off until you turn it back on. |
| **Game mode** | On by default: lowest delay (frames shown as soon as decoded, low-latency audio, tight audio buffer). Turn off for smoother video watching. |
| **Stats overlay** | Upstream's FPS/bitrate/audio overlay plus decode-to-display delay, the active preset and the measured game-box shape. |
| **CRT scanlines** | Optional scanlines matched to the system's native line count, with an intensity slider. |
| **Theater mode** | Immersive big screen (Meta Spatial SDK). Dark room or passthrough. Adjustable screen height, distance, elevation and curve. Recenter by holding the Meta button. Presets and scanlines apply here too. The controls panel can be grabbed, moved, resized and minimized. |

### Pokémon battle helper

An optional card that watches the mirrored game, recognizes a Pokémon battle and shows what you'd otherwise look up on your phone. It reads the screen with on-device text recognition. It needs no ROM access, no save-file access and no internet, and it never touches the game.

What the card shows during a battle:

- **Who's fighting:** both Pokémon's names and types, base stats, possible abilities with what each one does, and evolution lines.
- **Opponent's weaknesses and resistances,** grouped by ×4, ×2, ×½, ×¼ and ×0.
- **Matchup summary:** which of your Pokémon's types hit the opponent hard, and which of the opponent's types threaten you.
- **Your moves:** when the FIGHT menu is open, each move's type, physical or special, power, and its multiplier against the current opponent (against both opponents in a double battle).
- **Wild battles:** the catch rate.
- **Status conditions** on either side, and what they change.
- **Double battles:** both opponents and your partner.
- **Switch screen:** each party Pokémon's best hit on the opponent and the opponent's best hit on it.
- **Learning a move:** the new move compared with the four your Pokémon already knows, with same-type (STAB) moves marked.

**Data generation:** choose Gen 3, Gen 4, Gen 5 or Latest for each game. This sets the type chart, types, move categories and abilities, so ROM hacks that use newer mechanics (Fairy type, the physical/special split) get correct matchups. The data comes from [PokéAPI](https://github.com/PokeAPI/pokeapi) and is bundled in the app.

**Nicknames:** if your Pokémon has a nickname, the card asks once which species it is and remembers it for that game.

**Supported games**, all English versions:

| Game profile | Status |
| --- | --- |
| Diamond / Pearl / Platinum | Works. Tested live on Platinum, including doubles. Needs the DS preset with both screens. |
| HeartGold / SoulSilver | Same screen layout as Platinum, not yet tested. |
| FireRed / LeafGreen, Ruby / Sapphire / Emerald, FireRed- and Emerald-based hacks | Experimental: the screen layout is a first guess and needs tuning. |
| Black / White, Black 2 / White 2 | Not yet: no screen layout. |

**Turning it on:** *Settings → Pokémon battle helper*. Pick your game there. The Poké Ball button next to theater and fullscreen shows or hides the card. The card can sit in the top left or top right of the panel, or open in its own window, and it also floats beside the screen in theater mode. When the helper is off, no recognition runs at all.

**Tuning a game's layout:** each game's screen regions are data in [`profiles.json`](app/src/main/assets/pokemon/profiles.json). *Calibration mode* draws them over the live game and saves frames on the headset, and *Test text reading* shows what the text recognition sees.

### Using it

1. Open **Quest AirPlay** from *Unknown Sources* in the Quest app library.
2. On the iPhone: Control Center → Screen Mirroring → **Quest**.
3. Tap the video to show the bar at the bottom: the ⧉ button opens the quick menu (theater mode, auto-detect, CRT, presets), and the slider sets volume. The cube icon next to fullscreen opens theater mode.
4. **Settings** has Volume, Display and Theater at the top. Logs are a collapsible section. Everything from the original app (receiver name and port, codecs, resolution, audio buffering, developer options) is under **Advanced**.

## Building and installing

### Requirements

- JDK 21, Android SDK platform 37 and build-tools 37.0.0, NDK `27.0.12077973`, CMake 3.22.1 (versions from upstream's CI).
- **The checkout path must not contain spaces.** FFmpeg's configure step (built from source) rejects them, and Gradle resolves symlinks, so a symlink doesn't help.
- Submodules: `git submodule update --init --recursive`.

### Signing

Every build is signed with one keystore, kept outside the repo. Android refuses an update signed with a different key, and the fix (uninstalling) wipes settings. `local.properties` (gitignored) points at it:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
storeFile=/Users/<you>/.android/quest-airplay.jks
storePassword=...
keyAlias=quest-airplay
keyPassword=...
```

### Build, install, run

```bash
scripts/quest-install.sh
```

That builds the `quest` flavor for arm64, installs it with `adb install -r`, launches it, and prints any crash. Never uninstall the app: that wipes its settings. By hand:

```bash
./gradlew assembleQuestRelease -Pabis=arm64-v8a
adb install -r app/build/outputs/apk/quest/release/app-quest-release.apk
```

Unit tests (pure logic: crop geometry, bar detection, preset matching, hysteresis, volume curve, panel choice, theater geometry, `presets.json`):

```bash
./gradlew testQuestDebugUnitTest
```

### Wireless ADB on the Quest

Horizon OS has no *Wireless debugging* menu. Once per boot, with a USB cable and Developer Mode on (set in the Meta Horizon phone app):

```bash
adb tcpip 5555
adb connect <quest-ip>:5555
```

Accept *Allow USB debugging* in the headset the first time. After that the cable can be unplugged until the Quest restarts.

## How it's built

### Flavors

- **`quest`**: the Quest app. `applicationId` `io.github.snowcivilian.questairplay` (installs alongside upstream), minSdk 34, Horizon OS manifest, theater mode, Spatial SDK.
- **`generic`**: upstream's app, essentially unchanged. Kept as a baseline for comparisons.

### Video path

Upstream already keeps the AirPlay session in a foreground service and decodes into a GPU texture, so the stream survives surface changes. This fork replaces the final blit with [`QuestFrameRenderer`](app/src/main/kotlin/io/github/snowcivilian/questairplay/video/QuestFrameRenderer.kt), which:

- crops to the active preset and letterboxes it at the preset's aspect,
- applies the scanline shader,
- every 400 ms, draws the uncropped frame into a 320×180 buffer and reads it back for auto-detection (on the GL thread, never the decoder thread).

The panel and the theater screen are just different surfaces for the same renderer.

### Code layout

New code lives in new packages so upstream merges stay easy:

| Where | What |
| --- | --- |
| `app/src/main/kotlin/io/github/snowcivilian/questairplay/` | `video/` (presets, crop geometry, detection, renderer, delay stats), `audio/` (volume curve), `panel/` (per-aspect panel switching), `theater/` (geometry, launcher), `ui/` (Compose controls and settings), `GameMode.kt` |
| `app/src/quest/` | Quest manifest, default settings, per-aspect panel activities, `TheaterActivity` (Spatial SDK), string overrides |
| `app/src/main/assets/presets.json` | System presets (data, not code) |
| `app/src/test/` | Unit tests for the pure logic |

Upstream files are touched only with small hooks marked `// quest fork`. The hooks are in `Prefs.kt` (new settings), `AirPlayService.kt` (volume, controller init), `VideoPipeline.kt` (renderer), `VideoRenderer.kt` (delay timestamps), `MainActivity.kt` (open class, panel switcher), `MainScreen.kt` and `SettingsScreen.kt` (UI entry points) and `app/build.gradle.kts` (flavors, signing, dependencies).

Settings stay in upstream's single SharedPreferences store, so the in-headset settings and upstream's ADB interface share it:

```bash
adb shell am broadcast -n io.github.snowcivilian.questairplay/io.github.jqssun.airplay.SettingsReceiver \
  -a io.github.jqssun.airplay.SET_SETTING --es key app_volume --es value 60
```

### Tuning presets

If a system crops wrong or isn't auto-detected, turn on *Show stats overlay* and read the **Detected** line (the measured shape of Delta's game box). Put that number in the system's `sourceAspect` in `presets.json`, or set an explicit `crop`. No code change needed.

### Theater mode

[`TheaterActivity`](app/src/quest/kotlin/io/github/snowcivilian/questairplay/theater/TheaterActivity.kt) is the hybrid app's immersive activity (Spatial SDK 0.15.0, following Meta's HybridSample). The screen is a `VideoSurfacePanelRegistration` whose surface goes to the AirPlay service. The controls are a Compose panel using the Interaction SDK grab, handle and resize components. Switching back to the panel goes through Home, as Meta's hybrid guide describes.

## Status

All required milestones (M0–M6, R1–R10) are built and in use. Stretch goals S1 (split DS screens) and S4 (Pokémon battle helper) are merged and in use. S4 is still being tuned on more games. S3 (extra theater rooms) was dropped. What still needs checking against the spec, and the known issues, are in [docs/TESTING.md](docs/TESTING.md).

## Merging upstream

```bash
git fetch upstream
git merge upstream/main
```

Conflicts should be limited to the `// quest fork` hooks listed above. Merge about once a month, or whenever an iOS update breaks mirroring.

## License and credits

GPL-3.0, like upstream (see [LICENSE](LICENSE)). This is a modified version of [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server), changed from November 2026 onward; every change and its date are in the git history, and changed upstream files are marked `// quest fork`. Anyone who shares a built APK must share its source too.

Built on:

- [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server): the receiver this is built on
- [UxPlay](https://github.com/FDH2/UxPlay): AirPlay/RAOP implementation
- [FFmpeg](https://ffmpeg.org): ALAC audio decoding
- [libplist](https://github.com/libimobiledevice/libplist), [OpenSSL](https://www.openssl.org): plist parsing and cryptography
- [PokéAPI](https://github.com/PokeAPI/pokeapi): Pokémon type, stat, move and ability data (BSD-3-Clause, [license](app/src/main/assets/pokemon/LICENSE-POKEAPI.txt))
- [Meta Spatial SDK](https://developers.meta.com/horizon/documentation/spatial-sdk/): theater mode
- [ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android): reading the battle screen

Full list with licenses: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Not affiliated with, endorsed by or sponsored by Apple, Meta, Nintendo, The Pokémon Company, GAME FREAK or the makers of Delta. AirPlay is a trademark of Apple Inc.; Meta Quest is a trademark of Meta Platforms, Inc.; Pokémon is a trademark of Nintendo, Creatures Inc. and GAME FREAK inc. No games, ROMs or game assets are included.
