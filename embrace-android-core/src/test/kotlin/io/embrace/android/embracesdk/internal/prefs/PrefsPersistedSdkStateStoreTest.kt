package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class PrefsPersistedSdkStateStoreTest {

    private lateinit var kvStore: FakeKeyValueStore
    private lateinit var clock: FakeClock
    private lateinit var store: PrefsPersistedSdkStateStore

    private val expectedDeviceId = TestUuidSource().createUuid()

    private val legacyValues: Map<String, Any> = mapOf(
        "io.embrace.deviceid" to "legacy-id",
        "io.embrace.installtimestamp" to 99L,
        "io.embrace.userid" to "user-id",
        "io.embrace.useremail" to "user@example.com",
        "io.embrace.username" to "user-name",
        "io.embrace.userpersonas" to setOf("persona"),
        "io.embrace.session.properties" to mapOf("prop" to "value"),
    )

    private val legacyState = PersistedSdkState(
        deviceId = "legacy-id",
        installTimestampMs = 99L,
        userId = "user-id",
        userEmail = "user@example.com",
        userName = "user-name",
        userPersonas = setOf("persona"),
        sessionProperties = mapOf("prop" to "value"),
    )

    @Before
    fun setUp() {
        kvStore = FakeKeyValueStore()
        clock = FakeClock()
        store = PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource())
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
        assertEquals(expected, PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource(seed = 1)).snapshot())
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
        }
        val expected = legacyState.copy(deviceId = expectedDeviceId, installTimestampMs = clock.now())
        assertEquals(expected, state)
        assertEquals(
            legacyValues + mapOf("io.embrace.deviceid" to expectedDeviceId, "io.embrace.installtimestamp" to clock.now()),
            storedValues(),
        )
        assertEquals(2, kvStore.commitCount)
        assertEquals(expected, PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource()).snapshot())
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
        }
        assertEquals(
            mapOf("io.embrace.deviceid" to "legacy-id", "io.embrace.installtimestamp" to 99L),
            storedValues(),
        )
        assertEquals(
            PersistedSdkState(deviceId = "legacy-id", installTimestampMs = 99L),
            PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource()).snapshot(),
        )
    }

    @Test
    fun `values left unset by an update are kept`() {
        seedLegacyValues()
        assertEquals(legacyState.copy(userId = "new-id"), store.update { userId = "new-id" })
        assertEquals(legacyValues + ("io.embrace.userid" to "new-id"), storedValues())
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
