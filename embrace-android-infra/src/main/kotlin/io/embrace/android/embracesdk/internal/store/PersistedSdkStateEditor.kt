package io.embrace.android.embracesdk.internal.store

import io.embrace.android.embracesdk.internal.session.UserSessionMetadata

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
    var userSession: UserSessionMetadata.Classified? = state.userSession
    var hostedSdkVersion: String? = state.hostedSdkVersion
    var hostedPlatformVersion: String? = state.hostedPlatformVersion
    var unityBuildIdNumber: String? = state.unityBuildIdNumber
    var javaScriptPatchNumber: String? = state.javaScriptPatchNumber
    var rnBundleUrl: String? = state.rnBundleUrl
    var rnBundleId: String? = state.rnBundleId

    internal fun build(): PersistedSdkState = state.copy(
        userId = userId,
        userEmail = userEmail,
        userName = userName,
        userPersonas = userPersonas,
        sessionProperties = sessionProperties,
        userSession = userSession,
        hostedSdkVersion = hostedSdkVersion,
        hostedPlatformVersion = hostedPlatformVersion,
        unityBuildIdNumber = unityBuildIdNumber,
        javaScriptPatchNumber = javaScriptPatchNumber,
        rnBundleUrl = rnBundleUrl,
        rnBundleId = rnBundleId,
    )
}

/**
 * Returns a copy of this state with [action] applied.
 */
fun PersistedSdkState.edit(action: PersistedSdkStateEditor.() -> Unit): PersistedSdkState =
    PersistedSdkStateEditor(this).apply(action).build()
