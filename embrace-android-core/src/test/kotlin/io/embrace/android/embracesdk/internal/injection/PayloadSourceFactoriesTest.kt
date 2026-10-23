package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeCachedLogEnvelopeStore
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionPartSpan
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeIntakeService
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeOtelPayloadMapper
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeApplication
import io.embrace.android.embracesdk.fakes.fakeContext
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.internal.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PayloadSourceFactoriesTest {

    @Test
    fun `factory default values`() {
        val initServices = TestInitServices(otelSdkMode = OtelSdkMode.COMPAT)
        val application = fakeApplication()
        val context = fakeContext(application)
        val store = FakeKeyValueStore()
        val configService = FakeConfigService()
        val spanRepository = SpanRepository(FakeErrorHandler())
        val cachedLogEnvelopeStore = FakeCachedLogEnvelopeStore()
        val worker = FakeWorkerThreadPool().backgroundWorker(Worker.Background.NonIoRegWorker)
        val envelopeMetadataSource = FakeEnvelopeMetadataSource()

        val resourceSource = createEnvelopeResourceSource(
            context = context,
            configService = configService,
            worker = worker,
            systemInfo = initServices.systemInfo,
            errorHandler = initServices.errorHandler,
            hostedSdkVersionInfo = createHostedSdkVersionInfo(AppFramework.NATIVE, store),
            rnBundleIdTracker = lazy { createRnBundleIdTracker(context, configService, store, worker) },
            useKotlinSdkProvider = { false },
        )
        val metadataService = createMetadataService(
            resourceSource,
            context,
            configService,
            store,
            initServices.clock,
            worker,
        )
        assertTrue(metadataService is EmbraceMetadataService)
        assertNotNull(
            createSessionPartEnvelopeSource(
                symbolMap = null,
                currentSessionPartSpan = FakeCurrentSessionPartSpan(),
                spanRepository = spanRepository,
                otelPayloadMapper = FakeOtelPayloadMapper(),
                processStateTracker = FakeProcessStateTracker(),
                clock = initServices.clock,
                errorHandler = initServices.errorHandler,
                envelopeMetadataSource = envelopeMetadataSource,
                resourceSource = resourceSource,
            ),
        )
        assertNotNull(
            createLogEnvelopeSource(
                LogSinkImpl(),
                envelopeMetadataSource,
                resourceSource,
                cachedLogEnvelopeStore,
            ),
        )
        assertNotNull(
            createPayloadResurrectionService(
                FakeIntakeService(),
                FakePayloadStorageService(),
                FakePayloadStorageService(),
                cachedLogEnvelopeStore,
                initServices.errorHandler,
                initServices.jsonSerializer,
            ),
        )
    }
}
