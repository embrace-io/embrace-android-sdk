package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState.FOREGROUND
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollatorImpl
import org.junit.Before
import org.junit.Test

internal class PayloadFactoryImplTest {

    private lateinit var factory: PayloadFactoryImpl

    @Before
    fun setUp() {
        val configService = FakeConfigService()
        val initModule = FakeInitModule()
        val collator = PayloadMessageCollatorImpl(
            gatingService = FakeGatingService(),
            preferencesService = FakePreferenceService(),
            currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan,
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
        val session = checkNotNull(factory.startPayloadWithState(FOREGROUND, 0, false))
        checkNotNull(factory.endPayloadWithState(FOREGROUND, 0, session))
    }
}
