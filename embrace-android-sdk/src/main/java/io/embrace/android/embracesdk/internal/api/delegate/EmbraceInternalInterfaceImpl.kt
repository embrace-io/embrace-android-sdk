@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.api.delegate

import android.annotation.SuppressLint
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.LogType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.InternalTracingApi
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.network.logging.NetworkCaptureService
import io.embrace.android.embracesdk.payload.TapBreadcrumb

@SuppressLint("EmbracePublicApiPackageRule")
internal class EmbraceInternalInterfaceImpl(
    private val embraceImpl: EmbraceImpl,
    private val initModule: InitModule,
    private val networkCaptureService: NetworkCaptureService,
    private val eventService: EventService,
    private val internalErrorService: InternalErrorService,
    private val configService: ConfigService,
    internalTracer: InternalTracer
) : EmbraceInternalInterface, InternalTracingApi by internalTracer {

    override fun logInfo(message: String, properties: Map<String, Any>?) {
        embraceImpl.logMessage(message, Severity.INFO, properties)
    }

    override fun logWarning(
        message: String,
        properties: Map<String, Any>?,
        stacktrace: String?
    ) {
        embraceImpl.logMessage(message, Severity.WARNING, properties)
    }

    override fun logError(
        message: String,
        properties: Map<String, Any>?,
        stacktrace: String?,
        isException: Boolean,
    ) {
        embraceImpl.logMessage(message, Severity.ERROR, properties)
    }

    override fun logHandledException(
        throwable: Throwable,
        type: LogType,
        properties: Map<String, Any>?,
        customStackTrace: Array<StackTraceElement>?
    ) {
        val eventType = when (type) {
            LogType.ERROR -> EventType.ERROR_LOG
            LogType.WARNING -> EventType.WARNING_LOG
            else -> EventType.INFO_LOG
        }
        embraceImpl.logMessage(
            eventType,
            throwable.message ?: "",
            properties,
            customStackTrace ?: throwable.stackTrace,
            null,
            LogExceptionType.NONE,
            null,
            null
        )
    }

    override fun logComposeTap(point: Pair<Float, Float>, elementName: String) {
        embraceImpl.logTap(point, elementName, TapBreadcrumb.TapBreadcrumbType.TAP)
    }

    override fun recordCompletedNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        bytesSent: Long,
        bytesReceived: Long,
        statusCode: Int,
        traceId: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        embraceImpl.recordNetworkRequest(
            EmbraceNetworkRequest.fromCompletedRequest(
                url,
                HttpMethod.fromString(httpMethod),
                startTime,
                endTime,
                bytesSent,
                bytesReceived,
                statusCode,
                traceId,
                null,
                networkCaptureData
            )
        )
    }

    override fun recordIncompleteNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        error: Throwable?,
        traceId: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        embraceImpl.recordNetworkRequest(
            EmbraceNetworkRequest.fromIncompleteRequest(
                url,
                HttpMethod.fromString(httpMethod),
                startTime,
                endTime,
                error?.javaClass?.canonicalName ?: "",
                error?.localizedMessage ?: "",
                traceId,
                null,
                networkCaptureData
            )
        )
    }

    override fun recordIncompleteNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        traceId: String?,
        networkCaptureData: NetworkCaptureData?
    ) {
        embraceImpl.recordNetworkRequest(
            EmbraceNetworkRequest.fromIncompleteRequest(
                url,
                HttpMethod.fromString(httpMethod),
                startTime,
                endTime,
                errorType ?: "",
                errorMessage ?: "",
                traceId,
                null,
                networkCaptureData
            )
        )
    }

    override fun recordNetworkRequest(embraceNetworkRequest: EmbraceNetworkRequest) {
        embraceImpl.recordNetworkRequest(embraceNetworkRequest)
    }

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean {
        return networkCaptureService.getNetworkCaptureRules(url, method).isNotEmpty()
    }

    override fun setProcessStartedByNotification() {
        eventService.setProcessStartedByNotification()
    }

    override fun isNetworkSpanForwardingEnabled(): Boolean = configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()

    override fun getSdkCurrentTime(): Long = initModule.clock.now()

    override fun isInternalNetworkCaptureDisabled(): Boolean = ApkToolsConfig.IS_NETWORK_CAPTURE_DISABLED

    override fun isAnrCaptureEnabled(): Boolean = configService.anrBehavior.isAnrCaptureEnabled()

    override fun isNdkEnabled(): Boolean = configService.autoDataCaptureBehavior.isNdkEnabled()

    override fun logInternalError(message: String?, details: String?) {
        if (message == null) {
            return
        }
        val messageWithDetails: String = if (details != null) {
            "$message: $details"
        } else {
            message
        }
        internalErrorService.handleInternalError(RuntimeException(messageWithDetails))
    }

    override fun logInternalError(error: Throwable) {
        internalErrorService.handleInternalError(error)
    }

    override fun stopSdk() {
        embraceImpl.stop()
    }
}
