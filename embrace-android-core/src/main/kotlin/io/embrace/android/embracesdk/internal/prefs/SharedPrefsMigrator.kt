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
import io.embrace.android.embracesdk.internal.store.file.FilePersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.file.MmapOrdinalStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath

fun createSharedPrefsMigrator(
    context: Context,
    fileSystem: FileSystem,
    installStateDir: Path,
    serializer: PlatformSerializer,
    errorHandler: ErrorHandler,
    appFramework: AppFramework,
): SharedPrefsMigrator = SharedPrefsMigrator(
    fileSystem = fileSystem,
    installStateDir = installStateDir,
    prefsFile = defaultPrefsFile(context).toOkioPath(),
    loadPrefs = { PreferenceManager.getDefaultSharedPreferences(context) },
    serializer = serializer,
    errorHandler = errorHandler,
    appFramework = appFramework,
)

/**
 * Moves the SDK's values out of the app's default [SharedPreferences] into the files in [installStateDir], which must
 * exist. `ordinals.bin` is written last and marks the migration as complete.
 */
class SharedPrefsMigrator(
    private val fileSystem: FileSystem,
    private val installStateDir: Path,
    private val prefsFile: Path,
    private val loadPrefs: () -> SharedPreferences,
    private val serializer: PlatformSerializer,
    private val errorHandler: ErrorHandler,
    private val appFramework: AppFramework,
) {

    private val ordinalsPath = installStateDir / SdkStorageLayout.ORDINALS_FILE_NAME

    /**
     * Returns true if this call wrote `ordinals.bin`, including on a fresh install with nothing to migrate. Returns
     * false if a previous launch already migrated, or if this migration failed.
     */
    fun migrate(): Boolean {
        return !fileSystem.exists(ordinalsPath) && EmbTrace.trace("prefs-migration") {
            errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, false) {
                val snapshot = if (fileSystem.exists(prefsFile)) {
                    LegacyPrefsReader(loadPrefs(), serializer, errorHandler, appFramework).read()
                } else {
                    null
                }
                if (snapshot != null) {
                    writeFiles(snapshot)
                } else {
                    MmapOrdinalStore.create(fileSystem, ordinalsPath, emptyMap())
                }
                true
            }
        }
    }

    private fun writeFiles(snapshot: LegacyPrefsSnapshot) {
        FilePersistedSdkStateStore.create(
            fileSystem,
            installStateDir / SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME,
            snapshot.persistedSdkState.copy(appVersionStartupScope = snapshot.appVersionStartupScope),
        )
        MmapOrdinalStore.create(fileSystem, ordinalsPath, snapshot.ordinals)
    }
}
