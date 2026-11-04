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
     * Sets the version of the hosted SDK, e.g. the Embrace React Native/Flutter/Unity SDK, and the version of the
     * platform it runs on, e.g. React Native/Dart/Unity, in a single write. A null or empty value leaves that version
     * unchanged.
     */
    fun setHostedSdkVersions(sdkVersion: String?, platformVersion: String?)
}
