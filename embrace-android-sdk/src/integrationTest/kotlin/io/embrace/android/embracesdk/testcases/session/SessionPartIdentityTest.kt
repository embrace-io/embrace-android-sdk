package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.assertions.assertSessionIds
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.assertions.toMap
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_SESSION_PART_NUMBER
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_NUMBER
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_PART_INDEX
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_START_TS
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SESSION_COUNT = 200

/**
 * Asserts the identity attributes on session parts: which user-session keys appear on spans vs logs,
 * the user-session/part numbering, the per-session part index, and part-id uniqueness.
 *
 * Test cases should assert both on the HTTP request sent to Embrace's servers and the OTLP request.
 */
@RunWith(AndroidJUnit4::class)
internal class SessionPartIdentityTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `session span and envelope contains various user session attributes`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val attrMap = checkNotNull(getSingleSessionEnvelope().findSessionSpan().attributes?.toMap())
                assertFirstSessionSpanAttributes(attrMap)
            },
            otelExportAssertion = {
                assertFirstSessionSpanAttributes(awaitSpansWithType(1, EmbType.Ux.Session).single().attributes.toStringMap())
            },
        )
    }

    private fun assertFirstSessionSpanAttributes(attrs: Map<String, String>) {
        attrs.assertSessionIds()
        assertEquals("1", attrs[EMB_USER_SESSION_NUMBER])
        assertEquals("1", attrs[EMB_USER_SESSION_PART_INDEX])
        assertFalse(attrs[EMB_USER_SESSION_START_TS].isNullOrBlank())
        assertFalse(attrs[EMB_USER_SESSION_MAX_DURATION_SECONDS].isNullOrBlank())
        assertFalse(attrs[EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS].isNullOrBlank())
    }

    @Test
    fun `log does not contain user session attributes apart from user session ID and session part iD`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logMessage("test", Severity.INFO)
                }
            },
            assertAction = {
                val attrMap = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.Log).attributes?.toMap())
                assertLogSessionAttributes(attrMap)
            },
            otelExportAssertion = {
                val log = awaitLogs(1) { it.attributes.toStringMap().containsKey(EmbType.System.Log.key) }.single()
                assertLogSessionAttributes(log.attributes.toStringMap())
            },
        )
    }

    private fun assertLogSessionAttributes(attrs: Map<String, String?>) {
        attrs.assertSessionIds()
        assertNull(attrs[EMB_USER_SESSION_NUMBER])
        assertNull(attrs[EMB_USER_SESSION_PART_INDEX])
        assertNull(attrs[EMB_USER_SESSION_START_TS])
        assertNull(attrs[EMB_USER_SESSION_MAX_DURATION_SECONDS])
        assertNull(attrs[EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS])
        assertNull(attrs[EMB_USER_SESSION_TERMINATION_REASON])
    }

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
                assertEquals("1", us1p1?.findAttributeValue(EMB_USER_SESSION_PART_INDEX))

                assertEquals("1", us1p2?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("2", us1p2?.findAttributeValue(EMB_USER_SESSION_PART_INDEX))

                assertEquals("2", us2p1?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("1", us2p1?.findAttributeValue(EMB_USER_SESSION_PART_INDEX))

                assertEquals("2", us2p2?.findAttributeValue(EMB_USER_SESSION_NUMBER))
                assertEquals("2", us2p2?.findAttributeValue(EMB_USER_SESSION_PART_INDEX))
            },
        )
    }

    @Test
    fun `load existing user session results in using existing session part index`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        val startMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 1_000L
        val lastActivityMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 100L
        testRule.runTest(
            setupAction = {
                persistUserSession(userSessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs, partIndex = 2)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertEquals(persistedId, session.getUserSessionId())
                assertEquals("3", session.findSessionSpan().attributes?.findAttributeValue(EMB_USER_SESSION_PART_INDEX))
            }
        )
    }

    @Test
    fun `load expired user session results in resetting session part index`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        val startMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - 3_000_000L
        val defaultInactivityMs = 1800L * 1_000L
        val lastActivityMs = SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS - defaultInactivityMs - 1L
        testRule.runTest(
            setupAction = {
                persistUserSession(userSessionId = persistedId, startMs = startMs, lastActivityMs = lastActivityMs, partIndex = 5)
            },
            testCaseAction = { recordSession() },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertNotEquals(persistedId, session.getUserSessionId())
                assertEquals("1", session.findSessionSpan().attributes?.findAttributeValue(EMB_USER_SESSION_PART_INDEX))
            }
        )
    }

    @Test
    fun `many sequential sessions each get a distinct foreground and background part id`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = true)),
            testCaseAction = {
                repeat(SESSION_COUNT) {
                    recordSession {
                        embrace.addBreadcrumb("Hello, World!")
                    }
                }
            },
            assertAction = {
                val messages = getSessionEnvelopes(SESSION_COUNT, waitTimeMs = 10000)
                val ids = messages.map { it.getSessionPartId() }.toSet()
                assertEquals(SESSION_COUNT, ids.size)

                val bas = getSessionEnvelopes(SESSION_COUNT, AppState.BACKGROUND, waitTimeMs = 10000)
                val baIds = bas.map { it.getSessionPartId() }.toSet()
                assertEquals(SESSION_COUNT, baIds.size)
            }
        )
    }
}
