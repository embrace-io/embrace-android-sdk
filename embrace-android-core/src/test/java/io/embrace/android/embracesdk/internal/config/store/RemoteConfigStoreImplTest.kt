package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

internal class RemoteConfigStoreImplTest {

    private lateinit var store: RemoteConfigStoreImpl
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("test").toFile()
        store = RemoteConfigStoreImpl(TestPlatformSerializer(), dir)
    }

    @Test
    fun `test config store`() {
        assertNull(store.getConfig())

        // store a config
        val config = RemoteConfig(50)
        store.save(config)

        // load the config
        val loaded = checkNotNull(store.getConfig())
        assertEquals(config, loaded)

        val newConfig = RemoteConfig(100)
        store.save(newConfig)

        val newLoaded = checkNotNull(store.getConfig())
        assertEquals(newConfig, newLoaded)
    }
}
