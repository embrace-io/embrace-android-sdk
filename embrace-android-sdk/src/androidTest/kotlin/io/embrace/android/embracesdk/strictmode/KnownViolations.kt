package io.embrace.android.embracesdk.strictmode

import android.os.Build
import android.os.strictmode.DiskReadViolation
import android.os.strictmode.DiskWriteViolation
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
        signature = "internal.injection.CoreModuleImpl.<init>",
        violation = DiskReadViolation::class,
        reason = "CoreModuleImpl.store eagerly opens the default SharedPreferences. Only fires when it beats " +
            "ModuleInitBootstrapper.prewarmSharedPreferences, which it usually doesn't",
    ),
    KnownViolation(
        signature = "internal.injection.CoreModuleImpl.<init>",
        violation = DiskWriteViolation::class,
        reason = "First launch has to mkdir shared_prefs",
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
    KnownViolation(
        signature = "internal.storage.EmbraceStorageService.getOrCreateEmbraceFilesDir",
        violation = DiskWriteViolation::class,
        reason = "Resolving the filesDirectory lazy calls File(filesDir, \"embrace\").mkdirs()",
    ),
    KnownViolation(
        signature = "internal.storage.EmbraceStorageService.getOrCreateEmbraceFilesDir",
        violation = DiskReadViolation::class,
        reason = "As above - the mkdirs() is followed by an exists() check",
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
