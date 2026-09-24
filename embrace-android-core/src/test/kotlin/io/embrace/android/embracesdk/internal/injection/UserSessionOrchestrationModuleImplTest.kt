package io.embrace.android.embracesdk.internal.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fakes.createPersistenceBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class UserSessionOrchestrationModuleImplTest {

    private val openTelemetryModule = FakeOpenTelemetryModule()

    @Test
    fun `session parts are written when they can be read back`() {
        val module = createModule(FakeDeliveryModule())
        assertNotNull(module.sessionPartReader)
        storeCompletedSpan()
        assertEquals(0, openTelemetryModule.spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `session parts are not written without a reader`() {
        val module = createModule(null)
        assertNull(module.sessionPartReader)
        storeCompletedSpan()
        assertEquals(1, openTelemetryModule.spanRepository.completedOtelSpans().size)
    }

    private fun storeCompletedSpan() {
        openTelemetryModule.spanRepository.storeCompletedOtelSpans(listOf(Span(name = "completed-span")))
    }

    private fun createModule(deliveryModule: DeliveryModule?): UserSessionOrchestrationModuleImpl {
        val initModule = FakeInitModule()
        val workerThreadModule = FakeWorkerThreadModule(fakeInitModule = initModule)
        val configService = FakeConfigService(
            persistenceBehavior = createPersistenceBehavior(
                remoteCfg = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f),
            ),
        )
        val coreModule = FakeCoreModule()
        val essentialServiceModule = FakeEssentialServiceModule()
        return UserSessionOrchestrationModuleImpl(
            initModule = initModule,
            openTelemetryModule = openTelemetryModule,
            coreModule = coreModule,
            essentialServiceModule = essentialServiceModule,
            configService = configService,
            deliveryModule = deliveryModule,
            instrumentationModule = InstrumentationModuleImpl(
                initModule = initModule,
                openTelemetryModule = openTelemetryModule,
                workerThreadModule = workerThreadModule,
                configService = configService,
                essentialServiceModule = essentialServiceModule,
                coreModule = coreModule,
                storageService = FakeStorageService(),
                userSessionIdsProvider = { null },
                activeSessionIdsProvider = { FakeSessionIdsProvider().getActiveSessionIds() },
            ),
            payloadSourceModule = FakePayloadSourceModule(),
            startupDurationProvider = { null },
            appVersionStartupCounterProvider = { null },
            logModule = FakeLogModule(),
            workerThreadModule = workerThreadModule,
        )
    }
}
