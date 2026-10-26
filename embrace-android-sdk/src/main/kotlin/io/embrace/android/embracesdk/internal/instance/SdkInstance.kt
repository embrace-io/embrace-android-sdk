package io.embrace.android.embracesdk.internal.instance

import android.content.Context

/**
 * An instance that [SdkStateHolder] points the public API at: the [BufferingSdkInstance] used before the SDK starts, the
 * instance it starts, or one that drops calls once the SDK fails or is disabled. Only [SdkStateHolder] calls these
 * functions.
 */
internal interface SdkInstance : InternalSdkApi, AutoCloseable {

    /**
     * Enables the instance, on the thread that starts the SDK: builds what it needs before it can serve calls. The SDK is
     * enabled with this instance only if this returns and the instance then reports [isStarted].
     */
    fun enable(context: Context)

    /**
     * Invoked once the SDK is enabled with this instance, which now serves calls, on the thread that enabled it. Finishes
     * the instance's init, which is complete when this returns.
     */
    fun onEnabled()

    /**
     * Invoked when the SDK transitions from [buffer], used before the SDK started, to this instance, before the instance
     * takes calls and before [buffer] is closed.
     */
    fun onTransition(buffer: BufferingSdkInstance)

    /**
     * Disables the instance: does everything [close] does, and also deletes any telemetry that it persists.
     *
     * A more generalized cleanup step is coordinated by [SdkStateHolder], deleting persisted telemetry globally that might
     * have been left by aborted or partial SDK starts that are no longer wanted, when appropriate.
     */
    fun disable()

    /**
     * Closes the instance: it stops serving calls and releases its resources, but doesn't delete any telemetry that's
     * already persisted.
     */
    override fun close()
}
