@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.api.delegate

import android.annotation.SuppressLint
import io.embrace.android.embracesdk.LogType
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.InternalTracingApi
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

@SuppressLint("EmbracePublicApiPackageRule")
internal class UninitializedSdkInternalInterfaceImpl(
    internalTracer: InternalTracingApi
) : EmbraceInternalInterface, InternalTracingApi by internalTracer {

    override fun logInfo(message: String, properties: Map<String, Any>?) {}

    override fun logWarning(message: String, properties: Map<String, Any>?, stacktrace: String?) {}

    override fun logError(message: String, properties: Map<String, Any>?, stacktrace: String?, isException: Boolean) {}

    @Suppress("DEPRECATION")
    override fun logHandledException(
        throwable: Throwable,
        type: LogType,
        properties: Map<String, Any>?,
        customStackTrace: Array<StackTraceElement>?
    ) {
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
    }

    override fun recordNetworkRequest(embraceNetworkRequest: EmbraceNetworkRequest) {}

    override fun logComposeTap(point: Pair<Float, Float>, elementName: String) {}

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean = false

    override fun setProcessStartedByNotification() {}

    override fun isNetworkSpanForwardingEnabled(): Boolean = false

    override fun getSdkCurrentTime(): Long = System.currentTimeMillis()

    override fun isInternalNetworkCaptureDisabled(): Boolean = false

    override fun isAnrCaptureEnabled(): Boolean = false

    override fun isNdkEnabled(): Boolean = false

    override fun logInternalError(message: String?, details: String?) {}

    override fun logInternalError(error: Throwable) {}

    override fun stopSdk() {}
}
