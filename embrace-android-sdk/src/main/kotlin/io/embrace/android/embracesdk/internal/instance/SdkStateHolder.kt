package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.logging.BufferedInternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import java.io.Closeable

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
 * Only one attempt is made to start the SDK. A start that does not succeed disables any instance that was created, moves
 * the SDK to [SdkState.FAILED], routes all subsequent calls to [failed] and discards any buffered calls. Neither [SdkState.FAILED] nor [SdkState.DISABLED]
 * can be started again. Exceptions are reported to [errorHandler] rather than thrown.
 */
internal class SdkStateHolder(
    instanceFactory: (Context) -> SdkInstance?,
    preStart: BufferingSdkInstance,
    private val failed: Provider<SdkInstance>,
    private val disabled: Provider<SdkInstance>,
    telemetryService: InternalTelemetryService,
    private val errorHandler: InternalErrorHandler,
    private val bufferedErrorHandler: BufferedInternalErrorHandler,
) : Closeable {

    private val lock = Any()

    // released once used, so the static `Embrace` object does not keep the module graph reachable through it
    private var instanceFactory: ((Context) -> SdkInstance?)? = instanceFactory

    val dispatcher: SdkApiDispatcher = SdkApiDispatcher(preStart, telemetryService, errorHandler)

    /**
     * The [SdkApi] that backs the public `Embrace` object. Calls that change SDK state are handled here and
     * everything else is delegated to [dispatcher].
     */
    val api: SdkApi = object : SdkApi by dispatcher {
        override fun start(context: Context) = guard { this@SdkStateHolder.start(context) }
        override fun disable() = guard { this@SdkStateHolder.close() }
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
                    next?.let { guard { it.close() } }
                    transition(failed(), SdkState.FAILED)
                    guard { bufferedErrorHandler.close() }
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
            // disabling is terminal even if teardown fails partway
            guard { dispatcher.target.close() }
            transition(disabled(), SdkState.DISABLED)
            guard { bufferedErrorHandler.close() }
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
            guard { next.onPostStart(previous) }
            guard { previous.close() }
        }
    }

    private fun reportStartFailure(exc: Throwable) {
        Log.w("Embrace", "Failed to start the Embrace SDK", exc)
        errorHandler.trackInternalError(InternalErrorType.PublicApiFail, exc)
    }

    inline fun guard(call: () -> Unit) {
        try {
            call()
        } catch (exc: Throwable) {
            errorHandler.trackInternalError(InternalErrorType.PublicApiFail, exc)
        }
    }
}
