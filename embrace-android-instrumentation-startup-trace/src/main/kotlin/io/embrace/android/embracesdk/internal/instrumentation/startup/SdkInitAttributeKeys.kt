package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.os.Build.VERSION_CODES
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.APP_IMAGE_AT_INIT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.ART_COMPILER_FILTER
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.ART_COMPILER_FILTER_NOT_FOUND
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_CPU_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_GC_COUNT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_GC_COUNT_MIN_API
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.INIT_RUN_DELAY_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.MEM_AVAILABLE_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.NUMERIC_ERROR
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.STRING_ERROR
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_HEADROOM_PCT
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_HEADROOM_PCT_MIN_API
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_HEADROOM_PCT_UNAVAILABLE
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_STATUS
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitAttributeKeys.THERMAL_STATUS_MIN_API

/**
 * Keys of the attributes recorded on the SDK init spans to contextualize and explain
 * performance anomalies. [SdkInitResourceUsageTracker] produces the ones measured as deltas across
 * the init window, while [sdkInitEnvironmentAttributes] produces the ones describing the ambient
 * conditions the init ran under, read at span record time.
 *
 * These are candidates to become proper semantic conventions once their shapes settle. Until
 * then, they are deliberately kept as plain constants so nothing is locked in.
 *
 * An attribute should always be present unless one of the following conditions hold:
 *
 * - The device itself, based on knowable dimensions like OEM, OS version, and APK architecture,
 *   does not support the capturing of the underlying data to record the attribute.
 * - The attribute captures a rare/anomolous state (explicitly called out per attribute)
 *
 * Otherwise, you should always expect an attribute with this name, even if it carries a value
 * that denotes there was an error during its capture (i.e. [NUMERIC_ERROR] or [STRING_ERROR]
 * depending on type). If you see such a value, it means the data is expected to be there, but
 * there was an error in capturing it. These types of errors should be investigated because
 * they are unexpected.
 *
 * An attribute may also define a value of its own for the case where we looked and there was
 * nothing to be had, which is a reading rather than an error and is documented on that attribute -
 * see [THERMAL_HEADROOM_PCT_UNAVAILABLE] and [ART_COMPILER_FILTER_NOT_FOUND]. Those exist because
 * what they record varies from one launch to the next on the same device, so omitting them would
 * lose something no per-device dimension could reconstruct afterwards.
 *
 * Note: 0 is a validate value for attributes. Seeing a zero implies the instrumentation captured
 * the data correctly.
 */
object SdkInitAttributeKeys {

    /**
     * Value of a numeric attribute that the instrumentation could not get when it should have
     * worked. It should only be used on a device where you can conceivably get this attribute.
     */
    const val NUMERIC_ERROR: String = "-1"

    /**
     * Same as [NUMERIC_ERROR] but for attributes with a string value.
     */
    const val STRING_ERROR: String = "ERROR"

    /**
     * Percentage of the init window the init thread spent executing on a CPU, rounded to a
     * whole number. Together with [INIT_RUN_DELAY_PCT], this can split every slow run into
     * ran-slow vs was-blocked/starved.
     *
     * Read from [android.os.SystemClock.currentThreadTimeMillis], which every supported OS version
     * has, so a failure to read it is [NUMERIC_ERROR]. Absent only when the init finished inside
     * the millisecond the clock counts in, an unrealistic scenario.
     */
    const val INIT_CPU_PCT: String = "init-cpu-pct"

    /**
     * Percentage of the init window the init thread spent runnable but waiting for a CPU,
     * as a whole number. This is the direct measure of CPU contention: other work (other
     * apps, system services, our own background threads, concurrent GC, etc.) held the cores
     * while init waited. A high value means "the device was busy", which could mean the main
     * thread was delayed by activities external to the app, but also blocking activities
     * caused by the app or SDK. A high value doesn't automatically mean "not our fault".
     *
     * Read from the thread's procfs `schedstat`, which no Android API level governs, but an
     * SELinux policy or a vendor kernel can withhold. Contents we can read but not parse are
     * recorded as [NUMERIC_ERROR]. Like [INIT_CPU_PCT], if SDK init duration is 0, this will be
     * absent.
     */
    const val INIT_RUN_DELAY_PCT: String = "init-run-delay-pct"

    /**
     * Kilobytes actually read from storage by the process during the init window. Values above the
     * norm for an app can indicate more data is being read than normal, which might be related to
     * tasks that drag down performance like extra IO contention or data parsing. 0 is a valid value.
     *
     * Read from procfs `io` under the same vendor and policy caveats as [INIT_RUN_DELAY_PCT], so
     * unreadable leaves this absent while unparseable is [NUMERIC_ERROR].
     */
    const val INIT_DISK_READ_KB: String = "init-disk-read-kb"

    /**
     * ART garbage collections (any type, process-wide) during the init window. A deliberately
     * imprecise, directional signal: it says there is some amount of GC, but we don't try to
     * determine exactly how much time it took. 0 is a real and common value, when there are no
     * GCs during SDK init.
     *
     * Absent below [INIT_GC_COUNT_MIN_API], and when the stat is not available to be read at
     * all, but a stat that comes back non-numeric is unexpected and considered an error.
     */
    const val INIT_GC_COUNT: String = "init-gc-count"

