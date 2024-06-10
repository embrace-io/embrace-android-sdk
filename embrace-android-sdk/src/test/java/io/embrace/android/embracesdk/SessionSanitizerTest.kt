package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.gating.SessionSanitizer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class SessionSanitizerTest {

    private val session = fakeSession().copy(
        terminationTime = 100L
    )

    @Test
    fun `test if it keeps all session info`() {
        // enabled components contains everything about session
        val components = setOf(
            SessionGatingKeys.SESSION_PROPERTIES,
            SessionGatingKeys.SESSION_ORIENTATIONS,
            SessionGatingKeys.SESSION_USER_TERMINATION,
            SessionGatingKeys.SESSION_MOMENTS,
            SessionGatingKeys.LOGS_INFO,
            SessionGatingKeys.LOGS_WARN,
            SessionGatingKeys.STARTUP_MOMENT
        )

        val result = SessionSanitizer(session, components).sanitize()

        assertNotNull(result.terminationTime)
    }

    @Test
    fun `test if it sanitizes session info`() {
        val components = setOf<String>()

        val result = SessionSanitizer(session, components).sanitize()

        assertNull(result.terminationTime)
    }
}
