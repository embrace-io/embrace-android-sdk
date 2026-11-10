package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeSharedPreferences
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.LegacyPrefsFixtures.ALL_LIVE_KEYS
import io.embrace.android.embracesdk.internal.prefs.LegacyPrefsFixtures.IDENTITY
import io.embrace.android.embracesdk.internal.prefs.LegacyPrefsFixtures.USER_SESSION
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LegacyPrefsReaderTest {

    private lateinit var prefs: FakeSharedPreferences
    private val serializer = TestPlatformSerializer()
    private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())

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
        prefs.seed(
            "io.embrace.deviceid" to "device-id",
            "io.embrace.sessionnumber" to 41,
        )
        assertNull(read())
    }

    @Test
    fun `a missing device id reads as null`() {
        prefs.seed(
            "io.embrace.installtimestamp" to 99L,
            "io.embrace.sessionnumber" to 41,
        )
        assertNull(read())
    }

    @Test
    fun `every live key is read`() {
        prefs.seed(*ALL_LIVE_KEYS)
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
        prefs.seed(
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
        prefs.seed(
            *IDENTITY,
            "io.embrace.usersessionnumber" to 99,
            "io.embrace.bgactivitynumber" to 98,
        )
        assertTrue(readSnapshot().ordinals.isEmpty())
    }

    @Test
    fun `values of the wrong type read as absent`() {
        prefs.seed(
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
        prefs.seed(
            *IDENTITY,
            "networkcapturerule1" to 5,
            "my.app.counter" to 3,
        )
        assertTrue(readSnapshot().networkCaptureRemaining.isEmpty())
    }

    @Test
    fun `the -1 sentinel reads as absent`() {
        prefs.seed(*IDENTITY, "io.embrace.sessionnumber" to -1)
        assertTrue(readSnapshot().ordinals.isEmpty())
    }

    @Test
    fun `a background-only user session keeps its marker`() {
        seedUserSession(userSessionAttrs() + ("emb.is_background_only_part" to "1"))
        assertEquals(USER_SESSION.copy(isBackgroundOnly = true), readSnapshot().persistedSdkState.userSession)
    }

    @Test
    fun `a user session with an unrecognised background marker is read as not background-only`() {
        seedUserSession(userSessionAttrs() + ("emb.is_background_only_part" to "true"))
        assertEquals(USER_SESSION, readSnapshot().persistedSdkState.userSession)
    }

    @Test
    fun `a user session missing a required attribute is read as absent`() {
        userSessionAttrs().keys.forEach { key ->
            prefs = FakeSharedPreferences()
            seedUserSession(userSessionAttrs() - key)
            assertNull(key, readSnapshot().persistedSdkState.userSession)
        }
    }

    @Test
    fun `a user session with an unparseable attribute is read as absent`() {
        seedUserSession(userSessionAttrs() + ("emb.user_session_part_index" to "not-a-number"))
        assertNull(readSnapshot().persistedSdkState.userSession)
    }

    private fun userSessionAttrs(): Map<String, String> = serializer.fromJson(
        ResourceReader.readResourceAsText("legacy_prefs_user_session.json"),
        stringMapSerializer,
    )

    private fun seedUserSession(attrs: Map<String, String>) {
        prefs.seed(*IDENTITY, "embrace.user_session" to serializer.toJson(attrs, stringMapSerializer))
    }

    private fun read(appFramework: AppFramework = AppFramework.NATIVE): LegacyPrefsSnapshot? = LegacyPrefsReader(
        prefs.all,
        serializer,
        FakeErrorHandler(throwOnSdkError = false),
        appFramework,
    ).read()

    private fun readSnapshot(appFramework: AppFramework = AppFramework.NATIVE): LegacyPrefsSnapshot =
        checkNotNull(read(appFramework))
}
