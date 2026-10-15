package io.embrace.android.embracesdk.internal.instance

/**
 * The lifecycle state of the SDK.
 */
internal enum class SdkState {

    /**
     * The SDK has not started yet, or a previous attempt to start it did not succeed.
     */
    NOT_STARTED,

    /**
     * The SDK started successfully.
     */
    STARTED,

    /**
     * The SDK was disabled. This state is terminal.
     */
    DISABLED,
}
