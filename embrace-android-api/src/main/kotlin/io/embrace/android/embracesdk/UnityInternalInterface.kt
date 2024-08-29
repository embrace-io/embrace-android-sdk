package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface

/**
 * Provides an internal interface to Embrace that is intended for use by the Embrace Unity SDK as its
 * sole source of communication with the Android SDK.
 * @suppress
 */
@InternalApi
public interface UnityInternalInterface : EmbraceInternalInterface {

    /**
     * @suppress
     */
    public fun setUnityMetaData(unityVersion: String?, buildGuid: String?, unitySdkVersion: String?)

    /**
     * @suppress
     */
    public fun logUnhandledUnityException(
        name: String,
        message: String,
        stacktrace: String?
    )

    /**
     * @suppress
     */
    public fun logHandledUnityException(
        name: String,
        message: String,
        stacktrace: String?
    )

    /**
     * @suppress
     */
    public fun recordIncompleteNetworkRequest(
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
    public fun recordCompletedNetworkRequest(
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
    public fun installUnityThreadSampler()
}
