package io.embrace.android.embracesdk.internal.anr.detection

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.MessageQueue
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.ExecutorService

/**
 * A [Handler] that processes messages enqueued on the target [Looper]. If a message is not
 * processed by this class in a timely manner then it indicates the target thread is blocked
 * with too much work.
 *
 * When this class processes the message it submits the [action] for execution on the supplied
 * [ExecutorService].
 *
 * Basically speaking: if [handleMessage] takes a long time, the monitor thread assumes there is
 * an ANR after a certain time threshold. Once [handleMessage] is invoked, the monitor thread
 * knows for sure that the target thread is responsive, so resets the timer for any ANRs.
 */
internal class TargetThreadHandler(
    looper: Looper,
    private val anrMonitorWorker: BackgroundWorker,
    private val configService: ConfigService,
    private val messageQueue: MessageQueue? = LooperCompat.getMessageQueue(looper),
    private val clock: Clock,
) : Handler(looper) {

    lateinit var action: (time: Long) -> Unit

    @Volatile
    var installed: Boolean = false

    fun start() {
        // set an IdleHandler that automatically gets invoked when the Handler
        // has processed all pending messages. We retain the callback to avoid
        // unnecessary allocations.

        if (configService.anrBehavior.isIdleHandlerEnabled()) {
            messageQueue?.addIdleHandler(::onIdleThread)
            installed = true
        }
    }

    fun onIdleThread(): Boolean {
        onMainThreadUnblocked()
        return true
    }

    override fun handleMessage(msg: Message) {
        runCatching {
            if (msg.what == HEARTBEAT_REQUEST) {
                // We couldn't obtain the target thread message queue. This should not happen,
                // but if it does then we just log an internal error & consider the ANR ended at
                // this point.
                if (messageQueue == null || !installed) {
                    onMainThreadUnblocked()
                }
            }
        }
    }

    private fun onMainThreadUnblocked() {
        val timestamp = clock.now()
        anrMonitorWorker.submit {
            action.invoke(timestamp)
        }
    }

    companion object {

        /**
         * Unique ID for message (arbitrary number).
         */
        const val HEARTBEAT_REQUEST: Int = 34593
    }
}
