package io.embrace.android.embracesdk.fakes

import android.content.res.Configuration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.internal.session.lifecycle.AppState
import io.embrace.android.embracesdk.internal.session.lifecycle.AppStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.AppStateService

class FakeAppStateService(
    override var isInBackground: Boolean = false,
) : AppStateService {

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    }

    val listeners: MutableList<AppStateListener> = mutableListOf()
    var config: Configuration? = null
    var sessionDataUpdated = false

    override fun addListener(listener: AppStateListener) {
        listeners.add(listener)
    }

    override fun close() {
    }

    override fun onForeground() {
    }

    override fun onBackground() {
    }

    override fun getAppState(): AppState = when (isInBackground) {
        true -> AppState.BACKGROUND
        false -> AppState.FOREGROUND
    }

    override fun isInitialized(): Boolean = true

    override fun sessionUpdated() {
        sessionDataUpdated = true
    }
}
