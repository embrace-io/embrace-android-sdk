package io.embrace.android.embracesdk.arch.schema

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.spans.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.payload.NetworkCapturedCall
import io.embrace.android.embracesdk.utils.NetworkUtils.getValidTraceId
import io.embrace.android.embracesdk.utils.NetworkUtils.stripUrl
import io.opentelemetry.semconv.ErrorAttributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.incubating.ExceptionIncubatingAttributes
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes

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
    protected abstract val schemaAttributes: Map<String, String>

    private val commonAttributes: Map<String, String> = mutableMapOf<String, String>().apply {
        if (telemetryType.sendImmediately) {
            plusAssign(SendImmediately.toEmbraceKeyValuePair())
        }
    }

    /**
     * The attributes defined for this schema that should be used to populate telemetry objects
     */
    fun attributes(): Map<String, String> = schemaAttributes.plus(commonAttributes)

    internal class Breadcrumb(message: String) : SchemaType(
        telemetryType = EmbType.System.Breadcrumb,
        fixedObjectName = "breadcrumb"
    ) {
        override val schemaAttributes = mapOf("message" to message)
    }

    internal class View(viewName: String) : SchemaType(
        telemetryType = EmbType.Ux.View,
        fixedObjectName = "screen-view"
    ) {
        override val schemaAttributes = mapOf("view.name" to viewName)
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
        override val schemaAttributes = mapOf(
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
        override val schemaAttributes = mapOf(
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
        override val schemaAttributes = mapOf(
            "notification.title" to title,
            "notification.type" to type,
            "notification.body" to body,
            "notification.id" to id,
            "notification.from" to from,
            "notification.priority" to priority.toString()
        ).toNonNullMap()
    }

    /**
     * Represents a span in which a native thread was blocked.
     */
    internal class NativeThreadBlockage(
        threadId: Int,
        threadName: String,
        threadPriority: Int,
        threadState: String,
        samplingOffsetMs: Long,
        stackUnwinder: String,
    ) : SchemaType(
        telemetryType = EmbType.Performance.NativeThreadBlockage,
        fixedObjectName = "native_thread_blockage"
    ) {
        override val schemaAttributes = mapOf(
            "thread_id" to threadId.toString(),
            "thread_name" to threadName,
            "thread_priority" to threadPriority.toString(),
            "thread_state" to threadState,
            "sampling_offset_ms" to samplingOffsetMs.toString(),
            "stack_unwinder" to stackUnwinder,
        )
    }

    /**
     * Represents a point in time when a native thread was blocked.
     */
    internal class NativeThreadBlockageSample(
        result: Int,
        sampleOverheadMs: Long,
        stacktrace: String,
    ) : SchemaType(
        telemetryType = EmbType.Performance.NativeThreadBlockageSample,
        fixedObjectName = "native_thread_blockage_sample"
    ) {
        override val schemaAttributes = mapOf(
            "result" to result.toString(),
            "sample_overhead_ms" to sampleOverheadMs.toString(),
            "stacktrace" to stacktrace
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
        override val schemaAttributes = mapOf(
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
        override val schemaAttributes = mapOf(
            "webview.url" to url
        ).toNonNullMap()
    }

    internal class MemoryWarning : SchemaType(
        telemetryType = EmbType.Performance.MemoryWarning,
        fixedObjectName = "memory-warning"
    ) {
        override val schemaAttributes = emptyMap<String, String>()
    }

    internal class AeiLog(message: AppExitInfoData) : SchemaType(EmbType.System.Exit) {
        override val schemaAttributes = mapOf(
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

    internal class NetworkRequest(networkRequest: EmbraceNetworkRequest) : SchemaType(EmbType.Performance.Network) {
        override val schemaAttributes = mapOf(
            "url.full" to stripUrl(networkRequest.url),
            HttpAttributes.HTTP_REQUEST_METHOD.key to networkRequest.httpMethod,
            HttpAttributes.HTTP_RESPONSE_STATUS_CODE.key to networkRequest.responseCode,
            HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE.key to networkRequest.bytesSent,
            HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE.key to networkRequest.bytesReceived,
            ErrorAttributes.ERROR_TYPE.key to networkRequest.errorType,
            "error.message" to networkRequest.errorMessage,
            "emb.w3c_traceparent" to networkRequest.w3cTraceparent,
            "emb.trace_id" to getValidTraceId(networkRequest.traceId),
        ).toNonNullMap().mapValues { it.value.toString() }
    }

    internal class Log(attributes: TelemetryAttributes) : SchemaType(EmbType.System.Log) {
        override val schemaAttributes = attributes.snapshot()
    }

    internal class Exception(attributes: TelemetryAttributes) :
        SchemaType(EmbType.System.Exception) {
        override val schemaAttributes = attributes.snapshot()
    }

    internal class FlutterException(attributes: TelemetryAttributes) :
        SchemaType(EmbType.System.FlutterException) {
        override val schemaAttributes = attributes.snapshot()
    }

    internal class Crash(attributes: TelemetryAttributes) : SchemaType(EmbType.System.Crash) {
        override val schemaAttributes = attributes.snapshot()
    }

    internal class ReactNativeCrash(attributes: TelemetryAttributes) : SchemaType(EmbType.System.ReactNativeCrash) {
        override val schemaAttributes = attributes.snapshot()
    }

    internal class NativeCrash(attributes: TelemetryAttributes) : SchemaType(EmbType.System.NativeCrash) {
        override val schemaAttributes = attributes.snapshot()
    }

    internal object LowPower : SchemaType(
        telemetryType = EmbType.System.LowPower,
        fixedObjectName = "device-low-power"
    ) {
        override val schemaAttributes = emptyMap<String, String>()
    }

    internal object Sigquit : SchemaType(
        telemetryType = EmbType.System.Sigquit,
        fixedObjectName = "sigquit"
    ) {
        override val schemaAttributes = emptyMap<String, String>()
    }

    internal class NetworkCapturedRequest(networkCapturedCall: NetworkCapturedCall) : SchemaType(
        telemetryType = EmbType.System.NetworkCapturedRequest
    ) {
        override val schemaAttributes = mapOf(
            "duration" to networkCapturedCall.duration.toString(),
            "end-time" to networkCapturedCall.endTime.toString(),
            "http-method" to networkCapturedCall.httpMethod,
            "matched-url" to networkCapturedCall.matchedUrl,
            "network-id" to networkCapturedCall.networkId,
            "request-body" to networkCapturedCall.requestBody,
            "request-body-size" to networkCapturedCall.requestBodySize.toString(),
            "request-query" to networkCapturedCall.requestQuery,
            "request-query-headers" to networkCapturedCall.requestQueryHeaders.toString(),
            "request-size" to networkCapturedCall.requestSize.toString(),
            "response-body" to networkCapturedCall.responseBody,
            "response-body-size" to networkCapturedCall.responseBodySize.toString(),
            "response-headers" to networkCapturedCall.responseHeaders.toString(),
            "response-size" to networkCapturedCall.responseSize.toString(),
            "response-status" to networkCapturedCall.responseStatus.toString(),
            "session-id" to networkCapturedCall.sessionId,
            "start-time" to networkCapturedCall.startTime.toString(),
            "url" to networkCapturedCall.url,
            "error-message" to networkCapturedCall.errorMessage,
            "encrypted-payload" to networkCapturedCall.encryptedPayload
        ).toNonNullMap()
    }

    internal class NetworkStatus(
        networkStatus: io.embrace.android.embracesdk.comms.delivery.NetworkStatus
    ) : SchemaType(
        telemetryType = EmbType.System.NetworkStatus,
        fixedObjectName = "network-status"
    ) {
        override val schemaAttributes = mapOf(
            "network" to networkStatus.value
        ).toNonNullMap()
    }

    internal class WebViewInfo(
        url: String,
        webVitals: String,
        tag: String?
    ) : SchemaType(
        telemetryType = EmbType.System.WebViewInfo,
        fixedObjectName = "webview-info"
    ) {
        override val schemaAttributes = mapOf(
            "emb.webview_info.url" to url,
            "emb.webview_info.web_vitals" to webVitals,
            "emb.webview_info.tag" to tag
        ).toNonNullMap()
    }

    internal class ReactNativeAction(
        name: String,
        outcome: String,
        payloadSize: Int,
        properties: Map<String?, Any?>,
    ) : SchemaType(
        telemetryType = EmbType.System.ReactNativeAction,
        fixedObjectName = "rn-action"
    ) {
        override val schemaAttributes = mapOf(
            "name" to name,
            "outcome" to outcome,
            "payload_size" to payloadSize.toString(),
        )
            .plus(
                properties
                    .mapKeys { it.key.toString().toSessionPropertyAttributeName() }
                    .mapValues { it.value.toString() }
            )
            .toNonNullMap()
    }

    internal class ThermalState(
        status: Int
    ) : SchemaType(
        telemetryType = EmbType.Performance.ThermalState,
        fixedObjectName = "thermal-state"
    ) {
        override val schemaAttributes = mapOf(
            "status" to status.toString()
        )
    }

    internal class InternalError(throwable: Throwable) : SchemaType(
        telemetryType = EmbType.System.InternalError,
        fixedObjectName = "internal-error"
    ) {
        override val schemaAttributes = mapOf(
            ExceptionIncubatingAttributes.EXCEPTION_TYPE.key to throwable.javaClass.name,
            ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE.key to throwable.stackTrace.joinToString(
                "\n",
                transform = StackTraceElement::toString
            ),
            ExceptionIncubatingAttributes.EXCEPTION_MESSAGE.key to (throwable.message ?: "")
        )
    }
}