    /**
     * Minimum API level at which [INIT_GC_COUNT] can be read, being the version that introduced
     * [android.os.Debug.getRuntimeStat]. Below this the attribute is absent by design.
     */
    const val INIT_GC_COUNT_MIN_API: Int = VERSION_CODES.M

    /**
     * Seconds of non deep-sleep time between device boot and SDK init
     * (i.e. [android.os.SystemClock.uptimeMillis]). Small values indicate a device that has
     * not yet had the running time to finish its post-boot work. 0 is a real reading for an
     * app launched during boot.
     */
    const val SECONDS_SINCE_BOOT: String = "seconds-since-boot"

    /**
     * The device's overall thermal throttling status at record time, as reported by
     * [android.os.PowerManager.getCurrentThermalStatus]: one of light/moderate/severe/critical/
     * emergency/shutdown.
     *
     * Absent when the OS is below [THERMAL_STATUS_MIN_API] and if the status is `none`. This means
     * presence is a signal the device was being throttled at the moment, while absence is not
     * a signal because it could be due to a lag in the update, or there is no actual throttling.
     * This is collected because heat measurably slows init, and this is a signal for that.
     */
    const val THERMAL_STATUS: String = "thermal-status"

    /**
     * Minimum API level at which [THERMAL_STATUS] can be read, being the version that introduced
     * [android.os.PowerManager.getCurrentThermalStatus]. Below this the attribute is absent by
     * design.
     */
    const val THERMAL_STATUS_MIN_API: Int = VERSION_CODES.Q

    /**
     * How far the device's thermal forecast is toward the severe-throttling threshold, as a whole
     * percentage per [android.os.PowerManager.getThermalHeadroom]. Note the polarity: 100 means
     * AT or beyond the severe-throttling threshold and low values mean cool. Forecasts beyond
     * severe are capped into the single 100 group - the headroom scale is uncalibrated up there,
     * and [THERMAL_STATUS]'s severe/critical/emergency/shutdown levels are the calibrated signal
     * for that region.
     *
     * Absent below [THERMAL_HEADROOM_PCT_MIN_API], and [THERMAL_HEADROOM_PCT_UNAVAILABLE] when the
     * platform answers with a non-finite number. Do not read that as "this device does not
     * forecast": the platform uses it both for a device that never forecasts and for one with no
     * forecast to give at this moment. A device that never forecasts can be systematically filtered
     * out by using the relevant dimension if that needs to be determined.
     */
    const val THERMAL_HEADROOM_PCT: String = "thermal-headroom-pct"

    /**
     * Value of [THERMAL_HEADROOM_PCT] when the platform was asked for a forecast and answered that
     * it has none. Distinct from [NUMERIC_ERROR] because nothing went wrong but the platform did
     * not return a value we can use. It's like the OS saying "nah, come back later".
     */
    const val THERMAL_HEADROOM_PCT_UNAVAILABLE: String = "-2"

    /**
     * Minimum API level at which [THERMAL_HEADROOM_PCT] can be read, being the version that
     * introduced [android.os.PowerManager.getThermalHeadroom]. Below this the attribute is absent
     * by design.
     */
    const val THERMAL_HEADROOM_PCT_MIN_API: Int = VERSION_CODES.R

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
     *
     * Presence-only where [APP_IMAGE_AT_INIT] records both true and false, because the polarity differs: being under
     * memory pressure is the notable state here, whereas for the app image it is the absence of one that explains a
     * slow init. When the read behind this fails, [MEM_AVAILABLE_PCT] carries the error and this stays absent.
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
     *
     * A first launch has no such file yet, which is recorded as the zero bytes it will load
     * rather than as no data - the two are the same thing to the duration this explains.
     */
    const val PREFS_FILE_BYTES: String = "prefs-file-bytes"

    /**
     * The compiler filter used by ART for compiling the app's primary DEX. It gives clues as to how
     * much optimization could have been done by ART to reduce DEX compilation time.
     *
     * If the expected location where the odex file was successfully searched but the filter value was
     * not found, the value [ART_COMPILER_FILTER_NOT_FOUND] is used to denote that.
     *
     * If the location was found but the search filed, the value [STRING_ERROR] is used to denote that.
     *
     * This attribute is absent only when the location is not found, which means there was nowhere to
     * look for it.
     *
     * More details can be found here: https://source.android.com/docs/core/runtime/configure
     */
    const val ART_COMPILER_FILTER: String = "art-compiler-filter"

    /**
     * Value of [ART_COMPILER_FILTER] when the conventional location was searched but no filter was found.
     */
    const val ART_COMPILER_FILTER_NOT_FOUND: String = "NOT_FOUND"

    /**
     * Whether the app image (base.art) exists when the SDK initializes. True indicates that during SDK init, classes
     * might be preloaded from the profile rather than always loaded from the DEX. False is the ordinary state for an
     * app compiled without a profile, meaning classes must come from the DEX instead.
     *
     * This attribute is absent only when the expected directory for the art file is not found, so there's nowhere to
     * look for it.
     */
    const val APP_IMAGE_AT_INIT: String = "app-image-at-init"
}
