# Upgrade guide

# Upgrading from 6.x to 7.x

Version 7 of the Embrace Android SDK contains the following breaking changes:

- The `startMoment/endMoment` API has been removed. Use `startSpan/recordSpan` instead.
- `Embrace.AppFramework` is now its own top level class, `AppFramework`
- `Embrace.LastRunEndState` is now its own top level class, `LastRunEndState`
- Several public APIs are now implemented in Kotlin rather than Java. Generally this will not affect backwards compatibility but the following may have slight changes to their signatures:
  - `EmbraceNetworkRequest` Java overloads replaced with default parameters

### Removed APIs

The following deprecated APIs have been removed:

| Old API                                                       | New API                                    |
|---------------------------------------------------------------|--------------------------------------------|
| `Embrace.getInstance().getSessionProperties()`                | N/A                                        |
| `Embrace.getInstance().isTracingAvailable()`                  | `Embrace.getInstance().isStarted()`        |
| `Embrace.getInstance().start(Context, boolean)`               | `Embrace.getInstance().start(Context)`     |
| `Embrace.getInstance().start(Context, boolean, AppFramework)` | `Embrace.getInstance().isStarted(Context)` |

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
