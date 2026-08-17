package io.embrace.android.embracesdk.internal.instrumentation.startup

import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_RUN_DELAY_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_STATUS

/**
 * Keys of the attributes recorded on the SDK init spans to contextualize and explain
 * performance anomalies. [SdkInitResourceUsageTracker] produces the ones measured as deltas across
 * the init window; [sdkInitEnvironmentAttributes] produces the ones describing the ambient
 * conditions the init ran under, read at record time.
 *
 * These are candidates to become proper semantic conventions once their shapes settle; until
 * then they are deliberately kept as plain constants so nothing is locked in.
 */
object SdkInitAttributeKeys {

    /**
     * Percentage of the init window the init thread spent executing on a CPU, rounded to a
     * whole number. Triage: a slow init with HIGH cpu-pct ran slowly (thermal throttling,
     * uncompiled/interpreted code, weak silicon - the work itself took longer); a slow init
     * with LOW cpu-pct didn't get to run (see run-delay for who to blame). Together with
     * [INIT_RUN_DELAY_PCT] this splits every slow run into ran-slow vs was-blocked vs
     * was-starved without needing a trace.
     */
    const val INIT_CPU_PCT: String = "init-cpu-pct"

    /**
     * Percentage of the init window the init thread spent runnable but waiting for a CPU,
     * as a whole number. This is the direct measure of CPU contention: other work (other
     * apps, system services, our own background threads, concurrent GC) held the cores
     * while init waited. Bench-measured as the strongest single correlate of slow inits -
     * a high value means "the device was busy", which is diagnostic (not our code) and
     * should be excluded from regression comparisons.
     *
     * This is the only CPU-contention signal available to us, so do not expect to corroborate it
     * with a device-wide one. The kernel's PSI counters (/proc/pressure/cpu) are the obvious
     * candidate and are unreachable: AOSP labels them with their own SELinux types and grants read
     * access to lmkd and system_server only - no app domain has it, on any Android version since
     * the types were introduced in Android 10. An attribute reading them was implemented, measured
     * across 12 launches on 4 devices spanning API 29-35 and two vendors, populated zero times,
     * and removed. Note that it reads fine from `adb shell`, whose domain has broad legacy /proc
     * access an app never gets - so verify any such idea with `run-as <pkg>`, never a plain shell.
     */
    const val INIT_RUN_DELAY_PCT: String = "init-run-delay-pct"

    /**
     * Kilobytes actually read from storage by the process during the init window. Normal
     * inits read little (config file + cold pages); elevated values mark storage-bound
     * runs - cold caches after reboot/update, slow flash, or IO contention from concurrent
     * installs.
     */
    const val INIT_DISK_READ_KB: String = "init-disk-read-kb"

    /**
     * ART garbage collections (any type, process-wide) during the init window. A
     * deliberately imprecise, directional signal: it says there is some amount of GC
     * and doesn't try to precisely determine how much time it took.
     */
    const val INIT_GC_COUNT: String = "init-gc-count"

    /**
     * Seconds of non deep-sleep time between device boot and SDK init
     * (i.e. [android.os.SystemClock.uptimeMillis]). Small values indicate a device that has
     * not yet had the running time to finish its post-boot work.
     */
    const val SECONDS_SINCE_BOOT: String = "seconds-since-boot"

    /**
     * The device's overall thermal throttling status at record time, as reported by
     * [android.os.PowerManager.getCurrentThermalStatus] (API 29+): light/moderate/severe/
     * critical/emergency/shutdown. Present only when the status is not none, so presence means
     * the device was being throttled at the moment, while absence is not a strong signal
     * because it could be due to a lag in the update or that there is no actual throttling.
     * This is collected because heat measurably slows init, and this is a signal for that.
     */
    const val THERMAL_STATUS: String = "thermal-status"

    /**
     * How far the device's thermal forecast is toward the severe-throttling threshold, as a whole
     * percentage per [android.os.PowerManager.getThermalHeadroom] (API 30+). Note the polarity:
     * 100 means AT or beyond the severe-throttling threshold and low values mean cool. Forecasts
     * beyond severe are capped into the single 100 group - the headroom scale is uncalibrated up
     * there, and [THERMAL_STATUS]'s severe/critical/emergency/shutdown levels are the calibrated
     * signal for that region. Omitted where the device does not support forecasting.
     */
    const val THERMAL_HEADROOM_PCT: String = "thermal-headroom-pct"

    /**
     * Seconds between the app's first install and SDK init. Small values indicate init ran close
     * to when install happened, when there could be more concurrent work vying for CPU and RAM,
     * like dexopt or app-specific tasks.
     */
    const val SECONDS_SINCE_INSTALL: String = "seconds-since-install"

    /**
     * Seconds between the app's last update and SDK init. Small values indicate init ran close
     * to when app update happened, when there could be similar factors to slow down init we see
     * in fresh installs, as well as post-update tasks like DB migrations.
     */
    const val SECONDS_SINCE_UPDATE: String = "seconds-since-update"

    /**
     * Whole percentage of device RAM available at record time. Collected because memory pressure
     * is a distinct slow-init cause on small-RAM devices: when little is available, init runs
     * alongside our own GC and the system's reclaim/kill activity, slowing things down. Recorded
     * as a percentage to estimate load of the device, not trying to precisely count how many
     * bytes are left because that is often not relevant or (even more) misleading.
     */
    const val MEM_AVAILABLE_PCT: String = "mem-available-pct"

    /**
     * Boolean present only when it's true, when the system reports it is in a low-memory state
     * and the OS is actively reclaiming memory. The presence of this indicates memory pressure.
     */
    const val LOW_MEMORY: String = "low-memory"

    /**
     * Size in bytes of the HOST APP's default `SharedPreferences` file, which the SDK's key-value
     * store is backed by, used to explain the `prefs-first-read` duration. The bigger the file,
     * the longer it takes to load and parse its data, as Android needs to load the whole thing
     * before reading even one value from it.
     *
     * This should be obtained by reading looking at the file without opening it and incurring
     * the costs that this measurement is trying to quantify.
     */
    const val PREFS_FILE_BYTES: String = "prefs-file-bytes"
}
