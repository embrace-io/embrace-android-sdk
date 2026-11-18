@file:Suppress("DEPRECATION", "SharedPreferencesUsage")

package io.embrace.android.embracesdk.internal.prefs

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.storage.ATOMIC_WRITE_TMP_SUFFIX
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.file.FileDeliveredAeiStore
import io.embrace.android.embracesdk.internal.store.file.FileNetworkCaptureRuleStore
import io.embrace.android.embracesdk.internal.store.file.FilePersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.file.MmapOrdinalStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath

fun createSharedPrefsMigrator(
    context: Context,
    fileSystem: FileSystem,
    installStateDir: Path,
    serializer: PlatformSerializer,
    errorHandler: ErrorHandler,
    appFramework: AppFramework,
    gate: PrefsMigrationGate,
    liveNetworkCaptureRules: () -> Map<String, Int>,
    onDecoded: () -> Unit,
): SharedPrefsMigrator = SharedPrefsMigrator(
    fileSystem = fileSystem,
    installStateDir = installStateDir,
    prefsFile = defaultPrefsFile(context).toOkioPath(),
    loadPrefs = { PreferenceManager.getDefaultSharedPreferences(context) },
    serializer = serializer,
    errorHandler = errorHandler,
    appFramework = appFramework,
    gate = gate,
    liveNetworkCaptureRules = liveNetworkCaptureRules,
    onDecoded = onDecoded,
)

/**
 * Unless `ordinals.bin` exists, deletes the state files in [installStateDir] and writes empty ordinals, so the install
 * starts fresh and no later launch migrates the prefs.
 */
fun startFreshInstallState(fileSystem: FileSystem, installStateDir: Path) {
    val ordinalsPath = installStateDir / SdkStorageLayout.ORDINALS_FILE_NAME
    if (!fileSystem.exists(ordinalsPath)) {
        STATE_FILE_NAMES.forEach { fileSystem.delete(installStateDir / it, mustExist = false) }
        MmapOrdinalStore.create(fileSystem, ordinalsPath, emptyMap())
    }
}

private val STATE_FILE_NAMES = arrayOf(
    SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME,
    SdkStorageLayout.NETWORK_CAPTURE_FILE_NAME,
    SdkStorageLayout.AEI_DELIVERED_FILE_NAME,
)

/**
 * Migrates the SDK's live values from the app's default [SharedPreferences] into the files in [installStateDir], which
 * must exist, and deletes the SDK's keys from the prefs. `ordinals.bin` is written last and marks the migration as
 * complete. The migration is attempted once and never retried. Failing to write the SDK state or ordinals leaves the
 * install state fresh on disk, with no legacy data, and failing to write the network capture or AEI file drops just
 * that file. [gate] is told what was decoded before any state file is written, so the stores can start from it, and
 * nothing but the staged ordinals is written once the stores have given up on the migration.
 *
 * The ordinals are first written unsynced to a staged file, which [gate] hands to the ordinal store already mapped, and
 * [onDecoded] then runs.
 */
