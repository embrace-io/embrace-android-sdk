package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeSharedPreferences
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LegacyPrefsReaderTest {

    private lateinit var prefs: FakeSharedPreferences

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
    }

    @Test
    fun `empty prefs read as null without writing`() {
        assertNull(read())
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun `a missing install timestamp reads as null`() {
        seed(
            "io.embrace.deviceid" to "device-id",
            "io.embrace.sessionnumber" to 41,
        )
        assertNull(read())
    }

    @Test
    fun `a missing device id reads as null`() {
        seed(
            "io.embrace.installtimestamp" to 99L,
            "io.embrace.sessionnumber" to 41,
        )
        assertNull(read())
    }

    @Test
    fun `every live key is read`() {
        seed(
            *IDENTITY,
            "io.embrace.userid" to "user-id",
            "io.embrace.useremail" to "user@example.com",
            "io.embrace.username" to "user-name",
            "io.embrace.userpersonas" to setOf("persona"),
            "io.embrace.session.properties" to """{"prop":"value"}""",
            "embrace.user_session" to USER_SESSION_JSON,
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
        assertEquals(
            LegacyPrefsSnapshot(
                persistedSdkState = PersistedSdkState(
                    deviceId = "device-id",
                    installTimestampMs = 99L,
                    userId = "user-id",
                    userEmail = "user@example.com",
                    userName = "user-name",
                    userPersonas = setOf("persona"),
                    sessionProperties = mapOf("prop" to "value"),
                    userSession = USER_SESSION,
                    rnBundleUrl = "assets://index.android.bundle",
                    rnBundleId = "bundle-id",
                ),
                ordinals = mapOf(
                    Ordinal.SESSION to 41,
                    Ordinal.SESSION_PART to 50,
                    Ordinal.CRASH to 2,
                    Ordinal.NATIVE_CRASH to 3,
                    Ordinal.AEI_CRASH to 4,
                    Ordinal.APP_VERSION_STARTUP to 7,
                ),
                appVersionStartupScope = "1.0_1",
                networkCaptureRemaining = mapOf("1" to 5, "rule-b" to 0),
                deliveredAeiIds = setOf("100_1", "200_2"),
            ),
            readSnapshot(),
        )
    }

    @Test
    fun `each framework's hosted sdk keys feed the typed fields`() {
        seed(
            *IDENTITY,
            "io.embrace.reactnative.sdk.version" to "rn-sdk",
            "io.embrace.reactnative.version" to "rn",
            "io.embrace.javascript.patch" to "js-patch",
            "io.embrace.unity.sdk.version" to "unity-sdk",
            "io.embrace.unity.version" to "unity",
            "io.embrace.unity.build.id" to "unity-build",
            "io.embrace.flutter.sdk.version" to "flutter-sdk",
            "io.embrace.dart.sdk.version" to "dart",
        )
        val expected = mapOf(
            AppFramework.NATIVE to listOf(null, null, null, null),
            AppFramework.REACT_NATIVE to listOf("rn-sdk", "rn", null, "js-patch"),
            AppFramework.UNITY to listOf("unity-sdk", "unity", "unity-build", null),
            AppFramework.FLUTTER to listOf("flutter-sdk", "dart", null, null),
        )
        expected.forEach { (framework, values) ->
            val state = readSnapshot(framework).persistedSdkState
            assertEquals(
                framework.name,
                values,
                listOf(state.hostedSdkVersion, state.hostedPlatformVersion, state.unityBuildIdNumber, state.javaScriptPatchNumber),
            )
        }
    }

    @Test
    fun `dead and stale keys are not read`() {
        seed(
            *IDENTITY,
            "io.embrace.usersessionnumber" to 99,
            "io.embrace.bgactivitynumber" to 98,
        )
        assertTrue(readSnapshot().ordinals.isEmpty())
    }

    @Test
    fun `values of the wrong type read as absent`() {
        seed(
            *IDENTITY,
            "io.embrace.userid" to 1,
            "io.embrace.session.properties" to "{not json",
            "io.embrace.sessionnumber" to "41",
            "io.embrace.appversionstartupcounterscope" to 1,
            "io.embrace.networkcapturerule1" to "5",
            "io.embrace.aeiHashCode" to "100_1",
        )
        val snapshot = readSnapshot()
        assertNull(snapshot.persistedSdkState.userId)
        assertTrue(snapshot.persistedSdkState.sessionProperties.isEmpty())
        assertTrue(snapshot.ordinals.isEmpty())
        assertNull(snapshot.appVersionStartupScope)
        assertTrue(snapshot.networkCaptureRemaining.isEmpty())
        assertTrue(snapshot.deliveredAeiIds.isEmpty())
    }

    @Test
    fun `host app keys are ignored`() {
        seed(
            *IDENTITY,
            "networkcapturerule1" to 5,
            "my.app.counter" to 3,
        )
        assertTrue(readSnapshot().networkCaptureRemaining.isEmpty())
    }

    private fun read(appFramework: AppFramework = AppFramework.NATIVE): LegacyPrefsSnapshot? = LegacyPrefsReader(
        prefs,
        TestPlatformSerializer(),
        FakeErrorHandler(throwOnSdkError = false),
        appFramework,
    ).read()

    private fun readSnapshot(appFramework: AppFramework = AppFramework.NATIVE): LegacyPrefsSnapshot =
        checkNotNull(read(appFramework))

    private fun seed(vararg values: Pair<String, Any>) {
        val editor = prefs.edit()
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

    private companion object {
        val IDENTITY: Array<Pair<String, Any>> = arrayOf(
            "io.embrace.deviceid" to "device-id",
            "io.embrace.installtimestamp" to 99L,
        )

        val USER_SESSION_JSON = ResourceReader.readResourceAsText("legacy_prefs_user_session.json")

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
}
