package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

internal class RemoteConfigStoreImplTest {

    private lateinit var store: RemoteConfigStoreImpl
    private lateinit var dir: File
    private val serializer = TestPlatformSerializer()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("test").toFile()
        store = RemoteConfigStoreImpl(serializer, dir, deviceIdProvider = { ENABLED_DEVICE_ID })
    }

    @Test
    fun `config round-trips through the store`() {
        assertNull(store.loadResponse())

        val config = RemoteConfig(threshold = 100, maxUserSessionProperties = 7)
        store.saveResponse(ConfigHttpResponse(config, "etag"))

        val loaded = checkNotNull(store.loadResponse())
        assertEquals(config, loaded.cfg)
        assertEquals("etag", loaded.etag)

        val newConfig = RemoteConfig(threshold = 100, maxUserSessionProperties = 9)
        store.saveResponse(ConfigHttpResponse(newConfig, "another"))

        val newLoaded = checkNotNull(store.loadResponse())
        assertEquals(newConfig, newLoaded.cfg)
        assertEquals("another", newLoaded.etag)
    }

    @Test
    fun `null etag loads as null`() {
        store.saveResponse(ConfigHttpResponse(RemoteConfig(threshold = 100), null))
        assertNull(checkNotNull(store.loadResponse()).etag)
    }

    @Test
    fun `saving always writes a complete JSON primary`() {
        val config = RemoteConfig(threshold = 100, maxUserSessionProperties = 5)
        store.saveResponse(ConfigHttpResponse(config, "etag"))
        assertEquals(config, readJsonPrimary())
    }

    @Test
    fun `disabled threshold short-circuits the binary read to a minimal config`() {
        // threshold 0 disables the SDK for every device, so the gate fires regardless of deviceId
        val config = RemoteConfig(threshold = 0, maxUserSessionProperties = 99)
        store.saveResponse(ConfigHttpResponse(config, "etag"))

        val loaded = checkNotNull(store.loadResponse())
        assertEquals(RemoteConfig(threshold = 0), loaded.cfg)
    }

    @Test
    fun `falls back to the JSON primary when the binary cache is absent`() {
        // an install with only the JSON primary (e.g. before the first binary write, or after an upgrade)
        writeJsonPrimary(RemoteConfig(threshold = 100, maxUserSessionProperties = 3))
        File(dir, "etag").writeText("primary-etag")

        val loaded = checkNotNull(store.loadResponse())
        assertEquals(RemoteConfig(threshold = 100, maxUserSessionProperties = 3), loaded.cfg)
        assertEquals("primary-etag", loaded.etag)
    }

    @Test
    fun `loadDeviceId returns the deviceId cached in the binary config`() {
        assertNull(store.loadDeviceId())

        store.saveResponse(ConfigHttpResponse(RemoteConfig(threshold = 100), "etag"))
        assertEquals(ENABLED_DEVICE_ID, store.loadDeviceId())
    }

    @Test
    fun `loadDeviceId returns null when only a JSON primary exists`() {
        writeJsonPrimary(RemoteConfig(threshold = 100))
        assertNull(store.loadDeviceId())
    }

    private fun readJsonPrimary(): RemoteConfig =
        File(dir, "most_recent_response").inputStream().buffered().use {
            serializer.fromJson(it, RemoteConfig::class.java)
        }

    private fun writeJsonPrimary(config: RemoteConfig) {
        File(dir, "most_recent_response").outputStream().buffered().use {
            serializer.toJson(config, RemoteConfig::class.java, it)
        }
    }

    private companion object {
        // deviceId whose normalized value is 0, so any positive threshold leaves the SDK enabled
        const val ENABLED_DEVICE_ID = "00000000000000000000000000000000"
    }
}
