package io.embrace.android.embracesdk.internal.vitals

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class VitalsInstrumentationProviderTest {

    @Test
    fun `registers instrumentation`() {
        val application: Application = ApplicationProvider.getApplicationContext()
        assertNotNull(VitalsInstrumentationProvider().register(FakeInstrumentationArgs(application)))
    }
}
