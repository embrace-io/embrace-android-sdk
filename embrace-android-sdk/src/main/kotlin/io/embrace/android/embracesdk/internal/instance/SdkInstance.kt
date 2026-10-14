package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.internal.api.SdkApi

/**
 * An [SdkApi] that drains the [BufferingSdkInstance] used before the SDK starts.
 */
internal interface SdkInstance : InternalSdkApi {

    /**
     * Invoked after the instance has started successfully. Not invoked if the start did not succeed.
     */
    fun onPostStart(buffer: BufferingSdkInstance)
}
