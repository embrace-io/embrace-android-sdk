package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.arch.attrs.embAeiNumber
import io.embrace.android.embracesdk.internal.arch.attrs.embCrashNumber
import io.embrace.android.embracesdk.internal.arch.attrs.embSendMode
import io.embrace.android.embracesdk.internal.arch.attrs.embStateInitialValue
import io.embrace.android.embracesdk.semconv.EmbAeiAttributes
import io.embrace.android.embracesdk.semconv.EmbBreadcrumbAttributes
import io.embrace.android.embracesdk.semconv.EmbNetworkCapturedRequestAttributes
import io.embrace.android.embracesdk.semconv.EmbNetworkStatusAttributes
import io.embrace.android.embracesdk.semconv.EmbPushNotificationAttributes
import io.embrace.android.embracesdk.semconv.EmbTapAttributes
import io.embrace.android.embracesdk.semconv.EmbThermalStateAttributes
import io.embrace.android.embracesdk.semconv.EmbViewAttributes
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.opentelemetry.kotlin.semconv.HttpAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes
import io.opentelemetry.kotlin.semconv.UrlAttributes

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
        override val schemaAttributes: Map<String, String> = mapOf(EmbBreadcrumbAttributes.MESSAGE to message)
    }

    class View(viewName: String) : SchemaType(
        telemetryType = EmbType.Ux.View,
        fixedObjectName = "screen-view"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(EmbViewAttributes.VIEW_NAME to viewName)
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
            EmbPushNotificationAttributes.NOTIFICATION_TITLE to title,
            EmbPushNotificationAttributes.NOTIFICATION_TYPE to type,
            EmbPushNotificationAttributes.NOTIFICATION_BODY to body,
            EmbPushNotificationAttributes.NOTIFICATION_ID to id,
            EmbPushNotificationAttributes.NOTIFICATION_FROM to from,
            EmbPushNotificationAttributes.NOTIFICATION_PRIORITY to priority.toString()
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
            EmbViewAttributes.VIEW_NAME to viewName,
            EmbTapAttributes.TAP_TYPE to type,
            EmbTapAttributes.TAP_COORDS to coords
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
            EmbAeiAttributes.AEI_SESSION_ID to sessionId,
            EmbAeiAttributes.SESSION_ID_ERROR to sessionIdError,
            EmbAeiAttributes.PROCESS_IMPORTANCE to importance.toString(),
            EmbAeiAttributes.PSS to pss.toString(),
            EmbAeiAttributes.REASON to reason.toString(),
            EmbAeiAttributes.RSS to rss.toString(),
            EmbAeiAttributes.EXIT_STATUS to status.toString(),
            EmbAeiAttributes.TIMESTAMP to timestamp.toString(),
            EmbAeiAttributes.DESCRIPTION to description,
            EmbAeiAttributes.TRACE_STATUS to traceStatus,
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
        override val schemaAttributes: Map<String, String> = mapOf(
            EmbNetworkCapturedRequestAttributes.DURATION to duration.toString(),
            EmbNetworkCapturedRequestAttributes.END_TIME to endTime.toString(),
            HttpAttributes.HTTP_REQUEST_METHOD to httpMethod,
            UrlAttributes.URL_FULL to matchedUrl,
            EmbNetworkCapturedRequestAttributes.NETWORK_ID to networkId,
            EmbNetworkCapturedRequestAttributes.REQUEST_BODY to requestBody,
            HttpAttributes.HTTP_REQUEST_BODY_SIZE to requestBodySize.toString(),
            EmbNetworkCapturedRequestAttributes.REQUEST_QUERY to requestQuery,
            HttpAttributes.HTTP_REQUEST_HEADER to requestQueryHeaders.toString(),
            EmbNetworkCapturedRequestAttributes.REQUEST_SIZE to requestSize.toString(),
            EmbNetworkCapturedRequestAttributes.RESPONSE_BODY to responseBody,
            HttpAttributes.HTTP_RESPONSE_BODY_SIZE to responseBodySize.toString(),
            HttpAttributes.HTTP_RESPONSE_HEADER to responseHeaders.toString(),
            EmbNetworkCapturedRequestAttributes.RESPONSE_SIZE to responseSize.toString(),
            HttpAttributes.HTTP_RESPONSE_STATUS_CODE to responseStatus.toString(),
            SessionAttributes.SESSION_ID to sessionId,
            EmbNetworkCapturedRequestAttributes.START_TIME to startTime.toString(),
            EmbNetworkCapturedRequestAttributes.URL to url,
            ExceptionAttributes.EXCEPTION_MESSAGE to errorMessage,
            EmbNetworkCapturedRequestAttributes.ENCRYPTED_PAYLOAD to encryptedPayload
        ).toNonNullMap()
    }

    class NetworkStatus(
        networkStatus: String,
    ) : SchemaType(
        telemetryType = EmbType.System.NetworkStatus,
        fixedObjectName = "network-status"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            EmbNetworkStatusAttributes.NETWORK to networkStatus
        ).toNonNullMap()
    }

    class ThermalState(
        status: Int,
    ) : SchemaType(
        telemetryType = EmbType.Performance.ThermalState,
        fixedObjectName = "thermal-state"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            EmbThermalStateAttributes.STATUS to status.toString()
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

    /**
     * Base [SchemaType] to handle common logic for States. This includes expecting the type [T] as the value of the State
     * whose value can be encoded uniquely as a string via [toString], which will be used to presented it in any serialized forms.
     */
    abstract class State<T : Any>(
        initialValue: T,
        val stateName: String,
    ) : SchemaType(
        telemetryType = EmbType.State,
        fixedObjectName = "state-$stateName"
    ) {
        override val schemaAttributes: Map<String, String> = mapOf(
            embStateInitialValue.name to initialValue.toString()
        )
    }

    class NetworkState(
        initialValue: Status,
    ) : State<NetworkState.Status>(initialValue, "network") {
        enum class Status(private val value: String) {
            NOT_REACHABLE("none"),
            UNVERIFIED("unverified"),
            WIFI("wifi"),
            WIFI_CONNECTING("wifi_connecting"),
            WAN("wan"),
            WAN_CONNECTING("wan_connecting"),
            UNKNOWN("unknown"),
            UNKNOWN_CONNECTING("unknown_connecting");

            override fun toString(): String = value
        }
    }

    class PowerState(initialValue: PowerMode) :
        State<PowerState.PowerMode>(initialValue, "power") {
        enum class PowerMode(private val value: String) {
            NORMAL("normal"),
            LOW("low"),
            UNKNOWN("unknown");

            override fun toString(): String = value
        }
    }

    /**
     * A custom telemetry type. This allows the hybrid SDKs (and others) to pass in custom
     * telemetry schemas if required.
     */
    class Custom(
        type: String,
        subType: String,
        attributes: TelemetryAttributes,
        sendMode: SendMode,
    ) : SchemaType(
        telemetryType = EmbType.Custom(type, subType, sendMode)
    ) {
        override val schemaAttributes: Map<String, String> = attributes.snapshot()
    }
}

@Suppress("UNCHECKED_CAST")
private fun <K, V> Map<K, V?>.toNonNullMap(): Map<K, V> {
    return filter { it.value != null } as Map<K, V>
}
