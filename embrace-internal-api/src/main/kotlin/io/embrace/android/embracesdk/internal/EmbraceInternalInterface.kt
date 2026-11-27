package io.embrace.android.embracesdk.internal

/**
 * Provides an internal interface to Embrace that is intended for use by hosted SDKs as their sole source of communication
 * with the Android SDK. This is not publicly supported and methods can change at any time.
 */
interface EmbraceInternalInterface {

    /**
     * Whether the Network Span Forwarding feature is enabled
     */
    fun isNetworkSpanForwardingEnabled(): Boolean

    /**
     * Adds a key-value-pair to the 'resource' object on session and log envelopes.
     *
     * This can be used to add arbitrary values such as the hybrid SDK's versioning for example.
     * It should be used sparingly and a schema must be agreed with the backend before
     * setting values here.
     */
    fun addEnvelopeResource(key: String, value: String)

    /**
     * Sets the version of the hosted SDK, e.g. the Embrace React Native/Flutter/Unity SDK. Null or empty values
     * are ignored.
     */
    fun setHostedSdkVersion(version: String?)

    /**
     * Sets the version of the platform the hosted SDK runs on, e.g. React Native/Dart/Unity. Null or empty values
     * are ignored.
     */
    fun setHostedPlatformVersion(version: String?)

    /**
     * Reports that the state called [name] has had [value] since [changedAtMs], in milliseconds since the epoch on the hosted
     * SDK's own clock. This is how a hosted SDK supplies what only it knows, such as the screen a route or a scene is.
     *
     * [name] has to be a state this SDK already defines, such as `screen-automatic`. A report for any other name is discarded:
     * a hosted SDK cannot create a state by naming one. Nothing happens either unless the app state data model is switched on.
     */
    fun reportState(name: String, value: String, changedAtMs: Long)
}
