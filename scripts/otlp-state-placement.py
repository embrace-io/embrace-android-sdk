"""Where the values of a handful of changing states can live in OTLP, and what each placement costs. The last three
placements are v2 as the state capture design proposes it.

It builds real OTLP export requests (protobuf) for one stretch of an app's telemetry, the same stretch for every
placement, and reports their size as protobuf and gzipped, how many Resource groups they hold, and what each placement
cannot say. Run with: uv run --with opentelemetry-proto python otlp_placement.py <output.tsv>
"""
import gzip
import itertools
import os
import sys

from opentelemetry.proto.collector.logs.v1.logs_service_pb2 import ExportLogsServiceRequest
from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import ExportTraceServiceRequest
from opentelemetry.proto.common.v1.common_pb2 import AnyValue, InstrumentationScope, KeyValue, KeyValueList
from opentelemetry.proto.logs.v1.logs_pb2 import LogRecord, ResourceLogs, ScopeLogs
from opentelemetry.proto.resource.v1.resource_pb2 import Resource
from opentelemetry.proto.trace.v1.trace_pb2 import ResourceSpans, ScopeSpans, Span

STATE_NAMES = [
    "network", "power", "screen-automatic", "orientation", "thermal", "battery", "memory-pressure", "dark-mode",
    "locale", "connectivity-type",
]
VALUES = ["state-value-a", "state-value-b"]
INITIAL = "state-value-initial"
START_NS = 1_750_000_000_000_000_000
STEP_NS = 137_000_000

# what an Android app's Resource carries before any state: the attributes the SDK sets when it builds its providers
RESOURCE = {
    "service.name": "com.example.shop",
    "service.version": "3.14.2",
    "os.name": "android",
    "os.type": "linux",
    "os.version": "15",
    "android.os.api_level": "35",
    "device.manufacturer": "Google",
    "device.model.identifier": "Pixel 8",
    "device.model.name": "Pixel 8",
    "telemetry.distro.name": "io.embrace.android.embracesdk",
    "telemetry.distro.version": "8.0.0",
    "telemetry.sdk.name": "opentelemetry",
    "telemetry.sdk.language": "kotlin",
    "telemetry.sdk.version": "0.10.0",
}
RECORD = {"session.id": "4f3c9a7d0b8e5f2a4c116c9b1f2ec1d3", "emb.session_part_id": "00000000000000000000000000000002"}
SCOPE = InstrumentationScope(name="io.embrace.android.embracesdk", version="8.0.0")


def kv(key, value):
    if isinstance(value, int):
        return KeyValue(key=key, value=AnyValue(int_value=value))
    if isinstance(value, dict):
        return KeyValue(key=key, value=AnyValue(kvlist_value=KeyValueList(values=[kv(k, v) for k, v in value.items()])))
    return KeyValue(key=key, value=AnyValue(string_value=value))


def attributes(mapping):
    return [kv(key, value) for key, value in mapping.items()]


def timeline(states, changes_of_each, spans, logs):
    """The stretch of telemetry: changes, spans and logs spread evenly, each with every state's value as it was made."""
    totals = [states * changes_of_each, spans, logs]
    done = [0, 0, 0]
    values = [INITIAL] * states
    counts = [0] * states
    sequence = 0
    items = []
    for step in range(sum(totals)):
        kind = min((k for k in range(3) if done[k] < totals[k]), key=lambda k: done[k] / totals[k])
        now = START_NS + step * STEP_NS
        if kind == 0:
            index = done[0] % states
            counts[index] += 1
            values[index] = VALUES[counts[index] % len(VALUES)]
            sequence += 1
            items.append(("change", now, index, tuple(values), sequence))
        else:
            items.append(("span" if kind == 1 else "log", now, None, tuple(values), sequence))
        done[kind] += 1
    return items


def stamp(values):
    return {f"emb.state.{STATE_NAMES[i]}": value for i, value in enumerate(values)}


