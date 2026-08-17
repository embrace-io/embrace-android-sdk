package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.app.ActivityManager
import android.content.pm.PackageInfo
import android.os.Build.VERSION_CODES
import android.os.PowerManager
import android.os.SystemClock
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.LOW_MEMORY
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.MEM_AVAILABLE_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.PREFS_FILE_BYTES
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.SECONDS_SINCE_BOOT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.SECONDS_SINCE_INSTALL
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.SECONDS_SINCE_UPDATE
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_HEADROOM_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_STATUS
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import kotlin.math.roundToLong

/**
 * Attributes describing the state of the execution environment around SDK init that are read at
 * record time rather than captured during the init window. These attributes still have to be
 * valid for SDK init despite the post-init retrieval. For instance, the device's thermal state
 * changes over tens of seconds, so a momentary delay is mostly going to be correct. Meanwhile,
 * recently the app was installed or updated is immutable during the lifetime of an app instance,
 * so they are safe to be retrieved at any time. Some of these attributes may be OS version
 * dependent, so attributes whose underlying data aren't available will just be dropped.
 *
 * Reading the raw data to inform these attributes might involve binder calls, so do not call
 * this should not be called on the SDK init thread during startup.
 */
fun sdkInitEnvironmentAttributes(
    powerManagerProvider: () -> PowerManager?,
    activityManagerProvider: () -> ActivityManager?,
    packageInfo: PackageInfo?,
    nowMs: Long,
    versionChecker: VersionChecker = BuildVersionChecker,
    uptimeMs: () -> Long = { SystemClock.uptimeMillis() },
    prefsFileSizeProvider: () -> Long? = { null },
): Map<String, String> = try {
    buildMap {
        putThermalAttributes(powerManagerProvider, versionChecker)
        putInstallRecencyAttributes(packageInfo, nowMs)
        putMemoryAttributes(activityManagerProvider)
        put(SECONDS_SINCE_BOOT, (uptimeMs() / 1000L).toString())
        putPrefsFileSize(prefsFileSizeProvider)
    }
} catch (_: Throwable) {
    emptyMap()
}

private fun MutableMap<String, String>.putPrefsFileSize(prefsFileSizeProvider: () -> Long?) {
    val bytes = runCatching { prefsFileSizeProvider() }.getOrNull()
    if (bytes != null && bytes > 0) {
        put(PREFS_FILE_BYTES, bytes.toString())
    }
}

private fun MutableMap<String, String>.putThermalAttributes(
    powerManagerProvider: () -> PowerManager?,
    versionChecker: VersionChecker,
) {
    if (versionChecker.isAtLeast(VERSION_CODES.Q)) {
        val powerManager = powerManagerProvider() ?: return
        val thermalStatus = powerManager.currentThermalStatus
        if (thermalStatus != PowerManager.THERMAL_STATUS_NONE) {
            put(THERMAL_STATUS, thermalStatusName(thermalStatus))
        }
        if (versionChecker.isAtLeast(VERSION_CODES.R)) {
            val headroom = runCatching { powerManager.getThermalHeadroom(0) }.getOrNull()
            if (headroom != null && headroom.isFinite()) {
                val headroomPct = (headroom * 100).roundToLong().coerceAtMost(MAX_THERMAL_HEADROOM_PCT)
                put(THERMAL_HEADROOM_PCT, headroomPct.toString())
            }
        }
    }
}

private fun MutableMap<String, String>.putInstallRecencyAttributes(packageInfo: PackageInfo?, nowMs: Long) {
    if (packageInfo != null) {
        secondsBetween(packageInfo.firstInstallTime, nowMs)?.let { seconds ->
            put(SECONDS_SINCE_INSTALL, seconds.toString())
        }
        secondsBetween(packageInfo.lastUpdateTime, nowMs)?.let { seconds ->
            put(SECONDS_SINCE_UPDATE, seconds.toString())
        }
    }
}

private fun MutableMap<String, String>.putMemoryAttributes(activityManagerProvider: () -> ActivityManager?) {
    val memoryInfo = runCatching {
        activityManagerProvider()?.let { activityManager ->
            ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        }
    }.getOrNull()
    if (memoryInfo != null && memoryInfo.totalMem > 0) {
        put(MEM_AVAILABLE_PCT, (100.0 * memoryInfo.availMem / memoryInfo.totalMem).roundToLong().toString())
        if (memoryInfo.lowMemory) {
            put(LOW_MEMORY, "true")
        }
    }
}

private fun thermalStatusName(status: Int): String = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> "none"
    PowerManager.THERMAL_STATUS_LIGHT -> "light"
    PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
    PowerManager.THERMAL_STATUS_SEVERE -> "severe"
    PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
    PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
    PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
    else -> status.toString()
}

private fun secondsBetween(thenMs: Long, nowMs: Long): Long? = if (thenMs in 1..nowMs) {
    (nowMs - thenMs) / 1000L
} else {
    null
}

/**
 * At and beyond the severe-throttling threshold the headroom scale is not calibrated across
 * vendors, and the thermal-status levels carry that region instead - so everything at or past
 * the threshold collapses into the single 100 group.
 */
private const val MAX_THERMAL_HEADROOM_PCT = 100L
