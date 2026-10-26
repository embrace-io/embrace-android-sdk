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
 * Only one attempt is made to start the SDK. A start that does not succeed closes any instance that was created, keeping
 * the telemetry the SDK persisted, moves the SDK to [SdkState.FAILED], routes all subsequent calls to [failed] and
 * discards any buffered calls. The holder coordinates deleting that telemetry, which only [close] does, once.
 * It disables the instance a start enabled, which deletes the telemetry once its own workers have stopped. After a start
 * that failed or was disabled by config, and so left no instance to disable, it runs the deletion that
 * [persistedTelemetryDeletion] returned for the start's context instead. Nothing else the SDK stores, such as remote
 * config, is deleted. Neither [SdkState.FAILED] nor [SdkState.DISABLED] can be started again. Exceptions are reported to
 * [errorHandler] rather than thrown. A disable made during start, by code running on the start thread, is applied once
 * the start completes rather than interrupting it.
 */
internal class SdkStateHolder(
    realSdkInstanceFactory: SdkInstanceFactory,
    preStart: BufferingSdkInstance,
    private val failed: Provider<SdkInstance>,
    private val disabled: Provider<SdkInstance>,
    private val persistedTelemetryDeletion: PersistedTelemetryDeletion,
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

    // deletes the telemetry the SDK persisted, held after a start that left no instance to disable and run once by the next
    // disable. Only read and written under lock
    private var telemetryDeletionOnDisable: (() -> Unit)? = null

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
     * Disables the SDK, as the public `disable()` asks, which is the only way it's called. It is also the only call that
     * deletes the telemetry the SDK persisted: a start that leaves no instance holds a deletion for it but never runs one.
     * Disabling it again has no further effect.
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

            // Unless the SDK is already disabled, disable the instance, which deletes the telemetry if a start enabled it,
            // and shut down the error handler.
            if (state != SdkState.DISABLED) {
                val previous = api.target
                transition(disabled(), SdkState.DISABLED)
                errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { previous.disable() }
                errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { errorHandler.close() }
            }

            // Run the deletion held by a start that left no instance to disable. When a start enabled an instance,
            // disabling it above deletes the telemetry instead.
            val deletion = telemetryDeletionOnDisable
            if (deletion != null) {
                telemetryDeletionOnDisable = null
                errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { deletion() }
            }
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
                terminate(
                    next = failed(),
                    nextState = SdkState.FAILED,
                    context = context,
                    deleteTelemetryOnDisable = true,
                )
                return
            }

            // A null instance means the SDK is disabled by config, so no instance is left to disable: hold the deletion of
            // the telemetry earlier launches persisted for a later disable.
            if (next == null) {
                terminate(
                    next = disabled(),
                    nextState = SdkState.DISABLED,
                    context = context,
                    deleteTelemetryOnDisable = true,
                )
            } else {
                val enabled = tryEnable(next, context)
                if (enabled) {
                    transition(next, SdkState.ENABLED)
                    next.onEnabled()
                } else if (pendingCall == PendingCall.Disable) {
                    // A disable was made during this start, so disable the instance, which deletes the telemetry once its
                    // own workers have finished, so we don't need to queue a deletion on disable.
                    errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { next.disable() }
                    terminate(
                        next = failed(),
                        nextState = SdkState.FAILED,
                        context = context,
                        deleteTelemetryOnDisable = false,
                    )
                } else {
                    errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) { next.close() }
                    terminate(
                        next = failed(),
                        nextState = SdkState.FAILED,
                        context = context,
                        deleteTelemetryOnDisable = true,
                    )
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

    /**
     * Transitions the SDK to a no-op instance. When [deleteTelemetryOnDisable] is true, holds a deletion of the telemetry
     * the SDK persisted for the next disable to run, as no instance is left whose own disable would delete it.
     */
    private fun terminate(next: SdkInstance, nextState: SdkState, context: Context, deleteTelemetryOnDisable: Boolean) {
        if (deleteTelemetryOnDisable) {
            telemetryDeletionOnDisable = errorHandler.guardSdkCode(SdkErrorType.PublicApiFail, null) {
                persistedTelemetryDeletion(context)
            }
        }
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

/**
 * Finds the telemetry the SDK persisted through the [Context] it was started with, and returns a function
 * that deletes it if the location can be found through the context. Otherwise, it returns null.
 */
internal typealias PersistedTelemetryDeletion = (Context) -> (() -> Unit)?
