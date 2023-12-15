@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

/**
 * Simple callback that can be registered for ON_PAUSE events in the LifecycleProcessOwner
 */
public class PauseProcessListener : LifecycleObserver {

    public var onPauseCallback: (() -> Unit)? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public fun onPauseCalled() {
        onPauseCallback?.invoke()
        onPauseCallback = null
    }
}
