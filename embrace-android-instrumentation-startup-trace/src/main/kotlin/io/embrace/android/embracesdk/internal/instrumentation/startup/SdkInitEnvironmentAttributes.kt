package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.app.ActivityManager
import android.content.pm.PackageInfo
import android.os.Build.VERSION_CODES
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.APP_IMAGE_AT_INIT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.ART_COMPILER_FILTER
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.LOW_MEMORY
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.MEM_AVAILABLE_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.NUMERIC_ERROR
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.PREFS_FILE_BYTES
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.SECONDS_SINCE_BOOT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.SECONDS_SINCE_INSTALL
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.SECONDS_SINCE_UPDATE
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.STRING_ERROR
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_HEADROOM_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_HEADROOM_PCT_MIN_API
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_HEADROOM_PCT_UNAVAILABLE
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_STATUS
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_STATUS_MIN_API
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import kotlin.math.roundToLong

/**
 * Attributes describing the state of the execution environment around SDK init that are read at
 * record time rather than captured during the init window. These attributes still have to be
 * valid for SDK init despite the post-init retrieval. For instance, the device's thermal state
 * changes over tens of seconds, so a momentary delay is mostly going to be correct. Meanwhile,
 * recently the app was installed or updated is immutable during the lifetime of an app instance,
 * so they are safe to be retrieved at any time.
 *
 * Reading the raw data to produce these attributes might involve expensive operations such as
 * binder calls or disk reads, so this should not be called on the SDK init thread during startup.
 */
fun sdkInitEnvironmentAttributes(
    nowMs: Long,
    errorHandler: ErrorHandler,
    packageInfo: PackageInfo?,
    powerManagerProvider: () -> PowerManager?,
    activityManagerProvider: () -> ActivityManager?,
    versionChecker: VersionChecker = BuildVersionChecker,
    uptimeMs: () -> Long = { SystemClock.uptimeMillis() },
    prefsFileSizeProvider: () -> Long? = { null },
    artOptimizationProvider: () -> ArtOptimizationState? = { null },
): Map<String, String> = buildMap {
    safePutAttributes(errorHandler, "thermal") {
        putThermalAttributes(powerManagerProvider, versionChecker, errorHandler)
    }
    safePutAttributes(errorHandler, "install-recency") {
        putInstallRecencyAttributes(packageInfo, nowMs, errorHandler)
    }
    safePutAttributes(errorHandler, "memory") {
        putMemoryAttributes(activityManagerProvider, errorHandler)
    }
    safePutAttributes(errorHandler, SECONDS_SINCE_BOOT) {
        putSecondsSinceBoot(uptimeMs, errorHandler)
    }
    safePutAttributes(errorHandler, PREFS_FILE_BYTES) {
        putPrefsFileSize(prefsFileSizeProvider, errorHandler)
    }
    safePutAttributes(errorHandler, "art-optimization") {
        putArtOptimizationAttributes(artOptimizationProvider, errorHandler)
    }
}

/**
 * Reports that the value for the attribute [key] could not be derived on a device where it should have been
 * due to the underlying [cause], if available.
 */
internal fun ErrorHandler.trackAttributeError(key: String, cause: Throwable? = null) {
    trackSdkError(
        type = SdkErrorType.SdkInitAttributeCaptureFail,
        throwable = IllegalStateException("Attribute failed to record: $key", cause),
    )
}

/**
 * Runs [block] to contribute the attributes derived from one source, keeping a failure in it from costing us the
 * attributes derived from every other source. Each attribute inside the block is expected to handle its own computation
 * failure, but this provides a wrapper so SDK errors can be logged for the failure of the whole block.
 */
internal inline fun MutableMap<String, String>.safePutAttributes(
    errorHandler: ErrorHandler,
    attributesTypeName: String,
    block: MutableMap<String, String>.() -> Unit,
) {
    try {
        block()
    } catch (t: Throwable) {
        errorHandler.trackAttributeError(key = attributesTypeName, cause = t)
    }
}

