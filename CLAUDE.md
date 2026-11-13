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

## Public repo: never commit sensitive data
This repo is public. Anything committed or pushed, including in a PR, stays visible permanently: GitHub keeps PR commits even after a force-push.
- Commit only as `snowcivilian <32252381+snowcivilian@users.noreply.github.com>`, never with a personal email.
- `.githooks/pre-commit` enforces the rules below. Before the first commit in a clone or worktree, check that `git config core.hooksPath` is `.githooks`, and set it if not. Never use `--no-verify`. Never edit the hook or `.git/info/sensitive-patterns` to get a commit through.
- Never commit keystores or keys (`*.jks`, `*.keystore`, `*.p12`, `*.pem`), `local.properties`, `.env` files, passwords, API keys, tokens, APKs or AABs, logcat dumps, or bug reports.
- Keep personal info out of code, docs, comments, test data and commit messages. That includes real names, emails, home paths (`/Users/...`), LAN IPs, device serials, Wi-Fi names and locations. Use placeholders such as `<quest-ip>`, `$QUEST_IP` or `/Users/<you>`. The Quest's IP belongs in `questIp=` in `local.properties`.
- Don't commit screenshots, headset captures, game frames, ROMs or other game assets. Build test fixtures synthetically inside the test from measured values. If you need a real capture to measure something, keep it outside the repo.
- Before adding third-party code or data, check its license. Add it to `THIRD_PARTY_NOTICES.md` and ship its license file with it.
- Before every push or PR, run `gitleaks git --redact -v .` and read `git diff origin/main...HEAD` for anything above. If something sensitive was already committed, stop and tell me before pushing. Don't push it and plan to fix it later.
- You can show me logcat output, IPs and device details in chat, but never write them to files in the repo.

## Workflow
- Work milestone by milestone, in the order in docs/REQUIREMENTS.md. Commit and push after each one.
- After each milestone: build, install on the Quest, launch, and check `adb logcat` for crashes. Then give me a numbered test checklist and wait for my results.
- Stop and ask only when a step needs me physically (headset on, iPhone mirroring) or needs a decision the spec doesn't cover.
