package io.embrace.android.embracesdk.instrumentation.leaks

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class LeakDetectionInstrumentationProviderTest {

    private val provider = LeakDetectionInstrumentationProvider()

    @Test
    fun `registers instrumentation`() {
        assertNotNull(provider.register(FakeInstrumentationArgs()))
    }
}
