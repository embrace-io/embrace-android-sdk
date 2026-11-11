@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.BACKGROUND_ONLY_MARKER
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Reads every live value the SDK stored in the app's default `SharedPreferences` into [LegacyPrefsSnapshot] that
 * contains the same types as existing data stores. [all] is the result of `SharedPreferences.getAll`. The prefs are
 * shared with the host app, so a key can hold a value of another type, which reads as absent.
 *
 * Returns null if no device ID or install timestamp was stored, as the SDK writes both on first run, so their
 * absence means there is no useful data to migrate.
 */
class LegacyPrefsReader(
    private val all: Map<String, *>,
    private val serializer: PlatformSerializer,
    private val errorHandler: ErrorHandler,
    private val appFramework: AppFramework,
) {

    fun read(): LegacyPrefsSnapshot? {
        val deviceId = string(KEY_DEVICE_ID) ?: return null
        val installTimestampMs = long(KEY_INSTALL_TIMESTAMP) ?: return null
        val ordinals = LIVE_ORDINALS.mapNotNull { ordinal ->
            int(ordinal.key)?.let { ordinal to it }
        }.toMap()
        return LegacyPrefsSnapshot(
            persistedSdkState = readState(deviceId, installTimestampMs),
            ordinals = ordinals,
            appVersionStartupScope = Ordinal.APP_VERSION_STARTUP.scopeKey?.let(::string),
            networkCaptureRemaining = readNetworkCaptureRemaining(),
            deliveredAeiIds = stringSet(KEY_DELIVERED_AEI_IDS) ?: emptySet(),
        )
    }

    private fun readState(deviceId: String, installTimestampMs: Long): PersistedSdkState {
        val hostedSdkVersionKey = when (appFramework) {
            AppFramework.REACT_NATIVE -> KEY_REACT_NATIVE_SDK_VERSION
            AppFramework.UNITY -> KEY_UNITY_SDK_VERSION
            AppFramework.FLUTTER -> KEY_FLUTTER_SDK_VERSION
            AppFramework.NATIVE -> null
        }
        val hostedPlatformVersionKey = when (appFramework) {
            AppFramework.REACT_NATIVE -> KEY_REACT_NATIVE_VERSION
            AppFramework.UNITY -> KEY_UNITY_VERSION
            AppFramework.FLUTTER -> KEY_DART_SDK_VERSION
            AppFramework.NATIVE -> null
        }
        return PersistedSdkState(
            deviceId = deviceId,
            installTimestampMs = installTimestampMs,
            userId = string(KEY_USER_ID),
            userEmail = string(KEY_USER_EMAIL),
            userName = string(KEY_USER_NAME),
            userPersonas = stringSet(KEY_USER_PERSONAS),
            sessionProperties = stringMap(KEY_SESSION_PROPERTIES) ?: emptyMap(),
            userSession = stringMap(KEY_USER_SESSION)?.let(::decodeUserSession),
            hostedSdkVersion = hostedSdkVersionKey?.let(::string),
            hostedPlatformVersion = hostedPlatformVersionKey?.let(::string),
            unityBuildIdNumber = string(KEY_UNITY_BUILD_ID).takeIf { appFramework == AppFramework.UNITY },
            javaScriptPatchNumber = string(KEY_JAVASCRIPT_PATCH).takeIf { appFramework == AppFramework.REACT_NATIVE },
            rnBundleUrl = string(KEY_RN_BUNDLE_URL),
            rnBundleId = string(KEY_RN_BUNDLE_ID),
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

    private fun string(key: String): String? = all[key] as? String

    /**
     * The legacy store wrote -1 as its "absent" sentinel.
     */
    private fun int(key: String): Int? = (all[key] as? Int)?.takeIf { it != -1 }

    private fun long(key: String): Long? = (all[key] as? Long)?.takeIf { it != -1L }

    private fun stringSet(key: String): Set<String>? =
        (all[key] as? Set<*>)?.filterIsInstance<String>()?.toSet()

    private fun stringMap(key: String): Map<String, String>? {
        val json = string(key) ?: return null
        return errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail, null) {
            serializer.fromJson(json, STRING_MAP_SERIALIZER)
        }
    }

    /**
     * Returns null if any required attribute is absent or can't be parsed.
     */
    private fun decodeUserSession(attrs: Map<String, String>): UserSessionMetadata.Classified? {
        return UserSessionMetadata.Classified(
            startTimeMs = attrs[EmbSessionAttributes.EMB_USER_SESSION_START_TS]?.toLongOrNull() ?: return null,
            userSessionId = attrs[EmbSessionAttributes.EMB_USER_SESSION_ID] ?: return null,
            userSessionNumber = attrs[EmbSessionAttributes.EMB_USER_SESSION_NUMBER]?.toLongOrNull() ?: return null,
            maxDurationSecs = attrs[EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS]?.toLongOrNull() ?: return null,
            inactivityTimeoutSecs = attrs[EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS]?.toLongOrNull()
                ?: return null,
            partIndex = attrs[EmbSessionAttributes.EMB_USER_SESSION_PART_INDEX]?.toIntOrNull() ?: return null,
            lastActivityMs = attrs[KEY_LAST_ACTIVITY_TS]?.toLongOrNull() ?: return null,
            isBackgroundOnly = attrs[EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART] == BACKGROUND_ONLY_MARKER,
        )
    }

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

        val STRING_MAP_SERIALIZER: KSerializer<Map<String, String>> =
            MapSerializer(String.serializer(), String.serializer())

        const val KEY_DEVICE_ID = "io.embrace.deviceid"
        const val KEY_INSTALL_TIMESTAMP = "io.embrace.installtimestamp"
        const val KEY_DELIVERED_AEI_IDS = "io.embrace.aeiHashCode"
        const val KEY_NETWORK_CAPTURE_RULE_PREFIX = "io.embrace.networkcapturerule"
        const val KEY_USER_ID = "io.embrace.userid"
        const val KEY_USER_EMAIL = "io.embrace.useremail"
        const val KEY_USER_NAME = "io.embrace.username"
        const val KEY_USER_PERSONAS = "io.embrace.userpersonas"
        const val KEY_SESSION_PROPERTIES = "io.embrace.session.properties"
        const val KEY_USER_SESSION = "embrace.user_session"
        const val KEY_LAST_ACTIVITY_TS = "emb.user_session_last_activity_ts"
        const val KEY_REACT_NATIVE_SDK_VERSION = "io.embrace.reactnative.sdk.version"
        const val KEY_REACT_NATIVE_VERSION = "io.embrace.reactnative.version"
        const val KEY_JAVASCRIPT_PATCH = "io.embrace.javascript.patch"
        const val KEY_UNITY_SDK_VERSION = "io.embrace.unity.sdk.version"
        const val KEY_UNITY_VERSION = "io.embrace.unity.version"
        const val KEY_UNITY_BUILD_ID = "io.embrace.unity.build.id"
        const val KEY_FLUTTER_SDK_VERSION = "io.embrace.flutter.sdk.version"
        const val KEY_DART_SDK_VERSION = "io.embrace.dart.sdk.version"
        const val KEY_RN_BUNDLE_URL = "io.embrace.jsbundle.url"
        const val KEY_RN_BUNDLE_ID = "io.embrace.jsbundle.id"
    }
}
