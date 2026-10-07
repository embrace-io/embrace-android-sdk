package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.EmbraceSdk

/**
 * Holds the current [EmbraceSdk] and swaps it as the SDK starts. Every call on this object forwards to [current].
 *
 * Constructing this does no SDK work: the real instance is only built by [start].
 */
internal class EmbraceHolder(
    private val factory: () -> StartableEmbrace,
    private val warn: (String, Throwable) -> Unit = { msg, exc -> Log.w("Embrace", msg, exc) },
    now: () -> Long = System::currentTimeMillis,
) : ForwardingEmbraceSdk() {

    private val startLock = Any()
    private val preStart = PreStartEmbrace(::current, now)

    @Volatile
    var current: EmbraceSdk = preStart
        private set

    override fun delegate(): EmbraceSdk = current

    /**
     * Starts the SDK. Repeated calls do nothing.
     */
    fun start(context: Context) {
        synchronized(startLock) {
            if (current !== preStart) {
                return
            }
            var started = false
            try {
                val instance = factory()
                preStart.attach(instance)
                instance.start(context) {
                    current = instance
                    started = true
                    preStart.seal()
                }
            } catch (exc: Throwable) {
                warn("Failed to start the Embrace SDK", exc)
            }
            if (!started) {
                current = DisabledEmbrace
                preStart.seal()
            }
        }
    }

    override fun disable() {
        synchronized(startLock) {
            val instance = current
            instance.disable()
            if (instance !== preStart) {
                current = DisabledEmbrace
            }
        }
    }
}