def span(now, number, extra):
    return Span(
        trace_id=number.to_bytes(16, "big"), span_id=number.to_bytes(8, "big"), name="ordinary-work",
        start_time_unix_nano=now, end_time_unix_nano=now + STEP_NS // 2, kind=Span.SPAN_KIND_INTERNAL,
        attributes=attributes({**RECORD, **extra}),
    )


def log(now, body, extra, event_name=None):
    record = LogRecord(
        time_unix_nano=now, observed_time_unix_nano=now, severity_number=9, severity_text="INFO",
        body=AnyValue(string_value=body), attributes=attributes({**RECORD, **extra}),
    )
    if event_name and "event_name" in LogRecord.DESCRIPTOR.fields_by_name:
        record.event_name = event_name
    return record


def requests(groups):
    """groups: a list of (resource attributes, spans, logs), one for each Resource the telemetry is sent under."""
    traces = ExportTraceServiceRequest(resource_spans=[
        ResourceSpans(resource=Resource(attributes=attributes(resource)), scope_spans=[ScopeSpans(scope=SCOPE, spans=spans)])
        for resource, spans, _ in groups if spans
    ])
    logs = ExportLogsServiceRequest(resource_logs=[
        ResourceLogs(resource=Resource(attributes=attributes(resource)), scope_logs=[ScopeLogs(scope=SCOPE, log_records=records)])
        for resource, _, records in groups if records
    ])
    return traces, logs


def floor(items):
    spans = [span(now, n, {}) for n, (kind, now, *_rest) in enumerate(items) if kind == "span"]
    logs = [log(now, "ordinary-log-message", {}) for kind, now, *_rest in items if kind == "log"]
    return requests([(RESOURCE, spans, logs)])


def on_every_record(items):
    """What OpenTelemetry's Android SDK does: every span and log stamped, and a log record for every change."""
    spans, logs = [], []
    for n, (kind, now, index, values, _sequence) in enumerate(items):
        if kind == "span":
            spans.append(span(now, n, stamp(values)))
        elif kind == "log":
            logs.append(log(now, "ordinary-log-message", stamp(values)))
        else:
            change = {"emb.state.name": STATE_NAMES[index], "emb.state.new_value": values[index]}
            logs.append(log(now, "state-change", {**change, **stamp(values)}, event_name="emb.state.change"))
    return requests([(RESOURCE, spans, logs)])


def in_the_resource(items, with_change_events):
    """State as Resource attributes: a new Resource, and so a new group, whenever a value changes."""
    groups = []
    current = None
    for n, (kind, now, index, values, _sequence) in enumerate(items):
        if kind == "change":
            if with_change_events:
                current = [{**RESOURCE, **stamp(values)}, [], []]
                groups.append(current)
                change = {"emb.state.name": STATE_NAMES[index], "emb.state.new_value": values[index]}
                current[2].append(log(now, "state-change", change, event_name="emb.state.change"))
            else:
                current = None
            continue
        if current is None or current[0] != {**RESOURCE, **stamp(values)}:
            current = [{**RESOURCE, **stamp(values)}, [], []]
            groups.append(current)
        (current[1] if kind == "span" else current[2]).append(
            span(now, n, {}) if kind == "span" else log(now, "ordinary-log-message", {})
        )
    return requests([tuple(group) for group in groups]), len(groups)


def entity_events(items):
    """OpenTelemetry's entity state event: the complete description on every change, and nothing on the telemetry."""
    spans, logs = [], []
    for n, (kind, now, _index, values, _sequence) in enumerate(items):
        if kind == "span":
            spans.append(span(now, n, {}))
        elif kind == "log":
            logs.append(log(now, "ordinary-log-message", {}))
        else:
            description = {STATE_NAMES[i]: value for i, value in enumerate(values)}
            entity = {"entity.type": "app.instance", "entity.id": {"emb.process_identifier": "8f1c2b7a"},
                      "entity.description": description}
            logs.append(log(now, "", entity, event_name="entity.state"))
    return requests([(RESOURCE, spans, logs)])


