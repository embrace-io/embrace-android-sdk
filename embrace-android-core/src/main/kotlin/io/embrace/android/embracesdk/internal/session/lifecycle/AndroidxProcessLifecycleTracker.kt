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
) : LifecycleTracker, LifecycleEventObserver {

    private val mainLooper = Looper.getMainLooper()
    private val mainThread = mainLooper.thread

    @Volatile
    private var listener: ProcessStateListener? = null

    override fun getProcessState(): ProcessState = when {
        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> ProcessState.FOREGROUND
        else -> ProcessState.BACKGROUND
    }

    override fun register(listener: ProcessStateListener) {
        this.listener = listener

        // add lifecycle observer on main thread to avoid IllegalStateExceptions with
        // androidx.lifecycle
        val wrappedRunnable = Runnable {
            runCatching {
                lifecycleOwner.lifecycle.addObserver(this)
            }
        }
        if (Thread.currentThread() !== mainThread) {
            val mainHandler = Handler(mainLooper)
            mainHandler.post(wrappedRunnable)
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
