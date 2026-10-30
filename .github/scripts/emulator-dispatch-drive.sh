#!/usr/bin/env bash
# Driver for .github/workflows/emulator-dispatch.yml. Runs on the runner while the emulator is up.
#
# Installs and launches ExampleApp, runs the caller's adb script, waits for the session part to end and its payload to
# be delivered, then writes summary.json and the logcat files to $EMULATOR_DISPATCH_OUT. A failed install, launch or
# caller script fails the run, but the artifact is collected either way. An undelivered payload is recorded, not a
# failure: a dead data URL is a legitimate dry run.
#
# Environment: EMULATOR_DISPATCH_OUT, ADB_SCRIPT_FILE, APP_PACKAGE, APP_ACTIVITY, GITHUB_WORKSPACE.
set -uo pipefail

out="$EMULATOR_DISPATCH_OUT"
apk="$GITHUB_WORKSPACE/examples/ExampleApp/app/build/outputs/apk/debug/app-debug.apk"
mkdir -p "$out"
status=0
warnings=()

# Lists the files the SDK has stored in the app's private storage (run-as needs a debuggable app).
list_storage() {
  adb exec-out run-as "$APP_PACKAGE" find . -type f -path '*embrace*' 2>> "$out/run-as.err"
}

# Records every stored file name ever seen, so a payload delivered between two polls is still known to have existed.
watch_storage() {
  local -A seen=()
  local listing file
  while true; do
    if listing=$(list_storage); then
      while IFS= read -r file; do
        if [ -n "$file" ] && [ -z "${seen[$file]+x}" ]; then
          seen[$file]=1
          printf '%s\n' "$file" >> "$out/storage-seen.txt"
        fi
      done <<< "$listing"
    fi
    sleep 2
  done
}

lines_to_json() { jq -R -s 'split("\n") | map(select(length > 0))' "$1"; }

adb logcat -G 16M || true
adb logcat -c || true
adb devices -l > "$out/adb-devices.txt"
serial=$(adb get-serialno | tr -d '\r')
sdk_int=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
: > "$out/storage-seen.txt"

if ! adb install -r -t "$apk" > "$out/adb-install.log" 2>&1; then
  cat "$out/adb-install.log"
  warnings+=("adb install failed")
  status=1
fi

watcher=""
if [ "$status" -eq 0 ]; then
  adb shell am start -W -n "$APP_PACKAGE/$APP_ACTIVITY" > "$out/am-start.log" 2>&1
  cat "$out/am-start.log"
  if grep -q '^Error' "$out/am-start.log"; then
    warnings+=("am start reported an error")
    status=1
  fi
  watch_storage &
  watcher=$!
  # Give Embrace.start() and the first session part time to begin before the script runs.
  sleep 8
  if ! adb shell pidof "$APP_PACKAGE" > /dev/null; then
    warnings+=("the app is not running after launch (crashed?)")
    status=1
  fi
fi

script_status=null
if [ "$status" -eq 0 ]; then
  export APP_PACKAGE APP_ACTIVITY EMULATOR_DISPATCH_OUT
  timeout 10m bash -ex -o pipefail "$ADB_SCRIPT_FILE" 2>&1 | tee "$out/adb-script.log"
  script_status=${PIPESTATUS[0]}
  if [ "$script_status" -ne 0 ]; then
    warnings+=("adb_script exited with $script_status")
    status=1
  fi
fi

# Backgrounding ends the session part, which is what makes ExampleApp's exporter log its id. The output is captured
# before it is searched: grep -q in a pipe closes it early and, under pipefail, the pipeline can report 141.
adb shell input keyevent KEYCODE_HOME || true
for _ in $(seq 1 15); do
  exporter_log=$(adb logcat -d -s EmbraceTestApp:I 2> /dev/null) || true
  [[ "$exporter_log" == *'Session Part'* ]] && break
  sleep 2
done
sleep 3

adb logcat -d -v threadtime > "$out/logcat-full.txt"
# Anything past 20 MB is the oldest lines, which are the emulator's boot noise.
if [ "$(stat -c %s "$out/logcat-full.txt")" -gt 20000000 ]; then
  tail -c 20000000 "$out/logcat-full.txt" > "$out/logcat-full.tail" && mv "$out/logcat-full.tail" "$out/logcat-full.txt"
  warnings+=("logcat-full.txt was truncated to its last 20 MB")
fi
# The SDK logs under "[Embrace]" (and "Embrace" for start-up failures); ExampleApp's exporters log the spans and logs
# the SDK produced under their own tags.
adb logcat -d -v threadtime '[Embrace]:V' 'Embrace:V' '*:S' > "$out/logcat-embrace.txt"
adb logcat -d -v threadtime 'EmbraceTestApp:V' 'LogcatLogRecordExporter:V' '*:S' > "$out/logcat-exampleapp.txt"

