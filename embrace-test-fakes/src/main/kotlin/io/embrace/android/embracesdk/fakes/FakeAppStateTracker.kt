package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker

class FakeAppStateTracker(
    var state: AppState = AppState.FOREGROUND,
) : AppStateTracker {

    val listeners: MutableList<AppStateListener> = mutableListOf()

    override fun addListener(listener: AppStateListener) {
        listeners.add(listener)
    }

    override fun getAppState(): AppState = state
}
