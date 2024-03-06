package io.embrace.android.embracesdk.capture.envelope.session

import org.junit.Test

internal class SessionPayloadSourceImplTest {

    @Test(expected = NotImplementedError::class)
    fun getSessionPayload() {
        SessionPayloadSourceImpl().getSessionPayload()
    }
}
