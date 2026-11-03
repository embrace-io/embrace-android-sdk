package io.embrace.android.embracesdk.internal.store

import io.embrace.android.embracesdk.internal.session.UserSessionMetadata

/**
 * The SDK's persisted mutable state.
 */
data class PersistedSdkState(

    /**
     * Identifies this install. The store generates and records this itself, so it can't be edited.
     */
    val deviceId: String,

    /**
     * When the SDK first ran on this install. The store records this itself, so it can't be edited.
     */
    val installTimestampMs: Long,

    /**
     * The user ID set via the public API.
     */
    val userId: String? = null,

    /**
     * The user email set via the public API.
     */
    val userEmail: String? = null,

    /**
     * The username set via the public API.
     */
    val userName: String? = null,

    /**
     * The personas last stored by the user service. This may include the first-day persona, which is re-derived
     * when read.
     */
    val userPersonas: Set<String>? = null,

    /**
     * Session properties that persist across user sessions.
     */
    val sessionProperties: Map<String, String> = emptyMap(),

    /**
     * The user session that was last persisted, or null if there is none.
     */
    val userSession: UserSessionMetadata.Classified? = null,

    /**
     * The version of the hosted (React Native, Unity or Flutter) SDK.
     */
    val hostedSdkVersion: String? = null,

    /**
     * The version of the hosted platform (React Native, Unity or Dart).
     */
    val hostedPlatformVersion: String? = null,

    /**
     * The Unity build ID.
     */
    val unityBuildIdNumber: String? = null,

    /**
     * The React Native JavaScript patch number.
     */
    val javaScriptPatchNumber: String? = null,

    /**
     * The React Native JavaScript bundle URL last set by the hosted SDK.
     */
    val rnBundleUrl: String? = null,

    /**
     * The React Native bundle ID computed for [rnBundleUrl], if the hosted SDK asked for it to be cached.
     */
    val rnBundleId: String? = null,
)
