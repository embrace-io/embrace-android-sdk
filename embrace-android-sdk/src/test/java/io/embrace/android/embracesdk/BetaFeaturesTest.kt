package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.payload.BetaFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class BetaFeaturesTest {

    @Test
    fun `test beta features not included by default`() {
        assertNull(fakeSession().betaFeatures)
    }

    @Test
    fun `test beta features settable via builder`() {
        val be = BetaFeatures()
        val msg = fakeSession().copy(betaFeatures = BetaFeatures())
        assertEquals(be, msg.betaFeatures)
    }
}
