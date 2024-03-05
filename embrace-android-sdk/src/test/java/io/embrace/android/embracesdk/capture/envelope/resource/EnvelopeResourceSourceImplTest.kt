package io.embrace.android.embracesdk.capture.envelope.resource

import org.junit.Test

internal class EnvelopeResourceSourceImplTest {

    @Test(expected = NotImplementedError::class)
    fun `test source`() {
        EnvelopeResourceSourceImpl().getEnvelopeResource()
    }
}
