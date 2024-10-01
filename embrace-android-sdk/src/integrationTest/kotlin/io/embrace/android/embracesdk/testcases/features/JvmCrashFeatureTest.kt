package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashId
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class JvmCrashFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    private val testException = RuntimeException("Boom!")
    private val serializer = EmbraceSerializer()

    @Test
    fun `app crash generates an OTel Log and matches the crashId in the session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    handleException()

                }
            },
            assertAction = {
                checkNotNull(getStoredLogPayloads(1).getLastLog()).assertCrash(
                    state = "foreground",
                    crashId = checkNotNull(getSingleSession().getCrashedId())
                )
            }
        )
    }

    @Test
    fun `app crash in the background generates a crash log`() {
        testRule.runTest(
            testCaseAction = {
                handleException()
            },
            assertAction = {
                checkNotNull(getStoredLogPayloads(1).getLastLog()).assertCrash(
                    crashId = getSentBackgroundActivities(1).single().getCrashedId()
                )
            }
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `React Native crash generates an OTel Log and matches the crashId in the session`() {

        testRule.runTest(
            testCaseAction = {
                startSdk(appFramework = Embrace.AppFramework.REACT_NATIVE)
                recordSession {
                    embrace.reactNativeInternalInterface?.logUnhandledJsException(
                        "name",
                        "message",
                        "type",
                        "stacktrace"
                    )
                    val handler = checkNotNull(Thread.getDefaultUncaughtExceptionHandler())
                    handler.uncaughtException(Thread.currentThread(), testException)
                }
            },
            assertAction = {
                val log = getStoredLogPayloads(1).getLastLog()
                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "",
                    expectedSeverityNumber = Severity.ERROR.severityNumber,
                    expectedSeverityText = Severity.ERROR.name,
                    expectedExceptionName = testException.javaClass.canonicalName,
                    expectedExceptionMessage = checkNotNull(testException.message),
                    expectedStacktrace = testException.getSafeStackTrace()?.toList(),
                    expectedProperties = emptyMap(),
                    expectedEmbType = "sys.android.react_native_crash",
                    expectedState = "foreground"
                )
                val exceptionInfo = LegacyExceptionInfo.ofThrowable(testException)
                val expectedExceptionCause = serializer.toJson(listOf(exceptionInfo), List::class.java)
                val expectedJsException = "{\"n\":\"name\",\"m\":\"message\",\"t\":\"type\",\"st\":\"stacktrace\"}"
                val attrs = checkNotNull(log.attributes)
                assertEquals(expectedJsException, attrs.findAttributeValue("emb.android.react_native_crash.js_exception"))
                assertEquals("1", attrs.findAttributeValue("emb.android.crash_number"))
                assertEquals(expectedExceptionCause, attrs.findAttributeValue("emb.android.crash.exception_cause"))
                assertNotNull(attrs.findAttributeValue("emb.android.threads"))

                val message = getSingleSession()
                val crashId = message.getSessionSpan()?.attributes?.findAttributeValue(embCrashId.name)
                assertNotNull(crashId)
                assertEquals(crashId, attrs.findAttributeValue(LogIncubatingAttributes.LOG_RECORD_UID.key))
            }
        )
    }

    private fun handleException() {
        Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), testException)
    }

    private fun Envelope<SessionPayload>.getCrashedId(): String {
        val crashId = checkNotNull(getSessionSpan()?.attributes?.findAttributeValue(embCrashId.name))
        assertFalse(crashId.isBlank())
        return crashId
    }

    private fun Log.assertCrash(
        crashId: String,
        state: String = "background"
    ) {
        assertOtelLogReceived(
            logReceived = this,
            expectedMessage = "",
            expectedSeverityNumber = Severity.ERROR.severityNumber,
            expectedSeverityText = Severity.ERROR.name,
            expectedExceptionName = testException.javaClass.canonicalName,
            expectedExceptionMessage = checkNotNull(testException.message),
            expectedStacktrace = testException.getSafeStackTrace()?.toList(),
            expectedProperties = emptyMap(),
            expectedEmbType = "sys.android.crash",
            expectedState = state,
        )

        val exceptionInfo = LegacyExceptionInfo.ofThrowable(testException)
        val expectedExceptionCause = serializer.toJson(listOf(exceptionInfo), List::class.java)

        with(checkNotNull(attributes)) {
            assertEquals(state, findAttributeValue(embState.attributeKey.key))
            assertEquals("1", findAttributeValue("emb.android.crash_number"))
            assertEquals(expectedExceptionCause, findAttributeValue("emb.android.crash.exception_cause"))
            assertNotNull(findAttributeValue("emb.android.threads"))
            assertEquals(crashId, findAttributeValue(LogIncubatingAttributes.LOG_RECORD_UID.key))
        }
    }
}
