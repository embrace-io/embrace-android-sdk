package io.embrace.android.embracesdk.internal.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ConfigModuleImplTest {

    @Test
    fun `test defaults`() {
        val module = ConfigModuleImpl(
            initModule = FakeInitModule(),
            openTelemetryModule = FakeOpenTelemetryModule(),
            workerThreadModule = FakeWorkerThreadModule(),
            androidServicesModule = FakeAndroidServicesModule(),
            framework = AppFramework.NATIVE,
            foregroundAction = {},
            appIdFromConfig = "AbCeD",
        )
        assertNotNull(module.configService)
    }

    @Test
    fun `validate appId in appIdProvider is used`() {
        val module = ConfigModuleImpl(
            initModule = FakeInitModule(),
            openTelemetryModule = FakeOpenTelemetryModule(),
            workerThreadModule = FakeWorkerThreadModule(),
            androidServicesModule = FakeAndroidServicesModule(),
            framework = AppFramework.NATIVE,
            foregroundAction = {},
            appIdFromConfig = "AbCeD",
        )
        assertSame("AbCeD", module.configService.appId)
    }
}
