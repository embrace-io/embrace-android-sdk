package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.message.FinalEnvelopeParams
import io.embrace.android.embracesdk.session.message.InitialEnvelopeParams
import io.embrace.android.embracesdk.session.message.PayloadMessageCollator

internal class FakePayloadCollator : PayloadMessageCollator {
    override fun buildInitialSession(params: InitialEnvelopeParams): Session {
        TODO("Not yet implemented")
    }

    override fun buildFinalSessionMessage(params: FinalEnvelopeParams.SessionParams): SessionMessage {
        TODO("Not yet implemented")
    }

    override fun buildFinalBackgroundActivityMessage(params: FinalEnvelopeParams.BackgroundActivityParams): SessionMessage {
        TODO("Not yet implemented")
    }
}
