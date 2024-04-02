package io.embrace.android.embracesdk.internal

import android.os.Build
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes

/**
 * Information about the the device or OS that can be retrieved without disk or platform API access
 */
internal data class SystemInfo(
    val osName: String = "Android OS",
    val osType: String = OsIncubatingAttributes.OsTypeValues.LINUX,
    val osBuild: String = getOsBuild(),
    val osVersion: String = getOsVersion(),
    val androidOsApiLevel: String = getOsApiLevel(),
    val deviceManufacturer: String = getDeviceManufacturer(),
    val deviceModel: String = getDeviceModel()
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
