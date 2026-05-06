package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.assertions.assertSessionIds
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.toMap
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_NUMBER
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_PART_NUMBER
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_START_TS
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts user session ID keys added to spans and logs.
 *
 * Test cases should assert both on the HTTP request sent to Embrace's servers and the OTLP request.
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionIdKeysTest {

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
        assertEquals("1", attrs[EMB_USER_SESSION_PART_NUMBER])
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
        assertNull(attrs[EMB_USER_SESSION_PART_NUMBER])
        assertNull(attrs[EMB_USER_SESSION_START_TS])
        assertNull(attrs[EMB_USER_SESSION_MAX_DURATION_SECONDS])
        assertNull(attrs[EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS])
        assertNull(attrs[EMB_USER_SESSION_TERMINATION_REASON])
    }
}
