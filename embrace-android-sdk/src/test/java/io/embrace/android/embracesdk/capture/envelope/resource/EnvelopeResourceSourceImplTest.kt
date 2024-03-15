package io.embrace.android.embracesdk.capture.envelope.resource

import org.junit.Assert.assertNotNull
import org.junit.Test

internal class EnvelopeResourceSourceImplTest {

    @Test
    fun `test source`() {
        val resource = EnvelopeResourceSourceImpl().getEnvelopeResource()
        assertNotNull(resource)
    }
}
