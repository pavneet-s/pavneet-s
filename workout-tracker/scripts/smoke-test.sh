#!/usr/bin/env bash
# Smoke test against a running emulator (see .github/workflows/workout-cycle-smoke-test.yml).
# Installs the previous release and makes progress, upgrades in place to the new build (which
# runs any database migration), then checks the core loop, the cable animations and the picker.
set -eu

USAGE="usage: smoke-test.sh <previous apk> <new apk> [screenshot dir]"
BASELINE_APK="${1:?$USAGE}"
NEW_APK="${2:?$USAGE}"
SHOTS="${3:-screenshots}"
PACKAGE="com.pavneet.workoutcycle"
mkdir -p "$SHOTS"

fail() {
  echo "::error::Smoke test failed: $1"
  adb exec-out screencap -p > "$SHOTS/failure.png" || true
  echo "--- crash log ---"
  adb logcat -d -b crash || true
  exit 1
}

# Prints the current screen's accessibility tree as XML.
screen() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/window.xml 2>/dev/null || true
}

# Waits up to 30 seconds for a node whose attribute $1 contains $2.
wait_for() {
  for _ in $(seq 1 30); do
    if screen | grep -q "$1=\"[^\"]*$2"; then return 0; fi
    sleep 1
  done
  return 1
}
wait_for_text() { wait_for text "$1"; }
wait_for_desc() { wait_for content-desc "$1"; }

# Taps the centre of the first node matching an attribute, e.g. 'text="Done"'.
tap() {
  local bounds
  bounds=$(screen | grep -o "$1[^>]*bounds=\"[^\"]*\"" | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -n 1)
  [ -n "$bounds" ] || fail "nothing on screen matches $1"
  set -- $(echo "$bounds" | tr -c '0-9' ' ')
  adb shell input tap $((($1 + $3) / 2)) $((($2 + $4) / 2))
}

screenshot() { adb exec-out screencap -p > "$SHOTS/$1.png"; }
launch() { adb shell am start -W -n "$PACKAGE/.MainActivity" >/dev/null; }

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true

echo "== Previous release: complete one set"
adb install -r "$BASELINE_APK"
launch
wait_for_text "Push-ups" || fail "the previous release did not start"
tap 'text="Done"'
wait_for_text "2 of 5" || fail "the previous release did not advance"

echo "== Upgrade in place, keeping the saved data"
adb shell am force-stop "$PACKAGE"
adb install -r "$NEW_APK"
adb logcat -c
launch
wait_for_text "2 of 5" || fail "progress was lost, or the app crashed, after upgrading"
wait_for_text "Back stretches" || fail "the current exercise changed after upgrading"
wait_for_text "Cable row" || fail "no cable animation caption for Back stretches"
screenshot 1-active-row

tap 'text="Done"'
wait_for_text "3 of 5" || fail "tapping Done did not advance after upgrading"
wait_for_text "Cable lateral raise" || fail "no lateral raise animation for Shoulders"
screenshot 2-active-lateral-raise

echo "== Manage exercises and pick a different animation"
tap 'content-desc="Manage exercises"'
wait_for_text "Drag the handle" || fail "the Manage exercises screen never appeared"
wait_for_text "Biceps" || fail "the exercise list never appeared"
screenshot 3-manage

tap 'content-desc="Animation for Push-ups: Cable chest press"'
wait_for_text "Animation for Push-ups" || fail "the animation picker never opened"
screenshot 4-picker
tap 'text="Face pull'
wait_for_desc "Animation for Push-ups: Face pull" || fail "the new animation choice was not saved"
screenshot 5-manage-after-pick

adb shell pidof "$PACKAGE" >/dev/null || fail "the app is no longer running"
echo "Smoke test passed: upgraded with progress kept, animations shown, picker choice saved."
