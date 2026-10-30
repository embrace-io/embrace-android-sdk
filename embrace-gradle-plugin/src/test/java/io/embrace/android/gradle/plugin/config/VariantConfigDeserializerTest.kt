package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.ResourceReader
import io.embrace.android.gradle.plugin.util.serialization.JsonSerializer
import io.embrace.bytecode.config.model.EmbraceVariantConfig
import org.junit.Assert.assertNotNull
import org.junit.Test

class VariantConfigDeserializerTest {

    @Test
    fun testSDKConfiguration() {
        // TODO: this test doesn't make sense
        val configFile = ResourceReader.readResourceAsText("config_file_expected.json")
        val obj = JsonSerializer().fromJson(configFile, EmbraceVariantConfig.serializer())
        assertNotNull(obj)
    }
}
