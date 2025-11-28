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
internal class AnrModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val module = AnrModuleImpl(
            FakeInstrumentationArgs(
                application,
                configService = FakeConfigService()
            ),
        )
        assertNotNull(module.anrService)
    }

    @Test
    fun testBehaviorDisabled() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val module = AnrModuleImpl(
            FakeInstrumentationArgs(
                application,
                configService = FakeConfigService(
                    autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(anrServiceEnabled = false)
                )
            ),
        )
        assertNull(module.anrService)
    }
}
