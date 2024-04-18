package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSession
import org.junit.Test

/**
 * Verifies the stacktrace sample is included in the session JSON when set
 */
internal class SessionStacktraceSampleJsonTest {

    @Test
    fun testSymbolSerialization() {
        val session = fakeSession().copy(symbols = mapOf("foo" to "bar"))
        assertJsonMatchesGoldenFile("session_stacktrace_symbols.json", session)
    }
}
