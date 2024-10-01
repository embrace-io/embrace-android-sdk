package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findEventOfType
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class BreadcrumbFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `custom breadcrumb feature`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.addBreadcrumb("Hello, world!")
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                message.assertBreadcrumbWithMessage("Hello, world!")
                testRule.action.embrace.addBreadcrumb("Bye, world!")
                testRule.action.recordSession {
                    val bas = getSessionEnvelopes(2, ApplicationState.BACKGROUND)
                    bas.last().assertBreadcrumbWithMessage("Bye, world!")
                }
            }
        )
    }

    private fun Envelope<SessionPayload>.assertBreadcrumbWithMessage(message: String) {
        findSessionSpan().findEventOfType(EmbType.System.Breadcrumb).attributes?.assertMatches {
            "message" to message
        }
    }
}
