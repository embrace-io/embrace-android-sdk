package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.hasSpanSnapshotsOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.assertions.assertMatches
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a stateful session can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class StatefulSessionPartTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

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
                val messages = getSessionEnvelopes(2)
                val first = messages[0]
                first.findSessionSpan().attributes?.assertMatches(mapOf(
                    EmbSessionAttributes.EMB_SESSION_START_TYPE to LifeEventType.STATE.name.lowercase(Locale.ENGLISH),
                    EmbSessionAttributes.EMB_SESSION_END_TYPE to LifeEventType.STATE.name.lowercase(Locale.ENGLISH),
                    EmbSessionAttributes.EMB_ERROR_LOG_COUNT to 0
                ))

                assertFalse(first.hasSpanSnapshotsOfType(EmbType.Ux.Session))

                // verify second session
                val second = messages[1]
                assertNotEquals(first.getSessionPartId(), second.getSessionPartId())
            }
        )
    }
}
