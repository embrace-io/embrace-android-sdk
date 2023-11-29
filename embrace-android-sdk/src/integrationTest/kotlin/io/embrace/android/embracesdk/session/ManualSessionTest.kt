package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.getSentSessionMessages
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
internal class ManualSessionTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Before
    fun setUp() {
        assertTrue(testRule.harness.getSentSessionMessages().isEmpty())
    }

    @Test
    fun `manual session ends stateful session`() {
        with(testRule) {
            harness.recordSession {
                embrace.endSession()
            }
            val messages = harness.getSentSessionMessages()

            // FIXME (future): ending a session manually drops the session end event of a
            //  stateful session.
            assertEquals(3, messages.size)
            val second = messages[2]
            verifySessionHappened(messages[1], second)
            assertEquals(2, second.session.number)
        }
    }
}
