package io.embrace.android.embracesdk.internal.instrumentation.startup

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
     */
    const val INIT_RUN_DELAY_PCT: String = "init-run-delay-pct"

    /**
     * Major page faults taken by the process during the init window - reads that had to go
     * to storage (cold code pages, cold files), including flash contention from concurrent
     * installs.
     */
    const val INIT_MAJ_FAULTS: String = "init-maj-faults"

    /**
     * Kilobytes actually read from storage by the process during the init window. Normal
     * inits read little (config file + cold pages); elevated values mark storage-bound
     * runs - cold caches after reboot/update, slow flash, or IO contention from concurrent
     * installs - and pair with [INIT_MAJ_FAULTS] to confirm an IO-class slow run.
     */
    const val INIT_DISK_READ_KB: String = "init-disk-read-kb"

    /**
     * ART garbage collections (any type, process-wide) during the init window. A
     * deliberately imprecise, directional signal: it says there is some amount of GC
     * and doesn't try to precisely determine how much time it took.
     */
    const val INIT_GC_COUNT: String = "init-gc-count"

    /**
     * Seconds between device boot and SDK init. Small values indicate a recently rebooted
     * device: cold caches everywhere plus post-boot system activity.
     */
    const val SECONDS_SINCE_BOOT: String = "seconds-since-boot"

    /**
     * The device's overall thermal throttling status at record time, as reported by
     * [android.os.PowerManager.getCurrentThermalStatus] (API 29+): none/light/moderate/severe/
     * critical/emergency/shutdown. Collected because heat measurably slows init, and this is a
     * signal for that.
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
     * The kernel's Pressure Stall Information indicating that there is some CPU pressure over the
     * last 10 seconds on the device, as reported in /proc/pressure/cpu, rounded to a whole
     * percentage. Corroborates a high run-delay reading with a device-wide view that its CPUs
     * have been busy. Not always available, so the absence of this should not be seen as
     * evidence, but the presence is.
     */
    const val PSI_CPU_SOME_AVG10: String = "psi-cpu-some-avg10"
}
