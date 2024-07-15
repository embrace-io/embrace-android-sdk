package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.injection.PayloadModule
import io.embrace.android.embracesdk.internal.capture.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.capture.envelope.log.LogEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.capture.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.internal.capture.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.capture.envelope.session.SessionPayloadSource

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