class SharedPrefsMigrator(
    private val fileSystem: FileSystem,
    private val installStateDir: Path,
    private val prefsFile: Path,
    private val loadPrefs: () -> SharedPreferences,
    private val serializer: PlatformSerializer,
    private val errorHandler: ErrorHandler,
    private val appFramework: AppFramework,
    private val gate: PrefsMigrationGate,
    private val liveNetworkCaptureRules: () -> Map<String, Int>,
    private val onDecoded: () -> Unit = {},
) {

    private val ordinalsPath = installStateDir / SdkStorageLayout.ORDINALS_FILE_NAME
    private val stagedOrdinalsPath = installStateDir / STAGED_ORDINALS_FILE_NAME

    /**
     * Returns true if this call ran the migration, or false if a previous launch already did or the stores gave up on
     * it. Loaded prefs keys are removed whatever the outcome.
     */
    fun migrate(): Boolean = errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, false) {
        !fileSystem.exists(ordinalsPath) && EmbTrace.trace("prefs-migration") {
            val hasPrefs = prefsFileExists()
            val loaded = if (hasPrefs) load() else null
            val snapshot = loaded?.snapshot
            val staged = stageOrdinals(snapshot?.ordinals.orEmpty())
            val migrated = snapshot?.takeIf { staged != null }?.let(::toInstallState)
            val committed = errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, false) {
                gate.commit(migrated, staged) {
                    if (staged != null) {
                        onDecoded()
                    }
                    writeInstallState(migrated, staged != null, hasPrefs)
                }
            }
            errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail) {
                fileSystem.delete(stagedOrdinalsPath, mustExist = false)
            }
            if (loaded != null) {
                EmbTrace.trace("prefs-remove-keys") {
                    errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail) { removeKeys(loaded.prefs, loaded.all.keys) }
                }
            }
            committed
        }
    }

    private fun toInstallState(snapshot: LegacyPrefsSnapshot): MigratedInstallState = MigratedInstallState(
        persistedSdkState = snapshot.persistedSdkState.copy(appVersionStartupScope = snapshot.appVersionStartupScope),
        networkCaptureUsed = networkCaptureUsed(snapshot.networkCaptureRemaining),
        deliveredAeiIds = snapshot.deliveredAeiIds,
    )

    private fun stageOrdinals(ordinals: Map<Ordinal, Int>): MmapOrdinalStore? =
        EmbTrace.trace("prefs-stage-ordinals") {
            errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, null) {
                MmapOrdinalStore.stage(fileSystem, stagedOrdinalsPath, ordinals, errorHandler)
            }
        }

    /**
     * Returns true if the ordinals and, unless [migrated] is null, its SDK state were written, or false if the install
     * was started fresh instead. The staged ordinals hold [migrated]'s, so they're only committed if it was written or
     * is null. If there are prefs but no [migrated], the state files a launch that died part way through may have left
     * are deleted first.
     */
    private fun writeInstallState(migrated: MigratedInstallState?, staged: Boolean, hasPrefs: Boolean): Boolean {
        val stateWritten = migrated != null && writeStateFiles(migrated)
        val written = EmbTrace.trace("prefs-write-ordinals") {
            errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, false) {
                if (migrated == null && hasPrefs) {
                    STATE_FILE_NAMES.forEach { fileSystem.delete(installStateDir / it, mustExist = false) }
                }
                if (staged && (stateWritten || migrated == null)) {
                    MmapOrdinalStore.commitStaged(fileSystem, stagedOrdinalsPath, ordinalsPath)
                } else {
                    MmapOrdinalStore.create(fileSystem, ordinalsPath, emptyMap())
                }
                true
            }
        }
        if (!written) {
            errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail) { startFreshInstallState(fileSystem, installStateDir) }
        }
        return written && (stateWritten || migrated == null)
    }

    /**
     * Android renames SharedPreferences to `.bak` while rewriting it.
     */
    private fun prefsFileExists(): Boolean =
        fileSystem.exists(prefsFile) || fileSystem.exists("$prefsFile.bak".toPath())

    private fun load(): LoadedPrefs? = errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, null) {
        val prefs = EmbTrace.trace("prefs-load") { loadPrefs() }
        val all = EmbTrace.trace("prefs-load-all") { prefs.all }
        val snapshot = EmbTrace.trace("prefs-decode") { LegacyPrefsReader(all, serializer, errorHandler, appFramework).read() }
        LoadedPrefs(prefs, all, snapshot)
    }

    private fun writeStateFiles(migrated: MigratedInstallState): Boolean {
        val written = EmbTrace.trace("prefs-write-state") {
            errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, false) {
                FilePersistedSdkStateStore.create(
                    fileSystem,
                    installStateDir / SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME,
                    migrated.persistedSdkState,
                )
                true
            }
        }
        if (!written) {
            errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail) {
                STATE_FILE_NAMES.forEach { fileSystem.delete(installStateDir / it, mustExist = false) }
            }
            return false
        }
        EmbTrace.trace("prefs-write-network-capture") {
            val used = migrated.networkCaptureUsed
            writeDisposableFile(SdkStorageLayout.NETWORK_CAPTURE_FILE_NAME, used.isEmpty()) {
                FileNetworkCaptureRuleStore.create(fileSystem, it, used)
            }
        }
        EmbTrace.trace("prefs-write-aei") {
            val ids = migrated.deliveredAeiIds
            writeDisposableFile(SdkStorageLayout.AEI_DELIVERED_FILE_NAME, ids.isEmpty()) {
                FileDeliveredAeiStore.create(fileSystem, it, ids)
            }
        }
        return true
    }

    private class LoadedPrefs(
        val prefs: SharedPreferences,
        val all: Map<String, *>,
        val snapshot: LegacyPrefsSnapshot?,
    )

    /**
     * Losing these files only resets AEI dedupe or capture counts, so carry on with the migration
     * if they do fail
     */
    private fun writeDisposableFile(name: String, empty: Boolean, write: (Path) -> Unit) {
        val path = installStateDir / name
        val written = errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, false) {
            if (empty) fileSystem.delete(path, mustExist = false) else write(path)
            true
        }
        if (!written) {
            errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail) { fileSystem.delete(path, mustExist = false) }
        }
    }

    private fun networkCaptureUsed(remaining: Map<String, Int>): Map<String, Int> {
        val live = errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, emptyMap()) { liveNetworkCaptureRules() }
        return remaining.mapNotNull { (ruleId, left) ->
            live[ruleId]?.let { maxCount -> ruleId to (maxCount - left).coerceIn(0, maxCount.coerceAtLeast(0)) }
        }.filter { (_, used) -> used > 0 }.toMap()
    }

    private fun removeKeys(prefs: SharedPreferences, keys: Set<String>) {
        val editor = prefs.edit()
        keys.filter(::isSdkKey).forEach(editor::remove)
        editor.apply()
    }

    private fun isSdkKey(key: String): Boolean = key.startsWith(SDK_KEY_PREFIX) || key == KEY_USER_SESSION

    private companion object {
        const val SDK_KEY_PREFIX = "io.embrace."
        const val KEY_USER_SESSION = "embrace.user_session"
        const val STAGED_ORDINALS_FILE_NAME = "${SdkStorageLayout.ORDINALS_FILE_NAME}.staged$ATOMIC_WRITE_TMP_SUFFIX"
    }
}
