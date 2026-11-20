package io.embrace.android.embracesdk.internal.instrumentation.thermalstate

import android.os.Build
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class ThermalStateInstrumentationProviderTest {

    private val provider = ThermalStateInstrumentationProvider()

    @Test
    fun `returns null below API 29`() {
        assertNull(provider.register(argsAt(Build.VERSION_CODES.P)))
    }

    @Test
    fun `registers instrumentation at API 29 and above`() {
        assertNotNull(provider.register(argsAt(Build.VERSION_CODES.Q)))
    }

    private fun argsAt(sdkInt: Int) = FakeInstrumentationArgs(versionChecker = VersionChecker { min -> sdkInt >= min })
}
