package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.hasSpanSnapshotsOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.opentelemetry.embColdStart
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a stateful session can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class SequentialSessionTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `cold start and session number are recorded correctly`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
                recordSession()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(3)
                val first = sessions[0]
                val second = sessions[1]
                val third = sessions[2]

                assertFalse(first.hasSpanSnapshotsOfType(EmbType.Ux.Session))
                first.findSessionSpan().attributes?.assertMatches(mapOf(
                    embSessionNumber.name to 1,
                    embColdStart.name to true
                ))

                assertFalse(second.hasSpanSnapshotsOfType(EmbType.Ux.Session))
                second.findSessionSpan().attributes?.assertMatches(mapOf(
                    embSessionNumber.name to 2,
                    embColdStart.name to false
                ))

                assertFalse(third.hasSpanSnapshotsOfType(EmbType.Ux.Session))
                third.findSessionSpan().attributes?.assertMatches(mapOf(
                    embSessionNumber.name to 3,
                    embColdStart.name to false
                ))
            }
        )
    }
}
