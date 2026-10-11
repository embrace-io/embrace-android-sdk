# embrace-benchmark-common

Holds what both sides of a macrobenchmark use: `embrace-macrobenchmark`, the test APK that drives and
measures, and `embrace-macrobenchmark-app`, the app it measures. Code that only one side runs belongs in
that side's module.

- **The contract between the two processes.** `ScenarioProtocol` names what one side writes and the
  other reads: global settings and the statuses the app reports. `HostTask` is what a launch of the host
  runs and what it runs on, and it carries itself to the app as the launch intent's extras, so only it
  knows their names. A name both sides' code uses is defined here, never spelled out on both. Which app
  the benchmark drives is the benchmark's own business, so its package and host activity stay in
  `embrace-macrobenchmark`.
- **The scenarios.** The app runs them, and the benchmark looks them up by id to parameterize its tests.
  `ResurrectionScenarios` are recorded as fixtures instead: the benchmark launches the app to run one, and
  the app keeps what it left on disk to restore before every iteration.
- **The capture profiles.** `CaptureProfile` and its perfetto configs are only used by the benchmark.
  They live here because `embrace-macrobenchmark` is a `com.android.test` module, which never runs a
  JVM unit test placed under its `src/test`, and `CaptureProfileTest` checks the configs.

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
