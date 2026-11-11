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
