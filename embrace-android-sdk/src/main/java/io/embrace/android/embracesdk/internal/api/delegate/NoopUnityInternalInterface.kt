package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.UnityInternalInterface
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface

internal class NoopUnityInternalInterface(
    private val delegate: EmbraceInternalInterface,
) : UnityInternalInterface, EmbraceInternalInterface by delegate {

    override fun setUnityMetaData(unityVersion: String?, buildGuid: String?, unitySdkVersion: String?) {
    }

    override fun logUnhandledUnityException(name: String, message: String, stacktrace: String?) {
    }

    override fun logHandledUnityException(name: String, message: String, stacktrace: String?) {
    }

    override fun recordIncompleteNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        traceId: String?,
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
    ) {
    }

    override fun installUnityThreadSampler() {
    }
}
