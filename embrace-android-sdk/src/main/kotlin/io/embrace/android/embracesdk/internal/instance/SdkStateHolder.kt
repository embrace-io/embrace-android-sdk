package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.internal.api.SdkApi

/**
 * Holds the [SdkApi] instance that backs the public `Embrace` object, and the lifecycle state of the SDK.
 *
 * All lifecycle transitions happen under a single lock and follow [SdkState]:
 *
 * ```
 * NOT_STARTED --start() succeeds--> STARTED --disable()--> DISABLED
 * ```
 *
 * A start that does not succeed leaves the SDK in [SdkState.NOT_STARTED] so that it can be retried. [SdkState.DISABLED]
 * is terminal: the SDK cannot be restarted once it has been disabled.
 */
internal class SdkStateHolder(instance: SdkApi) {

    private val lock = Any()

    val dispatcher: SdkApiDispatcher = SdkApiDispatcher(instance)

    @Volatile
    var state: SdkState = SdkState.NOT_STARTED
        private set

    /**
     * Starts the SDK. Has no effect unless the SDK is in [SdkState.NOT_STARTED].
     */
    fun start(context: Context) {
        synchronized(lock) {
            if (state == SdkState.STARTED) {
                return
            }
            val instance = dispatcher.target
            val started = try {
                instance.start(context)
                instance.isStarted
            } catch (ignored: Throwable) {
                Log.w("Embrace", "Failed to start the Embrace SDK", ignored)
                false
            }
            if (started) {
                state = SdkState.STARTED
            }
        }
    }

    /**
     * Disables the SDK. Has no effect unless the SDK is in [SdkState.STARTED].
     */
    fun disable() {
        synchronized(lock) {
            if (state != SdkState.STARTED) {
                return
            }
            // TODO: future: dispatcher.target will be changed here and at other state transitions.
            dispatcher.target.disable()
            state = SdkState.DISABLED
        }
    }
}

/**
 * The lifecycle state of the SDK.
 */
internal enum class SdkState {

    /**
     * The SDK has not started yet, or a previous attempt to start it did not succeed.
     */
    NOT_STARTED,

    /**
     * The SDK started successfully.
     */
    STARTED,

    /**
     * The SDK was disabled after it started. This state is terminal.
     */
    DISABLED,
}
