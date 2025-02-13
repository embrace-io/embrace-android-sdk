package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.ResourceReader
import io.embrace.android.gradle.plugin.instrumentation.config.model.VariantConfig
import io.embrace.android.gradle.plugin.util.serialization.MoshiSerializer
import org.junit.Assert.assertNotNull
import org.junit.Test

class VariantConfigDeserializerTest {

    @Test
    fun testSDKConfiguration() {
        val configFile = ResourceReader.readResourceAsText("config_file_expected.json")
        val obj = MoshiSerializer().fromJson(configFile, VariantConfig::class.java)
        assertNotNull(obj)
    }
}
