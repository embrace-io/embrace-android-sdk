package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.TestUuidSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DeviceIdProviderTest {

    private val key = "io.embrace.deviceid"

    @Test
    fun `cached device id is preferred and nothing is persisted`() {
        val store = FakeKeyValueStore()
        val provider = DeviceIdProvider(store, cachedDeviceId = "cached-id", uuidSource = TestUuidSource())

        assertEquals("cached-id", provider.deviceId)
        assertTrue(store.values().isEmpty())
    }

    @Test
    fun `falls back to the persisted store value when there is no cached id`() {
        val store = FakeKeyValueStore().apply {
            edit { putString(key, "persisted-id") }
        }
        val provider = DeviceIdProvider(store, cachedDeviceId = null, uuidSource = TestUuidSource())

        assertEquals("persisted-id", provider.deviceId)
    }

    @Test
    fun `generates and persists a new id when nothing is stored`() {
        val store = FakeKeyValueStore()
        val provider = DeviceIdProvider(store, cachedDeviceId = null, uuidSource = TestUuidSource())

        val generated = provider.deviceId
        assertTrue(generated.isNotEmpty())
        // persisted so it is stable across launches
        assertEquals(generated, store.values()[key])
        // and stable within the process
        assertEquals(generated, provider.deviceId)
    }
}
