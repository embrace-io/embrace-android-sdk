package io.embrace.android.embracesdk.internal.payload

import android.content.pm.PackageInfo

/**
 * Default string value for app info missing strings
 */
private const val UNKNOWN_VALUE = "UNKNOWN"

@Suppress("DEPRECATION")
public class PackageVersionInfo(
    packageInfo: PackageInfo,

    public val versionName: String = runCatching {
        packageInfo.versionName.toString().trim { it <= ' ' }
    }.getOrDefault(UNKNOWN_VALUE),

    public val versionCode: String = runCatching {
        packageInfo.versionCode.toString()
    }.getOrDefault(UNKNOWN_VALUE),

    public val packageName: String = runCatching {
        packageInfo.packageName
    }.getOrDefault(UNKNOWN_VALUE)
)
