package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_NUMBER
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_PART_NUMBER
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class UserSessionPartNumberTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `user session sequence numbers`() {
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
                assertEquals("1", us1p1?.findAttributeValue(EMB_USER_SESSION_PART_NUMBER))

                assertEquals("1", us1p2?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("2", us1p2?.findAttributeValue(EMB_USER_SESSION_PART_NUMBER))

                assertEquals("2", us2p1?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("1", us2p1?.findAttributeValue(EMB_USER_SESSION_PART_NUMBER))

                assertEquals("2", us2p2?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("2", us2p2?.findAttributeValue(EMB_USER_SESSION_PART_NUMBER))
            },
        )
    }

    @Test
    fun `load existing user session results in using existing session part number`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        val startMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 1_000L
        val lastActivityMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 100L
        testRule.runTest(
            setupAction = {
                persistUserSession(sessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs, partNumber = 2)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertEquals(persistedId, session.getUserSessionId())
                assertEquals("3", session.findSessionSpan().attributes?.findAttributeValue(EMB_USER_SESSION_PART_NUMBER))
            }
        )
    }

    @Test
    fun `load expired user session results in resetting session part number`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        val startMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 3_000_000L
        val defaultInactivityMs = 1800L * 1_000L
        val lastActivityMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - defaultInactivityMs - 1L
        testRule.runTest(
            setupAction = {
                persistUserSession(sessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs, partNumber = 5)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertNotEquals(persistedId, session.getUserSessionId())
                assertEquals("1", session.findSessionSpan().attributes?.findAttributeValue(EMB_USER_SESSION_PART_NUMBER))
            }
        )
    }
}
