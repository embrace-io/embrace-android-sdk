package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public sealed class EmbType(type: String, subtype: String?) : TelemetryType {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey(id = "type")
    override val value: String = type + (subtype?.run { ".$this" } ?: "")
    override val sendImmediately: Boolean = false

    /**
     * Keys that track how fast a time interval is. Only applies to spans.
     */
    public sealed class Performance(subtype: String?) : EmbType("perf", subtype) {

        public object Default : Performance(null)

        public object Network : Performance("network_request")

        public object ThreadBlockage : Performance("thread_blockage")

        public object ThreadBlockageSample : Performance("thread_blockage_sample")

        public object MemoryWarning : Performance("memory_warning")

        public object NativeThreadBlockage : Performance("native_thread_blockage")

        public object NativeThreadBlockageSample : Performance("native_thread_blockage_sample")

        public object ThermalState : Performance("thermal_state")
    }

    /**
     * Keys that track telemetry that is explicitly tied to user behaviour or visual in nature.
     * Applies to spans, logs, and span events.
     */
    public sealed class Ux(subtype: String) : EmbType("ux", subtype) {

        public object Session : Ux("session")

        public object View : Ux("view")

        public object Tap : Ux("tap")

        public object WebView : Ux("webview")
    }

    /**
     * Keys that track telemetry that is not explicitly tied to user behaviour and is not visual in nature.
     * Applies to spans, logs, and span events.
     */
    public sealed class System(
        subtype: String,
        override val sendImmediately: Boolean = false
    ) : EmbType("sys", subtype) {

        public object Breadcrumb : System("breadcrumb")

        public object Log : System("log")

        public object Exception : System("exception")

        public object InternalError : System("internal")

        public object FlutterException : System("flutter_exception") {
            /**
             * Attribute name for the exception context in a log representing an exception
             */
            public val embFlutterExceptionContext: EmbraceAttributeKey = EmbraceAttributeKey("exception.context")

            /**
             * Attribute name for the exception library in a log representing an exception
             */
            public val embFlutterExceptionLibrary: EmbraceAttributeKey = EmbraceAttributeKey("exception.library")
        }

        public object Exit : System("exit", true)

        public object PushNotification : System("push_notification")

        public object Crash : System("android.crash", true) {
            /**
             * The list of [Throwable] that caused the exception responsible for a crash
             */
            public val embAndroidCrashExceptionCause: EmbraceAttributeKey =
                EmbraceAttributeKey("android.crash.exception_cause")
        }

        public object ReactNativeCrash : System("android.react_native_crash", true) {
            /**
             * The JavaScript unhandled exception from the ReactNative layer
             */
            public val embAndroidReactNativeCrashJsException: EmbraceAttributeKey = EmbraceAttributeKey(
                "android.react_native_crash.js_exception"
            )
        }

        public object ReactNativeAction : System("rn_action", true)

        public object NativeCrash : System("android.native_crash", true) {
            /**
             * Exception coming from the native layer
             */
            public val embNativeCrashException: EmbraceAttributeKey =
                EmbraceAttributeKey("android.native_crash.exception")

            /**
             * Native symbols used to symbolicate a native crash
             */
            public val embNativeCrashSymbols: EmbraceAttributeKey = EmbraceAttributeKey("android.native_crash.symbols")

            /**
             * Errors associated with the native crash
             */
            public val embNativeCrashErrors: EmbraceAttributeKey = EmbraceAttributeKey("android.native_crash.errors")

            /**
             * Error encountered during stack unwinding
             */
            public val embNativeCrashUnwindError: EmbraceAttributeKey =
                EmbraceAttributeKey("android.native_crash.unwind_error")
        }

        public object LowPower : System("low_power")

        public object Sigquit : System("sigquit")

        public object NetworkCapturedRequest : System("network_capture", true)

        public object NetworkStatus : System("network_status")

        public object WebViewInfo : System("webview_info")
    }
}
