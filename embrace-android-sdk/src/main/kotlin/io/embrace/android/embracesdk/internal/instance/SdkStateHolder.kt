package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.internal.api.SdkApi
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
    private val instanceFactory: (Context) -> SdkInstance?,
    preStart: Provider<BufferingSdkInstance>,
    private val failed: Provider<SdkInstance>,
    private val disabled: Provider<SdkInstance>,
    telemetryService: InternalTelemetryService,
    private val errorHandler: InternalErrorHandler,
) : Closeable {

    private val lock = Any()

    private var instance: SdkInstance? = null

    private val preStart: BufferingSdkInstance = preStart()

    val dispatcher: SdkApiDispatcher = SdkApiDispatcher(this.preStart, telemetryService, errorHandler)

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
                if (state != SdkState.NOT_STARTED) {
                    return
                }
                val started = try {
                    val next = instanceFactory(context)?.also { instance = it }
                    next != null && drainBufferedCalls(next) {
                        next.start(context)
                        next.isStarted.also { started ->
                            if (started) {
                                // swap before onPostStart invocation
                                dispatcher.target = next
                            }
                        }
                    }
                } catch (exc: Throwable) {
                    Log.w("Embrace", "Failed to start the Embrace SDK", exc)
                    errorHandler.trackInternalError(InternalErrorType.PublicApiFail, exc)
                    false
                }
                if (started) {
                    state = SdkState.STARTED
                } else {
                    instance?.let { guard { it.close() } }
                    val next = failed()
                    dispatcher.target = next
                    guard { drainBufferedCalls(next) { true } }
                    state = SdkState.FAILED
                }
            }
        }
    }

    /**
     * Disables the SDK. Has no effect if the SDK is already in [SdkState.DISABLED].
     */
    override fun close() {
        synchronized(lock) {
            when (state) {
                SdkState.DISABLED -> return
                SdkState.NOT_STARTED -> {
                    val next = disabled()
                    dispatcher.target = next
                    guard { drainBufferedCalls(next) { true } }
                    state = SdkState.DISABLED
                }
                SdkState.FAILED -> {
                    dispatcher.target = disabled()
                    state = SdkState.DISABLED
                }
                SdkState.STARTED -> {
                    // disabling is terminal even if teardown fails partway
                    guard { instance?.close() }
                    dispatcher.target = disabled()
                    state = SdkState.DISABLED
                }
            }
        }
    }

    private inline fun drainBufferedCalls(next: SdkInstance, start: () -> Boolean): Boolean {
        if (!start()) {
            return false
        }
        guard { next.onPostStart(preStart) }
        return true
    }

    inline fun guard(call: () -> Unit) {
        try {
            call()
        } catch (exc: Throwable) {
            errorHandler.trackInternalError(InternalErrorType.PublicApiFail, exc)
        }
    }
}
