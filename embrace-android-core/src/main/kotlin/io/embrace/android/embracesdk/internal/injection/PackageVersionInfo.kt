package io.embrace.android.embracesdk.internal.injection

import android.content.pm.PackageInfo

/**
 * Default string value for app info missing strings
 */
private const val UNKNOWN_VALUE = "UNKNOWN"

@Suppress("DEPRECATION")
class PackageVersionInfo(
    packageInfo: PackageInfo,

    val versionName: String = runCatching {
        packageInfo.versionName.toString().trim { it <= ' ' }
    }.getOrDefault(UNKNOWN_VALUE),

    val versionCode: String = runCatching {
        packageInfo.versionCode.toString()
    }.getOrDefault(UNKNOWN_VALUE),

    val packageName: String = runCatching {
        packageInfo.packageName
    }.getOrDefault(UNKNOWN_VALUE),
)
