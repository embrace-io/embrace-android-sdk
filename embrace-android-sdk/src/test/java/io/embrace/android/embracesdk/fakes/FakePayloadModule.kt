package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.capture.envelope.log.LogEnvelopeSourceImpl
import io.embrace.android.embracesdk.capture.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.capture.envelope.session.SessionPayloadSource
import io.embrace.android.embracesdk.injection.PayloadModule

internal class FakePayloadModule(
    sessionPayloadSource: SessionPayloadSource = FakeSessionPayloadSource(),
    logPayloadSource: LogPayloadSource = FakeLogPayloadSource()
) : PayloadModule {

    override val sessionEnvelopeSource: SessionEnvelopeSource = SessionEnvelopeSourceImpl(
        FakeEnvelopeMetadataSource(),
        FakeEnvelopeResourceSource(),
        sessionPayloadSource
    )

    override val logEnvelopeSource: LogEnvelopeSource = LogEnvelopeSourceImpl(
        FakeEnvelopeMetadataSource(),
        FakeEnvelopeResourceSource(),
        logPayloadSource
    )
}
