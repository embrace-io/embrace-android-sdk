package io.embrace.android.embracesdk.internal.prefs

import android.content.SharedPreferences
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.Ordinal

/**
 * Reads every live value the SDK stored in [prefs] into [LegacyPrefsSnapshot] that contains the same types
 * as existing data stores. [all] is the result of [SharedPreferences.getAll], already read by the caller.
 *
 * Returns null if no device ID or install timestamp was stored, as the SDK writes both on first run, so their
 * absence means there is no useful data to migrate.
 */
@Suppress("SharedPreferencesUsage")
class LegacyPrefsReader(
    private val prefs: SharedPreferences,
    private val all: Map<String, *>,
    private val serializer: PlatformSerializer,
    private val errorHandler: ErrorHandler,
    private val appFramework: AppFramework,
) {

    fun read(): LegacyPrefsSnapshot? {
        val store = SharedPrefsStore(prefs, serializer, errorHandler)
        val deviceId = store.getString(KEY_DEVICE_ID) ?: return null
        val installTimestampMs = store.getLong(KEY_INSTALL_TIMESTAMP) ?: return null

        val state = PrefsPersistedSdkStateStore(
            store = store,
            clock = { installTimestampMs },
            uuidSource = { deviceId },
            appFramework = appFramework,
        ).snapshot()
        val ordinals = LIVE_ORDINALS.mapNotNull { ordinal ->
            store.getInt(ordinal.key)?.let { ordinal to it }
        }.toMap()
        return LegacyPrefsSnapshot(
            persistedSdkState = state,
            ordinals = ordinals,
            appVersionStartupScope = Ordinal.APP_VERSION_STARTUP.scopeKey?.let(store::getString),
            networkCaptureRemaining = readNetworkCaptureRemaining(),
            deliveredAeiIds = store.getStringSet(KEY_DELIVERED_AEI_IDS) ?: emptySet(),
        )
    }

    private fun readNetworkCaptureRemaining(): Map<String, Int> =
        all.mapNotNull { (key, value) ->
            val ruleId = key.removePrefix(KEY_NETWORK_CAPTURE_RULE_PREFIX)
            if (ruleId == key || value !is Int) {
                null
            } else {
                ruleId to value
            }
        }.toMap()

    private companion object {

        /**
         * [Ordinal.USER_SESSION] shares [Ordinal.SESSION]'s value, so its own stale key is not read.
         * [Ordinal.BACKGROUND_ACTIVITY] is never read.
         */
        val LIVE_ORDINALS = listOf(
            Ordinal.SESSION,
            Ordinal.SESSION_PART,
            Ordinal.CRASH,
            Ordinal.NATIVE_CRASH,
            Ordinal.AEI_CRASH,
            Ordinal.APP_VERSION_STARTUP,
        )

        const val KEY_DEVICE_ID = "io.embrace.deviceid"
        const val KEY_INSTALL_TIMESTAMP = "io.embrace.installtimestamp"
        const val KEY_DELIVERED_AEI_IDS = "io.embrace.aeiHashCode"
        const val KEY_NETWORK_CAPTURE_RULE_PREFIX = "io.embrace.networkcapturerule"
    }
}
