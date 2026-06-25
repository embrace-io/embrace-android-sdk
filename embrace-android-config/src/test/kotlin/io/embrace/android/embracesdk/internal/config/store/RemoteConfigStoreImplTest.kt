package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

internal class RemoteConfigStoreImplTest {

    private lateinit var store: RemoteConfigStoreImpl
    private lateinit var dir: File
    private var deviceId: String = "device-id"

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("test").toFile()
        store = RemoteConfigStoreImpl(TestPlatformSerializer(), dir, { deviceId })
    }

    @Test
    fun `test config store`() {
        assertNull(store.loadResponse())

        // store a config
        val config = RemoteConfig(50)
        store.saveResponse(ConfigHttpResponse(config, "etag"))

        // load the config
        val loaded = checkNotNull(store.loadResponse())
        assertEquals(config, loaded.cfg)
        assertEquals("etag", loaded.etag)

        val newConfig = RemoteConfig(100)
        store.saveResponse(ConfigHttpResponse(newConfig, "another"))

        val newLoaded = checkNotNull(store.loadResponse())
        assertEquals(newConfig, newLoaded.cfg)
        assertEquals("another", newLoaded.etag)

        store.saveResponse(ConfigHttpResponse(newConfig, "another"))
        assertEquals(newConfig, newLoaded.cfg)
        assertEquals("another", newLoaded.etag)
    }

    @Test
    fun `fast path returns device id from binary cache`() {
        val config = RemoteConfig(50)
        store.saveResponse(ConfigHttpResponse(config, "etag"))

        val loaded = checkNotNull(store.loadResponse())
        assertEquals(config, loaded.cfg)
        assertEquals("etag", loaded.etag)
        assertEquals("device-id", loaded.deviceId)
        assertTrue(cachedConfigFile().exists())
    }

    @Test
    fun `corrupt binary cache falls back to json path`() {
        val config = RemoteConfig(50)
        store.saveResponse(ConfigHttpResponse(config, "etag"))

        // corrupt the binary fast-path blob
        cachedConfigFile().writeBytes(byteArrayOf(1, 2, 3, 4))

        val loaded = checkNotNull(store.loadResponse())
        assertEquals(config, loaded.cfg)
        assertEquals("etag", loaded.etag)
        // device id is only sourced from the binary fast-path, so the fallback omits it
        assertNull(loaded.deviceId)
    }

    @Test
    fun `corrupt binary cache is deleted to avoid reloading it`() {
        val config = RemoteConfig(50)
        store.saveResponse(ConfigHttpResponse(config, "etag"))
        assertTrue(cachedConfigFile().exists())

        // overwrite the blob with a valid-length but wrong version header, which the binary format
        // rejects with an IllegalArgumentException (a deserialization error, not an IO error).
        cachedConfigFile().writeBytes(ByteArray(8))

        // the json fallback still loads...
        val loaded = checkNotNull(store.loadResponse())
        assertEquals(config, loaded.cfg)
        // ...and the corrupt blob is purged so it is never read a second time.
        assertFalse(cachedConfigFile().exists())
    }

    @Test
    fun `unreadable binary cache is preserved as the error may be recoverable`() {
        val config = RemoteConfig(50)
        store.saveResponse(ConfigHttpResponse(config, "etag"))
        assertTrue(cachedConfigFile().exists())

        // a truncated blob fails the readLong() header with an EOFException. That is an IO error
        // which may be transient (e.g. too many open FDs), so the file must not be deleted.
        cachedConfigFile().writeBytes(byteArrayOf(1, 2, 3, 4))

        val loaded = checkNotNull(store.loadResponse())
        assertEquals(config, loaded.cfg)
        assertTrue(cachedConfigFile().exists())
    }

    @Test
    fun `corrupt json config is deleted to avoid reloading it`() {
        val config = RemoteConfig(50)
        store.saveResponse(ConfigHttpResponse(config, "etag"))

        // force the slow path: a missing binary cache is a clean miss that falls back to json.
        cachedConfigFile().delete()

        // corrupt the canonical json so deserialization fails with an IllegalArgumentException.
        configFile().writeText("!!! not json !!!")

        // nothing loads...
        assertNull(store.loadResponse())
        // ...and the corrupt file is purged so it is never read a second time.
        assertFalse(configFile().exists())
    }

    @Test
    fun `unreadable json config is preserved as the error may be recoverable`() {
        val serializer = TestPlatformSerializer()
        store = RemoteConfigStoreImpl(serializer, dir, { deviceId })

        val config = RemoteConfig(50)
        store.saveResponse(ConfigHttpResponse(config, "etag"))

        // force the slow path so the json deserialization is actually exercised.
        cachedConfigFile().delete()

        // make the next deserialization throw a non-IllegalArgumentException, standing in for a
        // recoverable IO error (e.g. too many open FDs) rather than corrupt data.
        serializer.errorOnNextOperation()

        // the load fails...
        assertNull(store.loadResponse())
        // ...but the file is intact so a later attempt can succeed once the error clears.
        assertTrue(configFile().exists())
    }

    @Test
    fun `binary cache only written after json files succeed`() {
        // force the binary write to fail while the canonical json + etag files still succeed
        store = RemoteConfigStoreImpl(
            TestPlatformSerializer(),
            dir,
            { error("device id unavailable") },
        )

        val config = RemoteConfig(50)
        store.saveResponse(ConfigHttpResponse(config, "etag"))

        // the failed blob is deleted so the fast path is skipped...
        assertFalse(cachedConfigFile().exists())

        // ...but the json fallback was written successfully and still loads
        val loaded = checkNotNull(store.loadResponse())
        assertEquals(config, loaded.cfg)
        assertEquals("etag", loaded.etag)
        assertNull(loaded.deviceId)
    }

    private fun cachedConfigFile() = File(dir, "cached_config")

    private fun configFile() = File(dir, "most_recent_response")
}
