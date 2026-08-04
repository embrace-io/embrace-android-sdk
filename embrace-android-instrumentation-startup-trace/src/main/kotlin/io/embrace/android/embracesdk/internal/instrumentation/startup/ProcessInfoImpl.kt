package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.os.Build.VERSION_CODES
import android.os.Process
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbStartupLaunchReasonValues
import java.util.concurrent.TimeUnit

internal class ProcessInfoImpl(
    private val deviceStartTimeMs: Long,
    private val versionChecker: VersionChecker,
    private val activityManager: ActivityManager? = null,
) : ProcessInfo {

    @Volatile
    private var launchReasonValue: String? = null

    @Volatile
    private var launchReasonResolved: Boolean = false

    override fun startRequestedTimeMs(): Long? {
        return if (versionChecker.isAtLeast(VERSION_CODES.TIRAMISU)) {
            deviceStartTimeMs + Process.getStartRequestedElapsedRealtime()
        } else if (versionChecker.isAtLeast(VERSION_CODES.N)) {
            deviceStartTimeMs + Process.getStartElapsedRealtime()
        } else {
            null
        }
    }

    @Synchronized
    override fun prefetchLaunchReason() {
        if (!launchReasonResolved) {
            launchReasonValue = resolveLaunchReason()
            launchReasonResolved = true
        }
    }

    override fun launchReason(): String? {
        if (!launchReasonResolved) {
            prefetchLaunchReason()
        }
        return launchReasonValue
    }

    private fun resolveLaunchReason(): String? {
        if (!versionChecker.isAtLeast(VERSION_CODES.VANILLA_ICE_CREAM)) {
            return null
        }
        val manager = activityManager ?: return null

        // this makes a binder call, hence being kept off the thread the app is starting up on
        return runCatching { manager.startInfoForThisProcess()?.reason?.toLaunchReason() }.getOrNull()
    }

    /**
     * Read rather than waiting on `ActivityManager.addApplicationStartInfoCompletionListener`, which reports only once the platform treats
     * startup as finished at first frame drawn. That callback comes back from the system server after our own first draw callback does, so
     * it would arrive too late to put on the trace. What we get here instead is the in-progress record for this launch, which is the
     * documented use of this call, and the reason is set when a record is created so it is populated even though most of the record is not.
     *
     * Records are not narrowed to a single process. They cover the whole package, are returned newest first, and carry no pid or process
     * name until the start they describe completes, so a start logged for this app after ours is indistinguishable from ours by identity
     * alone. Rule those out by time instead: a start requested after the system was already about to fork this process cannot be what
     * caused this process to exist. Anything we cannot place before our own fork is discarded rather than reported as if it were ours.
     */
    @RequiresApi(VERSION_CODES.VANILLA_ICE_CREAM)
    internal fun ActivityManager.startInfoForThisProcess(): ApplicationStartInfo? {
        val processStartRequestedMs = Process.getStartRequestedUptimeMillis()
        return getHistoricalProcessStartReasons(MAX_START_INFO_RECORDS_QUERIED)
            .firstOrNull { it.startedBy(processStartRequestedMs) }
    }

    /**
     * Whether this record describes a start that was already underway when the system was about to fork us, given as a
     * [android.os.SystemClock.uptimeMillis] value. The platform stamps startup timestamps from the uptime clock, so
     * [Process.getStartRequestedUptimeMillis] is the comparable reading of our own start; the elapsed realtime equivalents used elsewhere
     * in this class are not, as they count time spent in deep sleep.
     */
    @RequiresApi(VERSION_CODES.VANILLA_ICE_CREAM)
    private fun ApplicationStartInfo.startedBy(processStartRequestedMs: Long): Boolean {
        val launchUptimeNs = startupTimestamps[ApplicationStartInfo.START_TIMESTAMP_LAUNCH] ?: return false
        return TimeUnit.NANOSECONDS.toMillis(launchUptimeNs) <= processStartRequestedMs
    }

    /**
     * Maps a platform start reason to the value used in telemetry. Returns null for reasons added in future Android versions that we don't
     * know about yet, as reporting those as [EmbStartupLaunchReasonValues.OTHER] would be indistinguishable from the platform itself
     * saying the launch was uncategorised.
     */
    @RequiresApi(VERSION_CODES.VANILLA_ICE_CREAM)
    internal fun Int.toLaunchReason(): String? = when (this) {
        ApplicationStartInfo.START_REASON_ALARM -> EmbStartupLaunchReasonValues.ALARM
        ApplicationStartInfo.START_REASON_BACKUP -> EmbStartupLaunchReasonValues.BACKUP
        ApplicationStartInfo.START_REASON_BOOT_COMPLETE -> EmbStartupLaunchReasonValues.BOOT_COMPLETE
        ApplicationStartInfo.START_REASON_BROADCAST -> EmbStartupLaunchReasonValues.BROADCAST
        ApplicationStartInfo.START_REASON_CONTENT_PROVIDER -> EmbStartupLaunchReasonValues.CONTENT_PROVIDER
        ApplicationStartInfo.START_REASON_JOB -> EmbStartupLaunchReasonValues.JOB
        ApplicationStartInfo.START_REASON_LAUNCHER -> EmbStartupLaunchReasonValues.LAUNCHER
        ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS -> EmbStartupLaunchReasonValues.LAUNCHER_RECENTS
        ApplicationStartInfo.START_REASON_OTHER -> EmbStartupLaunchReasonValues.OTHER
        ApplicationStartInfo.START_REASON_PUSH -> EmbStartupLaunchReasonValues.PUSH
        ApplicationStartInfo.START_REASON_SERVICE -> EmbStartupLaunchReasonValues.SERVICE
        ApplicationStartInfo.START_REASON_START_ACTIVITY -> EmbStartupLaunchReasonValues.START_ACTIVITY
        else -> null
    }

    companion object {
        private const val MAX_START_INFO_RECORDS_QUERIED = 5
    }
}
