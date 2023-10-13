package io.embrace.android.embracesdk.capture.cpu

/**
 * Component to get detailed CPU information from a device
 */
internal interface CpuInfoDelegate {
    /**
     * Get the name of the primary CPU of the device
     */
    fun getCpuName(): String?

    /**
     * Get the ELG of the primary CPU of the device
     */
    fun getElg(): String?
}
