package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateEditor
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.edit

class FakePersistedSdkStateStore(
    var state: PersistedSdkState = PersistedSdkState(deviceId = "fake-device-id", installTimestampMs = 0L),
) : PersistedSdkStateStore {

    var updateCount: Int = 0
        private set

    override fun snapshot(): PersistedSdkState = state

    override fun update(action: PersistedSdkStateEditor.() -> Unit): PersistedSdkState {
        updateCount++
        state = state.edit(action)
        return state
    }
}
