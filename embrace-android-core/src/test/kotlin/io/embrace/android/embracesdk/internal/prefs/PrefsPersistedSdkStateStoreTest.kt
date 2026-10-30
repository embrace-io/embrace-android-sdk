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
            kvStore.values(),
        )
    }

    @Test
    fun `generated values are recorded once`() {
        val expected = store.snapshot()
        clock.tick(1000L)
        assertEquals(expected, store.snapshot())
        assertEquals(expected, PrefsPersistedSdkStateStore(kvStore, clock, TestUuidSource(seed = 1)).snapshot())
        assertEquals(2, kvStore.commitCount)
    }

    @Test
    fun `values written by the legacy code are read`() {
        kvStore.editAndCommit {
            putString("io.embrace.deviceid", "legacy-id")
            putLong("io.embrace.installtimestamp", 99L)
        }
        assertEquals(PersistedSdkState(deviceId = "legacy-id", installTimestampMs = 99L), store.snapshot())
        assertEquals(1, kvStore.commitCount)
    }

    @Test
    fun `an update persists the current values in one commit`() {
        kvStore.editAndCommit {
            putString("io.embrace.deviceid", "legacy-id")
            putLong("io.embrace.installtimestamp", 99L)
        }
        val state = store.update { }
        assertEquals(PersistedSdkState(deviceId = "legacy-id", installTimestampMs = 99L), state)
        assertEquals(
            mapOf("io.embrace.deviceid" to "legacy-id", "io.embrace.installtimestamp" to 99L),
            kvStore.values(),
        )
        assertEquals(2, kvStore.commitCount)
    }
}
