package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Provides an internal interface to Embrace that is intended for use by the Embrace Unity SDK as its
 * sole source of communication with the Android SDK.
 * @suppress
 */
@InternalApi
interface UnityInternalInterface :
    EmbraceInternalInterface {

    /**
     * @suppress
     */
    fun setUnityMetaData(unityVersion: String?, buildGuid: String?, unitySdkVersion: String?)

    /**
     * @suppress
     */
    fun logUnhandledUnityException(
        name: String,
        message: String,
        stacktrace: String?
    )

    /**
     * @suppress
     */
    fun logHandledUnityException(
        name: String,
        message: String,
        stacktrace: String?
    )

    /**
     * @suppress
     */
    fun recordIncompleteNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        traceId: String?
    )

    /**
     * @suppress
     */
    fun recordCompletedNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        bytesSent: Long,
        bytesReceived: Long,
        statusCode: Int,
        traceId: String?
    )

    /**
     * @suppress
     */
    fun installUnityThreadSampler()
}
