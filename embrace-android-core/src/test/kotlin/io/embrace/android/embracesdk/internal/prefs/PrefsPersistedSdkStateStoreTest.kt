package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class PrefsPersistedSdkStateStoreTest {

    private lateinit var kvStore: FakeKeyValueStore
    private lateinit var clock: FakeClock
    private lateinit var store: PrefsPersistedSdkStateStore

    private val expectedDeviceId = TestUuidSource().createUuid()

    private val legacyUserSessionAttrs: Map<String, String> = mapOf(
        "emb.user_session_start_ts" to "1000",
        "emb.user_session_id" to "session-id",
        "emb.user_session_number" to "3",
        "emb.user_session_max_duration_seconds" to "3600",
        "emb.user_session_inactivity_timeout_seconds" to "1800",
        "emb.user_session_part_index" to "2",
        "emb.user_session_last_activity_ts" to "5000",
    )

    private val userSession = UserSessionMetadata.Classified(
        startTimeMs = 1000L,
        userSessionId = "session-id",
        userSessionNumber = 3L,
        maxDurationSecs = 3600L,
        inactivityTimeoutSecs = 1800L,
        partIndex = 2,
        lastActivityMs = 5000L,
        isBackgroundOnly = false,
    )

    private val legacyValues: Map<String, Any> = mapOf(
        "io.embrace.deviceid" to "legacy-id",
        "io.embrace.installtimestamp" to 99L,
        "io.embrace.userid" to "user-id",
        "io.embrace.useremail" to "user@example.com",
        "io.embrace.username" to "user-name",
        "io.embrace.userpersonas" to setOf("persona"),
        "io.embrace.session.properties" to mapOf("prop" to "value"),
        "embrace.user_session" to legacyUserSessionAttrs,
        "io.embrace.unity.sdk.version" to "1.2.0",
        "io.embrace.unity.version" to "2022.3",
        "io.embrace.unity.build.id" to "5092abc",
        "io.embrace.jsbundle.url" to "assets://index.android.bundle",
        "io.embrace.jsbundle.id" to "bundle-id",
    )

    private val allHostedSdkKeys = listOf(
        "io.embrace.reactnative.sdk.version",
        "io.embrace.reactnative.version",
        "io.embrace.javascript.patch",
        "io.embrace.unity.sdk.version",
        "io.embrace.unity.version",
        "io.embrace.unity.build.id",
        "io.embrace.flutter.sdk.version",
        "io.embrace.dart.sdk.version",
    )

    private val legacyState = PersistedSdkState(
        deviceId = "legacy-id",
        installTimestampMs = 99L,
        userId = "user-id",
        userEmail = "user@example.com",
        userName = "user-name",
        userPersonas = setOf("persona"),
        sessionProperties = mapOf("prop" to "value"),
        userSession = userSession,
        hostedSdkVersion = "1.2.0",
        hostedPlatformVersion = "2022.3",
        unityBuildIdNumber = "5092abc",
        rnBundleUrl = "assets://index.android.bundle",
        rnBundleId = "bundle-id",
    )

    @Before
    fun setUp() {
        kvStore = FakeKeyValueStore()
        clock = FakeClock()
        store = PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource(), AppFramework.UNITY)
    }

    @Test
    fun `empty store generates and records a device id and install timestamp`() {
        assertEquals(PersistedSdkState(deviceId = expectedDeviceId, installTimestampMs = clock.now()), store.snapshot())
        assertEquals(
            mapOf("io.embrace.deviceid" to expectedDeviceId, "io.embrace.installtimestamp" to clock.now()),
            storedValues(),
        )
    }

    @Test
    fun `generated values are recorded once`() {
        val expected = store.snapshot()
        clock.tick(1000L)
        assertEquals(expected, store.snapshot())
        assertEquals(expected, PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource(seed = 1), AppFramework.UNITY).snapshot())
        assertEquals(1, kvStore.commitCount)
    }

    @Test
    fun `values written by the legacy code are read`() {
        seedLegacyValues()
        assertEquals(legacyState, store.snapshot())
        assertEquals(1, kvStore.commitCount)
    }

    @Test
    fun `an update persists every field under its legacy key in one commit`() {
        val state = store.update {
            userId = "user-id"
            userEmail = "user@example.com"
            userName = "user-name"
            userPersonas = setOf("persona")
            sessionProperties = mapOf("prop" to "value")
            userSession = this@PrefsPersistedSdkStateStoreTest.userSession
            hostedSdkVersion = "1.2.0"
            hostedPlatformVersion = "2022.3"
            unityBuildIdNumber = "5092abc"
            rnBundleUrl = "assets://index.android.bundle"
            rnBundleId = "bundle-id"
        }
        val expected = legacyState.copy(deviceId = expectedDeviceId, installTimestampMs = clock.now())
        assertEquals(expected, state)
        assertEquals(
            legacyValues + mapOf("io.embrace.deviceid" to expectedDeviceId, "io.embrace.installtimestamp" to clock.now()),
            storedValues(),
        )
        assertEquals(2, kvStore.commitCount)
        assertEquals(expected, PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource(), AppFramework.UNITY).snapshot())
    }

    @Test
    fun `clearing values removes their keys`() {
        seedLegacyValues()
        store.update {
            userId = null
            userEmail = null
            userName = null
            userPersonas = null
            sessionProperties = emptyMap()
            userSession = null
            hostedSdkVersion = null
            hostedPlatformVersion = null
            unityBuildIdNumber = null
            rnBundleUrl = null
            rnBundleId = null
        }
        assertEquals(
            mapOf("io.embrace.deviceid" to "legacy-id", "io.embrace.installtimestamp" to 99L),
            storedValues(),
        )
        assertEquals(
            PersistedSdkState(deviceId = "legacy-id", installTimestampMs = 99L),
            PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource(), AppFramework.UNITY).snapshot(),
        )
    }

    @Test
    fun `values left unset by an update are kept`() {
        seedLegacyValues()
        assertEquals(legacyState.copy(userId = "new-id"), store.update { userId = "new-id" })
        assertEquals(legacyValues + ("io.embrace.userid" to "new-id"), storedValues())
    }

    @Test
    fun `each framework reads and writes only its own hosted SDK keys`() {
        mapOf(
            AppFramework.REACT_NATIVE to listOf(
                "io.embrace.reactnative.sdk.version",
                "io.embrace.reactnative.version",
                null,
                "io.embrace.javascript.patch",
            ),
            AppFramework.UNITY to listOf(
                "io.embrace.unity.sdk.version",
                "io.embrace.unity.version",
                "io.embrace.unity.build.id",
                null,
            ),
            AppFramework.FLUTTER to listOf("io.embrace.flutter.sdk.version", "io.embrace.dart.sdk.version", null, null),
            AppFramework.NATIVE to listOf(null, null, null, null),
        ).forEach { (framework, keys) ->
            kvStore = FakeKeyValueStore()
            kvStore.editAndCommit { allHostedSdkKeys.forEach { putString(it, "old") } }
            val store = PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource(), framework)
            val state = store.snapshot()
            assertEquals(
                framework.name,
                keys.map { key -> key?.let { "old" } },
                listOf(state.hostedSdkVersion, state.hostedPlatformVersion, state.unityBuildIdNumber, state.javaScriptPatchNumber),
            )

            store.update {
                hostedSdkVersion = "1"
                hostedPlatformVersion = "2"
                unityBuildIdNumber = "3"
                javaScriptPatchNumber = null
            }
            val written = keys.zip(listOf("1", "2", "3", null)).mapNotNull { (key, value) -> key?.let { it to value } }
            assertEquals(
                framework.name,
                allHostedSdkKeys.associateWith { "old" } + written,
                allHostedSdkKeys.associateWith(kvStore::getString),
            )
        }
    }

    @Test
    fun `a legacy payer flag adds the payer persona and is never written`() {
        seedLegacyValues()
        kvStore.editAndCommit { putBoolean("io.embrace.userispayer", true) }
        val expected = legacyState.copy(userPersonas = setOf("persona", "payer"))
        assertEquals(expected, store.snapshot())

        store.update { userPersonas = setOf("persona") }
        assertEquals(
            legacyValues + ("io.embrace.userispayer" to true),
            storedValues(),
        )
    }

    @Test
    fun `a legacy payer flag with no personas becomes the payer persona`() {
        kvStore.editAndCommit { putBoolean("io.embrace.userispayer", true) }
        assertEquals(setOf("payer"), store.snapshot().userPersonas)
    }

    @Test
    fun `a legacy payer flag that is false adds nothing`() {
        seedLegacyValues()
        kvStore.editAndCommit { putBoolean("io.embrace.userispayer", false) }
        assertEquals(legacyState, store.snapshot())
    }

    @Test
    fun `a background-only user session is written with the legacy marker`() {
        store.update { userSession = this@PrefsPersistedSdkStateStoreTest.userSession.copy(isBackgroundOnly = true) }
        val expectedAttrs = legacyUserSessionAttrs + ("emb.is_background_only_part" to "1")
        assertEquals(expectedAttrs, kvStore.getStringMap("embrace.user_session"))
        assertEquals(
            userSession.copy(isBackgroundOnly = true),
            PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource(), AppFramework.UNITY).snapshot().userSession,
        )
    }

    @Test
    fun `a user session with an unrecognised background marker is read as not background-only`() {
        seedUserSession(legacyUserSessionAttrs + ("emb.is_background_only_part" to "true"))
        assertEquals(userSession, store.snapshot().userSession)
    }

    @Test
    fun `a user session missing a required attribute is read as absent`() {
        legacyUserSessionAttrs.keys.forEach { key ->
            kvStore = FakeKeyValueStore()
            seedUserSession(legacyUserSessionAttrs - key)
            assertNull(key, PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource(), AppFramework.UNITY).snapshot().userSession)
        }
    }

    @Test
    fun `a user session with an unparseable attribute is read as absent`() {
        seedUserSession(legacyUserSessionAttrs + ("emb.user_session_part_index" to "not-a-number"))
        assertNull(store.snapshot().userSession)
    }

    private fun seedUserSession(attrs: Map<String, String>) {
        kvStore.editAndCommit { putStringMap("embrace.user_session", attrs) }
    }

    private fun seedLegacyValues() {
        kvStore.editAndCommit {
            legacyValues.forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value as Set<String>)
                    is Map<*, *> -> putStringMap(key, value as Map<String, String>)
                }
            }
        }
    }

    private fun storedValues(): Map<String, Any?> = kvStore.values().filterValues { it != null }
}
