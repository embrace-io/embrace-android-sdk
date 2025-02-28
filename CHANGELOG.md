# Embrace Android SDK Changelog

## 7.2.0
*February 27, 2025*

- Fixed stacktrace deobfuscation in React Native
- Fixed build issues on some apps that use DexGuard
- Fixed memory leak of the Activity loaded during app launch
- Fixed rare crash when the Jetpack Compose tap detection feature is enabled

## 7.1.0
*February 7, 2025*

- Fixed stacktrace symbolication issue caused by incorrect ProGuard rules
- Added API for sending log messages that contain binary attachments
- Internal refactoring of gradle plugin

## 7.0.0
*January 28, 2025*

> ### Important 
> This version has an issue where JVM symbol mapping files are sometimes not being uploaded correctly, leading to some call stacks being partially obfuscated (e.g. in crashes and ANRs). We are investigating the issue, and in the meantime, please refrain from putting this SDK version in production.

- API and functional changes in this major release are documented in the [Upgrade Guide](https://embrace.io/docs/android/upgrading/). Key ones to be aware of include:
    - Moments feature and API have been removed in favor of [Traces](https://embrace.io/docs/android/features/traces/), which should be used instead to track how long workflows in the app took to complete.
    - Public API methods are all implemented in Kotlin, so passing in nulls in Java for parameters annotated with `@NonNull` will cause a runtime exception and could cause a crash.
    - Firebase Cloud Messaging and Compose tap instrumentation require explicit inclusion of modules in your Gradle files.
    - Remove support for deprecated properties in `embrace-config.json` and the Embrace Gradle plugin.

- New features and other changes in this release include:
    - Customizable, automatic instrumentation of Activity load and Jetpack Compose Navigation (requires opt-in for now)
    - Auto-termination of spans based on navigation
    - Customization of app startup trace through custom attributes and child spans
    - Public API to get the timestamp used by the SDK so custom spans for app startup and UI load traces can be in sync with other spans in the trace
    - API to disable data export at programmatically for the currently running instance of the app
    - Associate native crashes with the device and app metadata at the time of crash instead of the time of reporting
    - Configuration setting to enable Network Span Forwarding traceparent injection into network request spans for non-Embrace users

- Dependency updates:
    - OpenTelemetry API and SDK to `1.46.0`
    - OpenTelemetry Semantic Conventions to `1.29.0-alpha`
    - AndroidX Lifecycle to `2.7.0`
    - Moshi to `1.15.2`
