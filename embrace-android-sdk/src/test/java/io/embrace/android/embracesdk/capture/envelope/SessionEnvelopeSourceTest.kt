package io.embrace.android.embracesdk.capture.envelope

import io.embrace.android.embracesdk.capture.envelope.session.SessionPayloadSourceImpl
import org.junit.Test

internal class SessionEnvelopeSourceTest {

    @Test(expected = NotImplementedError::class)
    fun getEnvelope() {
        SessionEnvelopeSource(SessionPayloadSourceImpl()).getEnvelope()
    }
}
