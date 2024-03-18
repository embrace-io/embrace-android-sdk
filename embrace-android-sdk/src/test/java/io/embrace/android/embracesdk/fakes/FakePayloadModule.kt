package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSourceImpl
import io.embrace.android.embracesdk.injection.PayloadModule

internal class FakePayloadModule : PayloadModule {

    override val sessionEnvelopeSource: SessionEnvelopeSource = SessionEnvelopeSourceImpl(
        FakeEnvelopeMetadataSource(),
        FakeEnvelopeResourceSource(),
        FakeSessionPayloadSource()
    )

    override val logEnvelopeSource: LogEnvelopeSource
        get() = TODO("Not yet implemented")
}