private fun MutableMap<String, String>.putThermalAttributes(
    powerManagerProvider: () -> PowerManager?,
    versionChecker: VersionChecker,
    errorHandler: ErrorHandler,
) {
    val statusSupported = versionChecker.isAtLeast(THERMAL_STATUS_MIN_API)
    val headroomSupported = versionChecker.isAtLeast(THERMAL_HEADROOM_PCT_MIN_API)
    if (!statusSupported && !headroomSupported) {
        return
    }
    val result = runCatching { powerManagerProvider() }

    val powerManager = result.getOrNull()
    if (powerManager == null) {
        // Record error sentinels as attribute values if the OS version specific PowerManger APIs but the PowerManager cannot be obtained.
        if (statusSupported) {
            put(THERMAL_STATUS, STRING_ERROR)
        }
        if (headroomSupported) {
            put(THERMAL_HEADROOM_PCT, NUMERIC_ERROR)
        }

        errorHandler.trackAttributeError(key = THERMAL_STATUS, cause = result.exceptionOrNull())
    } else {
        if (statusSupported) {
            putThermalStatus(powerManager, errorHandler)
        }
        if (headroomSupported) {
            putThermalHeadroom(powerManager, errorHandler)
        }
    }
}

@RequiresApi(VERSION_CODES.Q)
private fun MutableMap<String, String>.putThermalStatus(powerManager: PowerManager, errorHandler: ErrorHandler) {
    runCatching { powerManager.currentThermalStatus }.fold(
        onSuccess = { status ->
            // Do not record the attribute if the PowerManager reports there is no thermal status because is the expected case
            if (status != PowerManager.THERMAL_STATUS_NONE) {
                put(THERMAL_STATUS, thermalStatusName(status))
            }
        },
        onFailure = { throwable ->
            put(THERMAL_STATUS, STRING_ERROR)
            errorHandler.trackAttributeError(THERMAL_STATUS, throwable)
        },
    )
}

@RequiresApi(VERSION_CODES.R)
private fun MutableMap<String, String>.putThermalHeadroom(powerManager: PowerManager, errorHandler: ErrorHandler) {
    runCatching { powerManager.getThermalHeadroom(0) }.fold(
        onSuccess = { headroom ->
            if (headroom.isFinite()) {
                val pct = (headroom * 100).roundToLong().coerceIn(0L, MAX_THERMAL_HEADROOM_PCT)
                put(THERMAL_HEADROOM_PCT, pct.toString())
            } else {
                // The platform's way of answering that it has no forecast at this time, but it may later
                put(THERMAL_HEADROOM_PCT, THERMAL_HEADROOM_PCT_UNAVAILABLE)
            }
        },
        onFailure = { throwable ->
            put(THERMAL_HEADROOM_PCT, NUMERIC_ERROR)
            errorHandler.trackAttributeError(THERMAL_HEADROOM_PCT, throwable)
        },
    )
}

private fun MutableMap<String, String>.putInstallRecencyAttributes(
    packageInfo: PackageInfo?,
    nowMs: Long,
    errorHandler: ErrorHandler,
) {
    if (packageInfo == null) {
        put(SECONDS_SINCE_INSTALL, NUMERIC_ERROR)
        put(SECONDS_SINCE_UPDATE, NUMERIC_ERROR)
        errorHandler.trackAttributeError(SECONDS_SINCE_INSTALL)
    } else {
        putRecency(SECONDS_SINCE_INSTALL, packageInfo.firstInstallTime, nowMs, errorHandler)
        putRecency(SECONDS_SINCE_UPDATE, packageInfo.lastUpdateTime, nowMs, errorHandler)
    }
}

/**
 * Records how long ago [thenMs] was, given that the package manager always knows when it installed and last updated an
 * app it is running. If the timestamps provided are not usable, it is an error that should be logged.
 */
