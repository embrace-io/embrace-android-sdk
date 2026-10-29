package io.embrace.android.embracesdk.internal.store

/**
 * Mutable view of [PersistedSdkState] used by [PersistedSdkStateStore.update]. Each value starts as the state's
 * current value.
 */
class PersistedSdkStateEditor internal constructor(private val state: PersistedSdkState) {
    var userId: String? = state.userId
    var userEmail: String? = state.userEmail
    var userName: String? = state.userName
    var userPersonas: Set<String>? = state.userPersonas
    var sessionProperties: Map<String, String> = state.sessionProperties

    internal fun build(): PersistedSdkState = state.copy(
        userId = userId,
        userEmail = userEmail,
        userName = userName,
        userPersonas = userPersonas,
        sessionProperties = sessionProperties,
    )
}

/**
 * Returns a copy of this state with [action] applied.
 */
fun PersistedSdkState.edit(action: PersistedSdkStateEditor.() -> Unit): PersistedSdkState =
    PersistedSdkStateEditor(this).apply(action).build()
