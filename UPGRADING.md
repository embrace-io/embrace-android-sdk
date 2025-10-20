# Upgrade guide

# Upgrading from 7.x to 8.x

## Minimum supported versions

| Technology                              | Minimum supported version |
|-----------------------------------------|---------------------------|
| Kotlin                                  | 2.0.21                    |
| AGP                                     | 8.0.2                     |
| Gradle                                  | 8.0.2                     |
| JDK (build-time)                        | 17                        |
| sourceCompatibility/targetCompatibility | 11                        |
| minSdk                                  | 21                        |

## Notable features

The SDK now uses [opentelemetry-kotlin](https://github.com/embrace-io/opentelemetry-kotlin)'s API for capturing telemetry internally.
Under the hood [opentelemetry-java](https://github.com/open-telemetry/opentelemetry-java) is currently still responsible for processing the telemetry.

The new `embrace-android-otel-java` module provides a compatibility layer if you wish to use opentelemetry-java's APIs to perform operations
such as adding exporters.

## Removed APIs

Various deprecated APIs have been removed. Please migrate to the documented new APIs where applicable, or
get in touch if you do have a use-case that is no longer met.

### Embrace Android SDK removed APIs

| Old API                                                                 | New API                                                                                                |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `Embrace.getInstance().start(Context, AppFramework)`                    | `Embrace.getInstance().start(Context)`                                                                 |
| `Embrace.getInstance().addLogRecordExporter(LogRecordExporter)`         | Type changed to opentelemetry-kotlin API. Alternative available in `embrace-android-otel-java` module. |
| `Embrace.getInstance().addSpanExporter(SpanExporter)`                   | Type changed to opentelemetry-kotlin API. Alternative available in `embrace-android-otel-java` module. |
| `Embrace.getInstance().getOpenTelemetry()`                              | `Embrace.getInstance().getOpenTelemetryKotlin()` or `Embrace.getInstance().getJavaOpenTelemetry()`     |
| `Embrace.getInstance().setResourceAttribute(AttributeKey, String)`      | `Embrace.getInstance().setResourceAttribute(String, String)`                                           |
| `EmbraceSpan.addLink(SpanContext)`                                      | Type changed to symbol declared in embrace-android-sdk.                                                |
| `EmbraceSpan.addLink(SpanContext, Map)`                                 | Type changed to symbol declared in embrace-android-sdk.                                                |
| `EmbraceSpan.getSpanContext()`                                          | Type changed to symbol declared in embrace-android-sdk.                                                |
| `Embrace.getInstance().trackWebViewPerformance(String, ConsoleMessage)` | Obsolete - no alternative provided.                                                                    |
| `Embrace.getInstance().trackWebViewPerformance(String, String)`         | Obsolete - no alternative provided.                                                                    |
| `AppFramework`                                                          | Obsolete - no alternative provided.                                                                    |
| `StartupActivity`                                                       | Obsolete - no alternative provided.                                                                    |

### Embrace Gradle Plugin removed APIs

| Old API                                               | New API                                                                 |
|-------------------------------------------------------|-------------------------------------------------------------------------|
| `swazzler.disableDependencyInjection`                 | `embrace.autoAddEmbraceDependencies`                                    |
| `swazzler.disableComposeDependencyInjection`          | `embrace.autoAddEmbraceComposeClickDependency`                          |
| `swazzler.instrumentOkHttp`                           | `embrace.bytecodeInstrumentation.okhttpEnabled`                         |
| `swazzler.instrumentOnClick`                          | `embrace.bytecodeInstrumentation.onClickEnabled`                        |
| `swazzler.instrumentOnLongClick`                      | `embrace.bytecodeInstrumentation.onLongClickEnabled`                    |
| `swazzler.instrumentWebview`                          | `embrace.bytecodeInstrumentation.webviewOnPageStartedEnabled`           |
| `swazzler.instrumentFirebaseMessaging`                | `embrace.bytecodeInstrumentation.firebasePushNotificationsEnabled`      |
| `swazzler.classSkipList`                              | `embrace.bytecodeInstrumentation.classIgnorePatterns`                   |
| `swazzler.variantFilter`                              | `embrace.buildVariantFilter`                                            |
| `SwazzlerExtension.Variant.enabled`                   | `embrace.buildVariantFilter.disableBytecodeInstrumentationForVariant()` |
| `SwazzlerExtension.Variant.swazzlerOff`               | `embrace.buildVariantFilter.disablePluginForVariant()`                  |
| `SwazzlerExtension.Variant.setSwazzlingEnabled()`     | `embrace.buildVariantFilter.disableBytecodeInstrumentationForVariant()` |
| `SwazzlerExtension.Variant.disablePluginForVariant()` | `embrace.buildVariantFilter.disablePluginForVariant()`                  |
| `embrace.disableCollectBuildData`                     | `embrace.telemetryEnabled`                                              |
| `swazzler.customSymbolsDirectory`                     | `embrace.customSymbolsDirectory`                                        |
| `swazzler.forceIncrementalOverwrite`                  | Obsolete - no alternative provided.                                     |
| `swazzler.disableRNBundleRetriever`                   | Obsolete - no alternative provided.                                     |

# Upgrading to 7.3.0 with the new Embrace Gradle Plugin DSL

The Embrace Gradle Plugin previously had a DSL via the 'swazzler' extension. This has been replaced with a new DSL via the 'embrace'
extension.
You can still use the 'swazzler' extension but it is deprecated and will be removed in a future release. It's recommended you use one
extension or the other, rather than combining their use. Migration instructions are shown
below:

| Old API                                               | New API                                                                 |
|-------------------------------------------------------|-------------------------------------------------------------------------|
| `swazzler.disableDependencyInjection`                 | `embrace.autoAddEmbraceDependencies`                                    |
| `swazzler.disableComposeDependencyInjection`          | `embrace.autoAddEmbraceComposeClickDependency`                          |
| `swazzler.instrumentOkHttp`                           | `embrace.bytecodeInstrumentation.okhttpEnabled`                         |
| `swazzler.instrumentOnClick`                          | `embrace.bytecodeInstrumentation.onClickEnabled`                        |
| `swazzler.instrumentOnLongClick`                      | `embrace.bytecodeInstrumentation.onLongClickEnabled`                    |
| `swazzler.instrumentWebview`                          | `embrace.bytecodeInstrumentation.webviewOnPageStartedEnabled`           |
| `swazzler.instrumentFirebaseMessaging`                | `embrace.bytecodeInstrumentation.firebasePushNotificationsEnabled`      |
| `swazzler.classSkipList`                              | `embrace.bytecodeInstrumentation.classIgnorePatterns`                   |
| `swazzler.variantFilter`                              | `embrace.buildVariantFilter`                                            |
| `SwazzlerExtension.Variant.enabled`                   | `embrace.buildVariantFilter.disableBytecodeInstrumentationForVariant()` |
| `SwazzlerExtension.Variant.swazzlerOff`               | `embrace.buildVariantFilter.disablePluginForVariant()`                  |
| `SwazzlerExtension.Variant.setSwazzlingEnabled()`     | `embrace.buildVariantFilter.disableBytecodeInstrumentationForVariant()` |
| `SwazzlerExtension.Variant.disablePluginForVariant()` | `embrace.buildVariantFilter.disablePluginForVariant()`                  |
| `embrace.disableCollectBuildData`                     | `embrace.telemetryEnabled`                                              |
| `swazzler.customSymbolsDirectory`                     | `embrace.customSymbolsDirectory`                                        |
| `swazzler.forceIncrementalOverwrite`                  | Obsolete - no alternative provided.                                     |
| `swazzler.disableRNBundleRetriever`                   | Obsolete - no alternative provided.                                     |


The following project properties are now ignored and have no effect. You should remove them from your `gradle.properties` file:

- `embrace.logLevel`
- `embrace.instrumentationScope`

# Upgrading from 6.x to 7.x

Version 7 of the Embrace Android SDK contains the following breaking changes:

- The `startMoment/endMoment` API has been removed. Use `startSpan/recordSpan` instead.
- `Embrace.AppFramework` is now its own top level class, `AppFramework`
- `Embrace.LastRunEndState` is now its own top level class, `LastRunEndState`
- Several public APIs are now implemented in Kotlin rather than Java. Generally this will not affect backwards
  compatibility but the following may have slight changes to their signatures:
    - `EmbraceNetworkRequest` Java overloads replaced with default parameters
- View taps do not capture coordinates by default. Set `sdk_config.taps.capture_coordinates` to `true` in your
  `embrace-config.json` to enable this feature
- Several internally used classes and symbols have been hidden from the public API
- Recording a custom trace ID for an HTTP request from a custom request header is no longer supported. IDs in the
  `x-emb-trace-id` header will still be recorded and displayed on the dashboard.
- Methods to add and remove the `payer` Persona has been removed.
    - Use the generic Persona API methods with the name `payer` to get the equivalent functionality.
- The `setAppId` API has been removed. Changing the `appId` at runtime is no longer supported.
- Removed several obsolete remote config + local config properties. If you specify the below in your
  `embrace-config.json` they will be ignored:
    - `sdk_config.beta_features_enabled`
    - `sdk_config.anr.capture_google`
    - `sdk_config.background_activity.manual_background_activity_limit`
    - `sdk_config.background_activity.min_background_activity_duration`
    - `sdk_config.background_activity.max_cached_activities`
    - `sdk_config.base_urls.images`
    - `sdk_config.networking.trace_id_header`
    - `sdk_config.startup_moment.automatically_end`
- Removed the following properties from the Embrace Gradle plugin, that can be removed if they remain in your buildscripts:
    - `customSymbolsDirectory`
    - `jarSkipList`
    - `encodeExtractedFileNames`
- Embrace no longer attempts to detect other signal handlers & reinstall itself by default. If you notice changes in your NDK crash report
  quality you can re-enable this behavior by setting `sdk_config.sig_handler_detection` to `true` in your `embrace-config.json`

### Removed APIs

The following deprecated APIs have been removed:

| Old API                                                       | New API                                           |
|---------------------------------------------------------------|---------------------------------------------------|
| `Embrace.getInstance().clearUserAsPayer()`                    | `Embrace.getInstance().clearUserPersona("payer")` |
| `Embrace.getInstance().getSessionProperties()`                | N/A                                               |
| `Embrace.getInstance().getTraceIdHeader()`                    | N/A                                               |
| `Embrace.getInstance().isTracingAvailable()`                  | `Embrace.getInstance().isStarted()`               |
| `Embrace.getInstance().setAppId()`                            | N/A                                               |
| `Embrace.getInstance().setUserAsPayer()`                      | `Embrace.getInstance().addUserPersona("payer")`   |
| `Embrace.getInstance().start(Context, boolean)`               | `Embrace.getInstance().start(Context)`            |
| `Embrace.getInstance().start(Context, boolean, AppFramework)` | `Embrace.getInstance().isStarted(Context)`        |

# Upgrading from 5.x to 6.x

Version 6 of the Embrace Android SDK renames some functions. This has been done to reduce
confusion & increase consistency across our SDKs.

Functions that have been marked as deprecated will still work as before, but will be removed in
the next major version release. Please upgrade when convenient, and get in touch if you have a
use-case that isn’t supported by the new API.

| Old API                                               | New API                                                             | Comments                                                              |
|-------------------------------------------------------|---------------------------------------------------------------------|:----------------------------------------------------------------------|
| `Embrace.getInstance().startFragment(String)`         | `Embrace.getInstance().startView(String)`                           | Renamed function to better describe functionality.                    |
| `Embrace.getInstance().endFragment(String)`           | `Embrace.getInstance().endView(String)`                             | Renamed function to better describe functionality.                    |
| `Embrace.getInstance().setUserPersona(String)`        | `Embrace.getInstance().addUserPersona(String)`                      | Renamed function to better describe functionality.                    |
| `Embrace.getInstance().logBreadcrumb(String)`         | `Embrace.getInstance().addBreadcrumb(String)`                       | Renamed function to better describe functionality.                    |
| `Embrace.getInstance().startEvent()`                  | `Embrace.getInstance().startMoment(String)`                         | Renamed function to better describe functionality.                    |
| `Embrace.getInstance().endEvent()`                    | `Embrace.getInstance().endMoment(String)`                           | Renamed function to better describe functionality.                    |
| `Embrace.getInstance().logInfo(String, ...)`          | `Embrace.getInstance().logMessage(...)`                             | Altered function signature to standardise behavior.                   |
| `Embrace.getInstance().logWarning(String, ...)`       | `Embrace.getInstance().logMessage(...)`                             | Altered function signature to standardise behavior.                   |
| `Embrace.getInstance().logError(String, ...)`         | `Embrace.getInstance().logMessage(...)`                             | Altered function signature to standardise behavior.                   |
| `Embrace.getInstance().logError(Throwable)`           | `Embrace.getInstance().logException()`                              | Altered function signature to standardise behavior.                   |
| `Embrace.getInstance().logError(StacktraceElement[])` | `Embrace.getInstance().logCustomStacktrace()`                       | Altered function signature to standardise behavior.                   |
| `EmbraceLogger`                                       | `Embrace.getInstance().logMessage()`                                | Moved function calls to main Embrace interface.                       |
| `LogType`                                             | `Severity`                                                          | Use Severity enum rather than LogType.                                |
| `PurchaseFlow`                                        | None                                                                | Please contact Embrace if you have a use-case for this functionality. |
| `RegistrationFlow`                                    | None                                                                | Please contact Embrace if you have a use-case for this functionality. |
| `SubscriptionFlow`                                    | None                                                                | Please contact Embrace if you have a use-case for this functionality. |
| `Embrace.getInstance().logNetworkCall()`              | `Embrace.getInstance().recordNetworkRequest(EmbraceNetworkRequest)` | Renamed function to better describe functionality.                    |
| `Embrace.getInstance().logNetworkClientError()`       | `Embrace.getInstance().recordNetworkRequest(EmbraceNetworkRequest)` | Renamed function to better describe functionality.                    |

### Previously deprecated APIs that have been removed

| Old API                                     | Comments                                                 |
|---------------------------------------------|----------------------------------------------------------|
| `ConnectionQuality`                         | Deprecated API that is no longer supported.              |
| `ConnectionQualityListener`                 | Deprecated API that is no longer supported.              |
| `Embrace.enableStartupTracing()`            | Deprecated API that is no longer supported.              |
| `Embrace.enableEarlyAnrCapture()`           | Deprecated API that is no longer supported.              |
| `Embrace.setLogLevel()`                     | Deprecated API that is no longer supported.              |
| `Embrace.enableDebugLogging()`              | Deprecated API that is no longer supported.              |
| `Embrace.disableDebugLogging()`             | Deprecated API that is no longer supported.              |
| `Embrace.logUnhandledJsException()`         | Deprecated internal API that was unintentionally visible |
| `Embrace.logUnhandledUnityException()`      | Deprecated internal API that was unintentionally visible |
| `Embrace.setReactNativeVersionNumber()`     | Deprecated internal API that was unintentionally visible |
| `Embrace.setJavaScriptPatchNumber()`        | Deprecated internal API that was unintentionally visible |
| `Embrace.setJavaScriptBundleURL()`          | Deprecated internal API that was unintentionally visible |
| `Embrace.setUnityMetaData()`                | Deprecated internal API that was unintentionally visible |
| `Embrace.logDartError()`                    | Deprecated internal API that was unintentionally visible |
| `Embrace.logDartErrorWithType()`            | Deprecated internal API that was unintentionally visible |
| `Embrace.setEmbraceFlutterSdkVersion()`     | Deprecated internal API that was unintentionally visible |
| `Embrace.setDartVersion()`                  | Deprecated internal API that was unintentionally visible |
| `Embrace.addConnectionQualityListener()`    | Deprecated internal API that was unintentionally visible |
| `Embrace.removeConnectionQualityListener()` | Deprecated internal API that was unintentionally visible |
| `Embrace.logPushNotification()`             | Deprecated internal API that was unintentionally visible |
| `EmbraceNetworkRequest.withByteIn()`        | Use `withBytesIn()` instead                              |
| `EmbraceNetworkRequest.withByteOut()`       | Use `withBytesOut()` instead                             |
| `EmbraceNetworkRequestV2.withByteIn()`      | Use `withBytesIn()` instead                              |
| `EmbraceNetworkRequestV2.withByteOut()`     | Use `withBytesOut()` instead                             |

## Hidden symbols

The following symbols have been hidden in version 6 of the Embrace Android SDK. These were
unintentionally
exposed in previous versions. Please get in touch if you had a use-case for these symbols that isn't
supported with the new API.

- `Absent`
- `ActivityListener`
- `AndroidToUnityCallback`
- `ApkToolsConfig`
- `BuildInfo`
- `CheckedBiConsumer`
- `CheckedBiFunction`
- `CheckedBinaryOperator`
- `CheckedBiPredicate`
- `CheckedConsumer`
- `CheckedFunction`
- `CheckedPredicate`
- `CheckedRunnable`
- `CheckedSupplier`
- `CountingOutputStream`
- `Embrace.<init>`
- `EmbraceConnection`
- `EmbraceHttpUrlConnection`
- `EmbraceHttpUrlConnectionOverride`
- `EmbraceHttpUrlStreamHandler`
- `EmbraceHttpsUrlConnection`
- `EmbraceHttpsUrlStreamHandler`
- `EmbraceUrl`
- `EmbraceUrlStreamHandler`
- `EmbraceUrlStreamHandlerFactory`
- `EmbraceEvent`
- `EmbraceConnectionImpl`
- `EmbraceUrlAdapter`
- `EmbraceUrlImpl`
- `Event`
- `ExecutorUtils`
- `HandleExceptionError`
- `NetworkCaptureEncryptionManager`
- `Optional`
- `Preconditions`
- `Present`
- `RnActionBreadcrumb`
- `ThreadUtils`
- `Unchecked`
- `Uuid`
