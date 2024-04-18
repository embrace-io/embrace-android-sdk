package io.embrace.android.embracesdk.arch.schema

internal sealed class EmbType(type: String, subtype: String?) : TelemetryType {
    override val key = EmbraceAttributeKey(id = "type")
    override val value = type + (subtype?.run { ".$this" } ?: "")
    override val sendImmediately: Boolean = false

    /**
     * Keys that track how fast a time interval is. Only applies to spans.
     */
    internal sealed class Performance(subtype: String?) : EmbType("perf", subtype) {

        internal object Default : Performance(null)

        internal object Network : Performance("network_request")

        internal object ThreadBlockage : Performance("thread_blockage")

        internal object ThreadBlockageSample : Performance("thread_blockage_sample")

        internal object MemoryWarning : Performance("memory_warning")

        internal object NativeThreadBlockage : Performance("native_thread_blockage")

        internal object NativeThreadBlockageSample : Performance("native_thread_blockage_sample")
    }

    /**
     * Keys that track telemetry that is explicitly tied to user behaviour or visual in nature.
     * Applies to spans, logs, and span events.
     */
    internal sealed class Ux(subtype: String) : EmbType("ux", subtype) {

        internal object Session : Ux("session")

        internal object View : Ux("view")

        internal object Tap : Ux("tap")

        internal object WebView : Ux("webview")
    }

    /**
     * Keys that track telemetry that is not explicitly tied to user behaviour and is not visual in nature.
     * Applies to spans, logs, and span events.
     */
    internal sealed class System(
        subtype: String,
        override val sendImmediately: Boolean = false
    ) : EmbType("sys", subtype) {

        internal object Breadcrumb : System("breadcrumb")

        internal object Log : System("log")

        internal object Exception : System("exception")

        internal object FlutterException : System("flutter_exception") {
            /**
             * Attribute name for the exception context in a log representing an exception
             */
            val embFlutterExceptionContext = EmbraceAttributeKey("exception.context")

            /**
             * Attribute name for the exception library in a log representing an exception
             */
            val embFlutterExceptionLibrary = EmbraceAttributeKey("exception.library")
        }

        internal object Exit : System("exit", true)

        internal object PushNotification : System("push_notification")

        internal object Crash : System("android.crash", true) {
            /**
             * The list of [Throwable] that caused the exception responsible for a crash
             */
            val embAndroidCrashExceptionCause = EmbraceAttributeKey("android.crash.exception_cause")
        }

        internal object ReactNativeCrash : System("android.react_native_crash", true) {
            /**
             * The JavaScript unhandled exception from the ReactNative layer
             */
            val embAndroidReactNativeCrashJsException = EmbraceAttributeKey("android.react_native_crash.js_exception")
        }

        internal object NativeCrash : System("android.native_crash", true) {
            /**
             * Exception coming from the native layer
             */
            val embNativeCrashException = EmbraceAttributeKey("android.native_crash.exception")

            /**
             * Native symbols used to symbolicate a native crash
             */
            val embNativeCrashSymbols = EmbraceAttributeKey("android.native_crash.symbols")

            /**
             * Errors associated with the native crash
             */
            val embNativeCrashErrors = EmbraceAttributeKey("android.native_crash.errors")

            /**
             * Error encountered during stack unwinding
             */
            val embNativeCrashUnwindError = EmbraceAttributeKey("android.native_crash.unwind_error")
        }

        internal object LowPower : System("low_power")
    }
}

/**
 * Represents a telemetry type (emb.type). For example, "ux.view" is a type that represents
 * a visual event around a UI element. ux is the type, and view is the subtype. This tells the
 * backend that it can assume the data in the event follows a particular schema.
 */
internal interface TelemetryType : FixedAttribute {
    val sendImmediately: Boolean
}
