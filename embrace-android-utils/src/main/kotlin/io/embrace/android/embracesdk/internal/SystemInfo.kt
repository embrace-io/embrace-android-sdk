package io.embrace.android.embracesdk.internal

import android.os.Build

/**
 * Information about the the device or OS that can be retrieved without disk or platform API access
 */
data class SystemInfo(
    /**
     * Name of the operating system of the device. To use the Android SDK, this has to be Android, so this is always "android"
     */
    val osName: String = "android",

    /**
     * Type of operating system the device is running. Android is built on Linux, so this is always "linux" for this SDK
     */
    val osType: String = "linux",

    /**
     *  Build ID of the version of the install OS. This will not be the Android API version, which is [osVersion]
     */
    val osBuild: String = getOsBuild(),

    /**
     * Version of the installed operating system on the device.
     */
    val osVersion: String = getOsVersion(),

    /**
     * Android API level running on the device
     */
    val androidOsApiLevel: String = getOsApiLevel(),

    /**
     * Name of the manufacturer of the device.
     */
    val deviceManufacturer: String = getDeviceManufacturer(),

    /**
     * Name of the model of the device.
     */
    val deviceModel: String = getDeviceModel(),
)

internal fun getOsBuild(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Build.VERSION.BASE_OS
    } else {
        ""
    }
}

internal fun getOsVersion(): String {
    return try {
        Build.VERSION.RELEASE
    } catch (t: Throwable) {
        ""
    }
}

internal fun getOsApiLevel(): String {
    return try {
        Build.VERSION.SDK_INT.toString()
    } catch (t: Throwable) {
        ""
    }
}

internal fun getDeviceManufacturer(): String {
    return try {
        Build.MANUFACTURER
    } catch (t: Throwable) {
        ""
    }
}

internal fun getDeviceModel(): String {
    return try {
        Build.MODEL
    } catch (t: Throwable) {
        ""
    }
}

/**
 * Tries to determine whether the device is an emulator by looking for known models and
 * manufacturers which correspond to emulators.
 *
 * @return true if the device is detected to be an emulator, false otherwise
 */
fun SystemInfo.isEmulator(): Boolean =
    Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.FINGERPRINT.contains("emulator") ||
        deviceModel.contains("google_sdk") ||
        deviceModel.contains("sdk_gphone64") ||
        deviceModel.contains("Emulator") ||
        deviceModel.contains("Android SDK built for") ||
        deviceManufacturer.contains("Genymotion") ||
        Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
        Build.PRODUCT.equals("google_sdk")