def sequence_and_small_events(items):
    """A number on every record that places it among the changes, and a small record for each change."""
    spans, logs = [], []
    for n, (kind, now, index, values, sequence) in enumerate(items):
        if kind == "span":
            spans.append(span(now, n, {"emb.state.seq": sequence}))
        elif kind == "log":
            logs.append(log(now, "ordinary-log-message", {"emb.state.seq": sequence}))
        else:
            change = {"emb.state.name": STATE_NAMES[index], "emb.state.new_value": values[index], "emb.state.seq": sequence}
            logs.append(log(now, "state-change", change, event_name="emb.state.change"))
    return requests([(RESOURCE, spans, logs)])


def change_log_span(items):
    """v2's change log for a session part: one span with every state's opening value and an event for each change.
    The events are in the order of the sequence, and each carries its own number as emb.app.state.seq."""
    changes = [item for item in items if item[0] == "change"]
    states = len(items[0][3])
    opening = {f"emb.app.state.initial_value.{STATE_NAMES[i]}": INITIAL for i in range(states)}
    events = [
        Span.Event(
            time_unix_nano=now, name=f"emb-state-{STATE_NAMES[index]}",
            attributes=attributes({"emb.app.state.seq": sequence, "emb.app.state.new_value": values[index]}),
        )
        for _kind, now, index, values, sequence in changes
    ]
    return Span(
        trace_id=(1 << 100).to_bytes(16, "big"), span_id=(1 << 60).to_bytes(8, "big"), name="emb-states",
        start_time_unix_nano=items[0][1], end_time_unix_nano=items[-1][1], kind=Span.SPAN_KIND_INTERNAL,
        attributes=attributes({**RECORD, "emb.type": "state", "emb.app.state.initial_seq": 0,
                               "emb.app.state.part_start_seq": 0, **opening}),
        events=events,
    )


def v2_stamp(values):
    """v2's names for a state's value, which have a prefix of their own."""
    return {f"emb.app.state.value.{STATE_NAMES[i]}": value for i, value in enumerate(values)}


def v2_packed(values):
    """v2's stamp in Embrace's own payload: every value in one attribute, name=value joined by semicolons."""
    return ";".join(f"{STATE_NAMES[i]}={value}" for i, value in enumerate(values))


def v2(items, change_log, events, verbose=False, packed=False):
    """v2 as the design has it. A log carries every value and the sequence, as one packed attribute in Embrace's payload
    (packed) and as one attribute for each state in what a customer's exporter receives. A span carries the sequence, and in the verbose
    rendering for a customer's exporter every value as well. The changes travel once in the session part's change log,
    which is what Embrace receives, or as a log record for each change, which is what a customer's exporter receives:
    with every value in the verbose rendering, and the change alone in the association rendering."""
    spans, logs = [], []
    previous = {}
    for n, (kind, now, index, values, sequence) in enumerate(items):
        if kind == "span":
            spans.append(span(now, n, {"emb.app.state.seq": sequence, **(v2_stamp(values) if verbose else {})}))
        elif kind == "log":
            stamped = ({"emb.app.state.packed": v2_packed(values), "emb.app.state.seq": str(sequence)} if packed
                       else {**v2_stamp(values), "emb.app.state.seq": sequence})
            logs.append(log(now, "ordinary-log-message", stamped))
        elif events:
            change = {"emb.type": "state", "emb.app.state.seq": sequence, "emb.entity.type": "process",
                      "emb.entity.id": "8f1c2b7a43e94c0d", "emb.app.state.name": STATE_NAMES[index],
                      "emb.app.state.new_value": values[index],
                      # the value the state had before, absent on a state's first change
                      **({"emb.app.state.previous_value": previous[index]} if index in previous else {})}
            previous[index] = values[index]
            logs.append(log(now, "", {**change, **(v2_stamp(values) if verbose else {})}, event_name="emb.app.state.change"))
    if change_log:
        spans.append(change_log_span(items))
    return requests([(RESOURCE, spans, logs)])


