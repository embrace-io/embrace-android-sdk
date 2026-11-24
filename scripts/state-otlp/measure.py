"""What state adds to the OTLP an app's own exporters receive from the SDK, measured from what a Collector wrote.

Reads the OTLP JSON a Collector's file exporter wrote for one run of the SDK's OTLP harness, and reports, in bytes of
protobuf and of JSON: what state adds to a span and to a log, what a change event and a snapshot event weigh, and the
total for the run with every record under one Resource, as a batching exporter would send it.

Run with: uv run --with opentelemetry-proto python scripts/state-otlp/measure.py <directory> [<directory> ...]
where each directory holds raw-traces.jsonl and raw-logs.jsonl.
"""
import base64
import gzip
import json
import os
import sys

from google.protobuf import json_format
from opentelemetry.proto.collector.logs.v1.logs_service_pb2 import ExportLogsServiceRequest
from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import ExportTraceServiceRequest
from opentelemetry.proto.common.v1.common_pb2 import KeyValue

STATE_EVENTS = ("emb.state.change", "emb.state.snapshot")


def is_state(key):
    # `emb.state` alone is the foreground or background attribute, which is not part of this
    return key.startswith(("emb.state.", "emb.entity."))


def hex_to_base64(record):
    for field in ("traceId", "spanId", "parentSpanId"):
        if record.get(field):
            record[field] = base64.b64encode(bytes.fromhex(record[field])).decode()
    for link in record.get("links", []):
        hex_to_base64(link)
    return record


def read(directory):
    spans, logs, resource, scope = [], [], None, None
    with open(os.path.join(directory, "raw-traces.jsonl")) as file:
        for line in file:
            for group in json.loads(line)["resourceSpans"]:
                resource = group["resource"]
                for scoped in group["scopeSpans"]:
                    scope = scoped.get("scope", {})
                    spans += [hex_to_base64(span) for span in scoped["spans"]]
    with open(os.path.join(directory, "raw-logs.jsonl")) as file:
        for line in file:
            for group in json.loads(line)["resourceLogs"]:
                for scoped in group["scopeLogs"]:
                    logs += [hex_to_base64(log) for log in scoped["logRecords"]]
    return resource, scope, spans, logs


def attribute_bytes(attributes):
    """The bytes a list of attributes takes in a record: protobuf, with the field's tag and length, and compact JSON."""
    proto = sum(json_format.ParseDict(a, KeyValue()).ByteSize() + 2 for a in attributes)
    text = sum(len(json.dumps(a, separators=(",", ":"))) + 1 for a in attributes)
    return proto, text


def one_batch(resource, scope, spans, logs):
    traces = json_format.ParseDict(
        {"resourceSpans": [{"resource": resource, "scopeSpans": [{"scope": scope, "spans": spans}]}]},
        ExportTraceServiceRequest(),
    )
    records = json_format.ParseDict(
        {"resourceLogs": [{"resource": resource, "scopeLogs": [{"scope": scope, "logRecords": logs}]}]},
        ExportLogsServiceRequest(),
    )
    raw = traces.SerializeToString() + records.SerializeToString()
    return len(raw), len(gzip.compress(raw))


def without_state(records):
    return [{**r, "attributes": [a for a in r.get("attributes", []) if not is_state(a["key"])]} for r in records]


def mean(values):
    return round(sum(values) / len(values)) if values else 0


def measure(directory):
    resource, scope, spans, logs = read(directory)
    events = [log for log in logs if log.get("eventName") in STATE_EVENTS]
    ordinary = [log for log in logs if log.get("eventName") not in STATE_EVENTS]
    changes = [log for log in events if log["eventName"] == "emb.state.change"]
    snapshots = [log for log in events if log["eventName"] == "emb.state.snapshot"]
    linked = [s for s in spans if any(a["key"] == "emb.state.seq" for a in s.get("attributes", []))]
    crossed = [s for s in spans if any(a["key"] == "emb.state.end_seq" for a in s.get("attributes", []))]

    def added(records):
        return [attribute_bytes([a for a in r.get("attributes", []) if is_state(a["key"])]) for r in records]

    def whole(records):
        sizes = []
        for record in records:
            message = json_format.ParseDict(
                {"resourceLogs": [{"scopeLogs": [{"logRecords": [record]}]}]}, ExportLogsServiceRequest(),
            )
            sizes.append((message.ByteSize(), len(json.dumps(record, separators=(",", ":")))))
        return sizes

    states = sorted({a["key"].rsplit(".", 1)[-1] for s in snapshots for a in s["attributes"]
                     if a["key"].startswith("emb.state.value.")})
    floor = one_batch({**resource, "attributes": [a for a in resource["attributes"] if not is_state(a["key"])
                                                   and a["key"] != "emb.process_identifier"]},
                      scope, without_state(spans), without_state(ordinary))
    total = one_batch(resource, scope, spans, logs)
    span_added, log_added = added(linked), added(ordinary)
    return {
        "run": os.path.basename(os.path.normpath(directory)),
        "states": len(states),
        "spans": len(spans), "spans_linked": len(linked), "spans_a_state_changed_during": len(crossed),
        "logs": len(ordinary), "change_events": len(changes), "snapshot_events": len(snapshots),
        "state_on_a_linked_span_protobuf": mean([p for p, _ in span_added]),
        "state_on_a_linked_span_json": mean([t for _, t in span_added]),
        "state_on_a_log_protobuf": mean([p for p, _ in log_added]),
        "state_on_a_log_json": mean([t for _, t in log_added]),
        "a_change_event_protobuf": mean([p for p, _ in whole(changes)]),
        "a_change_event_json": mean([t for _, t in whole(changes)]),
        "a_snapshot_event_protobuf": mean([p for p, _ in whole(snapshots)]),
        "a_snapshot_event_json": mean([t for _, t in whole(snapshots)]),
        "run_without_state_protobuf": floor[0], "run_without_state_gzip": floor[1],
        "run_protobuf": total[0], "run_gzip": total[1],
        "state_adds_protobuf": total[0] - floor[0], "state_adds_gzip": total[1] - floor[1],
    }


def main():
    rows = [measure(directory) for directory in sys.argv[1:]]
    for key in rows[0]:
        print("\t".join([key] + [str(row[key]) for row in rows]))


if __name__ == "__main__":
    main()
