# Testing status

All testing is on real hardware: Quest 3, an iPhone running Delta, and home Wi-Fi. Unit tests cover the pure logic and run with `./gradlew testQuestDebugUnitTest`.

## Confirmed on the headset

- Mirroring with sound; panel launches, auto-starts, shows *Waiting for iPhone*, receiver name **Quest** (R1, R3)
- In-headset settings, including the simplified layout and Logs section (R2)
- Volume slider, mute, ignore iPhone volume, persistence (R4)
- Presets and auto-detection in general use (R5, R6)
- Theater mode, with a movable, resizable and minimizable controls panel (R10)

## Still to check against the spec

1. **Every system's crop (R5).** One game per system: no black bars, no stretching. Include both DS layouts. If a preset is off, note the stats overlay's *Detected* value and update `presets.json`.
2. **Auto-detect timing (R6).** GBA game to SNES game resizes the panel within about 2 seconds. A fade to black never changes the preset.
3. **Delay (R7, R9).** Film the iPhone and one Quest lens together at 240 fps while pressing a controller button. Count frames between the press showing on the phone and on the Quest. Five readings each: AIpin, game mode off, game mode on. Note the overlay's decode-to-display numbers too.
4. **Scanlines (R8).** FPS in the overlay stays at the incoming rate with scanlines on.
5. **Stability.**
   - A 30-minute session with no disconnects and no audio drift.
   - Recovers after the Quest sleeps and wakes, and after the iPhone locks.
   - Rotating the iPhone mid-stream recovers.
   - Switching panel ↔ theater 10 times keeps the stream alive.

## Known issues

- **Screenshots of the 2D panel:** Quest screenshots leave out the Quest AirPlay window in panel/passthrough mode (theater screenshots work). The window isn't marked secure. Next step: compare against the `generic` flavor to find which change causes it.
- **Panel aspect lock:** keeping the panel's shape while resizing relies on undocumented `com.oculus.vrshell.*` manifest flags.
- **Theater screen:** adjusted with sliders only; the screen itself can't be grabbed yet.
