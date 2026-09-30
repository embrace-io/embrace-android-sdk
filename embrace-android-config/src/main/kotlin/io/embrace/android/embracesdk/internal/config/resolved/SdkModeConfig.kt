package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Resolved SDK mode config.
 */
interface SdkModeConfig {

    /**
     * Whether this device falls outside the remote threshold, meaning the SDK should not start.
     */
    val sdkDisabled: Boolean
}

/**
 * Creates an [SdkModeConfig]. A provider returning null means the default is used.
 */
inline fun SdkModeConfig(
    crossinline sdkDisabled: () -> Boolean? = { null },
): SdkModeConfig = object : SdkModeConfig {
    override val sdkDisabled: Boolean = sdkDisabled() ?: false
}
