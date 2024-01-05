package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.SessionLifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.verifySessionHappened
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a stateful session can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class StatefulSessionTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Before
    fun setUp() {
        assertTrue(testRule.harness.getSentSessionMessages().isEmpty())
    }

    @Test
    fun `session messages are recorded`() {
        with(testRule) {
            harness.recordSession {
                embrace.addBreadcrumb("Hello, World!")
            }

            // capture another session & verify it's a new session.
            harness.recordSession { }

            // verify first session
            val messages = testRule.harness.getSentSessionMessages()
            val first = messages[0]
            verifySessionHappened(first)
            assertEquals(SessionLifeEventType.STATE, first.session.startType)
            assertEquals(SessionLifeEventType.STATE, first.session.endType)
            val crumb = checkNotNull(first.breadcrumbs?.customBreadcrumbs?.single())
            assertEquals("Hello, World!", crumb.message)

            // verify second session
            val second = messages[1]
            verifySessionHappened(second)
            assertNotEquals(first.session.sessionId, second.session.sessionId)
            assertEquals(true, second.breadcrumbs?.customBreadcrumbs?.isEmpty())
        }
    }

    @Test
    fun `nested state calls`() {
        with(testRule) {
            harness.recordSession {
                harness.recordSession()
            }
            val messages = testRule.harness.getSentSessionMessages()

            // TODO: future the logic seems wrong here - nested calls should probably be ignored
            //  and should not drop a session. However, it's an unlikely scenario (if we trust)
            //  Google's process lifecycle implementation.
            assertEquals(1, messages.size)
        }
    }
}
