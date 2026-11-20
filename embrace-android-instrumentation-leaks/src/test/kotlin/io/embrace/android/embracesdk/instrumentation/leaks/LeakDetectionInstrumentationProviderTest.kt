package io.embrace.android.embracesdk.instrumentation.leaks

import android.os.Build
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class LeakDetectionInstrumentationProviderTest {

    private val provider = LeakDetectionInstrumentationProvider()

    @Test
    fun `returns null below API 23`() {
        assertNull(provider.register(argsAt(Build.VERSION_CODES.LOLLIPOP_MR1)))
    }

    @Test
    fun `registers instrumentation at API 23 and above`() {
        assertNotNull(provider.register(argsAt(Build.VERSION_CODES.M)))
    }

    private fun argsAt(sdkInt: Int) = FakeInstrumentationArgs(versionChecker = VersionChecker { min -> sdkInt >= min })
}
