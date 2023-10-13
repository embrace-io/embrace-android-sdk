package io.embrace.android.embracesdk

import com.google.gson.Gson
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class ConfigRoundTripTest {

    /**
     * Verifies that Config can be serialized then deserialized. If this fails then Config
     * can't be read from the cache service.
     */
    @Test
    fun testConfigRoundTrip() {
        val gson = Gson()
        val cfg = RemoteConfig()
        val json = gson.toJson(cfg)
        val observed = gson.fromJson(json, RemoteConfig::class.java)
        assertNotNull(observed)
    }
}
