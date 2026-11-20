package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.os.Build
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class AeiInstrumentationProviderTest {

    private val provider = AeiInstrumentationProvider()

    @Test
    fun `returns null below API 30`() {
        assertNull(provider.register(argsAt(Build.VERSION_CODES.Q)))
    }

    @Test
    fun `registers instrumentation at API 30 and above`() {
        assertNotNull(provider.register(argsAt(Build.VERSION_CODES.R)))
    }

    private fun argsAt(sdkInt: Int) = FakeInstrumentationArgs(versionChecker = VersionChecker { min -> sdkInt >= min })
}
