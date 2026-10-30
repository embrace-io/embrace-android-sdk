package io.embrace.android.embracesdk.internal.store

/**
 * Holds the SDK's mutable state that is read during SDK init.
 */
interface PersistedSdkStateStore {

    /**
     * Returns the current state.
     */
    fun snapshot(): PersistedSdkState

    /**
     * Edits the state with [action] and persists the returned result. The editor starts from the current
     * state, so values that aren't updated still retain their current value.
     */
    fun update(action: PersistedSdkStateEditor.() -> Unit): PersistedSdkState
}
