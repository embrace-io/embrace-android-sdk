# embrace-android-session-persistence

Contains code that persists session telemetry to disk.

Payloads are modelled as protobuf schemas under `src/main/proto` and compiled to Kotlin by
[Wire](https://github.com/square/wire) at build time.