private fun MutableMap<String, String>.putRecency(key: String, thenMs: Long, nowMs: Long, errorHandler: ErrorHandler) {
    if (thenMs in 1..nowMs) {
        put(key, ((nowMs - thenMs) / 1000L).toString())
    } else {
        put(key, NUMERIC_ERROR)
        errorHandler.trackAttributeError(key)
    }
}

private fun MutableMap<String, String>.putMemoryAttributes(
    activityManagerProvider: () -> ActivityManager?,
    errorHandler: ErrorHandler,
) {
    val result = runCatching {
        activityManagerProvider()?.let { activityManager ->
            ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        }
    }

    val memoryInfo = result.getOrNull()
    if (memoryInfo == null || memoryInfo.totalMem <= 0) {
        put(MEM_AVAILABLE_PCT, NUMERIC_ERROR)
        errorHandler.trackAttributeError(key = MEM_AVAILABLE_PCT, cause = result.exceptionOrNull())
    } else {
        val availablePct = (100.0 * memoryInfo.availMem / memoryInfo.totalMem).roundToLong()
        put(MEM_AVAILABLE_PCT, availablePct.coerceIn(0L, MAX_MEM_AVAILABLE_PCT).toString())

        if (memoryInfo.lowMemory) {
            put(LOW_MEMORY, "true")
        }
    }
}

private fun MutableMap<String, String>.putSecondsSinceBoot(uptimeMs: () -> Long, errorHandler: ErrorHandler) {
    runCatching { uptimeMs() }.fold(
        onSuccess = { millis ->
            if (millis >= 0) {
                put(SECONDS_SINCE_BOOT, (millis / 1000L).toString())
            } else {
                put(SECONDS_SINCE_BOOT, NUMERIC_ERROR)
                errorHandler.trackAttributeError(SECONDS_SINCE_BOOT)
            }
        },
        onFailure = { throwable ->
            put(SECONDS_SINCE_BOOT, NUMERIC_ERROR)
            errorHandler.trackAttributeError(SECONDS_SINCE_BOOT, throwable)
        },
    )
}

private fun MutableMap<String, String>.putPrefsFileSize(prefsFileSizeProvider: () -> Long?, errorHandler: ErrorHandler) {
    runCatching { prefsFileSizeProvider() }.fold(
        onSuccess = { bytes ->
            // If the file cannot be found, treat it as no bytes are loaded
            put(PREFS_FILE_BYTES, (bytes ?: 0L).coerceAtLeast(0L).toString())
        },
        onFailure = { throwable ->
            put(PREFS_FILE_BYTES, NUMERIC_ERROR)
            errorHandler.trackAttributeError(PREFS_FILE_BYTES, throwable)
        },
    )
}

private fun MutableMap<String, String>.putArtOptimizationAttributes(
    artOptimizationStateProvider: () -> ArtOptimizationState?,
    errorHandler: ErrorHandler,
) {
    runCatching { artOptimizationStateProvider() }
        .fold(
            onSuccess = { state ->
                // Only log these attributes if we can determine the ART optimization state.
                // Not being able to determine that is an expected scenario so no error should be logged.
                if (state != null) {
                    put(ART_COMPILER_FILTER, state.artCompilerFilter)
                    put(APP_IMAGE_AT_INIT, state.hasAppImage.toString())
                }
            },
            onFailure = { throwable ->
                put(ART_COMPILER_FILTER, STRING_ERROR)
                errorHandler.trackAttributeError(ART_COMPILER_FILTER, throwable)
            },
        )
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

/**
 * At and beyond the severe-throttling threshold the headroom scale is not calibrated across
 * vendors, and the thermal-status levels carry that region instead - so everything at or past
 * the threshold collapses into the single 100 group.
 */
private const val MAX_THERMAL_HEADROOM_PCT = 100L
private const val MAX_MEM_AVAILABLE_PCT = 100L
