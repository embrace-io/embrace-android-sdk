package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.opentelemetry.embSendMode
import io.embrace.android.embracesdk.internal.payload.AppExitInfoData
import io.embrace.android.embracesdk.internal.payload.NetworkCapturedCall
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes

/**
 * The collections of attribute schemas used by the associated telemetry types.
 *
 * Each schema contains a [TelemetryType] that it is being applied to, as well as an optional [fixedObjectName] used for the recorded
 * telemetry data object if the same, fixed name is used for every instance.
 */

sealed class SchemaType(
    val telemetryType: TelemetryType,
    val fixedObjectName: String = "",
) {
    protected abstract val schemaAttributes: Map<String, String>

    private val commonAttributes: Map<String, String> = mutableMapOf<String, String>().apply {
        if (telemetryType.sendMode != SendMode.DEFAULT) {
            plusAssign(embSendMode.name to telemetryType.sendMode.name)
        }
    }

    /**
     * The attributes defined for this schema that should be used to populate telemetry objects
     */
    fun attributes(): Map<String, String> = schemaAttributes.plus(commonAttributes)

    class Breadcrumb(message: String) : SchemaType(
        telemetryType = EmbType.System.Breadcrumb,
        fixedObjectName = "breadcrumb"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf("message" to message)
    }

    class View(viewName: String) : SchemaType(
        telemetryType = EmbType.Ux.View,
        fixedObjectName = "screen-view"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf("view.name" to viewName)
    }

    /**
     * Represents a push notification event.
     * @param viewName The name of the view that the tap event occurred in.
     * @param type The type of tap event. "tap"/"long_press". "tap" is the default.
     * @param coords The coordinates of the tap event.
     */
    class PushNotification(
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
        override val schemaAttributes: Map<String, String> = mapOf(
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
    class Tap(
        viewName: String?,
        type: String = "tap",
        coords: String
    ) : SchemaType(
        telemetryType = EmbType.Ux.Tap,
        fixedObjectName = "ui-tap"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            "view.name" to viewName,
            "tap.type" to type,
            "tap.coords" to coords
        ).toNonNullMap()
    }

    class WebViewUrl(
        url: String
    ) : SchemaType(
        telemetryType = EmbType.Ux.WebView,
        fixedObjectName = "web-view"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            UrlAttributes.URL_FULL.key to url
        ).toNonNullMap()
    }

    class MemoryWarning : SchemaType(
        telemetryType = EmbType.Performance.MemoryWarning,
        fixedObjectName = "memory-warning"
    ) {
        override val schemaAttributes: Map<String, String> = emptyMap<String, String>()
    }

    class AeiLog(message: AppExitInfoData) : SchemaType(EmbType.System.Exit) {
        override val schemaAttributes: Map<String, String> = mapOf(
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

    class NetworkRequest(networkRequestAttrs: Map<String, String>) : SchemaType(EmbType.Performance.Network) {
        // EmbraceNetworkRequest needs to stay in embrace-android-sdk module as it's a public API,
        // so pass a map of attributes directly.
        override val schemaAttributes: Map<String, String> = networkRequestAttrs
    }

    class Log(attributes: TelemetryAttributes) : SchemaType(EmbType.System.Log) {
        override val schemaAttributes: Map<String, String> = attributes.snapshot()
    }

    class Exception(attributes: TelemetryAttributes) :
        SchemaType(EmbType.System.Exception) {
        override val schemaAttributes: Map<String, String> = attributes.snapshot()
    }

    class FlutterException(attributes: TelemetryAttributes) :
        SchemaType(EmbType.System.FlutterException) {
        override val schemaAttributes: Map<String, String> = attributes.snapshot()
    }

    class Crash(attributes: TelemetryAttributes) : SchemaType(EmbType.System.Crash) {
        override val schemaAttributes: Map<String, String> = attributes.snapshot()
    }

    class ReactNativeCrash(attributes: TelemetryAttributes) : SchemaType(EmbType.System.ReactNativeCrash) {
        override val schemaAttributes: Map<String, String> = attributes.snapshot()
    }

    class NativeCrash(attributes: TelemetryAttributes) : SchemaType(EmbType.System.NativeCrash) {
        override val schemaAttributes: Map<String, String> = attributes.snapshot()
    }

    object LowPower : SchemaType(
        telemetryType = EmbType.System.LowPower,
        fixedObjectName = "device-low-power"
    ) {
        override val schemaAttributes: Map<String, String> = emptyMap<String, String>()
    }

    object Sigquit : SchemaType(
        telemetryType = EmbType.System.Sigquit,
        fixedObjectName = "sigquit"
    ) {
        override val schemaAttributes: Map<String, String> = emptyMap<String, String>()
    }

    class NetworkCapturedRequest(networkCapturedCall: NetworkCapturedCall) : SchemaType(
        telemetryType = EmbType.System.NetworkCapturedRequest
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            "duration" to networkCapturedCall.duration.toString(),
            "end-time" to networkCapturedCall.endTime.toString(),
            HttpAttributes.HTTP_REQUEST_METHOD.key to networkCapturedCall.httpMethod,
            UrlAttributes.URL_FULL.key to networkCapturedCall.matchedUrl,
            "network-id" to networkCapturedCall.networkId,
            "request-body" to networkCapturedCall.requestBody,
            HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE.key to networkCapturedCall.requestBodySize.toString(),
            "request-query" to networkCapturedCall.requestQuery,
            "http.request.header" to networkCapturedCall.requestQueryHeaders.toString(),
            "request-size" to networkCapturedCall.requestSize.toString(),
            "response-body" to networkCapturedCall.responseBody,
            HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE.key to networkCapturedCall.responseBodySize.toString(),
            "http.response.header" to networkCapturedCall.responseHeaders.toString(),
            "response-size" to networkCapturedCall.responseSize.toString(),
            HttpAttributes.HTTP_RESPONSE_STATUS_CODE.key to networkCapturedCall.responseStatus.toString(),
            SessionIncubatingAttributes.SESSION_ID.key to networkCapturedCall.sessionId,
            "start-time" to networkCapturedCall.startTime.toString(),
            "url" to networkCapturedCall.url,
            ExceptionAttributes.EXCEPTION_MESSAGE.key to networkCapturedCall.errorMessage,
            "encrypted-payload" to networkCapturedCall.encryptedPayload
        ).toNonNullMap()
    }

    class NetworkStatus(
        networkStatus: io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
    ) : SchemaType(
        telemetryType = EmbType.System.NetworkStatus,
        fixedObjectName = "network-status"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            "network" to networkStatus.value
        ).toNonNullMap()
    }

    class WebViewInfo(
        url: String,
        webVitals: String,
        tag: String?
    ) : SchemaType(
        telemetryType = EmbType.System.WebViewInfo,
        fixedObjectName = "webview-info"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            UrlAttributes.URL_FULL.key to url,
            "emb.webview_info.web_vitals" to webVitals,
            "emb.webview_info.tag" to tag
        ).toNonNullMap()
    }

    class ReactNativeAction(
        name: String,
        outcome: String,
        payloadSize: Int,
        properties: Map<String?, Any?>,
    ) : SchemaType(
        telemetryType = EmbType.System.ReactNativeAction,
        fixedObjectName = "rn-action"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
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

    class ThermalState(
        status: Int
    ) : SchemaType(
        telemetryType = EmbType.Performance.ThermalState,
        fixedObjectName = "thermal-state"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            "status" to status.toString()
        )
    }

    class InternalError(throwable: Throwable) : SchemaType(
        telemetryType = EmbType.System.InternalError,
        fixedObjectName = "internal-error"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            ExceptionAttributes.EXCEPTION_TYPE.key to throwable.javaClass.name,
            ExceptionAttributes.EXCEPTION_STACKTRACE.key to throwable.stackTrace.joinToString(
                "\n",
                transform = StackTraceElement::toString
            ),
            ExceptionAttributes.EXCEPTION_MESSAGE.key to (throwable.message ?: "")
        )
    }
}
