#!/usr/bin/env bash
# Smoke test against a running emulator (see .github/workflows/workout-cycle-apk.yml).
# For each previous release: install it, complete a set, upgrade in place to the new build and
# check the progress survived (running every database migration). Then tap through the new
# build's main features, saving screenshots as it goes.
set -eu

USAGE="usage: smoke-test.sh <new apk> <screenshot dir> <previous apk>..."
NEW_APK="${1:?$USAGE}"
SHOTS="${2:?$USAGE}"
shift 2
[ "$#" -gt 0 ] || { echo "$USAGE"; exit 2; }
PACKAGE="com.pavneet.workoutcycle"
mkdir -p "$SHOTS"

fail() {
  echo "::error::Smoke test failed: $1"
  adb exec-out screencap -p > "$SHOTS/failure.png" || true
  echo "--- on screen ---"
  adb shell uiautomator dump /sdcard/window.xml 2>&1 | tail -n 1 || true
  adb shell cat /sdcard/window.xml 2>/dev/null | grep -o '\(text\|content-desc\)="[^"]\+"' | head -n 80 || true
  echo "--- notifications ---"
  adb shell dumpsys notification --noredact | grep -E "android\.(title|text)=" || true
  echo "--- crash log ---"
  adb logcat -d -b crash || true
  exit 1
}

# Prints the current screen's accessibility tree as XML.
screen() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/window.xml 2>/dev/null || true
}

# Waits up to $3 seconds (default 30) for a node whose attribute $1 contains $2.
wait_for() {
  for _ in $(seq 1 "${3:-30}"); do
    if screen | grep -q "$1=\"[^\"]*$2"; then return 0; fi
    sleep 1
  done
  return 1
}
wait_for_text() { wait_for text "$1"; }
wait_for_desc() { wait_for content-desc "$1"; }

# Waits for a node whose text is exactly $1.
wait_for_exact() {
  for _ in $(seq 1 30); do
    if screen | grep -q "text=\"$1\""; then return 0; fi
    sleep 1
  done
  return 1
}

# Taps the centre of the first node matching an attribute, e.g. 'text="Done"'. Case-insensitive.
tap() {
  local bounds
  bounds=$(screen | grep -io "$1[^>]*bounds=\"[^\"]*\"" | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -n 1)
  [ -n "$bounds" ] || fail "nothing on screen matches $1"
  set -- $(echo "$bounds" | tr -c '0-9' ' ')
  adb shell input tap $((($1 + $3) / 2)) $((($2 + $4) / 2))
  sleep 1
}

# Waits up to $2 seconds for a posted notification whose title or text contains $1.
wait_for_notification() {
  for _ in $(seq 1 "${2:-30}"); do
    if adb shell dumpsys notification --noredact | grep -E "android\.(title|text)=" | grep -q "$1"; then return 0; fi
    sleep 1
  done
  return 1
}

screenshot() { adb exec-out screencap -p > "$SHOTS/$1.png"; }
launch() { adb shell am start -W -n "$PACKAGE/.MainActivity" >/dev/null; }

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true

for BASELINE_APK in "$@"; do
  echo "== $BASELINE_APK: complete one set, then upgrade in place"
  adb uninstall "$PACKAGE" >/dev/null 2>&1 || true
  adb install "$BASELINE_APK"
  launch
  wait_for_text "Push-ups" || fail "$BASELINE_APK did not start"
  tap 'text="Done"'
  wait_for_text "2 of 5" || fail "$BASELINE_APK did not advance"

  adb shell am force-stop "$PACKAGE"
  adb install -r "$NEW_APK"
  # The new build asks for this on first launch; grant it up front so no dialog appears.
  adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS
  adb logcat -c
  launch
  wait_for_text "2 of 5" || fail "progress was lost, or the app crashed, upgrading from $BASELINE_APK"
  wait_for_text "Back stretches" || fail "the current exercise changed upgrading from $BASELINE_APK"
  wait_for_text "Cable row" || fail "no cable animation after upgrading from $BASELINE_APK"
done
screenshot 1-active

echo "== Log weight and reps, then rest"
wait_for_text "First time" || fail "no first-time hint for an exercise never logged"
wait_for_exact "None" || fail "an exercise never logged does not show None for weight and reps"
tap 'content-desc="Increase weight"'
tap 'content-desc="Increase weight"'
tap 'content-desc="Increase weight"'
tap 'content-desc="Increase reps"'
wait_for_exact "15" || fail "the weight stepper did not reach 15"
wait_for_exact "10" || fail "the reps stepper did not start at 10"
tap 'text="Done"'
wait_for_text "Skip rest" || fail "no rest timer after Done"
wait_for_text "3 of 5" || fail "Done did not advance to Shoulders"
wait_for_text "Get ready for Shoulders" || fail "the rest panel does not name the exercise after the rest"
screenshot 2-rest
wait_for_notification "Resting" || fail "the notification does not show the rest"
tap 'text="Skip rest"'
wait_for_exact "Done" || fail "Skip rest did not bring back the Done button"

echo "== Save the machine setup for Shoulders"
tap 'content-desc="Manage exercises"'
wait_for_text "Drag the handle" || fail "the Manage exercises screen never appeared"
tap 'text="Shoulders"'
wait_for_text "Edit Shoulders" || fail "the exercise editor never opened"
tap 'text="Handle"'
tap 'text="Pulley position"'
adb shell input text "Notch%s3"
adb shell input keyevent KEYCODE_BACK # hide the keyboard
sleep 1
screenshot 3-editor
tap 'text="Save"'
wait_for_text "Handle · Notch 3" || fail "the setup was not saved"
adb shell input keyevent KEYCODE_BACK
wait_for_text "Handle · Notch 3" || fail "the workout screen does not show the setup"
screenshot 4-active-setup

echo "== Tap Done in the notification, as a paired watch would"
wait_for_notification "Now: Shoulders" || fail "the notification does not show the current exercise"
adb shell cmd statusbar expand-notifications
sleep 2
screenshot 5-notification
tap 'text="Done"'
adb shell cmd statusbar collapse
wait_for_text "4 of 5" || fail "Done in the notification did not complete the set"
wait_for_text "Skip rest" || fail "Done in the notification did not start a rest"

tap 'text="Skip rest"'
wait_for_exact "Done" || fail "Skip rest did not bring back the Done button"

echo "== History"
tap 'content-desc="History"'
wait_for_text "Day streak" || fail "the History screen never appeared"
wait_for_text "Training days" || fail "no training calendar"
wait_for_text "Back stretches" || fail "History does not list the logged exercise"
screenshot 6-history
adb shell input keyevent KEYCODE_BACK
wait_for_exact "Done" || fail "could not get back to the workout from History"

# Last, because the rest-over alert drops down over the top of the screen.
echo "== Shorten the rest in Settings and wait for the rest-over alert"
tap 'content-desc="More options"'
tap 'text="Settings"'
wait_for_text "Rest between sets" || fail "the Settings screen never appeared"
tap 'text="30 s"'
screenshot 7-settings
adb shell input keyevent KEYCODE_BACK
wait_for_exact "Done" || fail "no Done button after Settings"
tap 'text="Done"'
wait_for_text "Skip rest" || fail "no rest after the third set"
wait_for_notification "Rest over" 45 || fail "no rest-over alert after a 30 s rest"
wait_for_exact "Done" || fail "the Done button did not come back after the rest"

adb shell pidof "$PACKAGE" >/dev/null || fail "the app is no longer running"
echo "Smoke test passed: upgrades kept progress; logging, rest, setup, notification Done, history and the rest alert work."
