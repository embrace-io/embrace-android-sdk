package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.spans.ErrorCode

class FakeEmbraceInternalInterface(
    var networkSpanForwardingEnabled: Boolean = false,
    var captureNetworkBody: Boolean = false,
) : EmbraceInternalInterface {

    var networkRequests: MutableList<EmbraceNetworkRequest> = mutableListOf()
    val internalErrors: MutableList<Throwable> = mutableListOf()

    override fun startSpan(name: String, parentSpanId: String?, startTimeMs: Long?): String? {
        return null
    }

    override fun isNetworkSpanForwardingEnabled(): Boolean = networkSpanForwardingEnabled

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean = captureNetworkBody

    override fun logInternalError(message: String?, details: String?) {
    }

    override fun logInternalError(error: Throwable) {
        internalErrors.add(error)
    }

    override fun stopSpan(spanId: String, errorCode: ErrorCode?, endTimeMs: Long?): Boolean {
        return true
    }

    override fun addSpanEvent(
        spanId: String,
        name: String,
        timestampMs: Long?,
        attributes: Map<String, String>,
    ): Boolean {
        return true
    }

    override fun addSpanAttribute(spanId: String, key: String, value: String): Boolean {
        return true
    }

    override fun <T> recordSpan(
        name: String,
        parentSpanId: String?,
        attributes: Map<String, String>,
        events: List<Map<String, Any>>,
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
        attributes: Map<String, String>,
        events: List<Map<String, Any>>,
    ): Boolean {
        return true
    }

    override fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest) {
        networkRequests.add(networkRequest)
    }
}
