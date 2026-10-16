package io.embrace.android.embracesdk.internal.instance

/**
 * The lifecycle state of the SDK.
 */
internal enum class SdkState {

    /**
     * The SDK has not started yet.
     */
    NOT_STARTED,

    /**
     * An attempt to start the SDK did not succeed. The SDK will not attempt to start again.
     */
    FAILED,

    /**
     * The SDK started successfully.
     */
    STARTED,

    /**
     * The SDK was disabled. This state is terminal.
     */
    DISABLED,
}
