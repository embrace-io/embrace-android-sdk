package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.embrace.android.embracesdk.payload.AppExitInfoData

/**
 * The collections of attribute schemas used by the associated telemetry types.
 *
 * Each schema contains a [TelemetryType] that it is being applied to, as well as an optional [fixedObjectName] used for the recorded
 * telemetry data object if the same, fixed name is used for every instance.
 */
internal sealed class SchemaType(
    val telemetryType: TelemetryType,
    val fixedObjectName: String = "",
) {
    protected abstract val attrs: Map<String, String>

    /**
     * The attributes defined for this schema that should be used to populate telemetry objects
     */
    fun attributes(): Map<String, String> = attrs.plus(telemetryType.toEmbraceKeyValuePair())

    internal class Breadcrumb(message: String) : SchemaType(
        telemetryType = EmbType.System.Breadcrumb,
        fixedObjectName = "breadcrumb"
    ) {
        override val attrs = mapOf("message" to message)
    }

    internal class View(viewName: String) : SchemaType(
        telemetryType = EmbType.Ux.View,
        fixedObjectName = "screen-view"
    ) {
        override val attrs = mapOf("view.name" to viewName)
    }

    /**
     * Represents a span in which a thread was blocked.
     */
    internal class ThreadBlockage(
        threadPriority: Int,
        lastKnownTimeMs: Long,
        intervalCode: Int
    ) : SchemaType(
        telemetryType = EmbType.Performance.ThreadBlockage,
        fixedObjectName = "thread_blockage"
    ) {
        override val attrs = mapOf(
            "thread_priority" to threadPriority.toString(),
            "last_known_time_unix_nano" to lastKnownTimeMs.millisToNanos().toString(),
            "interval_code" to intervalCode.toString()
        )
    }

    /**
     * Represents a point in time when a thread was blocked.
     */
    internal class ThreadBlockageSample(
        sampleOverheadMs: Long,
        frameCount: Int,
        stacktrace: String,
        sampleCode: Int,
        threadState: Thread.State
    ) : SchemaType(
        telemetryType = EmbType.Performance.ThreadBlockageSample,
        fixedObjectName = "thread_blockage_sample"
    ) {
        override val attrs = mapOf(
            "sample_overhead" to sampleOverheadMs.millisToNanos().toString(),
            "frame_count" to frameCount.toString(),
            "stacktrace" to stacktrace,
            "sample_code" to sampleCode.toString(),
            "thread_state" to threadState.toString()
        )
    }

    /**
     * Represents a push notification event.
     * @param viewName The name of the view that the tap event occurred in.
     * @param type The type of tap event. "tap"/"long_press". "tap" is the default.
     * @param coords The coordinates of the tap event.
     */
    internal class PushNotification(
        title: String?,
        type: String?,
        body: String?,
        id: String,
        from: String?,
        priority: Int
    ) : SchemaType(
        telemetryType = EmbType.System.PushNotification,
        fixedObjectName = "push-notification"
    ) {
        override val attrs = mapOf(
            "notification.title" to title,
            "notification.type" to type,
            "notification.body" to body,
            "notification.id" to id,
            "notification.from" to from,
            "notification.priority" to priority.toString()
        ).toNonNullMap()
    }

    /**
     * Represents a tap breadcrumb event.
     * @param viewName The name of the view that the tap event occurred in.
     * @param type The type of tap event. "tap"/"long_press". "tap" is the default.
     * @param coords The coordinates of the tap event.
     */
    internal class Tap(
        viewName: String?,
        type: String = "tap",
        coords: String
    ) : SchemaType(
        telemetryType = EmbType.Ux.Tap,
        fixedObjectName = "ui-tap"
    ) {
        override val attrs = mapOf(
            "view.name" to viewName,
            "tap.type" to type,
            "tap.coords" to coords
        ).toNonNullMap()
    }

    internal class WebViewUrl(
        url: String
    ) : SchemaType(
        telemetryType = EmbType.Ux.WebView,
        fixedObjectName = "web-view"
    ) {
        override val attrs = mapOf(
            "webview.url" to url
        ).toNonNullMap()
    }

    internal class MemoryWarning : SchemaType(EmbType.Performance.MemoryWarning) {
        override val attrs = emptyMap<String, String>()
    }

    internal class AeiLog(message: AppExitInfoData) : SchemaType(EmbType.System.Exit) {
        override val attrs = mapOf(
            "aei_session_id" to message.sessionId,
            "session_id_error" to message.sessionIdError,
            "process_importance" to message.importance.toString(),
            "pss" to message.pss.toString(),
            "reason" to message.reason.toString(),
            "rss" to message.rss.toString(),
            "exit_status" to message.status.toString(),
            "timestamp" to message.timestamp.toString(),
            "description" to message.description,
            "trace_status" to message.traceStatus
        ).toNonNullMap()
    }

    internal class Log(attributes: TelemetryAttributes) : SchemaType(EmbType.System.Log) {
        override val attrs = attributes.snapshot()
    }

    internal class Exception(attributes: TelemetryAttributes) : SchemaType(EmbType.System.Exception) {
        override val attrs = attributes.snapshot()
    }

    internal class FlutterException(attributes: TelemetryAttributes) : SchemaType(EmbType.System.FlutterException) {
        override val attrs = attributes.snapshot()
    }

    internal class Crash(attributes: TelemetryAttributes) : SchemaType(EmbType.System.Crash) {
        override val attrs = attributes.snapshot()
    }

    internal class ReactNativeCrash(attributes: TelemetryAttributes) : SchemaType(EmbType.System.ReactNativeCrash) {
        override val attrs = attributes.snapshot()
    }

    internal class NativeCrash(attributes: TelemetryAttributes) : SchemaType(EmbType.System.NativeCrash) {
        override val attrs = attributes.snapshot()
    }

    internal object LowPower : SchemaType(
        telemetryType = EmbType.System.LowPower,
        fixedObjectName = "device-low-power"
    ) {
        override val attrs = emptyMap<String, String>()
    }
}
