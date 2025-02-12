package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    /**
     * Sets some data on the first session, and asserts that it is not present on the second
     * session.
     *
     * This test case is not exhaustive - it's meant to be a canary in the coalmine that
     * warns if no data at all is being cleared between sessions.
     */
    @Test
    fun `session messages have a clean boundary`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.addSessionProperty("foo", "bar", false)
                }
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)

                // validate info added to first session
                val span = sessions[0].findSessionSpan()
                assertEquals("bar", span.getSessionProperty("foo"))

                // confirm info not added to next session
                val nextSpan = sessions[1].findSessionSpan()
                assertNull(nextSpan.getSessionProperty("foo"))
            }
        )
    }
}
