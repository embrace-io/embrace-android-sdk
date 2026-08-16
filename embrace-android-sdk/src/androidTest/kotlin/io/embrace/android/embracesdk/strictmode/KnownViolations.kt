package io.embrace.android.embracesdk.strictmode

import android.os.Build
import android.os.strictmode.DiskReadViolation
import android.os.strictmode.Violation
import android.util.Log
import androidx.annotation.RequiresApi
import org.junit.Assert.assertTrue
import kotlin.reflect.KClass

private const val LOG_TAG = "EmbStrictMode"

internal data class KnownViolation(
    val signature: String,
    val violation: KClass<out Violation>,
    val reason: String,
)

/**
 * Violations committed by SDK startup today.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal val KNOWN_VIOLATIONS: List<KnownViolation> = listOf(
    KnownViolation(
        signature = "internal.injection.ModuleInitBootstrapper.init",
        violation = DiskReadViolation::class,
        reason = "context.filesDir is passed to PersistedConfig as a ctor arg, and ContextImpl.getFilesDir " +
            "stats the directory on every call",
    ),
    KnownViolation(
        signature = "internal.instrumentation.startup.SdkInitResourceUsageTrackerKt.readProcFile",
        violation = DiskReadViolation::class,
        reason = "SdkInitResourceUsageTracker samples /proc/self/task/<tid>/schedstat, /proc/self/stat and " +
            "/proc/self/io at both edges of the init window. These cannot be moved off the main thread: " +
            "schedstat is per-TID and only means anything when read from the thread being measured, and the " +
            "other two have to be sampled at the window's edges to give a delta across it. procfs is " +
            "memory-backed, so the cost is microseconds rather than real disk I/O — StrictMode instruments " +
            "the syscall and cannot tell the two apart",
    ),
    KnownViolation(
        signature = "internal.config.store.RemoteConfigStoreImpl.loadFromCache",
        violation = DiskReadViolation::class,
        reason = "PersistedConfig's ctor loads the persisted response from the config store",
    ),
    KnownViolation(
        signature = "internal.config.store.RemoteConfigStoreImpl.loadFromJson",
        violation = DiskReadViolation::class,
        reason = "Fallback path for the read above",
    ),
    KnownViolation(
        signature = "internal.prefs.SharedPrefsStore.getString",
        violation = DiskReadViolation::class,
        reason = "A binary cache miss makes DeviceIdProvider read the device ID out of SharedPreferences. " +
            "Only fires when it beats ModuleInitBootstrapper.prewarmSharedPreferences and has to block in " +
            "awaitLoadedLocked",
    ),
    KnownViolation(
        signature = "internal.injection.SdkInitActionsKt.loadInstrumentationProviders",
        violation = DiskReadViolation::class,
        reason = "ServiceLoader reads META-INF/services out of the APK. R8 rewrites this away in release builds",
    ),
)

/**
 * Fails if SDK startup committed a violation that isn't in [KNOWN_VIOLATIONS]. Full stacks for
 * everything recorded are logged under [LOG_TAG].
 */
@RequiresApi(Build.VERSION_CODES.P)
internal fun assertNoUnexpectedViolations(recorded: List<RecordedViolation>) {
    recorded.forEach { Log.i(LOG_TAG, it.describeWithStack()) }

    val unexpected = recorded
        .filter { it.signature != null }
        .filterNot { violation -> KNOWN_VIOLATIONS.any { violation.matches(it) } }
        .distinctBy { it.violationName to it.signature }

    assertTrue(
        unexpected.joinToString(
            prefix = "Unexpected StrictMode violations (stacks in logcat under $LOG_TAG):\n",
            separator = "\n",
        ) { "  ${it.describe()}" },
        unexpected.isEmpty(),
    )
}

@RequiresApi(Build.VERSION_CODES.P)
private fun RecordedViolation.matches(known: KnownViolation): Boolean =
    signature == known.signature && known.violation.java.isInstance(violation)
