#!/usr/bin/env bash
# Smoke test against a running emulator (see .github/workflows/workout-cycle-smoke-test.yml):
# installs the app, checks the default cycle appears, taps Done, then opens Manage exercises.
set -eu

APK="${1:?usage: smoke-test.sh <path to apk>}"
PACKAGE="com.pavneet.workoutcycle"

fail() {
  echo "::error::Smoke test failed: $1"
  echo "--- crash log ---"
  adb logcat -d -b crash || true
  exit 1
}

# Prints the current screen's accessibility tree as XML.
screen() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/window.xml 2>/dev/null || true
}

# Waits up to 30 seconds for a node whose text contains $1.
wait_for_text() {
  for _ in $(seq 1 30); do
    if screen | grep -q "text=\"[^\"]*$1"; then return 0; fi
    sleep 1
  done
  return 1
}

# Taps the centre of the first node matching an attribute, e.g. 'text="Done"'.
tap() {
  local bounds
  bounds=$(screen | grep -o "$1[^>]*bounds=\"[^\"]*\"" | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -n 1)
  [ -n "$bounds" ] || fail "nothing on screen matches $1"
  set -- $(echo "$bounds" | tr -c '0-9' ' ')
  adb shell input tap $((($1 + $3) / 2)) $((($2 + $4) / 2))
}

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PACKAGE/.MainActivity"

wait_for_text "Push-ups" || fail "the first exercise never appeared"
wait_for_text "1 of 5" || fail "the round progress never appeared"

tap 'text="Done"'
wait_for_text "2 of 5" || fail "tapping Done did not advance to the next exercise"
wait_for_text "Shoulders" || fail "Up next did not move on to Shoulders"

tap 'content-desc="Manage exercises"'
wait_for_text "Drag the handle" || fail "the Manage exercises screen never appeared"
wait_for_text "Biceps" || fail "the exercise list never appeared"

adb shell pidof "$PACKAGE" >/dev/null || fail "the app is no longer running"
echo "Smoke test passed: launched, advanced Push-ups -> Back stretches, opened Manage exercises."
