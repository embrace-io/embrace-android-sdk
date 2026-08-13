package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.content.pm.PackageInfo
import android.os.Build.VERSION_CODES
import android.os.PowerManager
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
    packageInfo: PackageInfo?,
    nowMs: Long,
    versionChecker: VersionChecker = BuildVersionChecker,
): Map<String, String> = try {
    buildMap {
        if (versionChecker.isAtLeast(VERSION_CODES.Q)) {
            val powerManager = powerManagerProvider()
            if (powerManager != null) {
                put(THERMAL_STATUS_ATTR, thermalStatusName(powerManager.currentThermalStatus))
                if (versionChecker.isAtLeast(VERSION_CODES.R)) {
                    val headroom = runCatching { powerManager.getThermalHeadroom(0) }.getOrNull()
                    if (headroom != null && headroom.isFinite()) {
                        val headroomPct = (headroom * 100).roundToLong().coerceAtMost(MAX_THERMAL_HEADROOM_PCT)
                        put(THERMAL_HEADROOM_PCT_ATTR, headroomPct.toString())
                    }
                }
            }
        }
        if (packageInfo != null) {
            secondsBetween(packageInfo.firstInstallTime, nowMs)?.let { seconds ->
                put(SECONDS_SINCE_INSTALL_ATTR, seconds.toString())
            }
            secondsBetween(packageInfo.lastUpdateTime, nowMs)?.let { seconds ->
                put(SECONDS_SINCE_UPDATE_ATTR, seconds.toString())
            }
        }
    }
} catch (_: Throwable) {
    emptyMap()
}

/**
 * The device's overall thermal throttling status at record time, as reported by
 * [PowerManager.getCurrentThermalStatus] (API 29+): none/light/moderate/severe/critical/
 * emergency/shutdown.
 */
const val THERMAL_STATUS_ATTR: String = "thermal-status"

/**
 * How far the device's thermal forecast is toward the severe-throttling threshold, as a whole
 * percentage per [PowerManager.getThermalHeadroom] (API 30+). Note the polarity: 100 means AT
 * or beyond the severe-throttling threshold and low values mean cool. Forecasts beyond severe
 * are capped into the single 100 group - the headroom scale is uncalibrated up there, and
 * [THERMAL_STATUS_ATTR]'s severe/critical/emergency/shutdown levels are the calibrated signal
 * for that region. Omitted where the device does not support forecasting.
 */
const val THERMAL_HEADROOM_PCT_ATTR: String = "thermal-headroom-pct"

/**
 * Seconds between the app's first install and SDK init. Small values indicate init ran close
 * to when install happened, when there could be more concurrent work vying for CPU and RAM,
 * like dexopt or app-specific tasks.
 */
const val SECONDS_SINCE_INSTALL_ATTR: String = "seconds-since-install"

/**
 * Seconds between the app's last update and SDK init. Small values indicate init ran close
 * to when app update happened, when there could be similar factors to slow down init we see
 * in fresh installs, as well as post-update tasks like DB migrations.
 */
const val SECONDS_SINCE_UPDATE_ATTR: String = "seconds-since-update"

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
