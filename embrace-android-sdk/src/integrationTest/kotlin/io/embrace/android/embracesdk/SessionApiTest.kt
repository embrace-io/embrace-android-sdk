package io.embrace.android.embracesdk

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test for the internal implementation of the Embrace SDK
 */
@RunWith(AndroidJUnit4::class)
internal class SessionApiTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `session messages are recorded`() {
        with(testRule) {
            assertTrue(harness.getSentSessionMessages().isEmpty())

            val session = harness.recordSession {
                embrace.addBreadcrumb("Hello, World!")
            }

            // perform further assertions that only apply to this individual test case.
            val crumb = checkNotNull(session.breadcrumbs?.customBreadcrumbs?.single())
            assertEquals("Hello, World!", crumb.message)
        }
    }
}
