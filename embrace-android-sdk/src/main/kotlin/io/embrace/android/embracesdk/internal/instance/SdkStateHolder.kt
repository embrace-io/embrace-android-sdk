package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.EmbTrace

/**
 * Holds the [SdkApi] instance that backs the public `Embrace` object, and the lifecycle state of the SDK.
 *
 * All lifecycle transitions happen under a single lock and follow [SdkState]:
 *
 * ```
 * NOT_STARTED --start() succeeds--> ENABLED --close()----> DISABLED
 *      |   |                                                ^  ^
 *      |   +--start() fails--> FAILED --close()-------------+  |
 *      +-------close() or start() disabled by config-----------+
 * ```
 *
 * Starting means the public start call, from when it is made until it returns. During it, the [SdkInstance] is created by
 * [realSdkInstanceFactory] and then enabled: the start fails if [SdkInstance.enable] does. Once it has succeeded, the SDK
 * transitions to [SdkState.ENABLED] and serves calls, including through the public `isStarted`, while
 * [SdkInstance.onEnabled] finishes the instance's init before the start returns. A null instance means the SDK is
 * disabled by config. Each change of [SdkState] is a transition, which points the public API at the instance that serves
 * calls in the new state. The calls buffered before the SDK is enabled are handed to the instance before it is published,
 * so none is overtaken by a call made directly.
 *
 * Only one attempt is made to start the SDK. A start that does not succeed closes any instance that was created without
 * deleting its persisted data, moves the SDK to [SdkState.FAILED], routes all subsequent calls to [failed] and discards
 * any buffered calls. Only calling [close] deletes persisted data, by disabling the instance that serves calls. After a
 * start that failed or was disabled by config, that is [failed] or [disabled], which deletes what the SDK persisted.
 * Neither [SdkState.FAILED] nor [SdkState.DISABLED] can be started again. Exceptions are reported to [errorHandler]
 * rather than thrown. A disable made during start, by code running on the start thread, is applied once the start
 * completes rather than interrupting it.
 */
internal class SdkStateHolder(
    realSdkInstanceFactory: SdkInstanceFactory,
    preStart: BufferingSdkInstance,
    private val failed: Lazy<SdkInstance>,
    private val disabled: Lazy<SdkInstance>,
    telemetryService: InternalTelemetryService,
    private val errorHandler: ErrorHandler,
) : AutoCloseable {

    private val lock = Any()

    /**
     * The [SdkApi] that backs the public `Embrace` object.
     */
    val api: SdkApiDispatcher = SdkApiDispatcher(preStart, telemetryService, errorHandler, ::start, ::close)

    @Volatile
    var state: SdkState = SdkState.NOT_STARTED
        private set

    // only read and written under lock
    private var pendingCall: PendingCall = PendingCall.None(realSdkInstanceFactory)

    /**
     * Starts the SDK. Has no effect unless the SDK is in [SdkState.NOT_STARTED].
     */
    fun start(context: Context) {
        EmbTrace.trace("sdk-start") {
            synchronized(lock) {
                // the factory is invoked at most once, as only an SDK that no call has reached yet holds it. The lock is
                // reentrant, so a start made by code running during this start also reaches here, and is ignored.
                val pending = pendingCall
                if (pending is PendingCall.None) {
                    pendingCall = PendingCall.Start
                    runStart(pending.factory, context)
                }
            }
        }
    }

    /**
     * Disables the SDK. Disabling it again has no further effect.
     */
    override fun close() {
        synchronized(lock) {
            if (pendingCall == PendingCall.Start || pendingCall == PendingCall.Disable) {
                // only code running on the start thread gets here, as the lock is held for the whole start. The disable is
                // applied once the start completes rather than interrupting it.
                pendingCall = PendingCall.Disable
                return
            }
            pendingCall = PendingCall.Done
            val previous = api.target
            transition(disabled.value, SdkState.DISABLED)
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { previous.disable() }
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { errorHandler.close() }
        }
    }

    /**
     * Creates an [SdkInstance] with [factory] and enables it, or ends the start without one if that fails or the SDK is
     * disabled by config. Called under [lock], for the one start the SDK gets.
     */
    private fun runStart(factory: SdkInstanceFactory, context: Context) {
        try {
            val next = try {
                factory(context)
            } catch (exc: Throwable) {
                reportStartFailure(exc)
                terminate(failed.value, SdkState.FAILED)
                return
            }
            when {
                // a null instance means the SDK is disabled by config
                next == null -> terminate(disabled.value, SdkState.DISABLED)
                tryEnable(next, context) -> {
                    transition(next, SdkState.ENABLED)
                    next.onEnabled()
                }
                else -> {
                    errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) {
                        // a disable held during this start deletes data, which the instance does once its own
                        // workers finish
                        if (pendingCall == PendingCall.Disable) {
                            next.disable()
                        } else {
                            next.close()
                        }
                    }
                    terminate(failed.value, SdkState.FAILED)
                }
            }
        } finally {
            val disablePending = pendingCall == PendingCall.Disable
            pendingCall = PendingCall.Done
            if (disablePending) {
                close()
            }
        }
    }

    private fun tryEnable(next: SdkInstance, context: Context): Boolean {
        try {
            next.enable(context)
            if (!next.isStarted) {
                return false
            }
        } catch (exc: Throwable) {
            reportStartFailure(exc)
            return false
        }
        return true
    }

    private fun terminate(next: SdkInstance, nextState: SdkState) {
        transition(next, nextState)
        errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { errorHandler.close() }
    }

    private fun transition(next: SdkInstance, nextState: SdkState) {
        swapTarget(next)
        state = nextState
    }

    /**
     * Points the public API at [next]. The calls buffered before start are handed to [next] first, so that a call made
     * directly once [next] is published can't overtake them.
     */
    private fun swapTarget(next: SdkInstance) {
        val previous = api.target
        if (previous is BufferingSdkInstance) {
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { next.onTransition(previous) }
        }
        api.target = next
        if (previous is BufferingSdkInstance) {
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { previous.close() }
        }
    }

    private fun reportStartFailure(exc: Throwable) {
        Log.w("Embrace", "Failed to start the Embrace SDK", exc)
        errorHandler.trackSdkError(SdkErrorType.PublicApiFail, exc)
    }

    /**
     * A lifecycle call that the holder has yet to finish processing.
     */
    private sealed interface PendingCall {

        /**
         * No call has been processed yet, so the SDK can still be started, once, with [factory]. Any other state
         * releases the factory, so the static `Embrace` object does not keep the module graph reachable through it.
         */
        class None(val factory: SdkInstanceFactory) : PendingCall

        /**
         * A start call is being processed.
         */
        data object Start : PendingCall

        /**
         * A disable call was received while a start call was still being processed, and is applied once that start
         * completes.
         */
        data object Disable : PendingCall

        /**
         * All accepted calls have been processed, so the SDK can't be started again.
         */
        data object Done : PendingCall
    }
}

/**
 * Creates the [SdkInstance] that a start enables, or returns null if the SDK is disabled by config.
 */
internal typealias SdkInstanceFactory = (Context) -> SdkInstance?
