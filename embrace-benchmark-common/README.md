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
        recordRequest("https://api.example.com/v1/notifications/4821")
        advanceTime(180)
    }
    advanceTime(11_000)
}
```

A scenario calls the SDK under test directly, through `embrace`. Its `ScenarioScope` gives it what depends
on where it runs, the scenario clock and the app's foreground state, and simulates what an app does that
takes more than a single call to the SDK, such as a request made through the manual network API that
takes scenario time.
