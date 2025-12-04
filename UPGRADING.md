# Upgrade guide

# Upgrading from 7.x to 8.x

## Quick Start

If you wish to do the upgrade using step-by-step instructions that will work for most apps, follow this `Quick Start` guide. Scroll down to the rest of this guide to see all the changes described in greater detail.

1. **Change Embrace version**
    - Set the version of the Embrace Android SDK to `{{ embrace_sdk_version platform="android" }}`.

2. **Update dependency versions**
    - Ensure app dependencies like Gradle, AGP, Kotlin, and JDK meet [the new minimum version requirements](#minimum-supported-versions).
    - In addition, check Java and Kotlin language compatibility targets are at least `11` and `2.0`, respectively.
    - Check `android.compileOptions` and `android.kotlin` in your app's Gradle file to verify that the specified versions are supported.

3. **Update references to Embrace Gradle Plugin**
    - Replace Embrace Gradle Plugin artifact and plugin ID names:
        - Where you specify the artifact ID (i.e. `embrace-swazzler`), replace it with `embrace-gradle-plugin`.
        - Where you specify the plugin ID (i.e. `io.embrace.swazzler`), replace it with `io.embrace.gradle`.

4. **Update Embrace Gradle Plugin DSL references**
    - In your app's Gradle file, replace `swazzler {}` with `embrace {}`
    - Replace the attributes that were changed. See the [New Embrace Gradle Plugin DSL](#new-embrace-gradle-plugin-dsl) section for the full list of changes.

5. **Update Embrace module dependencies**
    - Search your app's TOML (Version Catalogue) and Gradle files for module references that start with `io.embrace` and update the module names that were changed.
        - See the [Internal Modularization](#internal-modularization) for the full list.
    - If you use OkHttp and want network requests made through it to be instrumented, add a dependency to `io.embrace:embrace-android-instrumentation-okhttp`.
    - If you use the SDK's Java OpenTelemetry implementation or add custom `Span` and `LogRecord` exporters, add a dependency to `io.embrace:embrace-android-otel-java`.

6. **Run Gradle sync**
    - Verify the sync runs successfully with no errors.

7. **Compile the app and fix errors**
    - You may get some build errors due to symbols that were renamed, moved, or deleted.
        - See the full list of symbols that were renamed in the [Altered API](#altered-apis) section.
    - Fix compilation errors as they show up.
        - Start by removing all import statements that references unknown symbols.
        - Any moved but not renamed symbols can be re-imported.
        - For deleted symbols, remove any references to them from your code.
        - For renamed symbols, find the new names and replace them in your code.
        - For method calls in Java, you may need to add in additional parameters, as some overloads were removed.
    - Recompile after the identified errors were fixed until there are no more errors.
        - Alternatively, you can proactively look for errors in your code and fix them preemptively to reduce the number of iterations.

8. **Fix deprecation warnings** (optional but recommended)
    - Deprecated methods should be replaced with the suggested alternatives to ensure a smoother upgrade in the future.
    - The bulk of this can be achieved by replaces calls to `Embrace.getInstance()` with `Embrace`.

9. **Build and install your app**
    - Verify the migration was successful by sending a test session and seeing it on the dashboard.

## Minimum supported versions

The Embrace Android SDK supports the following minimum versions of dependent technologies at build and run time:

| Technology                       | Old minimum version | New minimum version |
|----------------------------------|---------------------|---------------------|
| JDK (Build-time)                 | 11                  | 17                  |
| Kotlin (Run-time and build-time) | 1.8.22              | 2.0.21              |
| Gradle (minSdk 26+)              | 7.5.1               | 8.0.2               |
| AGP (minSdk 26+)                 | 7.4.2               | 8.0.2               |

At runtime, the minimum supported Android version remains 5.0 (i.e. `minSdk` = 21). If your minSdk is less than 26, you will still require
Gradle 8.4 and AGP 8.3.0 to workaround a desugaring issue.

## Language compatibility target

The Embrace Android SDK is compiled using the following language compatibility targets:

| Technology                | Old target | New target |
|---------------------------|------------|------------|
| Java                      | 1.8        | 11         |
| Kotlin (Language and API) | 1.8        | 2.0        |

## Embrace Gradle Plugin renamed

The Embrace Gradle Plugin artifact and plugin ID have been renamed:

| Type        | Old name                      | New name                           |
|-------------|-------------------------------|------------------------------------|
| Artifact ID | `io.embrace:embrace-swazzler` | `io.embrace:embrace-gradle-plugin` |
| Plugin ID   | `io.embrace.swazzler`         | `io.embrace.gradle`                |

Replace references to the old Embrace Gradle Plugin name with the new one. Your configuration files should reference the plugin in one
of the following ways:

**Version Catalogs (gradle/libs.versions.toml):**

```toml
[plugins]
embrace = { id = "io.embrace.gradle", version.ref = "embrace" }
```

**Non-Catalog Configuration (settings.gradle or settings.gradle.kts):**

```kotlin
pluginManagement {
    plugins {
        id("io.embrace.gradle") version "${embrace_version}"
    }
}
```

**Legacy Buildscript (root build.gradle):**

```groovy
buildscript {
    dependencies {
        classpath "io.embrace:embrace-gradle-plugin:${embrace_version}"
    }
}
```

## OpenTelemetry support evolution

The SDK is moving towards supporting OpenTelemetry via a native Kotlin
API ([opentelemetry-kotlin](https://github.com/embrace-io/opentelemetry-kotlin)). Currently, the Kotlin API
is used internally for capturing telemetry, but under the hood [opentelemetry-java](https://github.com/open-telemetry/opentelemetry-java) is
still responsible for processing the telemetry.

To use the Kotlin API in your app, which is in the process of being donated to OpenTelemetry and is still subject to changes before it
becomes stable, you must include the module `io.embrace.opentelemetry.kotlin:opentelemetry-kotlin-api` in your app. With that, you can
call the `Embrace.getOpenTelemetryKotlin()` function once the Embrace SDK is initialized to obtain an `OpenTelemetry` object with which
you can instantiate OTel Tracers and Loggers. You can also add your own implementations of Kotlin Span and Log exporters, then configure
the Embrace Android SDK to use them by calling them `Embrace.addSpanExporter()` and `Embrace.addLogRecordExporter()`.

As part of this evolution, opentelemetry-java's API are not used internally or exported by default by the Embrace SDK. A compatibility
layer is provided for those who currently use the Java OTel Tracing API or Java exporters with the Embrace SDK. To access that,
include the new `io.embrace:embrace-android-otel-java` module in your app's classpath, which adds the following extension functions
to the `Embrace` object:

- `getJavaOpenTelemetry()`
- `addJavaSpanExporter()`
- `addJavaLogRecordExporter()`

These methods allow you to use the associated Java OTel API integration points supported by older versions of the Embrace Android SDK.

## Embrace.getInstance() deprecation

`Embrace.getInstance()` is deprecated in favour of `Embrace`. For example, you can now call `Embrace.start(Context)` instead
of `Embrace.getInstance().start(Context)`.

## Internal modularization

The Embrace SDK is going through a process of modularization. If you rely on `embrace-android-sdk` then you should notice
very little change as most alterations are internal. However, you should be aware that:

- `embrace-android-compose` has been renamed as `embrace-android-instrumentation-compose-tap`
- `embrace-android-fcm` has been renamed as `embrace-android-instrumentation-fcm`
- `embrace-android-okhttp3` has been renamed as `embrace-android-instrumentation-okhttp`

`embrace.autoAddEmbraceDependencies` is deprecated and will be removed in a future release. You should add the
`io.embrace:embrace-android-sdk` module to your classpath manually if you reference any Embrace Android SDK API directly in your app.

## Http(s)URLConnection network request instrumentation changes

Basic instrumentation for HTTPS network requests made using the `HttpsURLConnection` API is enabled by default and controlled by
the `sdk_config.networking.enable_huc_lite_instrumentation` configuration flag. It will capture the request duration, status, as well
as errors. It does not support advanced features like network request forwarding or request or response body sizes. Use the detailed
instrumentation if you wish to enable those features.

Detailed instrumentation for both HTTP and HTTPS requests using the `HttpURLConnection` and `HttpsURLConnection` APIs, which was enabled
by default in previous versions, is now disabled by default. The configuration flag to control this
remains `sdk_config.networking.enable_native_monitoring`.

If you wish to use the detailed instrumentation for these requests, you must include the
module `io.embrace:embrace-android-instrumentation-huc` in your app's classpath.

This DOES NOT affect instrumentation of network requests made using `OkHttp`.

## Altered APIs

Various deprecated APIs have been removed. Please migrate to the documented new APIs where applicable, or
get in touch if you do have a use-case that is no longer met.

### Embrace Android SDK removed APIs

| Old API                                                                 | New API                                                                                                |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `Embrace.getInstance().start(Context, AppFramework)`                    | `Embrace.start(Context)`                                                                               |
| `Embrace.getInstance().addLogRecordExporter(LogRecordExporter)`         | Type changed to opentelemetry-kotlin API. Alternative available in `embrace-android-otel-java` module. |
| `Embrace.getInstance().addSpanExporter(SpanExporter)`                   | Type changed to opentelemetry-kotlin API. Alternative available in `embrace-android-otel-java` module. |
| `Embrace.getInstance().getOpenTelemetry()`                              | `Embrace.getOpenTelemetryKotlin()` or `Embrace.getJavaOpenTelemetry()`                                 |
| `Embrace.getInstance().setResourceAttribute(AttributeKey, String)`      | `Embrace.setResourceAttribute(String, String)`                                                         |
| `EmbraceSpan.addLink(SpanContext)`                                      | Type changed to symbol declared in embrace-android-sdk.                                                |
| `EmbraceSpan.addLink(SpanContext, Map)`                                 | Type changed to symbol declared in embrace-android-sdk.                                                |
| `EmbraceSpan.getSpanContext()`                                          | Type changed to symbol declared in embrace-android-sdk.                                                |
| `Embrace.getInstance().trackWebViewPerformance(String, ConsoleMessage)` | Obsolete - no alternative provided.                                                                    |
| `Embrace.getInstance().trackWebViewPerformance(String, String)`         | Obsolete - no alternative provided.                                                                    |
| `AppFramework`                                                          | Obsolete - no alternative provided.                                                                    |
| `StartupActivity`                                                       | Obsolete - no alternative provided.                                                                    |
| `Embrace.getInstance().registerComposeActivityListener()`               | Obsolete - no alternative provided.                                                                    |
| `Embrace.getInstance().unregisterComposeActivityListener()`             | Obsolete - no alternative provided.                                                                    |
| `Embrace.getInstance().logWebView(String)`                              | Obsolete - no alternative provided.                                                                    |

### New Embrace Gradle Plugin DSL

The Embrace Gradle Plugin previously had a DSL via the `swazzler` extension. This has been replaced with a new DSL via the `embrace`
extension.

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

### Embrace Android SDK overload changes

The following functions had overloads manually defined. These have been replaced with one function
that uses Kotlin's default parameter values.

| Altered APIs                                  |
|-----------------------------------------------|
| `Embrace.getInstance().logCustomStacktrace()` |
| `Embrace.getInstance().logException()`        |
| `Embrace.getInstance().logMessage()`          |
| `Embrace.getInstance().createSpan()`          |
| `Embrace.getInstance().recordCompletedSpan()` |
| `Embrace.getInstance().recordSpan()`          |
| `Embrace.getInstance().startSpan()`           |
| `Embrace.getInstance().endSession()`          |
| `EmbraceSpan.addEvent()`                      |
| `EmbraceSpan.addLink()`                       |
| `EmbraceSpan.recordException()`               |
| `EmbraceSpan.start()`                         |
| `EmbraceSpan.stop()`                          |

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
| `Embrace.getInstance().logInfo(String, ...)`          | `Embrace.getInstance().logMessage(...)`                             | Altered function signature to standardize behavior.                   |
| `Embrace.getInstance().logWarning(String, ...)`       | `Embrace.getInstance().logMessage(...)`                             | Altered function signature to standardize behavior.                   |
| `Embrace.getInstance().logError(String, ...)`         | `Embrace.getInstance().logMessage(...)`                             | Altered function signature to standardize behavior.                   |
| `Embrace.getInstance().logError(Throwable)`           | `Embrace.getInstance().logException()`                              | Altered function signature to standardize behavior.                   |
| `Embrace.getInstance().logError(StacktraceElement[])` | `Embrace.getInstance().logCustomStacktrace()`                       | Altered function signature to standardize behavior.                   |
| `EmbraceLogger`                                       | `Embrace.getInstance().logMessage()`                                | Moved function calls to main Embrace interface.                       |
| `LogType`                                             | `Severity`                                                          | Use Severity enum rather than LogType.                                |
| `PurchaseFlow`                                        | None                                                                | Please contact Embrace if you have a use-case for this functionality. |
| `RegistrationFlow`                                    | None                                                                | Please contact Embrace if you have a use-case for this functionality. |
| `SubscriptionFlow`                                    | None                                                                | Please contact Embrace if you have a use-case for this functionality. |
| `Embrace.getInstance().logNetworkCall()`              | `Embrace.getInstance().recordNetworkRequest(EmbraceNetworkRequest)` | Renamed function to better describe functionality.                    |
| `Embrace.getInstance().logNetworkClientError()`       | `Embrace.getInstance().recordNetworkRequest(EmbraceNetworkRequest)` | Renamed function to better describe functionality.                    |

## Previously deprecated APIs that have been removed

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