def lost_without_events(items):
    """What a Resource alone cannot say about the changes: no change has a time of its own, and beyond that, how many
    fall between the same two records as another change, so that their order is lost, and how many are never seen at
    all, because the same state changed again before any record, or no record followed."""
    sharing_a_gap = never_seen = 0
    pending = {}
    for kind, _now, index, _values, _sequence in items:
        if kind == "change":
            if index in pending:
                never_seen += 1
            pending[index] = pending.get(index, 0) + 1
        else:
            if sum(pending.values()) > 1:
                sharing_a_gap += sum(pending.values())
            pending = {}
    never_seen += len(pending)
    return sharing_a_gap, never_seen


def size(pair):
    raw = b"".join(message.SerializeToString() for message in pair)
    return len(raw), len(gzip.compress(raw))


def main():
    rows = []
    # the last scenario is one state that changes more often than telemetry is made, as a network that flaps does
    scenarios = list(itertools.product([3, 10], [5, 100], [100, 500], [100])) + [(1, 1000, 500, 100)]
    for states, changes, spans, logs in scenarios:
        items = timeline(states, changes, spans, logs)
        base = size(floor(items))
        sharing_a_gap, never_seen = lost_without_events(items)
        resource_only, groups_only = in_the_resource(items, with_change_events=False)
        resource_events, groups_events = in_the_resource(items, with_change_events=True)
        placements = [
            ("attributes on every record, an event for each change", size(on_every_record(items)), 1, 0, 0),
            ("resource attributes, a new Resource on each change", size(resource_only), groups_only, sharing_a_gap, never_seen),
            ("resource attributes, and an event for each change", size(resource_events), groups_events, 0, 0),
            ("entity state events, nothing on telemetry", size(entity_events(items)), 1, 0, 0),
            ("a sequence on every record, a small event for each change", size(sequence_and_small_events(items)), 1, 0, 0),
            ("v2 to Embrace: the packed values on logs, a link on spans, the change log as one span",
             size(v2(items, change_log=True, events=False, packed=True)), 1, 0, 0),
            ("v2 to a customer's exporter, verbose: values on logs and spans, an event for each change with every value",
             size(v2(items, change_log=False, events=True, verbose=True)), 1, 0, 0),
            ("v2 to a customer's exporter, association: values on logs, a link on spans, an event with the change alone",
             size(v2(items, change_log=False, events=True)), 1, 0, 0),
        ]
        for name, (raw, zipped), groups, unordered, unseen in placements:
            rows.append([states, changes, spans, logs, states * changes, name, raw - base[0], zipped - base[1], groups,
                         unordered, unseen])
    header = ["states", "changes_of_each_state", "spans", "logs", "changes", "placement", "protobuf_bytes_added",
              "gzip_bytes_added", "resource_groups", "changes_whose_order_is_lost", "changes_never_seen"]
    text = "\n".join("\t".join(str(cell) for cell in row) for row in [header] + rows) + "\n"
    with open(sys.argv[1], "w") as file:
        file.write(text)
    print(text)
    second = KeyValue(key="emb.app.state.end_seq", value=AnyValue(int_value=4218)).ByteSize()
    print("a second number on a span, for one a state changed during:", second + 2, "bytes of protobuf")
    one = Resource(attributes=attributes(RESOURCE)).SerializeToString()
    print("a Resource with no state:", len(one), "bytes;", "with ten states:",
          len(Resource(attributes=attributes({**RESOURCE, **stamp([INITIAL] * 10)})).SerializeToString()), "bytes")


if __name__ == "__main__":
    main()
