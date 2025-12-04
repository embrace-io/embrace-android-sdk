package io.embrace.android.embracesdk.internal.instrumentation.anr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class AnrServiceSupplierTest {

    @Test
    fun testDefaultImplementations() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val service = createAnrService(
            FakeInstrumentationArgs(
                application,
                configService = FakeConfigService()
            ),
        )
        assertNotNull(service)
    }

    @Test
    fun testBehaviorDisabled() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val service = createAnrService(
            FakeInstrumentationArgs(
                application,
                configService = FakeConfigService(
                    autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(anrServiceEnabled = false)
                )
            ),
        )
        assertNull(service)
    }
}
