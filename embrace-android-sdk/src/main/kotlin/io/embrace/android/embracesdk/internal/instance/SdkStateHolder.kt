package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Holds the [SdkApi] instance that backs the public `Embrace` object, and the lifecycle state of the SDK.
 *
 * All lifecycle transitions happen under a single lock and follow [SdkState]:
 *
 * ```
 * NOT_STARTED --start() succeeds--> STARTED --close()----> DISABLED
 *      |   |                                                ^  ^
 *      |   +--start() fails--> FAILED --close()-------------+  |
 *      +-----------------------close()-------------------------+
 * ```
 *
 * The [SdkInstance] is created by [instanceFactory] when the SDK starts. A null instance means the SDK could not be
 * instantiated.
 *
 * Only one attempt is made to start the SDK. A start that does not succeed closes any instance that was created without
 * deleting its persisted data, moves the SDK to [SdkState.FAILED], routes all subsequent calls to [failed] and discards
 * any buffered calls. Only disabling the SDK deletes persisted data. Neither [SdkState.FAILED] nor [SdkState.DISABLED]
 * can be started again. Exceptions are reported to [errorHandler] rather than thrown.
 */
internal class SdkStateHolder(
    instanceFactory: (Context) -> SdkInstance?,
    preStart: BufferingSdkInstance,
    private val failed: Provider<SdkInstance>,
    private val disabled: Provider<SdkInstance>,
    telemetryService: InternalTelemetryService,
    private val errorHandler: ErrorHandler,
) : AutoCloseable {

    private val lock = Any()

    // released once used, so the static `Embrace` object does not keep the module graph reachable through it
    private var instanceFactory: ((Context) -> SdkInstance?)? = instanceFactory

    val dispatcher: SdkApiDispatcher = SdkApiDispatcher(preStart, telemetryService, errorHandler)

    /**
     * The [SdkApi] that backs the public `Embrace` object. Calls that change SDK state are handled here and
     * everything else is delegated to [dispatcher].
     */
    val api: SdkApi = object : SdkApi by dispatcher {
        override fun start(context: Context) {
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { this@SdkStateHolder.start(context) }
        }

        override fun disable() {
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { this@SdkStateHolder.close() }
        }
    }

    @Volatile
    var state: SdkState = SdkState.NOT_STARTED
        private set

    /**
     * Starts the SDK. Has no effect unless the SDK is in [SdkState.NOT_STARTED].
     */
    fun start(context: Context) {
        EmbTrace.trace("sdk-start") {
            synchronized(lock) {
                // guarantees the factory is invoked at most once
                if (state != SdkState.NOT_STARTED) {
                    return
                }
                val factory = checkNotNull(instanceFactory)
                instanceFactory = null
                val next = try {
                    factory(context)
                } catch (exc: Throwable) {
                    reportStartFailure(exc)
                    null
                }
                if (next != null && tryStart(next, context)) {
                    state = SdkState.STARTED
                    swapTarget(next)
                } else {
                    // tear down buffered instance
                    dispatcher.target.close()
                    next?.let { errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { it.close() } }
                    transition(failed(), SdkState.FAILED)
                    errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { errorHandler.close() }
                }
            }
        }
    }

    /**
     * Disables the SDK. Has no effect if the SDK is already in [SdkState.DISABLED].
     */
    override fun close() {
        synchronized(lock) {
            if (state == SdkState.DISABLED) {
                return
            }
            instanceFactory = null
            val previous = dispatcher.target
            transition(disabled(), SdkState.DISABLED)
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { previous.disable() }
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { errorHandler.close() }
        }
    }

    private fun tryStart(next: SdkInstance, context: Context): Boolean {
        try {
            next.start(context)
            if (!next.isStarted) {
                return false
            }
        } catch (exc: Throwable) {
            reportStartFailure(exc)
            return false
        }
        return true
    }

    private fun transition(next: SdkInstance, nextState: SdkState) {
        swapTarget(next)
        state = nextState
    }

    private fun swapTarget(next: SdkInstance) {
        val previous = dispatcher.target
        dispatcher.target = next
        if (previous is BufferingSdkInstance) {
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { next.onPostStart(previous) }
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { previous.close() }
        }
    }

    private fun reportStartFailure(exc: Throwable) {
        Log.w("Embrace", "Failed to start the Embrace SDK", exc)
        errorHandler.trackSdkError(SdkErrorType.PublicApiFail, exc)
    }
}
