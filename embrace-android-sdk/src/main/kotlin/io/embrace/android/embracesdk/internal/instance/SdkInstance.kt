package io.embrace.android.embracesdk.internal.instance

import android.content.Context

/**
 * An instance that [SdkStateHolder] points the public API at: the [BufferingSdkInstance] used before the SDK starts, the
 * instance it starts, or one that drops calls once the SDK fails or is disabled. Only [SdkStateHolder] calls these
 * functions.
 */
internal interface SdkInstance : InternalSdkApi, AutoCloseable {

    /**
     * Enables the instance, on the thread that starts the SDK. The SDK starts only if this returns and the instance then
     * reports [isStarted].
     */
    fun enable(context: Context)

    /**
     * Invoked when the SDK transitions from [buffer], used before the SDK started, to this instance, before the instance
     * takes calls and before [buffer] is closed.
     */
    fun onTransition(buffer: BufferingSdkInstance)

    /**
     * Disables the instance: tears it down and deletes the data it persisted.
     */
    fun disable()
}
