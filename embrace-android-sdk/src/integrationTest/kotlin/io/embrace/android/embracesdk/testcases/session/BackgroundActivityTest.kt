package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.assertions.hasSpanSnapshotsOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a stateful background activity can be recorded.
 */
@RunWith(AndroidJUnit4::class)
internal class BackgroundActivityTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `bg activity messages are recorded`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)),
            testCaseAction = {
                recordSession()
                clock.tick(30000)
                recordSession()
            },
            assertAction = {
                // filter out dupes from overwritten saves
                val bgActivities = getSessionEnvelopes(2, ApplicationState.BACKGROUND).distinctBy { it.getSessionId() }
                assertEquals(2, bgActivities.size)

                // verify first bg activity
                val first = bgActivities[0]
                first.findSessionSpan().attributes?.assertMatches(mapOf(
                    embSessionNumber.name to 1
                ))
                assertFalse(first.hasSpanSnapshotsOfType(EmbType.Ux.Session))

                // verify second bg activity
                val second = bgActivities[1]
                second.findSessionSpan().attributes?.assertMatches(mapOf(
                    embSessionNumber.name to 2
                ))

                // ID should be different for each
                assertNotEquals(first.getSessionId(), second.getSessionId())
            }
        )
    }
}
