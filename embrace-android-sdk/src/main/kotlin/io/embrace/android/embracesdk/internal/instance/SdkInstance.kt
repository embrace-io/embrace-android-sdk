package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.internal.api.SdkApi

/**
 * An [SdkApi] that takes over from the [BufferingSdkInstance] used before the SDK starts.
 */
internal interface SdkInstance : InternalSdkApi, AutoCloseable {

    /**
     * Invoked when this instance replaces [buffer] as the dispatch target, before [buffer] is closed.
     */
    fun onPostStart(buffer: BufferingSdkInstance)
}
