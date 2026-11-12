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
 * complete. The migration is attempted once: any failure starts the install fresh, with no legacy data, and is never
 * retried. Writes go through [gate], so nothing is written once the stores have given up on the migration.
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
) {

    private val ordinalsPath = installStateDir / SdkStorageLayout.ORDINALS_FILE_NAME

    /**
     * Returns true if this call ran the migration, or false if a previous launch already did or the stores gave up on
     * it. Loaded prefs keys are removed whatever the outcome.
     */
    fun migrate(): Boolean = errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, false) {
        !fileSystem.exists(ordinalsPath) && EmbTrace.trace("prefs-migration") {
            val loaded = if (prefsFileExists()) load() else null
            val committed = gate.commit { writeInstallState(loaded?.snapshot) }
            if (loaded != null) {
                errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail) { removeKeys(loaded.prefs, loaded.all.keys) }
            }
            committed
        }
    }

    private fun writeInstallState(snapshot: LegacyPrefsSnapshot?) {
        val ordinals = snapshot?.let(::writeStateFiles) ?: emptyMap()
        val written = errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, false) {
            MmapOrdinalStore.create(fileSystem, ordinalsPath, ordinals)
            true
        }
        if (!written) {
            errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail) { startFreshInstallState(fileSystem, installStateDir) }
        }
    }

    /**
     * Android renames SharedPreferences to `.bak` while rewriting it.
     */
    private fun prefsFileExists(): Boolean =
        fileSystem.exists(prefsFile) || fileSystem.exists("$prefsFile.bak".toPath())

    private fun load(): LoadedPrefs? = errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, null) {
        val prefs = loadPrefs()
        val all = prefs.all
        LoadedPrefs(prefs, all, LegacyPrefsReader(all, serializer, errorHandler, appFramework).read())
    }

    private fun writeStateFiles(snapshot: LegacyPrefsSnapshot): Map<Ordinal, Int> {
        val written = errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, false) {
            FilePersistedSdkStateStore.create(
                fileSystem,
                installStateDir / SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME,
                snapshot.persistedSdkState.copy(appVersionStartupScope = snapshot.appVersionStartupScope),
            )
            true
        }
        if (!written) {
            errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail) {
                STATE_FILE_NAMES.forEach { fileSystem.delete(installStateDir / it, mustExist = false) }
            }
            return emptyMap()
        }
        val used = networkCaptureUsed(snapshot.networkCaptureRemaining)
        writeDisposableFile(SdkStorageLayout.NETWORK_CAPTURE_FILE_NAME, used.isEmpty()) {
            FileNetworkCaptureRuleStore.create(fileSystem, it, used)
        }
        writeDisposableFile(SdkStorageLayout.AEI_DELIVERED_FILE_NAME, snapshot.deliveredAeiIds.isEmpty()) {
            FileDeliveredAeiStore.create(fileSystem, it, snapshot.deliveredAeiIds)
        }
        return snapshot.ordinals
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
    }
}
