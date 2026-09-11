#!/bin/sh
# Runs the :embrace-macrobenchmark benchmark on a connected device.
#
# Usage: scripts/run-macrobenchmark.sh [--test CLASS] [--serial SERIAL] [--no-publish]
#            [--fast-publish] [--suppress-errors LIST] [--gradle-arg ARG] [--dry-run]
#
# Two gradle invocations are needed: the app reads embrace.macrobenchmark.instrument at
# configuration time, so the plugin must be in mavenLocal before the benchmark configures.
# Exit codes: 2 preflight, 3 publish, 4 benchmark.

set -eu

die() { printf '%b\n' "$2" >&2; exit "$1"; }

test_filter=io.embrace.android.embracesdk.macrobenchmark.SdkInitBenchmark
serial=${ANDROID_SERIAL:-}
publish=repo
suppress=""
extra=""
dry_run=false

while [ $# -gt 0 ]; do
    case $1 in
        --test) test_filter=${2:?--test needs a value}; shift 2 ;;
        --serial) serial=${2:?--serial needs a value}; shift 2 ;;
        --suppress-errors) suppress=${2:?needs a value}; shift 2 ;;
        --gradle-arg) extra="$extra ${2:?--gradle-arg needs a value}"; shift 2 ;;
        --no-publish) publish=none; shift ;;
        --fast-publish) publish=plugin; shift ;;
        --dry-run) dry_run=true; shift ;;
        -h|--help) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) die 1 "unknown option: $1" ;;
    esac
done

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

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
[ "$api" -ge 29 ] || die 2 "$serial is API $api; emb-sdk-start is only emitted on API 29+"

if [ -z "$suppress" ]; then
    # AGP splits instrumentation-args on commas, so only the FIRST value reaches the device and
    # any others are silently dropped. Pass exactly one. For several, set
    # testInstrumentationRunnerArguments in the module build file instead.
    case $("$adb" -s "$serial" shell getprop ro.build.fingerprint | tr -d '\r\n') in
        *generic*|*sdk_gphone*|*emu64*)
            suppress=EMULATOR
            printf 'WARNING: %s is an emulator; results are not comparable to a real device.\n' "$serial"
            ;;
        *)
            # Unrooted devices cannot lock CPU clocks; expect run-to-run variance.
            suppress=UNLOCKED
            ;;
    esac
fi

# androidx.benchmark runs an on-device trace_processor on port 9001. A live run owns it, and an
# orphan from a killed run squats it; either way the next run fails while computing
# TraceSectionMetric, with no error message.
if "$adb" -s "$serial" shell ss -lptn 2>/dev/null | grep -q ':9001'; then
    die 2 "port 9001 busy on $serial: another benchmark is running, or an orphan is left over.\nIf nothing is running: adb -s $serial shell pkill -f trace_processor_shell"
fi

case $publish in
    repo) pub=publishToMavenLocal ;;
    plugin) pub=:embrace-gradle-plugin:publishToMavenLocal ;;
    none) pub="" ;;
esac

set -- :embrace-macrobenchmark:connectedBenchmarkAndroidTest \
    -Pembrace.macrobenchmark.instrument=true \
    "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=$suppress" \
    "-Pandroid.testInstrumentationRunnerArguments.class=$test_filter"
set -- "$@" $extra

if [ "$dry_run" = true ]; then
    [ -z "$pub" ] || printf './gradlew %s -Psigning.skip\n' "$pub"
    printf './gradlew %s\n' "$*"
    exit 0
fi

if [ -n "$pub" ]; then
    ( cd "$root" && ./gradlew "$pub" -Psigning.skip ) || die 3 "publish failed"
fi

traces=$root/embrace-macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected
marker=$(mktemp)

set +e
( cd "$root" && ANDROID_SERIAL=$serial ./gradlew "$@" )
status=$?
set -e

# Gradle exits 0 even when the install or the test runner fails, so require fresh traces.
count=0
for f in "$traces"/*/*.perfetto-trace; do
    if [ -f "$f" ] && [ "$f" -nt "$marker" ]; then count=$((count + 1)); fi
done
rm -f "$marker"

if [ "$status" -ne 0 ]; then
    die 4 "benchmark failed (gradle exit $status) after $count traces; see embrace-macrobenchmark/build/reports"
fi
if [ "$count" -eq 0 ]; then
    die 4 "gradle reported success but produced no traces; the install or the test runner failed"
fi
printf '%d traces produced\n' "$count"
