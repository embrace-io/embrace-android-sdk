package io.embrace.android.embracesdk.internal.envelope.resource

import io.embrace.android.embracesdk.internal.SystemInfo

interface Device {

    /**
     * Tries to determine whether the device is jailbroken by looking for specific directories which
     * exist on jailbroken devices. Emulators are excluded and will always return false.
     *
     * @return true if the device is jailbroken and not an emulator, false otherwise
     */
    var isJailbroken: Boolean?

    /**
     * Gets the device's screen resolution.
     *
     * @param windowManager the {@link WindowManager} from the {@link Context}
     * @return the device's screen resolution
     */
    var screenResolution: String

    /**
     * Get information about the device and OS known before the SDK starts up
     */
    val systemInfo: SystemInfo

    /**
     * Get the number of available cores for device info
     *
     * @return Number of cores in long
     */
    val numberOfCores: Int

    /**
     * Gets the free capacity of the internal storage of the device.
     *
     * @param statFs the {@link StatFs} service for the device
     * @return the total free capacity of the internal storage of the device in bytes
     */
    val internalStorageTotalCapacity: Lazy<Long>
}
