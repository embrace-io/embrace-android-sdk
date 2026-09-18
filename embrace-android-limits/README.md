# embrace-android-limits

Holds the logic that enforces upper bounds on the telemetry the SDK captures, so that a
misbehaving app can't exhaust memory or produce payloads the backend will reject.

Consumers of this module (`embrace-android-otel`, `embrace-android-session-persistence`,
`embrace-android-core`) ask this module whether a given piece of telemetry is within limits, rather
than each re-implementing truncation and counting rules against the raw values.

## Layout

Everything lives under `io.embrace.android.embracesdk.internal.limits`.

## Relationship to `OtelLimitsConfig`

The limit **values** deliberately stay in `OtelLimitsConfig` in `embrace-android-payload`. That
interface is populated by the Embrace Gradle plugin's `@EmbraceInstrumented` bytecode plumbing, so
moving it would break instrumentation. This module depends on it and owns the *enforcement* of
those values only.
