package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceInternalInterfaceDefaultsTest {

    private val calls = mutableListOf<String>()

    /**
     * Implements everything an older hosted SDK's own implementation did, and not `reportState`, so it only compiles while that
     * method has a default body.
     */
    private val olderImplementation = object : EmbraceInternalInterface {
        override fun isNetworkSpanForwardingEnabled(): Boolean = false.also { calls.add("isNetworkSpanForwardingEnabled") }
        override fun addEnvelopeResource(key: String, value: String) {
            calls.add("addEnvelopeResource")
        }
        override fun setHostedSdkVersion(version: String?) {
            calls.add("setHostedSdkVersion")
        }
        override fun setHostedPlatformVersion(version: String?) {
            calls.add("setHostedPlatformVersion")
        }
    }

    @Test
    fun `an implementation that does not override reportState ignores a report`() {
        olderImplementation.reportState("screen-automatic", "Home", 1_000L)

        assertEquals(emptyList<String>(), calls)
    }
}
