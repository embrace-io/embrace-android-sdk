package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_SESSION_PART_NUMBER
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_NUMBER
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that `emb.session_part_number` is monotonic across user sessions and across SDK install
 * lifetime (seeded from `emb.session_number` on first creation).
 */
@RunWith(AndroidJUnit4::class)
internal class SessionPartNumberTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `session_part_number seeds from session_number and increments monotonically across user sessions`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
                recordSession {
                    clock.tick(10_000)
                    embrace.endUserSession()
                }
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(4)
                val us1p1 = sessions[0].findSessionSpan().attributes
                val us1p2 = sessions[1].findSessionSpan().attributes
                val us2p1 = sessions[2].findSessionSpan().attributes
                val us2p2 = sessions[3].findSessionSpan().attributes

                assertEquals("1", us1p1?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("1", us1p1?.findAttributeValue(EMB_SESSION_PART_NUMBER))

                assertEquals("1", us1p2?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("2", us1p2?.findAttributeValue(EMB_SESSION_PART_NUMBER))

                assertEquals("2", us2p1?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("3", us2p1?.findAttributeValue(EMB_SESSION_PART_NUMBER))

                assertEquals("2", us2p2?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("4", us2p2?.findAttributeValue(EMB_SESSION_PART_NUMBER))
            },
        )
    }

    @Test
    fun `session_part_number seeds from existing user session number on upgrade`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        val startMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 1_000L
        val lastActivityMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 100L
        testRule.runTest(
            setupAction = {
                persistUserSession(
                    userSessionId = persistedId,
                    startMs = startMs,
                    lastActivityMs = lastActivityMs,
                    sessionNumber = 7,
                    partIndex = 2,
                )
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val attrs = session.findSessionSpan().attributes
                assertEquals("7", attrs?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("7", attrs?.findAttributeValue(EMB_SESSION_PART_NUMBER))
            }
        )
    }
}
