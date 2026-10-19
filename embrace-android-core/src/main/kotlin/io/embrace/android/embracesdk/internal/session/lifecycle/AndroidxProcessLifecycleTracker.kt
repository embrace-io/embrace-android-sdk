package io.embrace.android.embracesdk.internal.session.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener

/**
 * Reports process state transitions as observed by androidx's ProcessLifecycleOwner.
 */
class AndroidxProcessLifecycleTracker(
    private val lifecycleOwner: LifecycleOwner,
    private val isMainThread: () -> Boolean = { Thread.currentThread() === Looper.getMainLooper().thread },
    private val postToMainThread: (Runnable) -> Unit = { Handler(Looper.getMainLooper()).post(it) },
) : LifecycleTracker, LifecycleEventObserver {

    @Volatile
    private var listener: ProcessStateListener? = null

    override fun getProcessState(): ProcessState = when {
        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> ProcessState.FOREGROUND
        else -> ProcessState.BACKGROUND
    }

    @Volatile
    private var closed = false

    override fun register(listener: ProcessStateListener) {
        this.listener = listener
        runOnMainThread {
            // close() may have been called before a posted registration ran
            if (!closed) {
                lifecycleOwner.lifecycle.addObserver(this)
            }
        }
    }

    override fun close() {
        closed = true
        listener = null
        runOnMainThread {
            lifecycleOwner.lifecycle.removeObserver(this)
        }
    }

    /**
     * Lifecycle observers must be added and removed on the main thread to avoid IllegalStateExceptions with
     * androidx.lifecycle.
     */
    private fun runOnMainThread(action: () -> Unit) {
        val wrappedRunnable = Runnable {
            runCatching(action)
        }
        if (!isMainThread()) {
            postToMainThread(wrappedRunnable)
        } else {
            wrappedRunnable.run()
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> listener?.onForeground()
            Lifecycle.Event.ON_STOP -> listener?.onBackground()
            else -> {
                // no-op
            }
        }
    }
}
