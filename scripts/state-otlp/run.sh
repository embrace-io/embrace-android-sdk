#!/bin/bash
# Runs the SDK's OTLP harness in one rendering against a fresh OpenTelemetry Collector, and keeps what the Collector
# wrote. Run from the repository root:
#
#   OTELCOL=/path/to/otelcol-contrib scripts/state-otlp/run.sh VERBOSE|ASSOCIATION|FIRST_STEP [output directory]
#
# The Collector is the contrib distribution, since the statements in collector.yaml need its transform processor. What
# arrived is in raw-traces.jsonl and raw-logs.jsonl, and what the statements made of it in ottl-traces.jsonl and
# ottl-logs.jsonl. scripts/state-otlp/measure.py reads the first two.
#
# FIRST_STEP is the app state data model's first step alone: a log is stamped, and no span is linked and no change replayed.
set -u
MODE=${1:?VERBOSE, ASSOCIATION or FIRST_STEP}
export STATE_OTLP_OUT=${2:-$PWD/build/state-otlp/$MODE}
OTELCOL=${OTELCOL:-otelcol-contrib}
rm -rf "$STATE_OTLP_OUT"; mkdir -p "$STATE_OTLP_OUT"
"$OTELCOL" --config scripts/state-otlp/collector.yaml > "$STATE_OTLP_OUT/collector.log" 2>&1 &
COLLECTOR=$!
for _ in $(seq 1 60); do curl -s -o /dev/null -m 1 http://127.0.0.1:4318/ && break; sleep 0.5; done
EMBRACE_OTLP_ENDPOINT=http://127.0.0.1:4318 EMBRACE_STATE_EXPORT_MODE=$MODE \
  ./gradlew :embrace-android-sdk:testDebugUnitTest --tests '*StateOtlpExportHarnessTest' --rerun -i > "$STATE_OTLP_OUT/harness.log" 2>&1
STATUS=$?
grep 'State OTLP harness' "$STATE_OTLP_OUT/harness.log" | sed 's/^ *//'
sleep 3
kill $COLLECTOR; wait $COLLECTOR 2>/dev/null
exit $STATUS
