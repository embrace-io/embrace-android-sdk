package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.payload.NetworkCallV2

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

    internal class AeiLog(message: AppExitInfoData) : SchemaType(EmbType.System.Exit) {
        override val attrs = mapOf(
            "session-id" to message.sessionId,
            "session-id-error" to message.sessionIdError,
            "process-importance" to message.importance.toString(),
            "pss" to message.pss.toString(),
            "rs" to message.reason.toString(),
            "rss" to message.rss.toString(),
            "exit-status" to message.status.toString(),
            "timestamp" to message.timestamp.toString(),
            "description" to message.description,
            "trace-status" to message.traceStatus
        ).toNonNullMap()
    }

    internal class NetworkRequest(networkCallV2: NetworkCallV2) : SchemaType(EmbType.Performance.Network) {
        override val attrs = mapOf(
            "url.full" to networkCallV2.url,
            "http.request.method" to networkCallV2.httpMethod,
            "http.response.status_code" to networkCallV2.responseCode.toString(),
            "http.request.body.size" to networkCallV2.bytesSent.toString(),
            "http.response.body.size" to networkCallV2.bytesReceived.toString(),
            "error.type" to networkCallV2.errorType,
            "error.message" to networkCallV2.errorMessage,
            "emb.w3c_traceparent" to networkCallV2.w3cTraceparent
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
}
