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
     * The SDK is enabled: it serves public API calls. It becomes enabled partway through its start, which returns once the
     * instance it enabled has finished its init.
     */
    ENABLED,

    /**
     * The SDK was disabled. This state is terminal.
     */
    DISABLED,
}
