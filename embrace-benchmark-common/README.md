# embrace-benchmark-common

Contains common code used for benchmarking scenarios within an SDK. Currently this mainly holds benchmarks for persistence
operations.

A scenario is a named block of telemetry-producing code written against the public Embrace API, in
the same declarative style as the SDK integration tests:

```kotlin
val quickCheck = ScenarioSpec(
    id = "quick_check",
    description = "12s glance at a single screen after opening from a notification",
) {
    embrace.addBreadcrumb("opened from notification")
    embrace.recordSpan("notification-target-load") {
        request("https://api.example.com/v1/notifications/4821")
        advanceTime(180)
    }
    advanceTime(11_000)
}
```
