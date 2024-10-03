package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SessionSpanTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `there is always a valid session when background activity is enabled`() {
        val ids = mutableListOf<String?>()

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    ids.add(embrace.currentSessionId)
                }
                ids.add(embrace.currentSessionId)
                recordSession {
                    ids.add(embrace.currentSessionId)
                }
            },
            assertAction = {
                ids.forEach {
                    assertFalse(it.isNullOrBlank())
                }
            }
        )
    }

    @Test
    fun `session span event limits do not affect logging maximum breadcrumbs`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    repeat(101) {
                        embrace.addBreadcrumb("breadcrumb $it")
                    }
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertEquals(100, session.getSessionSpan()?.events?.size)
            }
        )
    }
}
