package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.SessionLifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.verifySessionHappened
import io.embrace.android.embracesdk.verifySessionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that data is cleared between session boundaries.
 */
@RunWith(AndroidJUnit4::class)
internal class CleanSessionBoundaryTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    /**
     * Sets some data on the first session, and asserts that it is not present on the second
     * session.
     *
     * This test case is not exhaustive - it's meant to be a canary in the coalmine that
     * warns if no data at all is being cleared between sessions.
     */
    @Test
    fun `session messages have a clean boundary`() {
        with(testRule) {
            val message = harness.recordSession {
                embrace.addBreadcrumb("Hello, World!")
                embrace.addSessionProperty("foo", "bar", false)
            }
            checkNotNull(message)

            // validate info added to first session
            val crumbs = checkNotNull(message.breadcrumbs?.customBreadcrumbs)
            assertEquals("Hello, World!", crumbs.single().message)
            assertEquals(mapOf("foo" to "bar"), message.session.properties)

            // confirm info not added to next session
            val nextMessage = checkNotNull(harness.recordSession())
            assertEquals(0, nextMessage.breadcrumbs?.customBreadcrumbs?.size)
            assertEquals(emptyMap<String, String>(), nextMessage.session.properties)
        }
    }
}
