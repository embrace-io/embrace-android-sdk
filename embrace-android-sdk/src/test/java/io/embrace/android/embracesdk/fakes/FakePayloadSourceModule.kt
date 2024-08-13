package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.envelope.session.SessionPayloadSource
import io.embrace.android.embracesdk.internal.injection.PayloadSourceModule

internal class FakePayloadSourceModule(
    sessionPayloadSource: SessionPayloadSource = FakeSessionPayloadSource(),
    logPayloadSource: LogPayloadSource = FakeLogPayloadSource()
) : PayloadSourceModule {

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
