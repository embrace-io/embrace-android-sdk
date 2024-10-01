package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.findSpanSnapshotsOfType
import io.embrace.android.embracesdk.getSessionId
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.opentelemetry.embErrorLogCount
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionEndType
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionStartType
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun `session messages are recorded`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.addBreadcrumb("Hello, World!")
                }

                // capture another session
                recordSession()
            },
            assertAction = {
                // verify first session
                val messages = getSentSessions(2)
                val first = messages[0]
                val attrs = checkNotNull(first.findSessionSpan().attributes)
                assertEquals(
                    LifeEventType.STATE.name.lowercase(Locale.ENGLISH), attrs.findAttributeValue(
                        embSessionStartType.name
                    )
                )
                assertEquals(
                    LifeEventType.STATE.name.lowercase(Locale.ENGLISH), attrs.findAttributeValue(
                        embSessionEndType.name
                    )
                )
                assertEquals("0", attrs.findAttributeValue(embErrorLogCount.name))
                assertEquals(0, first.findSpanSnapshotsOfType(EmbType.Ux.Session).size)

                // verify second session
                val second = messages[1]
                assertNotEquals(first.getSessionId(), second.getSessionId())
            }
        )
    }

    @Test
    fun `nested state calls`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    recordSession()
                }
            },
            assertAction = {
                val messages = testRule.assertion.getSentSessions(1)

                // TODO: future the logic seems wrong here - nested calls should probably be ignored
                //  and should not drop a session. However, it's an unlikely scenario (if we trust)
                //  Google's process lifecycle implementation.
                assertEquals(1, messages.size)
            }
        )
    }
}
