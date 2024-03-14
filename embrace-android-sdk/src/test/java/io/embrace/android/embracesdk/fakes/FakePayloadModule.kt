package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.envelope.LogEnvelopeSource
import io.embrace.android.embracesdk.capture.envelope.SessionEnvelopeSource
import io.embrace.android.embracesdk.injection.PayloadModule

internal class FakePayloadModule : PayloadModule {

    override val sessionEnvelopeSource: SessionEnvelopeSource = SessionEnvelopeSource(
        FakeEnvelopeMetadataSource(),
        FakeEnvelopeResourceSource(),
        FakeSessionPayloadSource()
    )

    override val logEnvelopeSource: LogEnvelopeSource
        get() = TODO("Not yet implemented")
}
