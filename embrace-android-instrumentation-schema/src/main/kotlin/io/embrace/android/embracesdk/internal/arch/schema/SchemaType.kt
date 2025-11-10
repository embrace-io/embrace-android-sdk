package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.arch.attrs.embAeiNumber
import io.embrace.android.embracesdk.internal.arch.attrs.embCrashNumber
import io.embrace.android.embracesdk.internal.arch.attrs.embSendMode
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.HttpAttributes
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import io.embrace.opentelemetry.kotlin.semconv.UrlAttributes
import kotlin.Suppress

/**
 * The collections of attribute schemas used by the associated telemetry types.
 *
 * Each schema contains an [io.embrace.android.embracesdk.internal.arch.schema.EmbType]
 * that it is being applied to, as well as an optional [fixedObjectName] used for the recorded
 * telemetry data object if the same, fixed name is used for every instance.
 */

sealed class SchemaType(
    val telemetryType: EmbType,
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
     * @param type The type of tap event. "tap"/"long_press". "tap" is the default.
     */
    class PushNotification(
        title: String?,
        type: String?,
        body: String?,
        id: String,
        from: String?,
        priority: Int,
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
        coords: String,
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
        url: String,
    ) : SchemaType(
        telemetryType = EmbType.Ux.WebView,
        fixedObjectName = "web-view"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            UrlAttributes.URL_FULL to url
        ).toNonNullMap()
    }

    class AeiLog(
        sessionId: String?,
        sessionIdError: String?,
        importance: Int?,
        pss: Long?,
        reason: Int?,
        rss: Long?,
        status: Int?,
        timestamp: Long?,
        description: String?,
        traceStatus: String?,
        crashNumber: Int?,
        aeiNumber: Int?,
    ) : SchemaType(EmbType.System.Exit) {
        override val schemaAttributes: Map<String, String> = mapOf(
            "aei_session_id" to sessionId,
            "session_id_error" to sessionIdError,
            "process_importance" to importance.toString(),
            "pss" to pss.toString(),
            "reason" to reason.toString(),
            "rss" to rss.toString(),
            "exit_status" to status.toString(),
            "timestamp" to timestamp.toString(),
            "description" to description,
            "trace_status" to traceStatus,
            embCrashNumber.name to crashNumber.toString(),
            embAeiNumber.name to aeiNumber.toString()
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

    class JvmCrash(attributes: TelemetryAttributes) : SchemaType(EmbType.System.Crash) {
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
        override val schemaAttributes: Map<String, String> = emptyMap()
    }

    class NetworkCapturedRequest(
        duration: Long?,
        endTime: Long?,
        httpMethod: String?,
        matchedUrl: String?,
        networkId: String,
        requestBody: String?,
        requestBodySize: Int?,
        requestQuery: String?,
        requestQueryHeaders: Map<String, String>?,
        requestSize: Int?,
        responseBody: String?,
        responseBodySize: Int?,
        responseHeaders: Map<String, String>?,
        responseSize: Int?,
        responseStatus: Int?,
        sessionId: String?,
        startTime: Long?,
        url: String?,
        errorMessage: String?,
        encryptedPayload: String?,
    ) : SchemaType(
        telemetryType = EmbType.System.NetworkCapturedRequest
    ) {
        @OptIn(IncubatingApi::class)
        override val schemaAttributes: Map<String, String> = mapOf(
            "duration" to duration.toString(),
            "end-time" to endTime.toString(),
            HttpAttributes.HTTP_REQUEST_METHOD to httpMethod,
            UrlAttributes.URL_FULL to matchedUrl,
            "network-id" to networkId,
            "request-body" to requestBody,
            HttpAttributes.HTTP_REQUEST_BODY_SIZE to requestBodySize.toString(),
            "request-query" to requestQuery,
            "http.request.header" to requestQueryHeaders.toString(),
            "request-size" to requestSize.toString(),
            "response-body" to responseBody,
            HttpAttributes.HTTP_RESPONSE_BODY_SIZE to responseBodySize.toString(),
            "http.response.header" to responseHeaders.toString(),
            "response-size" to responseSize.toString(),
            HttpAttributes.HTTP_RESPONSE_STATUS_CODE to responseStatus.toString(),
            SessionAttributes.SESSION_ID to sessionId,
            "start-time" to startTime.toString(),
            "url" to url,
            ExceptionAttributes.EXCEPTION_MESSAGE to errorMessage,
            "encrypted-payload" to encryptedPayload
        ).toNonNullMap()
    }

    class NetworkStatus(
        networkStatus: String,
    ) : SchemaType(
        telemetryType = EmbType.System.NetworkStatus,
        fixedObjectName = "network-status"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            "network" to networkStatus
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
                    .mapKeys { it.key.toString().toEmbraceAttributeName() }
                    .mapValues { it.value.toString() }
            )
            .toNonNullMap()
    }

    class ThermalState(
        status: Int,
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
            ExceptionAttributes.EXCEPTION_TYPE to throwable.javaClass.name,
            ExceptionAttributes.EXCEPTION_STACKTRACE to throwable.stackTrace.joinToString(
                "\n",
                transform = StackTraceElement::toString
            ),
            ExceptionAttributes.EXCEPTION_MESSAGE to (throwable.message ?: "")
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun <K, V> Map<K, V?>.toNonNullMap(): Map<K, V> {
    return filter { it.value != null } as Map<K, V>
}
