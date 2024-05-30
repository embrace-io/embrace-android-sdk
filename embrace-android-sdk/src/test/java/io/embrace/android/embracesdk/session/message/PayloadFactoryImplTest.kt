package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.anr.AnrOtelMapper
import io.embrace.android.embracesdk.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.config.remote.OTelRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.fakes.fakeOTelBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.payload.LegacyExceptionError
import io.embrace.android.embracesdk.payload.isV2Payload
import io.embrace.android.embracesdk.session.lifecycle.ProcessState.FOREGROUND
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class PayloadFactoryImplTest {

    private var oTelConfig = OTelRemoteConfig()
    private lateinit var factory: PayloadFactoryImpl

    @Before
    fun setUp() {
        val configService = FakeConfigService(
            oTelBehavior = fakeOTelBehavior(
                remoteCfg = {
                    RemoteConfig(oTelConfig = oTelConfig)
                }
            )
        )
        val initModule = FakeInitModule()
        val v1Collator = V1PayloadMessageCollator(
            gatingService = FakeGatingService(),
            nativeThreadSamplerService = null,
            webViewService = FakeWebViewService(),
            preferencesService = FakePreferenceService(),
            eventService = FakeEventService(),
            logMessageService = FakeLogMessageService(),
            internalErrorService = FakeInternalErrorService().apply {
                data = LegacyExceptionError()
            },
            spanRepository = initModule.openTelemetryModule.spanRepository,
            spanSink = initModule.openTelemetryModule.spanSink,
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            sessionPropertiesService = FakeSessionPropertiesService(),
            startupService = FakeStartupService(),
            anrOtelMapper = AnrOtelMapper(FakeAnrService()),
            nativeAnrOtelMapper = NativeAnrOtelMapper(null, EmbraceSerializer()),
            logger = initModule.logger
        )
        val v2Collator = V2PayloadMessageCollator(
            gatingService = FakeGatingService(),
            nativeThreadSamplerService = null,
            preferencesService = FakePreferenceService(),
            eventService = FakeEventService(),
            logMessageService = FakeLogMessageService(),
            spanSink = initModule.openTelemetryModule.spanSink,
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            sessionPropertiesService = FakeSessionPropertiesService(),
            startupService = FakeStartupService(),
            anrOtelMapper = AnrOtelMapper(FakeAnrService()),
            nativeAnrOtelMapper = NativeAnrOtelMapper(null, EmbraceSerializer()),
            logger = initModule.logger,
            sessionEnvelopeSource = SessionEnvelopeSourceImpl(
                FakeEnvelopeMetadataSource(),
                FakeEnvelopeResourceSource(),
                FakeSessionPayloadSource()
            )
        )
        factory = PayloadFactoryImpl(
            v1payloadMessageCollator = v1Collator,
            v2payloadMessageCollator = v2Collator,
            configService = configService,
            logger = initModule.logger
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
        oTelConfig = OTelRemoteConfig(isDevEnabled = true)
        val session = checkNotNull(factory.startPayloadWithState(FOREGROUND, 0, false))
        val sessionMessage = checkNotNull(factory.endPayloadWithState(FOREGROUND, 0, session)).copy(data = SessionPayload(emptyList()))
        assertTrue(sessionMessage.isV2Payload())
    }
}
