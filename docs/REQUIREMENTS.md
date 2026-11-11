# Quest AirPlay Receiver: Requirements

Nov 10, 2026 · @snowcivilian

## Overview

We will fork the open-source [android-airplay-server](https://github.com/jqssun/android-airplay-server) into a free, sideloaded AirPlay receiver built for Meta Quest. Its one job is to show Delta games from the iPhone on the headset well. It replaces AIpin, which is buggy, hard to resize, has broken volume control and is paywalled.

The play setup stays the same. Delta runs on the iPhone, a Bluetooth controller is paired to the iPhone, and the screen is AirPlay-mirrored to the Quest. Saves stay in Delta.

**Success criteria**

- Every system Delta supports fills the panel at its correct aspect ratio, with no black bars.
- Volume can be adjusted from inside the headset, and the setting sticks.
- The delay from button press to the picture on the Quest is no worse than AIpin's, and ideally better.
- A theater mode shows the game on a large virtual screen in an immersive environment.
- No paywall, ads or account, and no ADB needed in normal use.

## Decision record: why a receiver, not a native Quest emulator

Running an emulator on the Quest itself and sharing progress with Delta only works for in-game saves, and only reliably in one direction (Delta to Quest). So we build the receiver. Checked 2026-11-10.

| What | Shareable? | Detail |
| --- | --- | --- |
| Save states | No | Save states are tied to a specific emulator core and version. Delta's exported .svs files won't load in RetroArch (recalled, not tested). |
| In-game saves, Delta to Quest | Yes, can be automated | When syncing through Dropbox, Delta keeps saves in a visible /Apps/Delta Emulator/ folder, named GameSave-{HASH}. Adding .sav makes them importable ([issue #533](https://github.com/rileytestut/Delta/issues/533), [issue #429](https://github.com/rileytestut/Delta/issues/429)). GBA, GB/GBC and SNES saves are raw SRAM, the same format RetroArch .srm files use ([PatchVault](https://patchvault.pages.dev/guides/how-to-transfer-save-files)). N64 and DS saves can need conversion. |
| In-game saves, Quest back to Delta | Manual only | Delta says its Dropbox files [are not meant to be edited](https://faq.deltaemulator.com/using-delta/delta-sync), and its Google Drive sync folder is hidden. The safe way back is Delta's per-game Import Save File, which risks overwriting newer progress with an older save. |

What we give up: playing natively would remove the streaming delay and allow the Quest controllers ([RetroArch sideloads on Quest 3 and supports them](https://retrohandhelds.gg/?p=9874)). Revisit if the receiver's delay is unacceptable. A one-way tool that pulls Delta saves to the Quest is a possible separate project later.

## Base project and fork setup

We start from [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) (GPL-3.0). It is built on [UxPlay](https://github.com/FDH2/UxPlay), a C library connected to the app through JNI, and plays the video with Android's built-in decoder and the audio with its audio player. It supports Android 7.0 and up. UxPlay already handles Apple's encryption handshake (FairPlay) and AirPlay mirroring, so we never touch that layer.

**What we get from upstream:** H.264/H.265 mirroring, AAC-ELD/AAC-LC/ALAC audio, HLS video playback, Picture-in-Picture, an optional PIN, resolution, overscan and frame-rate controls, an audio latency control, a debug overlay (FPS, bitrate, codec, resolution, volume), and settings changeable over ADB.

**Fork steps**

1. Create a private repo on Mer's GitHub from a clone of the original (commands in the Kickoff section), keeping the original repo as a remote named `upstream`.
2. `git submodule update --init --recursive`, then `./gradlew assembleDebug`. The native code under `app/src/main/cpp` builds with CMake. Take the NDK and AGP versions from `.github/workflows/apk.yml`.
3. Change the `applicationId` so the fork installs alongside upstream.
4. Add a `quest` product flavor with its own manifest for Horizon OS settings, following [Meta's porting guide](https://developers.meta.com/vr/documentation/android-apps/port-an-existing-app).
5. Put new features in new files or packages, and keep edits to upstream files small, so pulling upstream fixes stays easy. Merge upstream about once a month, or whenever an iOS update breaks mirroring.
6. Install with `adb install` on the Quest in developer mode, or with SideQuest.

**License:** GPL-3.0 allows a private fork for personal use. If the APK is ever shared, the source must be shared with it.

**Install and update workflow:** sideloading over Wi-Fi from the computer where Claude Code runs. Developer mode is already on, since AIpin was sideloaded the same way.

1. **One-time setup:** on the Quest, turn on Wireless Debugging (Settings > System > Developer). Pair the computer once with `adb pair`, using the pairing code shown in that menu.
2. **Every update:** Claude Code runs `./gradlew installQuestDebug`, or `adb connect` followed by `adb install -r` on the built APK. The app updates in place and keeps its settings. No cable needed.
3. **Sign every build with the same key.** Android rejects an update signed with a different key, and then you'd have to uninstall, losing settings and saved nicknames. Make one dedicated keystore, keep it outside the repo, and point the Gradle signing config at it.
4. **Find the app** under Unknown Sources in the Quest's app library.

Not needed: Meta store release channels, SideQuest, or an in-app updater. With wireless ADB, Claude Code can build and install in one step.

## Feature requirements

Phases 1 to 3 are required, theater mode included. Stretch items come after the required work.

| ID | Phase | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| R1 | 1 | **Quest panel setup.** Add a `<layout>` element in the manifest (`android:defaultWidth`/`defaultHeight`/`minWidth`/`minHeight`) and the `com.oculus.supportedDevices` meta-data. Lock resizing to a fixed aspect ratio ([Meta panel sizing](https://developers.meta.com/vr/essentials/horizon-os-panel-sizing)). | Launches as a large 16:9 panel. Dragging the resize handles keeps the active preset's aspect ratio. |
| R2 | 1 | **In-headset settings screen.** Every setting from upstream's ADB interface plus every new setting, usable with Quest controllers and hand tracking (touch targets at least 48dp). Settings persist. | Nothing in normal use requires ADB. |
| R3 | 1 | **Quality-of-life basics.** Start listening for AirPlay automatically on launch. Use a fixed receiver name ("Quest") so the iPhone remembers it. PIN off by default. Keep the display awake while streaming. Show a clear "waiting for iPhone" screen. | Open the app, pick it in iPhone Screen Mirroring, and the game appears with no other steps. |
| R4 | 1 | **Working volume.** An in-app volume control (0 to 100%) applied to the app's audio output, plus mute. An "ignore iPhone volume" setting (on by default), so AirPlay volume messages from the phone can't override it. | The volume slider changes loudness immediately and survives a restart. Changing volume on the iPhone does nothing while "ignore" is on. |
| R5 | 2 | **System presets.** Each preset sets a crop rectangle (which part of the incoming frame is the game) and the panel's aspect ratio. Values are in the System presets section. Presets can be picked manually from the settings screen and a small quick menu. | Every preset shows its system's game with no black bars and no stretching. |
| R6 | 2 | **Automatic preset detection.** Measure the black bars in the decoded video and match the nearest preset. Re-check when the stream's resolution changes and every few seconds. Use hysteresis so dark scenes and fades don't trigger a switch. A manual pick overrides detection until the user turns detection back on. | Moving from a GBA game to a SNES game in Delta resizes the panel within about 2 seconds. A fade to black never changes the preset. |
| R7 | 2 | **Low-latency game mode.** Request low-latency decoding (`MediaFormat.KEY_LOW_LATENCY` on Android 11+, when the decoder supports it). Set the audio latency as low as it goes. Display each frame as soon as it's decoded, with no extra buffering or smoothing. On by default; a toggle turns it off for video watching. | Measured delay (see Testing) is lower with game mode on than off, and no worse than AIpin. |
| R8 | 2 | **CRT/scanline shader.** An optional scanline effect, off by default, with an intensity setting. It needs the GL rendering path described in Architecture. | The shader is visible when on, and toggling it doesn't drop frames (stays at the incoming frame rate in the debug overlay). |
| R9 | 2 | **Delay readout in the debug overlay.** Add decode-to-display time and audio buffer size to upstream's overlay. | The overlay shows both numbers live. |
| R10 | 3 | **Immersive theater mode.** An immersive view built with the Meta Spatial SDK as a hybrid app (targets Horizon OS v69+, per [Meta's hybrid apps guide](https://developers.meta.com/horizon/documentation/spatial-sdk/hybrid-apps-overview)). The game plays on a large virtual screen in a dark environment, with a passthrough option. Screen size, distance and curvature are adjustable, and recenter works. The AirPlay session must survive switching between panel and theater. | One button switches panel to theater and back without disconnecting or a black screen of more than 1 second. Presets and shader work in theater mode too. |
| S1 | Stretch | **Split DS screens in theater mode.** With a DS preset that shows both screens, split the frame into two virtual screens: top screen large in front, bottom screen smaller, lower and tilted like a handheld. | Both screens show at their correct 4:3 aspect, positions are adjustable, and both stay in sync. |
| S3 | Stretch | **Theater environments.** More virtual rooms beyond the dark theater, such as a retro living room with a CRT TV. | At least two environments to pick from in settings. |

**Dropped:** integer and nearest-neighbor scaling. Delta has already scaled the game up, and the stream is compressed video, so we can't get pixel-perfect output on the receiving end.

## System presets

There is one preset for each system Delta 2.0 supports, plus a pass-through default. The native resolutions below are standard figures recalled from memory, not checked against Delta's code. The real crop rectangles have to be measured from actual Delta streams in milestone 3, because the receiver gets a full 16:9 frame with Delta's own black bars and skin layout around the game.

| Preset | Native resolution | Panel aspect | Notes |
| --- | --- | --- | --- |
| Full frame (default) | n/a | 16:9 | No crop. For anything that isn't Delta. |
| NES | 256x240 | 4:3 | Displayed at 4:3. Some NES setups show 8:7, so offer a toggle. |
| SNES | 256x224 | 4:3 |  |
| N64 | 320x240 | 4:3 |  |
| Game Boy / Color | 160x144 | 10:9 |  |
| GBA | 240x160 | 3:2 |  |
| DS, top screen only | 256x192 | 4:3 | Matches Delta's "AirPlay Only" DS skin, which puts the top screen on the TV and the bottom screen on the phone. Not the default, since Delta sends both screens in this setup. |
| DS, both screens stacked | 256x384 | 2:3 | Default DS preset, since Delta sends both screens in this setup. Tall panel. The input for stretch goal S1. |
| Genesis / Mega Drive | 320x224 | 4:3 | Added in Delta 2.0 (October 2026). Some games run at 256x224. |

Store presets as data (JSON in assets), not code, so new ones and crop fixes don't need a code change. Each entry has an id, label, crop rectangle as fractions of the frame, panel aspect ratio and an optional pixel-aspect correction.

## Stretch goal S4: Pokémon battle helper

An optional card that recognizes a Pokémon battle on screen and shows type matchups for both sides. It covers GBA (Gen 3) and DS (Gen 4 and 5) games. We build it after all required work. It's off by default, and one toggle turns it on.

### Scope

- **Game profiles:** FireRed/LeafGreen, Ruby/Sapphire/Emerald, Diamond/Pearl/Platinum, HeartGold/SoulSilver, Black/White, Black 2/White 2, plus "FireRed-based hack" and "Emerald-based hack". A hack with its own battle screen gets its own profile. Profiles are data files, not code.
- **Single battles only in v1.** In a double battle, show "Double battles not supported" instead of guessing.
- **English versions only.**
- The user picks the game profile once in settings. The GBA or DS system preset narrows the list.

### Data generation toggle

A setting with four choices: **Gen 3 / Gen 4 / Gen 5 / Latest**. It decides the type chart, Pokémon types, move types and categories, abilities, and the list of species and moves that names are matched against. Each game profile has a default (FireRed uses Gen 3), and the user's choice is saved per profile. This matters because many ROM hacks use later-generation mechanics.

Note: Gen 3, 4 and 5 use the same type chart. "Latest" adds Fairy and removes Steel's resistance to Ghost and Dark (recalled). The choice between Gen 3, 4 and 5 still matters for other data: Gen 4 changed whether a move is physical or special from depending on its type to being set per move, and "Latest" changes some Pokémon's and moves' types (for example, the Clefairy line becomes Fairy). "Latest" also adds the later-generation species that hacks include.

### What the card shows

| Item | When | Notes |
| --- | --- | --- |
| Opponent sprite | Always in battle | Cropped from the game frame itself. No sprite files are bundled. |
| Names and types, both sides | Always in battle |  |
| Opponent's weaknesses and resistances | Always in battle | Grouped by 4x, 2x, ½x, ¼x and 0x. |
| Matchup summary | Always in battle | Which of your Pokémon's types hit the opponent hard, and which of its types threaten you. Based on types only, since the opponent's moves aren't visible. |
| Your moves' effectiveness | When the FIGHT menu is on screen | Each move's type, physical or special, power, and its multiplier against the current opponent. |
| Base stats, both sides | Always in battle | Stats only. No prediction of who moves first. |
| Opponent's possible abilities | Gen 3 and later | The actual ability isn't visible, so list the species' possible abilities, including the hidden ability in Gen 5 and Latest. |
| Evolution line | Always in battle |  |
| Catch rate | Wild battles only |  |

**DS caveat:** in Gen 4 and 5, the move list appears on the bottom touchscreen (recalled). With Delta's "AirPlay Only" DS skin, the bottom screen stays on the phone, so the receiver never sees it. Move effectiveness on DS only works with a DS layout that sends both screens. With the other layout, the card shows everything else plus a short note. In the current setup, Delta sends both screens, so this works by default.

### How it works

1. **Battle detection.** Each game profile's layout file lists screen regions as fractions of the cropped game frame: the HP boxes, name fields, battle text box, move slots and opponent sprite area. Match the HP box templates 2 to 4 times a second on scaled-down frames, never on the rendering thread.
2. **Reading text.** Start with a quick test of Google ML Kit's on-device text recognition (the version with the model built into the app) on the Quest. Whether it works without Google Play Services is unverified. If it doesn't, match characters against each generation's fixed pixel font instead. Either way, fuzzy-match the result against the species and move names for the chosen data generation.
3. **Wild or trainer battle.** Read the battle text box ("Wild X appeared!").
4. **Nicknames.** When your Pokémon's name doesn't match a species, ask once which species it is and save that per game profile. Settings has an editable list of saved nicknames.
5. **Data.** A build-time script turns [PokeAPI](https://github.com/PokeAPI/pokeapi)'s CSV data into a compact JSON file for each data generation, bundled in the APK. Nothing is fetched from the internet while playing.
6. **States.** Idle, then In battle, then FIGHT menu open, then Battle over, then back to Idle. The card hides when the battle ends.

### Display and controls

- **Panel mode:** the card either sits beside the game, widening the panel, or floats semi-transparent in a corner. The user picks which.
- **Theater mode:** a floating card next to the screen.
- **On/off:** a toggle in the quick menu and in settings. When off, no recognition runs at all, so it costs no processing.
- **Calibration mode:** a debug view that draws the profile's regions over the live game and saves frame captures. It's used to tune layouts and record test sets.

### Acceptance criteria

- Identifies the opponent's species correctly in at least 95% of battle starts, across a recorded set of 50 battles per supported profile.
- The card appears within 1 second of the HP boxes appearing.
- With the FIGHT menu open, all 4 moves show the correct multiplier (GBA, and DS with the both-screens layout).
- Changing the data generation updates the card immediately.
- Unit tests check each generation's type chart against a reference table, plus the fuzzy name matching.

### Build order within S4

1. Data pipeline and type chart tests.
2. Text-reading test (ML Kit or font matching) on FireRed.
3. FireRed end to end: detection, names and card.
4. Move effectiveness.
5. The other GBA profiles, then the DS profiles.

## Out of scope

- **Controlling games with the Quest controllers.** iOS ignores generic Bluetooth gamepads, so this would need an ESP32 bridge board. Judged not worth the complexity. Game input stays a Bluetooth controller paired to the iPhone.
- **Native emulation and save syncing.** See the decision record.
- **DRM content** (for example the Apple TV app). Upstream doesn't support it, and neither will we.
- **New AirPlay casting features** beyond what upstream has. Mirroring is the focus.
- **Distribution.** No app store, no Meta store and no public builds. This is a sideloaded personal APK.
- **Other headsets.** Quest 3 only.

## Architecture and implementation notes

The key change is to move the AirPlay session into a background service and send the decoded video through our own GPU rendering step. That one step handles cropping, presets, the shader and theater mode.

&#91;embedded content: receiver architecture: session in a service, one renderer for both views\]

Everything inside the shaded box keeps running when you switch between panel and theater. Only the display surface changes.

- **Session in a foreground service.** UxPlay, the video decoder and the audio player live in a foreground service, not an Activity. The panel and the theater view connect to it and attach a display surface. This is what lets the stream survive switching between panel and theater (R10) (inferred; needs testing on Horizon OS).
- **Decode to a GPU texture, not straight to the screen.** The decoder outputs to a texture instead of a plain screen surface. A small OpenGL ES renderer draws that texture onto whatever surface is active, cropping by the preset's rectangle and optionally applying the CRT shader. Panel mode and theater mode use the same renderer.
- **Black-bar detection.** About every 500 ms, render the frame to a tiny offscreen buffer (around 64x36), read the pixels back, and find where the dark rows and columns start and end. Compare that to the presets' rectangles. Never run this on the decoder's output thread.
- **Settings.** One settings store backed by Jetpack DataStore. Upstream's ADB interface should read and write the same store, so both keep working.
- **Audio.** Apply our volume to the audio player. Find where UxPlay passes on the iPhone's volume messages (its volume callback) and skip it when "ignore iPhone volume" is on.
- **Discovery.** Hold a Wi-Fi multicast lock while listening (needs the `CHANGE_WIFI_MULTICAST_STATE` permission) so the iPhone can find the Quest on the network.
- **Theater mode.** Build it with the Meta Spatial SDK as a hybrid app, with a panel screen and an immersive screen in one APK, which switch by launching each other. The theater screen is a panel or mesh in the 3D scene, fed by the same renderer.
- **Quest tooling.** Meta publishes a `hz-android-2d-porting` skill for AI coding tools, which you get by setting up the Meta VR CLI. Use it for the Quest manifest and setup work in R1.

## Milestones and build order

Build in this order. Each milestone ends with an APK installed on the Quest and tested against Delta before the next one starts.

1. **M0, baseline.** Fork, build and sideload upstream unchanged. Confirm Delta mirrors to the Quest with sound. *Gate: if upstream doesn't work on the Quest, fix that before anything else.*
2. **M1, fit the Quest.** Quest product flavor, new applicationId, panel settings (R1), quality-of-life basics (R3), and a first in-headset settings screen (R2).
3. **M2, volume.** R4. AIpin's worst problem gets fixed early.
4. **M3, rendering rework.** Move the session into the foreground service, decode to a GPU texture, add the OpenGL renderer, presets loaded from data, and manual preset selection (R5). Measure the real crop rectangles from Delta streams for every system.
5. **M4, speed and auto-detection.** Low-latency game mode (R7), the delay readout (R9) and automatic preset detection (R6). Measure delay against AIpin.
6. **M5, shader.** CRT/scanline shader (R8).
7. **M6, theater mode.** R10, using the Spatial SDK hybrid app pattern.
8. **M7, stretch goals.** S1 split DS screens, then S4 Pokémon battle helper, then S3.

## Testing and acceptance

All testing happens on real hardware: the Quest, the iPhone running Delta and the home Wi-Fi. There is no emulator for the AirPlay path.

- **System test list.** For each preset system, run one game and check the crop (no black bars, no stretching), sound, and that auto-detection picks the right preset. Include one DS game with each DS skin.
- **Delay measurement.** Film the iPhone screen and the view through one Quest lens together with a 240 fps slow-motion camera while pressing a controller button. Count the frames between the button press showing on the phone and on the Quest. Take 5 readings each for AIpin, game mode off, and game mode on.
- **Stability.** A 30-minute play session with no disconnects and no sound drifting out of sync. Recovery after the Quest sleeps and wakes. Recovery after the iPhone locks.
- **Edge cases.** Fades to black and dark scenes don't change the preset. Rotating the iPhone mid-stream recovers. Switching panel to theater and back 10 times keeps the stream alive.
- **Unit tests.** Black-bar detection and preset matching are pure functions. Test them against recorded frame samples so they can be checked on a computer without the headset.

## Risks and open questions

| Risk | Likelihood | Mitigation |
| --- | --- | --- |
| Upstream has never been tested on Quest; its README doesn't mention Horizon OS | Medium | M0 checks this first, before any other work. |
| AirPlay mirroring has a delay of its own that we can't remove, so game mode may not make a noticeable difference | Medium | Measure in M4. If it's still too slow, revisit native emulation (decision record). |
| An iOS update changes AirPlay and breaks UxPlay | Low to medium | Track upstream UxPlay and the fork's upstream; merge their fixes quickly. |
| Horizon OS pauses or kills the session while switching between panel and theater | Medium | Foreground service; test early in M6. |
| Decoding to a GPU texture adds delay compared with drawing straight to the screen | Low | Compare in M3 using the debug overlay. |

**Answered questions**

- Headset: **Quest 3** (answered 2026-11-11). Set `com.oculus.supportedDevices` to `quest3`.
- DS layout: Delta currently sends **both DS screens** over AirPlay (answered 2026-11-11). The default DS preset is "both screens stacked", and move effectiveness in S4 works on DS without changes.
- Repo: **private** (answered 2026-11-11). GitHub doesn't allow private forks of public repos (recalled), so create a new private repo and push the upstream clone to it, keeping `upstream` as a remote.

## Kickoff prompt for Claude Code

A true one-shot isn't possible, because every milestone needs a real test with the headset on and the iPhone mirroring. With the Quest on wireless ADB, though, Claude Code can build, install and check the logs by itself, and only stop when it needs you.

1. **Create the private repo:**

```bash
git clone --recurse-submodules https://github.com/jqssun/android-airplay-server.git quest-airplay
cd quest-airplay
git remote rename origin upstream
gh repo create quest-airplay --private --source=. --remote=origin --push
```

2. **Add the two files:** save this doc, exported as Markdown, as `docs/REQUIREMENTS.md`, and the CLAUDE.md below in the repo's top folder. Commit and push.
3. **Pair the Quest** for wireless ADB (see the install workflow above) and note its IP address and port.
4. **Start Claude Code:** run `claude` in the repo folder and paste the kickoff prompt below, with the IP and port filled in. Choose "always allow" for gradle and adb commands so it doesn't stop to ask about each one.

**CLAUDE.md**

```markdown
# Quest AirPlay Receiver

Fork of jqssun/android-airplay-server for Meta Quest 3. Full spec: docs/REQUIREMENTS.md.

## Rules
- Verify Android, Media3, Meta Spatial SDK and Horizon OS APIs against current docs before using them. Do not rely on memory for versions or method names.
- Keep upstream files lightly modified. New code goes in new packages so merging upstream's default branch stays easy.
- Presets and Pokémon game profiles live in JSON assets, not code.
- Black-bar detection, preset matching and type-chart logic are pure functions with unit tests.
- Explain why before editing native or JNI code.
- Sign every build with the keystore at ~/.android/quest-airplay.jks. Never commit it, never switch keys.
- Install with `adb install -r`. Never uninstall the app (it wipes settings).

## Workflow
- Work milestone by milestone, in the order in docs/REQUIREMENTS.md. Commit and push after each one.
- After each milestone: build, install on the Quest, launch, and check `adb logcat` for crashes. Then give me a numbered test checklist and wait for my results.
- Stop and ask only when a step needs me physically (headset on, iPhone mirroring) or needs a decision the spec doesn't cover.
```

**Kickoff prompt**

```text
Read CLAUDE.md and docs/REQUIREMENTS.md fully before doing anything.

Build the Quest AirPlay receiver through milestones M0 to M6. Stretch goals (S1, S3, S4) are out of scope for this session.

The Quest 3 is on wireless ADB at <QUEST_IP>:<PORT>.

Start with M0: check the toolchain against .github/workflows/apk.yml (JDK, Android SDK, NDK, CMake) and install what's missing, create the signing keystore, build upstream unchanged, install it on the Quest, and tell me when to try mirroring Delta. M0 is a hard gate: if upstream doesn't work on the Quest, diagnose it with me before continuing.
```

## Sources

Checked 2026-11-10.

- [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server)
- [UxPlay](https://github.com/FDH2/UxPlay)
- [Meta: Panel sizing](https://developers.meta.com/horizon/documentation/android-apps/panel-sizing) (updated Dec 9, 2025)
- [Meta: Horizon OS panel sizing](https://developers.meta.com/vr/essentials/horizon-os-panel-sizing) (updated Apr 6, 2026)
- [Meta: Port an existing app](https://developers.meta.com/vr/documentation/android-apps/port-an-existing-app)
- [Meta: Hybrid apps overview](https://developers.meta.com/horizon/documentation/spatial-sdk/hybrid-apps-overview)
- [Delta FAQ: Delta Sync](https://faq.deltaemulator.com/using-delta/delta-sync)
- [Delta issue #533: Dropbox save visibility](https://github.com/rileytestut/Delta/issues/533)
- [Delta issue #429: exporting saves](https://github.com/rileytestut/Delta/issues/429)
- [PatchVault: save formats across emulators](https://patchvault.pages.dev/guides/how-to-transfer-save-files)
- [Retro Handhelds: RetroArch on Quest 3](https://retrohandhelds.gg/?p=9874)
- [Digital Trends: Delta 2.0](https://www.digitaltrends.com/?p=6018026) (Oct 2026)
