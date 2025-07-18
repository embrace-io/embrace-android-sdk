# Embrace Android SDK Changelog

## 7.7.0
*July 18, 2025*

- Improve cold startup trace by automatically instrumenting `Application.onCreate()` invocation
- Add `session.id` attribute to all Spans that denotes the session in which a Span started in
- Use Kotlin OTel API and Java adaptors internally (no visible API or behavior change to SDK users)
- Require desugaring at build time when minSdk < 26 due to OTel SDK issue

## 7.6.1
*July 17, 2025*

> This version is identical to 7.6.0 except that the desugaring requirement if Android 7.x is supported will be verified at build time.
> This patch is unnecessary if you are already running 7.6.0. But if you support Android 7.x, ensure that you have desugaring enabled or else the Embrace SDK will not start for apps running on those Android versions.

- Require desugaring at build time when minSdk < 26 due to OTel SDK issue

## 7.6.0
*June 25, 2025*

> This version requires desugaring if your app support Android 7.x. Previously, the requirement had been for support of Android 6.x or lower. For more information, please see [Google's documentation here](https://developer.android.com/studio/write/java8-support#library-desugaring)

- Fix a Dexguard issue when bundle and assemble are executed in the same Gradle command
- Stop tracking ANRs for sessions that start and stay in the background

## 7.5.0
*June 9, 2025*

- New configuration option to start the SDK automatically (default off)
- Link Spans with Sessions in which they ended via Span Links
- Bypass instrumentation-time restrictions for Spans and Embrace Logs for non-Embrace Users
- Use `BuildFeatures` API for Gradle 8.5+ instead of a deprecated feature to be removed in Gradle 10

## 7.4.0
*May 5, 2025*

- OTel integration improvements
  - API to add custom Resource attributes
  - API to create Span Links
  - ANRs exported as spans to configured `SpanExporters`
- Enabled UI Load traces by default
- Updated OpenTelemetry API and SDK to `1.49.0`

## 7.3.0
*March 18, 2025*

- Improved app startup instrumentation
  - Updated name of root and child spans logged for cold and warm app startups
  - Added support for programmatic termination of app startup as opt-in configuration option
  - Record failed and abandoned app startup spans if the app crashes or backgrounds before startup completes
  - Added new annotation (`@IgnoreForStartup`) for trampoline or splash-screen Activities, which will make the app startup instrumentation ignore them for the purposes of ending a startup
  - Deprecated (`@StartupActivity`). This annotation will no longer affect how the app startup instrumentation works.
  - Fixed issue with app startup instrumentation recording on Android 12 for Activities that use Jetpack Compose Navigation
- Fail app build if symbol upload request fails
- Modified logic of uploading `ApplicationExitInfo` (non-visible change)
- Updated OpenTelemetry API and SDK to `1.48.0`

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
