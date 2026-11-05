package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.FakeSharedPreferences
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata

internal object LegacyPrefsFixtures {

    val IDENTITY: Array<Pair<String, Any>> = arrayOf(
        "io.embrace.deviceid" to "device-id",
        "io.embrace.installtimestamp" to 99L,
    )

    val ALL_LIVE_KEYS: Array<Pair<String, Any>> = arrayOf(
        *IDENTITY,
        "io.embrace.userid" to "user-id",
        "io.embrace.useremail" to "user@example.com",
        "io.embrace.username" to "user-name",
        "io.embrace.userpersonas" to setOf("persona"),
        "io.embrace.session.properties" to """{"prop":"value"}""",
        "embrace.user_session" to ResourceReader.readResourceAsText("legacy_prefs_user_session.json"),
        "io.embrace.jsbundle.url" to "assets://index.android.bundle",
        "io.embrace.jsbundle.id" to "bundle-id",
        "io.embrace.sessionnumber" to 41,
        "io.embrace.sessionpartnumber" to 50,
        "io.embrace.crashnumber" to 2,
        "io.embrace.nativecrashnumber" to 3,
        "io.embrace.aeicrashnumber" to 4,
        "io.embrace.appversionstartupcounter" to 7,
        "io.embrace.appversionstartupcounterscope" to "1.0_1",
        "io.embrace.networkcapturerule1" to 5,
        "io.embrace.networkcapturerulerule-b" to 0,
        "io.embrace.aeiHashCode" to setOf("100_1", "200_2"),
    )

    val USER_SESSION = UserSessionMetadata.Classified(
        startTimeMs = 1000L,
        userSessionId = "session-id",
        userSessionNumber = 3L,
        maxDurationSecs = 3600L,
        inactivityTimeoutSecs = 1800L,
        partIndex = 2,
        lastActivityMs = 5000L,
        isBackgroundOnly = false,
    )
}

internal fun FakeSharedPreferences.seed(vararg values: Pair<String, Any>) {
    val editor = edit()
    values.forEach { (key, value) ->
        @Suppress("UNCHECKED_CAST")
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Set<*> -> editor.putStringSet(key, value as Set<String>)
            else -> error("Unsupported type for $key")
        }
    }
    editor.commit()
}
