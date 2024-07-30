package io.embrace.android.embracesdk.internal.capture.cpu

/**
 * Component to get detailed CPU information from a device
 */
public interface CpuInfoDelegate {
    /**
     * Get the name of the primary CPU of the device
     */
    public fun getCpuName(): String?

    /**
     * Get the ELG of the primary CPU of the device
     */
    public fun getEgl(): String?
}
