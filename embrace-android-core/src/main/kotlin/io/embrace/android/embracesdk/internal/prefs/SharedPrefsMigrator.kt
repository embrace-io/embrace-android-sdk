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
    liveNetworkCaptureRules: () -> Map<String, Int>,
): SharedPrefsMigrator = SharedPrefsMigrator(
    fileSystem = fileSystem,
    installStateDir = installStateDir,
    prefsFile = defaultPrefsFile(context).toOkioPath(),
    loadPrefs = { PreferenceManager.getDefaultSharedPreferences(context) },
    serializer = serializer,
    errorHandler = errorHandler,
    appFramework = appFramework,
    liveNetworkCaptureRules = liveNetworkCaptureRules,
)

/**
 * Migrates the SDK's live values from the app's default [SharedPreferences] into the files in [installStateDir], which
 * must exist, and deletes the SDK's keys from the prefs. `ordinals.bin` is written last and marks the migration as
 * complete.
 */
class SharedPrefsMigrator(
    private val fileSystem: FileSystem,
    private val installStateDir: Path,
    private val prefsFile: Path,
    private val loadPrefs: () -> SharedPreferences,
    private val serializer: PlatformSerializer,
    private val errorHandler: ErrorHandler,
    private val appFramework: AppFramework,
    private val liveNetworkCaptureRules: () -> Map<String, Int>,
) {

    private val ordinalsPath = installStateDir / SdkStorageLayout.ORDINALS_FILE_NAME

    /**
     * Returns true if this call wrote `ordinals.bin`, whether it migrated values or found nothing to migrate. Returns
     * false if a previous launch already migrated, or if this attempt failed: `ordinals.bin` is then absent and the
     * next launch retries.
     */
    fun migrate(): Boolean = errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, false) {
        !fileSystem.exists(ordinalsPath) && EmbTrace.trace("prefs-migration") {
            if (prefsFileExists()) {
                migrateFrom(loadPrefs())
            } else {
                MmapOrdinalStore.create(fileSystem, ordinalsPath, emptyMap())
            }
            true
        }
    }

    /**
     * Android renames SharedPreferences to `.bak` while rewriting it.
     */
    private fun prefsFileExists(): Boolean =
        fileSystem.exists(prefsFile) || fileSystem.exists("$prefsFile.bak".toPath())

    private fun migrateFrom(prefs: SharedPreferences) {
        val keys = prefs.all.keys
        val snapshot = LegacyPrefsReader(prefs, serializer, errorHandler, appFramework).read()
        if (snapshot == null) {
            MmapOrdinalStore.create(fileSystem, ordinalsPath, emptyMap())
        } else {
            writeFiles(snapshot)
        }
        errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail) { removeKeys(prefs, keys) }
    }

    private fun writeFiles(snapshot: LegacyPrefsSnapshot) {
        val used = networkCaptureUsed(snapshot.networkCaptureRemaining)
        FilePersistedSdkStateStore.create(
            fileSystem,
            installStateDir / SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME,
            snapshot.persistedSdkState.copy(appVersionStartupScope = snapshot.appVersionStartupScope),
        )
        val networkCapturePath = installStateDir / SdkStorageLayout.NETWORK_CAPTURE_FILE_NAME
        if (used.isEmpty()) {
            fileSystem.delete(networkCapturePath, mustExist = false)
        } else {
            FileNetworkCaptureRuleStore.create(fileSystem, networkCapturePath, used)
        }
        val aeiPath = installStateDir / SdkStorageLayout.AEI_DELIVERED_FILE_NAME
        if (snapshot.deliveredAeiIds.isEmpty()) {
            fileSystem.delete(aeiPath, mustExist = false)
        } else {
            FileDeliveredAeiStore.create(fileSystem, aeiPath, snapshot.deliveredAeiIds)
        }
        MmapOrdinalStore.create(fileSystem, ordinalsPath, snapshot.ordinals)
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
