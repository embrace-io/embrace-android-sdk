package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.RobolectricTest
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.assertNoPreviousSession
import io.embrace.android.embracesdk.assertions.assertPreviousSession
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.assertions.hasSpanSnapshotsOfType
import io.embrace.android.embracesdk.internal.arch.attrs.embColdStart
import io.embrace.android.embracesdk.internal.arch.attrs.embSessionNumber
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that a stateful session can be recorded with the appropriate metadata with respect to sequencing
 */
@RunWith(AndroidJUnit4::class)
internal class SequentialSessionTest: RobolectricTest() {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `session metadata are recorded correctly when background sessions enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)),
            testCaseAction = {
                recordSession()
                recordSession()
                recordSession()
            },
            assertAction = {
                val bas = getSessionEnvelopes(expectedSize = 3, state = AppState.BACKGROUND)
                val sessions = getSessionEnvelopes(3)
                val firstBa = bas[0]
                val secondBa = bas[1]
                val thirdBa = bas[2]
                val firstSession = sessions[0]
                val secondSession = sessions[1]
                val thirdSession = sessions[2]

                val firstBaSessionSpan = firstBa.getValidatedSessionSpan(
                    sessionNumber = 1,
                )

                val firstSessionSpan = firstSession.getValidatedSessionSpan(
                    sessionNumber = 1,
                    previousSessionSpan = firstBaSessionSpan,
                    previousSessionId = firstBa.getSessionId()
                )

                val secondBaSessionSpan = secondBa.getValidatedSessionSpan(
                    sessionNumber = 2,
                    isColdStart = false,
                    previousSessionSpan = firstSessionSpan,
                    previousSessionId = firstSession.getSessionId()
                )

                val secondSessionSpan = secondSession.getValidatedSessionSpan(
                    sessionNumber = 2,
                    isColdStart = false,
                    previousSessionSpan = secondBaSessionSpan,
                    previousSessionId = secondBa.getSessionId()
                )

                val thirdBaSessionSpan = thirdBa.getValidatedSessionSpan(
                    sessionNumber = 3,
                    isColdStart = false,
                    previousSessionSpan = secondSessionSpan,
                    previousSessionId = secondSession.getSessionId()
                )

                thirdSession.getValidatedSessionSpan(
                    sessionNumber = 3,
                    isColdStart = false,
                    previousSessionSpan = thirdBaSessionSpan,
                    previousSessionId = thirdBa.getSessionId()
                )
            }
        )
    }

    @Test
    fun `session metadata are recorded correctly when background sessions disabled`() {
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

                val firstSessionSpan = first.getValidatedSessionSpan(
                    sessionNumber = 1
                )

                val secondSessionSpan = second.getValidatedSessionSpan(
                    sessionNumber = 2,
                    isColdStart = false,
                    previousSessionSpan = firstSessionSpan,
                    previousSessionId = first.getSessionId()
                )

                third.getValidatedSessionSpan(
                    sessionNumber = 3,
                    isColdStart = false,
                    previousSessionSpan = secondSessionSpan,
                    previousSessionId = second.getSessionId()
                )
            }
        )
    }

    private fun Envelope<SessionPayload>.getValidatedSessionSpan(
        sessionNumber: Long,
        isColdStart: Boolean = true,
        previousSessionSpan: Span? = null,
        previousSessionId: String? = null,
    ): Span {
        val sessionSpan = findSessionSpan()
        assertFalse(hasSpanSnapshotsOfType(EmbType.Ux.Session))
        with(sessionSpan) {
            checkNotNull(attributes).assertMatches(
                mapOf(
                    embSessionNumber.name to sessionNumber,
                    embColdStart.name to isColdStart
                )
            )

            if (previousSessionSpan != null && previousSessionId != null) {
                assertPreviousSession(previousSessionSpan, previousSessionId)
            } else {
                assertNoPreviousSession()
            }
        }
        return sessionSpan
    }
}
