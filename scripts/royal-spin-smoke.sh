#!/usr/bin/env bash
set -Eeuo pipefail

APK="${1:?APK path required}"
MODE="${2:-modern}"
EVIDENCE="${3:-evidence}"
PACKAGE="cl.exequiel.royalspin.landscape"
ACTIVITY="$PACKAGE/.LandscapeMainActivity"
CURRENT_STEP="setup"

mkdir -p "$EVIDENCE"

collect_diagnostics() {
  local status=$?
  trap - EXIT
  printf 'step=%s\nexit_code=%s\n' "$CURRENT_STEP" "$status" > "$EVIDENCE/status.txt"
  if command -v adb >/dev/null 2>&1; then
    adb shell wm size > "$EVIDENCE/wm-size.txt" 2>&1 || true
    adb shell wm density > "$EVIDENCE/wm-density.txt" 2>&1 || true
    adb shell dumpsys activity activities > "$EVIDENCE/dumpsys-activity.txt" 2>&1 || true
    adb logcat -d -v threadtime > "$EVIDENCE/logcat.txt" 2>&1 || true
    if [[ $status -ne 0 ]]; then
      adb exec-out screencap -p > "$EVIDENCE/failure-final.png" 2>/dev/null || true
    fi
  fi
  exit "$status"
}
trap collect_diagnostics EXIT

mark() {
  CURRENT_STEP="$1"
  printf '%s\n' "$CURRENT_STEP" | tee -a "$EVIDENCE/progress.txt"
}

mark "wait-for-device"
adb wait-for-device
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 1 || true
adb shell wm size 1280x720 || true
adb shell wm density 240 || true

mark "clean-install"
# Mandatory clean install: remove every prior package/data instance before installing this APK.
adb uninstall "$PACKAGE" >/dev/null 2>&1 || true
adb install "$APK"
adb shell pm path "$PACKAGE" | tee "$EVIDENCE/package-path.txt"
adb logcat -c

alive() {
  local pid
  pid="$(adb shell pidof "$PACKAGE" | tr -d '\r' || true)"
  test -n "$pid"
  echo "$pid" > "$EVIDENCE/pid.txt"
}

validate_png() {
  python3 - "$1" <<'PY'
from pathlib import Path
import struct
import sys
path = Path(sys.argv[1])
data = path.read_bytes()
assert len(data) > 1000, (path.name, len(data))
assert data[:8] == b'\x89PNG\r\n\x1a\n', path.name
width, height = struct.unpack('>II', data[16:24])
assert width >= 640 and height >= 640, (path.name, width, height)
print(f'{path.name}: {width}x{height}, {len(data)} bytes')
PY
}

start_scene() {
  local scene="${1:-}"
  mark "start-scene-${scene:-initial}"
  adb shell am force-stop "$PACKAGE" || true
  if [[ -n "$scene" ]]; then
    adb shell am start -W -n "$ACTIVITY" --es demo "$scene" > "$EVIDENCE/start-$scene.txt"
  else
    adb shell am start -W -n "$ACTIVITY" > "$EVIDENCE/start-initial.txt"
  fi
  sleep 6
  alive
}

capture() {
  local name="$1"
  mark "capture-$name"
  adb exec-out screencap -p > "$EVIDENCE/$name.png"
  validate_png "$EVIDENCE/$name.png"
}

tap_spin() {
  mark "tap-spin"
  if [[ "$MODE" == "modern" ]]; then
    adb shell input tap 1147 475
  else
    adb shell input tap 1128 470
  fi
}

spin_and_capture() {
  local scene="$1"
  local final_name="$2"
  start_scene "$scene"
  tap_spin
  sleep 0.75
  capture "${scene}-speed"
  sleep 2.65
  capture "${scene}-last-reel"
  sleep 2.2
  alive
  capture "$final_name"
}

