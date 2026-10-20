package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeOtelPayloadMapper
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.internal.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PayloadSourceFactoriesTest {

    @Test
    fun `factory default values`() {
        val initModule = FakeInitModule(otelSdkMode = OtelSdkMode.COMPAT)
        val coreModule = FakeCoreModule()
        val configService = FakeConfigService()
        val otelModule = FakeOpenTelemetryModule(otelSdkMode = OtelSdkMode.REGULAR)
        val deliveryModule = FakeDeliveryModule()
        val worker = FakeWorkerThreadPool().backgroundWorker(Worker.Background.NonIoRegWorker)
        val envelopeMetadataSource = FakeEnvelopeMetadataSource()

        val resourceSource = createEnvelopeResourceSource(
            context = coreModule.context,
            configService = configService,
            worker = worker,
            systemInfo = initModule.systemInfo,
            errorHandler = initModule.errorHandler,
            hostedSdkVersionInfo = createHostedSdkVersionInfo(AppFramework.NATIVE, coreModule.store),
            rnBundleIdTracker = lazy { createRnBundleIdTracker(coreModule.context, configService, coreModule.store, worker) },
            useKotlinSdkProvider = { false },
        )
        val metadataService = createMetadataService(
            resourceSource,
            coreModule.context,
            configService,
            coreModule.store,
            initModule.clock,
            worker,
        )
        assertTrue(metadataService is EmbraceMetadataService)
        assertNotNull(
            createSessionPartEnvelopeSource(
                symbolMap = null,
                currentSessionPartSpan = otelModule.currentSessionPartSpan,
                spanRepository = otelModule.spanRepository,
                otelPayloadMapper = FakeOtelPayloadMapper(),
                processStateTracker = FakeEssentialServiceModule().processStateTracker,
                clock = initModule.clock,
                errorHandler = initModule.errorHandler,
                envelopeMetadataSource = envelopeMetadataSource,
                resourceSource = resourceSource,
            ),
        )
        assertNotNull(
            createLogEnvelopeSource(
                otelModule.logSink,
                envelopeMetadataSource,
                resourceSource,
                deliveryModule.cachedLogEnvelopeStore,
            ),
        )
        assertNotNull(
            createPayloadResurrectionService(
                deliveryModule.intakeService,
                deliveryModule.payloadStorageService,
                deliveryModule.cacheStorageService,
                deliveryModule.cachedLogEnvelopeStore,
                initModule.errorHandler,
                initModule.jsonSerializer,
            ),
        )
    }
}
