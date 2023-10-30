package io.embrace.android.embracesdk

import android.util.Pair
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.payload.TapBreadcrumb

internal class EmbraceInternalInterfaceImpl(
    private val embraceImpl: EmbraceImpl,
    private val initModule: InitModule
) : EmbraceInternalInterface {

    override fun logInfo(message: String, properties: Map<String, Any>?) {
        embraceImpl.logMessage(
            EmbraceEvent.Type.INFO_LOG,
            message,
            properties,
            null,
            null,
            LogExceptionType.NONE,
            null,
            null
        )
    }

    override fun logWarning(
        message: String,
        properties: Map<String, Any>?,
        stacktrace: String?
    ) {
        embraceImpl.logMessage(
            EmbraceEvent.Type.WARNING_LOG,
            message,
            properties,
            null,
            stacktrace,
            LogExceptionType.NONE,
            null,
            null
        )
    }

    override fun logError(
        message: String,
        properties: Map<String, Any>?,
        stacktrace: String?,
        isException: Boolean,
    ) {
        embraceImpl.logMessage(
            EmbraceEvent.Type.ERROR_LOG,
            message,
            properties,
            null,
            stacktrace,
            LogExceptionType.NONE,
            null,
            null
        )
    }

    override fun logHandledException(
        throwable: Throwable,
        type: LogType,
        properties: Map<String, Any>?,
        customStackTrace: Array<StackTraceElement>?
    ) {
        embraceImpl.logMessage(
            type.toEventType(),
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

    override fun recordAndDeduplicateNetworkRequest(
        callId: String,
        embraceNetworkRequest: EmbraceNetworkRequest
    ) {
        embraceImpl.recordAndDeduplicateNetworkRequest(callId, embraceNetworkRequest)
    }

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean = embraceImpl.shouldCaptureNetworkCall(url, method)

    override fun setProcessStartedByNotification() {
        embraceImpl.setProcessStartedByNotification()
    }

    override fun isNetworkSpanForwardingEnabled(): Boolean {
        return embraceImpl.configService?.networkSpanForwardingBehavior?.isNetworkSpanForwardingEnabled() ?: false
    }

    override fun getSdkCurrentTime(): Long = initModule.clock.now()

    override fun isInternalNetworkCaptureDisabled(): Boolean = ApkToolsConfig.IS_NETWORK_CAPTURE_DISABLED
}
