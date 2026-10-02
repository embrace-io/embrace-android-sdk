#!/bin/sh
# Generates the SDK's baseline profile on a connected device and copies it into place.
#
# Usage: scripts/generate-baseline-profile.sh [--serial SERIAL] [--no-publish] [--dry-run]
#
# Runs BaselineProfileGenerator against :embrace-macrobenchmark-app's baselineProfile variant, once per
# opentelemetry-kotlin implementation, then overwrites embrace-android-sdk/src/main/baseline-prof.txt
# with the union of both profiles. The device must be API 33+ or rooted.
# Exit codes: 2 preflight, 3 publish, 4 generation.

set -eu

die() { printf '\ngenerate-baseline-profile: %b\n' "$2" >&2; exit "$1"; }

serial=${ANDROID_SERIAL:-}
publish=true
dry_run=false

while [ $# -gt 0 ]; do
    case $1 in
        --serial) serial=${2:?--serial needs a value}; shift 2 ;;
        --no-publish) publish=false; shift ;;
        --dry-run) dry_run=true; shift ;;
        -h|--help) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) die 1 "unknown option: $1" ;;
    esac
done

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
generator=io.embrace.android.embracesdk.macrobenchmark.BaselineProfileGenerator
dest=$root/embrace-android-sdk/src/main/baseline-prof.txt

pub=:embrace-gradle-plugin:publishToMavenLocal
set -- :embrace-macrobenchmark:connectedBaselineProfileAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile" \
    "-Pandroid.testInstrumentationRunnerArguments.class=$generator"

if [ "$dry_run" = true ]; then
    [ "$publish" = false ] || printf './gradlew %s -Psigning.skip\n' "$pub"
    printf './gradlew %s\n' "$*"
    printf 'merge <output>/BaselineProfileGenerator_{compat,regular}-baseline-prof.txt > %s\n' "${dest#"$root"/}"
    exit 0
fi

adb=$(command -v adb || true)
[ -n "$adb" ] || adb=$HOME/Library/Android/sdk/platform-tools/adb
[ -x "$adb" ] || die 2 "adb not found; set ANDROID_HOME or add it to PATH"

if [ -z "$serial" ]; then
    found=$("$adb" devices | awk '$2 == "device" { print $1 }')
    count=$(printf '%s' "$found" | grep -c . || true)
    [ "$count" -eq 1 ] || die 2 "need exactly one device, found $count; use --serial"
    serial=$found
fi

api=$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r\n')
case $api in ''|*[!0-9]*) die 2 "could not read the API level of $serial" ;; esac

# Below API 33, BaselineProfileRule can only read the profile back as root.
if [ "$api" -lt 33 ] && [ "$("$adb" -s "$serial" shell id -u | tr -d '\r\n')" != 0 ]; then
    die 2 "$serial is API $api and not rooted; use API 33+, or run: adb -s $serial root"
fi

# androidx.benchmark runs an on-device trace_processor on port 9001; an orphan from a killed run,
# or an emulator snapshot, squats it and the collection fails with no error message. Read the
# kernel's socket table, as ss needs a netlink permission the shell user lacks: 2329 is 9001 in
# hex and 0A is LISTEN.
if "$adb" -s "$serial" shell cat /proc/net/tcp /proc/net/tcp6 | grep -q ':2329 [0-9A-F:]* 0A '; then
    die 2 "port 9001 busy on $serial: another benchmark is running, or an orphan is left over.\nIf nothing is running: adb -s $serial shell pkill -f trace_processor_shell"
fi

# Separate invocation: the app resolves the Embrace gradle plugin on its buildscript classpath, so
# the plugin must be in mavenLocal before the generator configures.
if [ "$publish" = true ]; then
    ( cd "$root" && ./gradlew "$pub" -Psigning.skip ) || die 3 "publish failed"
fi

outputs=$root/embrace-macrobenchmark/build/outputs/connected_android_test_additional_output/baselineProfile/connected
results=$root/embrace-macrobenchmark/build/outputs/androidTest-results/connected/baselineProfile
marker=$(mktemp)

set +e
( cd "$root" && ANDROID_SERIAL=$serial ./gradlew "$@" )
status=$?
set -e

if [ "$status" -ne 0 ]; then
    # The cause is in the test results, not in gradle's output, so show it here.
    failure=""
    for f in "$results"/TEST-*.xml; do
        if [ -f "$f" ] && [ "$f" -nt "$marker" ]; then
            failure=$(sed -n '/<failure/,/<\/failure>/p' "$f" | sed -e 's/<[^>]*>//g' -e 's/&quot;/"/g' \
                -e "s/&apos;/'/g" -e 's/&lt;/</g' -e 's/&gt;/>/g' -e 's/&amp;/\&/g' | head -20)
        fi
    done
    rm -f "$marker"
    [ -n "$failure" ] || failure="no test result; the failure is in the gradle output above"
    die 4 "generation failed (gradle exit $status):\n$failure\n\nLogcat: ${results#"$root"/}/<device>/logcat-*.txt"
fi

# Gradle exits 0 even when the install or the test runner fails, so require a fresh profile.
fresh_profile() {
    found=""
    for f in "$outputs"/*/"BaselineProfileGenerator_$1-baseline-prof.txt"; do
        if [ -s "$f" ] && [ "$f" -nt "$marker" ]; then found=$f; fi
    done
    [ -n "$found" ] || die 4 "gradle reported success but produced no $1 profile under $outputs"
    printf '%s' "$found"
}
compat=$(fresh_profile compat)
regular=$(fresh_profile regular)
rm -f "$marker"

# Sanity check each APK definitely used the correct SDK mode
grep -q 'Lio/opentelemetry/kotlin/CompatOpenTelemetryImpl;' "$compat" ||
    die 4 "the compat profile never loaded the compat implementation. Output: $compat"
grep -q 'Lio/opentelemetry/kotlin/OpenTelemetryImpl;' "$regular" ||
    die 4 "the regular profile never loaded the regular implementation. Output: $regular"

# unite both profiles
merged=$(mktemp)
awk '{
    rule = $0
    sub(/^[HSP]+/, "", rule)
    flags[rule] = flags[rule] substr($0, 1, length($0) - length(rule))
} END {
    for (rule in flags) {
        f = flags[rule]
        printf "%s\t%s%s%s\n", rule, (f ~ /H/ ? "H" : ""), (f ~ /S/ ? "S" : ""), (f ~ /P/ ? "P" : "")
    }
}' "$compat" "$regular" | LC_ALL=C sort | awk -F '\t' '{ print $2 $1 }' > "$merged"

# The generator keeps only HSP rules, and how many methods ART marks hot varies by image: an API 35
# Play image yields a handful where the API 34 aosp image CI uses yields thousands.
new=$(wc -l < "$merged" | tr -d ' ')
old=$(wc -l < "$dest" | tr -d ' ')
if [ "$((new * 2))" -lt "$old" ]; then
    rm -f "$merged"
    die 4 "the run worked but kept only $new rules ($old are shipped), so baseline-prof.txt is unchanged.\nThe generator keeps only rules ART marks hot (HSP), and this device's image marks few: use an\naosp emulator image, as CI does (API 34). Outputs: $compat $regular"
fi

cp "$merged" "$dest"
rm -f "$merged"
printf '%d rules written to %s (%d compat, %d regular)\n' "$new" "${dest#"$root"/}" \
    "$(wc -l < "$compat" | tr -d ' ')" "$(wc -l < "$regular" | tr -d ' ')"
