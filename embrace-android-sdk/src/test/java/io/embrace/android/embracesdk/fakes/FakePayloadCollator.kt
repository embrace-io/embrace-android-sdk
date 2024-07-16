package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.message.FinalEnvelopeParams
import io.embrace.android.embracesdk.internal.session.message.InitialEnvelopeParams
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollator
import io.embrace.android.embracesdk.payload.SessionZygote

internal class FakePayloadCollator : PayloadMessageCollator {
    override fun buildInitialSession(params: InitialEnvelopeParams): SessionZygote {
        TODO("Not yet implemented")
    }

    override fun buildFinalEnvelope(params: FinalEnvelopeParams): Envelope<SessionPayload> {
        TODO("Not yet implemented")
    }
}
