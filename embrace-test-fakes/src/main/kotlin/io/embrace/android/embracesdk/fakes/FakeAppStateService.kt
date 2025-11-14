package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.lifecycle.AppState
import io.embrace.android.embracesdk.internal.session.lifecycle.AppStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.AppStateService

class FakeAppStateService(
    var state: AppState = AppState.FOREGROUND,
) : AppStateService {

    val listeners: MutableList<AppStateListener> = mutableListOf()
    var sessionDataUpdated = false

    override fun addListener(listener: AppStateListener) {
        listeners.add(listener)
    }

    override fun getAppState(): AppState = state

    override fun sessionUpdated() {
        sessionDataUpdated = true
    }
}
