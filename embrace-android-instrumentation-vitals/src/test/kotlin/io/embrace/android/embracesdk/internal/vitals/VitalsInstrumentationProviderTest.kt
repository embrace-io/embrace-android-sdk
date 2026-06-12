package io.embrace.android.embracesdk.internal.vitals

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.util.ReflectionHelpers

@RunWith(AndroidJUnit4::class)
internal class VitalsInstrumentationProviderTest {

    private lateinit var args: FakeInstrumentationArgs
    private val provider = VitalsInstrumentationProvider()

    @Before
    fun setUp() {
        val application: Application = ApplicationProvider.getApplicationContext()
        args = FakeInstrumentationArgs(application)
    }

    private fun setSdkInt(level: Int) {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", level)
    }

    @Test
    fun `returns null below API 24`() {
        setSdkInt(Build.VERSION_CODES.M)
        assertNull(provider.register(args))
    }

    @Test
    fun `registers instrumentation at API 24 and above`() {
        setSdkInt(Build.VERSION_CODES.N)
        assertNotNull(provider.register(args))
    }
}
