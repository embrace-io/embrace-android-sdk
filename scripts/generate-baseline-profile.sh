#!/bin/sh
# Generates the SDK's baseline profile on an API 34 aosp emulator and copies it into place.
#
# Usage: scripts/generate-baseline-profile.sh [--no-publish] [--gradle-arg ARG] [--dry-run]
#
# Runs BaselineProfileGenerator on the pixel6Api34 Gradle managed device, which Gradle downloads and
# boots itself, once per opentelemetry-kotlin implementation. Then overwrites
# embrace-android-sdk/src/main/baseline-prof.txt with the union of both profiles.
# Exit codes: 3 publish, 4 generation.

set -eu

die() { printf '\ngenerate-baseline-profile: %b\n' "$2" >&2; exit "$1"; }

publish=true
dry_run=false
extra=""

while [ $# -gt 0 ]; do
    case $1 in
        --no-publish) publish=false; shift ;;
        --gradle-arg) extra="$extra ${2:?--gradle-arg needs a value}"; shift 2 ;;
        --dry-run) dry_run=true; shift ;;
        -h|--help) sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) die 1 "unknown option: $1" ;;
    esac
done

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
generator=io.embrace.android.embracesdk.macrobenchmark.BaselineProfileGenerator
dest=$root/embrace-android-sdk/src/main/baseline-prof.txt

pub=:embrace-gradle-plugin:publishToMavenLocal
set -- :embrace-macrobenchmark:pixel6Api34BaselineProfileAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile" \
    "-Pandroid.testInstrumentationRunnerArguments.class=$generator"
# shellcheck disable=SC2086 # extra holds whitespace-separated gradle args
set -- "$@" $extra

if [ "$dry_run" = true ]; then
    [ "$publish" = false ] || printf './gradlew %s -Psigning.skip\n' "$pub"
    printf './gradlew %s\n' "$*"
    printf 'merge <output>/BaselineProfileGenerator_{compat,regular}-baseline-prof.txt > %s\n' "${dest#"$root"/}"
    exit 0
fi

# Separate invocation: the app resolves the Embrace gradle plugin on its buildscript classpath, so
# the plugin must be in mavenLocal before the generator configures.
if [ "$publish" = true ]; then
    ( cd "$root" && ./gradlew "$pub" -Psigning.skip ) || die 3 "publish failed"
fi

outputs=$root/embrace-macrobenchmark/build/outputs/managed_device_android_test_additional_output/baselineProfile/pixel6Api34
results=$root/embrace-macrobenchmark/build/outputs/androidTest-results/managedDevice/baselineprofile/pixel6Api34
marker=$(mktemp)

set +e
( cd "$root" && ./gradlew "$@" )
status=$?
set -e

if [ "$status" -ne 0 ]; then
    # The cause is in the test results, not in gradle's output, so show it here.
    failure=""
    for f in $(find "$results" -name 'TEST-*.xml' -newer "$marker" 2>/dev/null); do
        failure=$(sed -n '/<failure/,/<\/failure>/p' "$f" | sed -e 's/<[^>]*>//g' -e 's/&quot;/"/g' \
            -e "s/&apos;/'/g" -e 's/&lt;/</g' -e 's/&gt;/>/g' -e 's/&amp;/\&/g' | head -20)
    done
    rm -f "$marker"
    [ -n "$failure" ] || failure="no test result; the failure is in the gradle output above"
    die 4 "generation failed (gradle exit $status):\n$failure\n\nLogcat: ${results#"$root"/}/logcat-*.txt"
fi

# Gradle exits 0 even when the install or the test runner fails, so require a fresh profile.
fresh_profile() {
    found=$(find "$outputs" -name "BaselineProfileGenerator_$1-baseline-prof.txt" -newer "$marker" -size +0 2>/dev/null | head -1)
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

# Unite both profiles, promoting every method rule to HSP.
merged=$(mktemp)
awk '{
    rule = $0
    sub(/^[HSP]+/, "", rule)
    printf "%s\t%s\n", rule, (index(rule, "->") ? "HSP" : "")
}' "$compat" "$regular" | LC_ALL=C sort -u | awk -F '\t' '{ print $2 $1 }' > "$merged"

# A profile under half the shipped size means the journey barely ran.
new=$(wc -l < "$merged" | tr -d ' ')
old=$(wc -l < "$dest" | tr -d ' ')
if [ "$((new * 2))" -lt "$old" ]; then
    rm -f "$merged"
    die 4 "the run worked but produced only $new rules ($old are shipped), so baseline-prof.txt is unchanged.\nThe journey barely ran: check the logcat under ${results#"$root"/}. Outputs: $compat $regular"
fi

cp "$merged" "$dest"
rm -f "$merged"
printf '%d rules written to %s (%d compat, %d regular)\n' "$new" "${dest#"$root"/}" \
    "$(wc -l < "$compat" | tr -d ' ')" "$(wc -l < "$regular" | tr -d ' ')"