# Session part ids ExampleApp's LogcatSpanExporter logs when the session part span ends.
grep -ahoE 'emb\.session_part_id=[0-9a-fA-F-]{32,36}' "$out/logcat-exampleapp.txt" | cut -d= -f2 > "$out/session-part-ids.txt"
grep -ahoE 'Session Part [0-9a-fA-F-]{32,36}' "$out/logcat-exampleapp.txt" | awk '{print $3}' >> "$out/session-part-ids.txt"
awk '!seen[$0]++' "$out/session-part-ids.txt" > "$out/session-part-ids.tmp" && mv "$out/session-part-ids.tmp" "$out/session-part-ids.txt"
grep -ahoE 'emb\.user_session_id=[0-9a-fA-F-]{32,36}' "$out/logcat-exampleapp.txt" | cut -d= -f2 | awk '!seen[$0]++' > "$out/user-session-ids.txt"
[ -s "$out/session-part-ids.txt" ] || warnings+=("no session part id in logcat (the session part may not have ended)")

# Wait for the SDK to deliver what it stored for those session parts: a delivered payload file is deleted. Killing
# the emulator earlier could cut an upload short.
mapfile -t part_ids < "$out/session-part-ids.txt"
payload_delivered=false
if [ "${#part_ids[@]}" -gt 0 ]; then
  deadline=$((SECONDS + 60))
  listing_ok=false
  remaining=()
  while true; do
    if listing=$(list_storage); then
      listing_ok=true
      remaining=()
      for id in "${part_ids[@]}"; do
        [[ "$listing" == *"_${id}_v2.json"* ]] && remaining+=("$id")
      done
      [ "${#remaining[@]}" -eq 0 ] && break
    else
      listing_ok=false
    fi
    [ "$SECONDS" -ge "$deadline" ] && break
    sleep 2
  done

  if [ "$listing_ok" != true ]; then
    warnings+=("could not list the app's storage, so payload delivery is unknown")
  elif [ "${#remaining[@]}" -gt 0 ]; then
    warnings+=("payload for session part(s) ${remaining[*]} was not delivered within 60 s (expected when the data URL is unreachable)")
  else
    payload_delivered=true
    for id in "${part_ids[@]}"; do
      if ! grep -qF "_${id}_v2.json" "$out/storage-seen.txt"; then
        payload_delivered=false
        warnings+=("no stored payload for session part $id was ever seen, so its delivery is unconfirmed")
      fi
    done
  fi
else
  warnings+=("no session part id, so payload delivery was not checked")
fi
[ -n "$watcher" ] && kill "$watcher" 2> /dev/null
wait 2> /dev/null

# The SDK keeps its device id in the app's default SharedPreferences.
adb exec-out run-as "$APP_PACKAGE" cat "shared_prefs/${APP_PACKAGE}_preferences.xml" > "$out/embrace-prefs.xml" 2>> "$out/run-as.err" || true
device_id=$(sed -n 's#.*<string name="io.embrace.deviceid">\([^<]*\)</string>.*#\1#p' "$out/embrace-prefs.xml" | head -n 1)
[ -n "$device_id" ] || warnings+=("could not read the Embrace device id from the app's shared preferences")

# What is still stored now. Payload files carry both ids in their names and are deleted once delivered, so this lists
# the session parts NOT YET delivered:
# <priority>_<timestamp>_<uuid>_<process>_<complete>_session_<userSessionId>_<sessionPartId>_v2.json
list_storage > "$out/app-storage-listing.txt" || true
grep -E '_session_[^_]+_[^_]+_v2\.json$' "$out/app-storage-listing.txt" | xargs -r -n1 basename | awk -F_ '$7 != "none" && $8 != "none" {print $8}' | awk '!seen[$0]++' > "$out/stored-session-part-ids.txt" || true

jq -n --slurpfile info "$out/run-info.json" \
  --arg serial "$serial" --arg sdk_int "$sdk_int" --arg device_id "$device_id" \
  --argjson script_status "$script_status" --argjson status "$status" --argjson payload_delivered "$payload_delivered" \
  --argjson session_part_ids "$(lines_to_json "$out/session-part-ids.txt")" \
  --argjson user_session_ids "$(lines_to_json "$out/user-session-ids.txt")" \
  --argjson stored_session_part_ids "$(lines_to_json "$out/stored-session-part-ids.txt")" \
  --argjson warnings "$(printf '%s\n' "${warnings[@]+"${warnings[@]}"}" | jq -R -s 'split("\n") | map(select(length > 0))')" \
  '$info[0] + {
    emulator_serial: $serial,
    android_api_level: ($sdk_int | tonumber? // null),
    embrace_device_id: (if $device_id == "" then null else $device_id end),
    session_part_ids: $session_part_ids,
    user_session_ids: $user_session_ids,
    stored_session_part_ids: $stored_session_part_ids,
    payload_delivered: $payload_delivered,
    adb_script_exit_code: $script_status,
    ok: ($status == 0),
    warnings: $warnings
  }' > "$out/summary.json"
cat "$out/summary.json"
for w in "${warnings[@]+"${warnings[@]}"}"; do echo "::warning::$w"; done

exit "$status"
