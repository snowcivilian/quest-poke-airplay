#!/usr/bin/env bash
# Build the quest flavor (arm64 only), install it on the Quest over adb with -r (never uninstall),
# launch it, and print any crash. Usage: scripts/quest-install.sh [release|debug] [quest-ip]
# Repo path must not contain spaces (FFmpeg's configure rejects them).
# After a Quest reboot, Wi-Fi adb is off: plug in USB once and run `adb tcpip 5555`.
set -euo pipefail
cd "$(dirname "$0")/.."
# Quest IP: argument, else $QUEST_IP, else `questIp=` in the gitignored local.properties.
TYPE="${1:-release}"; IP="${2:-${QUEST_IP:-$(sed -n 's/^questIp=//p' local.properties 2>/dev/null)}}"
[[ -n "$IP" ]] || { echo "Set the Quest's IP: pass it, export QUEST_IP, or add questIp=<ip> to local.properties" >&2; exit 1; }
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_SERIAL="$IP:5555"
PKG=io.github.snowcivilian.questairplay
CAP="$(tr '[:lower:]' '[:upper:]' <<< "${TYPE:0:1}")${TYPE:1}"

./gradlew "assembleQuest$CAP" -Pabis=arm64-v8a --console=plain -q
adb connect "$ANDROID_SERIAL" >/dev/null
adb install -r "app/build/outputs/apk/quest/$TYPE/app-quest-$TYPE.apk"
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -n "$PKG/io.github.jqssun.airplay.MainActivity" >/dev/null
sleep 8
PID="$(adb shell pidof "$PKG" || true)"
echo "pid: ${PID:-NOT RUNNING}"
adb logcat -d -b crash
[ -n "$PID" ] && adb logcat -d --pid="$PID" | grep -E "AirPlayService|NsdServiceManager| E " || true
