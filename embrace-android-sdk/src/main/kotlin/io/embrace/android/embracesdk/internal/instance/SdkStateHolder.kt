package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.NoopInternalInterfaceApi
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService

/**
 * Holds the [SdkApi] instance that backs the public `Embrace` object, and the lifecycle state of the SDK.
 *
 * All lifecycle transitions happen under a single lock and follow [SdkState]:
 *
 * ```
 * NOT_STARTED --start() succeeds--> STARTED --disable()--> DISABLED
 *                                       ^                         |
 *                                       +-------------------------+
 * ```
 *
 * A start that does not succeed leaves the SDK in [SdkState.NOT_STARTED] so that it can be retried. A disabled SDK can
 * currently be restarted by calling start() again. Exceptions are reported to [errorHandler] rather than thrown.
 */
internal class SdkStateHolder(
    instance: SdkApi,
    telemetryService: InternalTelemetryService,
    private val errorHandler: InternalErrorHandler,
    private val internalInterfaceApi: InternalInterfaceApi = NoopInternalInterfaceApi,
) {

    private val lock = Any()

    val dispatcher: SdkApiDispatcher = SdkApiDispatcher(instance, telemetryService, errorHandler)

    /**
     * The [SdkApi] that backs the public `Embrace` object. Calls that change SDK state are handled here and
     * everything else is delegated to [dispatcher].
     */
    val api: SdkApi = object : SdkApi by dispatcher {
        override fun start(context: Context) = guard { this@SdkStateHolder.start(context) }
        override fun disable() = guard { this@SdkStateHolder.disable() }
    }

    @Volatile
    var state: SdkState = SdkState.NOT_STARTED
        private set(value) {
            field = value
            updateInternalInterfaceApi()
        }

    init {
        updateInternalInterfaceApi()
    }

    /**
     * Starts the SDK. Has no effect if the SDK is already in [SdkState.STARTED].
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
            } catch (exc: Throwable) {
                Log.w("Embrace", "Failed to start the Embrace SDK", exc)
                errorHandler.trackInternalError(InternalErrorType.PublicApiFail, exc)
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
            try {
                dispatcher.target.disable()
                state = SdkState.DISABLED
            } catch (exc: Throwable) {
                errorHandler.trackInternalError(InternalErrorType.PublicApiFail, exc)
            }
        }
    }

    private fun updateInternalInterfaceApi() {
        EmbraceInternalApi.internalInterfaceApi = when (state) {
            SdkState.STARTED -> internalInterfaceApi
            SdkState.NOT_STARTED, SdkState.DISABLED -> NoopInternalInterfaceApi
        }
    }

    inline fun guard(call: () -> Unit) {
        try {
            call()
        } catch (exc: Throwable) {
            errorHandler.trackInternalError(InternalErrorType.PublicApiFail, exc)
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
     * The SDK was disabled after it started.
     */
    DISABLED,
}
