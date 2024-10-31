@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.LogType
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.internal.payload.TapBreadcrumb
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.spans.ErrorCode

class FakeEmbraceInternalInterface(
    var networkSpanForwardingEnabled: Boolean = false,
    var captureNetworkBody: Boolean = false,
) : EmbraceInternalInterface {

    var networkRequests: MutableList<EmbraceNetworkRequest> = mutableListOf()

    override fun startSpan(name: String, parentSpanId: String?, startTimeMs: Long?): String? {
        return null
    }

    override fun logInfo(message: String, properties: Map<String, Any>?) {
    }

    override fun logWarning(message: String, properties: Map<String, Any>?, stacktrace: String?) {
    }

    override fun logError(
        message: String,
        properties: Map<String, Any>?,
        stacktrace: String?,
        isException: Boolean,
    ) {
    }

    override fun logHandledException(
        throwable: Throwable,
        type: LogType,
        properties: Map<String, Any>?,
        customStackTrace: Array<StackTraceElement>?,
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
        networkCaptureData: NetworkCaptureData?,
    ) {
    }

    override fun recordIncompleteNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        error: Throwable?,
        traceId: String?,
        networkCaptureData: NetworkCaptureData?,
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
        networkCaptureData: NetworkCaptureData?,
    ) {
    }

    override fun recordNetworkRequest(embraceNetworkRequest: EmbraceNetworkRequest) {
        networkRequests.add(embraceNetworkRequest)
    }

    override fun logComposeTap(point: Pair<Float, Float>, elementName: String) {
    }

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean = captureNetworkBody

    override fun isNetworkSpanForwardingEnabled(): Boolean = networkSpanForwardingEnabled

    override fun getSdkCurrentTime(): Long {
        return 0
    }

    override fun isAnrCaptureEnabled(): Boolean {
        return true
    }

    override fun isNdkEnabled(): Boolean {
        return true
    }

    override fun logInternalError(message: String?, details: String?) {
    }

    override fun logInternalError(error: Throwable) {
    }

    override fun stopSdk() {
    }

    override fun stopSpan(spanId: String, errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        return true
    }

    override fun addSpanEvent(
        spanId: String,
        name: String,
        timestampMs: Long?,
        attributes: Map<String, String>?,
    ): Boolean {
        return true
    }

    override fun addSpanAttribute(spanId: String, key: String, value: String): Boolean {
        return true
    }

    override fun <T> recordSpan(
        name: String,
        parentSpanId: String?,
        attributes: Map<String, String>?,
        events: List<Map<String, Any>>?,
        code: () -> T,
    ): T {
        return code()
    }

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parentSpanId: String?,
        attributes: Map<String, String>?,
        events: List<Map<String, Any>>?,
    ): Boolean {
        return true
    }

    override fun logTap(point: Pair<Float?, Float?>, elementName: String, type: TapBreadcrumb.TapBreadcrumbType) {}
}
