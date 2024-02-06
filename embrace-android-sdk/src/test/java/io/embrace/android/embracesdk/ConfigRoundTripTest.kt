package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class ConfigRoundTripTest {

    /**
     * Verifies that Config can be serialized then deserialized. If this fails then Config
     * can't be read from the cache service.
     */
    @Test
    fun testConfigRoundTrip() {
        val serializer = EmbraceSerializer()
        val cfg = RemoteConfig()
        val json = serializer.toJson(cfg)
        val observed = serializer.fromJson(json, RemoteConfig::class.java)
        assertNotNull(observed)
    }
}
