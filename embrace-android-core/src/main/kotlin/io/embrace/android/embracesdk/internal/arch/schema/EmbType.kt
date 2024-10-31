package io.embrace.android.embracesdk.internal.arch.schema

sealed class EmbType(type: String, subtype: String?) : TelemetryType {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey(id = "type")
    override val value: String = type + (subtype?.run { ".$this" } ?: "")
    override val sendMode: SendMode = SendMode.DEFAULT

    /**
     * Keys that track how fast a time interval is. Only applies to spans.
     */
    sealed class Performance(subtype: String?) : EmbType("perf", subtype) {

        object Default : Performance(null)

        object Network : Performance("network_request")

        object ThreadBlockage : Performance("thread_blockage")

        object ThreadBlockageSample : Performance("thread_blockage_sample")

        object MemoryWarning : Performance("memory_warning")

        object NativeThreadBlockage : Performance("native_thread_blockage")

        object NativeThreadBlockageSample : Performance("native_thread_blockage_sample")

        object ThermalState : Performance("thermal_state")

        object ActivityOpen : Performance("activity_open")
    }

    /**
     * Keys that track telemetry that is explicitly tied to user behaviour or visual in nature.
     * Applies to spans, logs, and span events.
     */
    sealed class Ux(subtype: String) : EmbType("ux", subtype) {

        object Session : Ux("session")

        object View : Ux("view")

        object Tap : Ux("tap")

        object WebView : Ux("webview")
    }

    /**
     * Keys that track telemetry that is not explicitly tied to user behaviour and is not visual in nature.
     * Applies to spans, logs, and span events.
     */
    sealed class System(
        subtype: String,
        override val sendMode: SendMode = SendMode.DEFAULT,
    ) : EmbType("sys", subtype) {

        object Breadcrumb : System("breadcrumb")

        object Log : System("log")

        object Exception : System("exception")

        object InternalError : System("internal")

        object FlutterException : System("flutter_exception") {
            /**
             * Attribute name for the exception context in a log representing an exception
             */
            val embFlutterExceptionContext: EmbraceAttributeKey = EmbraceAttributeKey("exception.context")

            /**
             * Attribute name for the exception library in a log representing an exception
             */
            val embFlutterExceptionLibrary: EmbraceAttributeKey = EmbraceAttributeKey("exception.library")
        }

        object Exit : System("exit", SendMode.IMMEDIATE)

        object PushNotification : System("push_notification")

        object Crash : System("android.crash", SendMode.DEFER) {
            /**
             * The list of [Throwable] that caused the exception responsible for a crash
             */
            val embAndroidCrashExceptionCause: EmbraceAttributeKey =
                EmbraceAttributeKey("android.crash.exception_cause")
        }

        object ReactNativeCrash : System("android.react_native_crash", SendMode.DEFER) {
            /**
             * The JavaScript unhandled exception from the ReactNative layer
             */
            val embAndroidReactNativeCrashJsException: EmbraceAttributeKey = EmbraceAttributeKey(
                "android.react_native_crash.js_exception"
            )
        }

        object ReactNativeAction : System("rn_action")

        object NativeCrash : System("android.native_crash", SendMode.DEFER) {
            /**
             * Exception coming from the native layer
             */
            val embNativeCrashException: EmbraceAttributeKey =
                EmbraceAttributeKey("android.native_crash.exception")

            /**
             * Native symbols used to symbolicate a native crash
             */
            val embNativeCrashSymbols: EmbraceAttributeKey = EmbraceAttributeKey("android.native_crash.symbols")

            /**
             * Errors associated with the native crash
             */
            val embNativeCrashErrors: EmbraceAttributeKey = EmbraceAttributeKey("android.native_crash.errors")

            /**
             * Error encountered during stack unwinding
             */
            val embNativeCrashUnwindError: EmbraceAttributeKey =
                EmbraceAttributeKey("android.native_crash.unwind_error")
        }

        object LowPower : System("low_power")

        object NetworkCapturedRequest : System("network_capture", SendMode.IMMEDIATE)

        object NetworkStatus : System("network_status")

        object WebViewInfo : System("webview_info")
    }
}
