package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.config.remote.OTelRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.fakeOTelBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.session.lifecycle.ProcessState.FOREGROUND
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
        val collator = PayloadMessageCollatorImpl(
            gatingService = FakeGatingService(),
            preferencesService = FakePreferenceService(),
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
            logger = initModule.logger,
            sessionEnvelopeSource = SessionEnvelopeSourceImpl(
                FakeEnvelopeMetadataSource(),
                FakeEnvelopeResourceSource(),
                FakeSessionPayloadSource()
            )
        )
        factory = PayloadFactoryImpl(
            payloadMessageCollator = collator,
            configService = configService,
            logger = initModule.logger
        )
    }

    @Test
    fun `v2 payload generated`() {
        oTelConfig = OTelRemoteConfig(isDevEnabled = true)
        val session = checkNotNull(factory.startPayloadWithState(FOREGROUND, 0, false))
        checkNotNull(factory.endPayloadWithState(FOREGROUND, 0, session))
    }
}
