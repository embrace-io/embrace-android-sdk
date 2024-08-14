package io.embrace.android.embracesdk.internal.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class ConfigModuleImplTest {

    @Test
    fun `test defaults`() {
        val module = ConfigModuleImpl(
            FakeInitModule(),
            CoreModuleImpl(RuntimeEnvironment.getApplication(), FakeEmbLogger()),
            FakeOpenTelemetryModule(),
            FakeWorkerThreadModule(),
            FakeAndroidServicesModule(),
            "abcde",
            AppFramework.NATIVE,
            { null },
            {}
        )
        assertNotNull(module.configService)
    }

    @Test
    fun testConfigServiceProvider() {
        val fakeConfigService = FakeConfigService()
        val module = ConfigModuleImpl(
            FakeInitModule(),
            CoreModuleImpl(RuntimeEnvironment.getApplication(), FakeEmbLogger()),
            FakeOpenTelemetryModule(),
            FakeWorkerThreadModule(),
            FakeAndroidServicesModule(),
            "abcde",
            AppFramework.NATIVE,
            { fakeConfigService },
            {}
        )
        assertSame(fakeConfigService, module.configService)
    }
}
