package io.embrace.android.embracesdk.internal.capture.aei

internal sealed class TraceResult(val trace: String?, val errMsg: String?) {
    class Success(result: String?, errMsg: String? = null) : TraceResult(result, errMsg)
    class Failure(message: String?) : TraceResult(null, message)
}
