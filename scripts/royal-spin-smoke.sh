#!/usr/bin/env bash
set -euo pipefail

APK="${1:?APK path required}"
MODE="${2:-modern}"
EVIDENCE="${3:-evidence}"
PACKAGE="cl.exequiel.royalspin.landscape"
ACTIVITY="$PACKAGE/.LandscapeMainActivity"

mkdir -p "$EVIDENCE"
adb wait-for-device
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 1 || true
adb shell wm size 1280x720 || true
adb shell wm density 240 || true
adb install -r "$APK"
adb shell pm path "$PACKAGE" | tee "$EVIDENCE/package-path.txt"
adb logcat -c

alive() {
  local pid
  pid="$(adb shell pidof "$PACKAGE" | tr -d '\r' || true)"
  test -n "$pid"
  echo "$pid" > "$EVIDENCE/pid.txt"
}

start_scene() {
  local scene="${1:-}"
  adb shell am force-stop "$PACKAGE" || true
  if [[ -n "$scene" ]]; then
    adb shell am start -W -n "$ACTIVITY" --es demo "$scene" > "$EVIDENCE/start-$scene.txt"
  else
    adb shell am start -W -n "$ACTIVITY" > "$EVIDENCE/start-initial.txt"
  fi
  sleep 5
  alive
}

capture() {
  local name="$1"
  adb exec-out screencap -p > "$EVIDENCE/$name.png"
  test "$(stat -c%s "$EVIDENCE/$name.png")" -gt 6000
}

spin_and_capture() {
  local scene="$1"
  local final_name="$2"
  start_scene "$scene"
  adb shell input tap 1147 475
  sleep 0.75
  capture "${scene}-speed"
  sleep 2.65
  capture "${scene}-last-reel"
  sleep 2.2
  alive
  capture "$final_name"
}

if [[ "$MODE" == "modern" ]]; then
  start_scene ""
  capture "initial"
  # Mandatory 15-second cold-start liveness window (5 seconds in start_scene + 10 here).
  sleep 10
  alive

  adb shell input tap 1147 475
  sleep 0.7
  capture "spin-speed"
  sleep 4.8
  alive
  capture "spin-complete"

  spin_and_capture "anticipation" "anticipation"
  spin_and_capture "normal" "normal-win"
  spin_and_capture "big" "big-win"
  spin_and_capture "royal" "royal-win"
  spin_and_capture "free" "free-spins"

  # Record a real touch-driven spin video.
  start_scene "anticipation"
  adb shell rm -f /sdcard/royal-spin.mp4
  adb shell 'screenrecord --bit-rate 6000000 --time-limit 9 /sdcard/royal-spin.mp4 >/dev/null 2>&1 &' || true
  sleep 1
  adb shell input tap 1147 475
  sleep 10
  adb pull /sdcard/royal-spin.mp4 "$EVIDENCE/royal-spin.mp4"
  test "$(stat -c%s "$EVIDENCE/royal-spin.mp4")" -gt 30000
  alive

  # Record a deterministic large-prize sequence.
  start_scene "royal"
  adb shell rm -f /sdcard/royal-win.mp4
  adb shell 'screenrecord --bit-rate 6000000 --time-limit 10 /sdcard/royal-win.mp4 >/dev/null 2>&1 &' || true
  sleep 1
  adb shell input tap 1147 475
  sleep 11
  adb pull /sdcard/royal-win.mp4 "$EVIDENCE/royal-win.mp4"
  test "$(stat -c%s "$EVIDENCE/royal-win.mp4")" -gt 30000
  alive
else
  adb shell am force-stop "$PACKAGE" || true
  adb shell am start -W -n "$ACTIVITY" --ez force_fallback true > "$EVIDENCE/start-fallback.txt"
  sleep 5
  alive
  capture "initial"
  cp "$EVIDENCE/initial.png" "$EVIDENCE/fallback-initial.png"
  # Keep the API 24 fallback process alive for the same 15-second cold-start window.
  sleep 10
  alive
  adb shell input tap 1128 470 || true
  sleep 0.7
  capture "spin-speed"
  sleep 4.8
  alive
  capture "spin-complete"
  cp "$EVIDENCE/spin-complete.png" "$EVIDENCE/fallback-after-spin.png"
fi

adb shell dumpsys activity activities > "$EVIDENCE/dumpsys-activity.txt"
adb logcat -d -v threadtime > "$EVIDENCE/logcat.txt"

# Scope crash signatures to the Royal Spin package or renderer process associated with it.
if grep -E "FATAL EXCEPTION|ANR in $PACKAGE|OutOfMemoryError|Renderer process crash" "$EVIDENCE/logcat.txt" | grep -E "$PACKAGE|cr_WebView|chromium|AndroidRuntime"; then
  echo "Crash signature found in logcat" >&2
  exit 1
fi

python3 - "$EVIDENCE" <<'PY'
from pathlib import Path
import hashlib
import sys
root = Path(sys.argv[1])
mode = 'modern' if (root / 'royal-spin.mp4').exists() else 'legacy'
required = ['initial.png', 'spin-speed.png', 'spin-complete.png']
if mode == 'modern':
    required += ['anticipation.png', 'normal-win.png', 'big-win.png',
                 'royal-win.png', 'free-spins.png', 'royal-spin.mp4', 'royal-win.mp4']
else:
    required += ['fallback-initial.png', 'fallback-after-spin.png']
for name in required:
    path = root / name
    assert path.exists(), name
    assert path.stat().st_size > (30000 if path.suffix == '.mp4' else 6000), (name, path.stat().st_size)
# A real spin must visibly change the frame.
def digest(name):
    return hashlib.sha256((root / name).read_bytes()).hexdigest()
assert digest('initial.png') != digest('spin-speed.png')
assert digest('spin-speed.png') != digest('spin-complete.png')
print('media validation passed')
PY
