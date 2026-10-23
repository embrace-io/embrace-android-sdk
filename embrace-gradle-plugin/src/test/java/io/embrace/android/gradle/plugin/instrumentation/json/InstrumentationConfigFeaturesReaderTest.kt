package io.embrace.android.gradle.plugin.instrumentation.json

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentationConfigFeaturesReaderTest {

    @Test
    fun `features are parsed once and reused`() {
        val features = readBytecodeInstrumentationFeatures()
        assertTrue(features.isNotEmpty())
        assertSame(features, readBytecodeInstrumentationFeatures())
    }
}
