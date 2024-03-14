package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.capture.envelope.SessionEnvelopeSource
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePerformanceInfoService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.fakes.FakeThermalStatusService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.payload.LegacyExceptionError
import io.embrace.android.embracesdk.payload.isV2Payload
import io.embrace.android.embracesdk.session.lifecycle.ProcessState.FOREGROUND
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PayloadFactoryImplTest {

    private var sessionConfig = SessionRemoteConfig()
    private lateinit var factory: PayloadFactoryImpl

    @Before
    fun setUp() {
        val configService = FakeConfigService(
            sessionBehavior = fakeSessionBehavior(
                remoteCfg = {
                    RemoteConfig(sessionConfig = sessionConfig)
                }
            )
        )
        val initModule = FakeInitModule()
        val v1Collator = V1PayloadMessageCollator(
            configService = FakeConfigService(),
            nativeThreadSamplerService = null,
            thermalStatusService = FakeThermalStatusService(),
            webViewService = FakeWebViewService(),
            userService = FakeUserService(),
            preferencesService = FakePreferenceService(),
            eventService = FakeEventService(),
            logMessageService = FakeLogMessageService(),
            internalErrorService = FakeInternalErrorService().apply {
                currentExceptionError = LegacyExceptionError()
            },
            breadcrumbService = FakeBreadcrumbService(),
            metadataService = FakeMetadataService(),
            performanceInfoService = FakePerformanceInfoService(),
            spanSink = initModule.openTelemetryModule.spanSink,
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            sessionPropertiesService = FakeSessionPropertiesService(),
            startupService = FakeStartupService()
        )
        val v2Collator = V2PayloadMessageCollator(
            v1Collator,
            SessionEnvelopeSource(
                FakeEnvelopeMetadataSource(),
                FakeEnvelopeResourceSource(),
                FakeSessionPayloadSource()
            )
        )
        factory = PayloadFactoryImpl(
            v1payloadMessageCollator = v1Collator,
            v2payloadMessageCollator = v2Collator,
            configService = configService
        )
    }

    @Test
    fun `legacy payload generated`() {
        val session = checkNotNull(factory.startPayloadWithState(FOREGROUND, 0, false))
        val sessionMessage = checkNotNull(factory.endPayloadWithState(FOREGROUND, 0, session))
        assertFalse(sessionMessage.isV2Payload())
    }

    @Test
    fun `v2 payload generated`() {
        sessionConfig = SessionRemoteConfig(useV2Payload = true)
        val session = checkNotNull(factory.startPayloadWithState(FOREGROUND, 0, false))
        val sessionMessage = checkNotNull(factory.endPayloadWithState(FOREGROUND, 0, session))
        assertTrue(sessionMessage.isV2Payload())
    }
}