record_scene() {
  local scene="$1"
  local remote="$2"
  local local_name="$3"
  local seconds="$4"
  start_scene "$scene"
  adb shell rm -f "$remote"
  mark "record-$local_name"
  # Keep adb attached to screenrecord so the MP4 is finalized before it is pulled.
  adb shell screenrecord --bit-rate 6000000 --time-limit "$seconds" "$remote" \
    > "$EVIDENCE/${local_name%.mp4}-screenrecord.txt" 2>&1 &
  local recorder_pid=$!
  sleep 1
  tap_spin
  wait "$recorder_pid" || true
  adb pull "$remote" "$EVIDENCE/$local_name"
  python3 - "$EVIDENCE/$local_name" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
data = path.read_bytes()
assert len(data) > 5000, (path.name, len(data))
assert b'ftyp' in data[:64], path.name
print(f'{path.name}: {len(data)} bytes')
PY
  alive
}

if [[ "$MODE" == "modern" ]]; then
  start_scene ""
  capture "initial"
  # Mandatory 15-second cold-start liveness window (6 seconds in start_scene + 9 here).
  mark "cold-start-liveness"
  sleep 9
  alive

  tap_spin
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

  record_scene "anticipation" "/sdcard/royal-spin.mp4" "royal-spin.mp4" 9
  record_scene "royal" "/sdcard/royal-win.mp4" "royal-win.mp4" 10
else
  mark "start-fallback"
  adb shell am force-stop "$PACKAGE" || true
  adb shell am start -W -n "$ACTIVITY" --ez force_fallback true > "$EVIDENCE/start-fallback.txt"
  sleep 6
  alive
  capture "initial"
  cp "$EVIDENCE/initial.png" "$EVIDENCE/fallback-initial.png"
  # Keep the API 24 fallback process alive for the same 15-second cold-start window.
  mark "fallback-liveness"
  sleep 9
  alive
  tap_spin
  sleep 0.7
  capture "spin-speed"
  sleep 4.8
  alive
  capture "spin-complete"
  cp "$EVIDENCE/spin-complete.png" "$EVIDENCE/fallback-after-spin.png"
fi

mark "crash-scan"
adb shell dumpsys activity activities > "$EVIDENCE/dumpsys-activity.txt"
adb logcat -d -v threadtime > "$EVIDENCE/logcat.txt"

# Scope crash signatures to the Royal Spin package or renderer process associated with it.
if grep -E "FATAL EXCEPTION|ANR in $PACKAGE|OutOfMemoryError|Renderer process crash" "$EVIDENCE/logcat.txt" | grep -E "$PACKAGE|cr_WebView|chromium|AndroidRuntime"; then
  echo "Crash signature found in logcat" >&2
  exit 1
fi

mark "media-validation"
python3 - "$EVIDENCE" "$MODE" <<'PY'
from pathlib import Path
import hashlib
import struct
import sys
root = Path(sys.argv[1])
mode = sys.argv[2]
required = ['initial.png', 'spin-speed.png', 'spin-complete.png']
if mode == 'modern':
    required += ['anticipation.png', 'normal-win.png', 'big-win.png',
                 'royal-win.png', 'free-spins.png', 'royal-spin.mp4', 'royal-win.mp4']
else:
    required += ['fallback-initial.png', 'fallback-after-spin.png']
for name in required:
    path = root / name
    assert path.exists(), name
    data = path.read_bytes()
    if path.suffix == '.png':
        assert len(data) > 1000, (name, len(data))
        assert data[:8] == b'\x89PNG\r\n\x1a\n', name
        width, height = struct.unpack('>II', data[16:24])
        assert width >= 640 and height >= 640, (name, width, height)
    else:
        assert len(data) > 5000, (name, len(data))
        assert b'ftyp' in data[:64], name
# A real touch-driven spin must visibly change the frame and then settle to another frame.
def digest(name):
    return hashlib.sha256((root / name).read_bytes()).hexdigest()
assert digest('initial.png') != digest('spin-speed.png')
assert digest('spin-speed.png') != digest('spin-complete.png')
print('media validation passed')
PY

mark "complete"
