package io.embrace.android.embracesdk.internal.store

/**
 * Mutable view of [PersistedSdkState] used by [PersistedSdkStateStore.update]. Each value starts as the state's
 * current value.
 */
class PersistedSdkStateEditor internal constructor(private val state: PersistedSdkState) {
    internal fun build(): PersistedSdkState = state
}

/**
 * Returns a copy of this state with [action] applied.
 */
fun PersistedSdkState.edit(action: PersistedSdkStateEditor.() -> Unit): PersistedSdkState =
    PersistedSdkStateEditor(this).apply(action).build()
