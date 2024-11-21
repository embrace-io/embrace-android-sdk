package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashId
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LegacyExceptionInfo
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import io.embrace.android.embracesdk.testframework.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
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
                    simulateJvmUncaughtException(testException)
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertEquals(0, session.data.spanSnapshots?.size)
                getSingleLogEnvelope().getLastLog().assertCrash(
                    state = "foreground",
                    crashId = session.getCrashedId()
                )
            }
        )
    }

    @Test
    fun `app crash in the background generates a crash log`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)),
            testCaseAction = {
                simulateJvmUncaughtException(testException)
            },
            assertAction = {
                val ba = getSingleSessionEnvelope(ApplicationState.BACKGROUND)
                assertEquals(0, ba.data.spanSnapshots?.size)
                getSingleLogEnvelope().getLastLog().assertCrash(
                    crashId = ba.getCrashedId()
                )
            }
        )
    }

    @Test
    fun `React Native crash generates an OTel Log and matches the crashId in the session`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                project = FakeProjectConfig(
                    appId = "abcde",
                    appFramework = "react_native"
                )
            ),
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.getInstance().reactNativeInternalInterface.logUnhandledJsException(
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
                val log = getSingleLogEnvelope().getLastLog()
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

                val message = getSingleSessionEnvelope()
                val crashId = message.getSessionSpan()?.attributes?.findAttributeValue(embCrashId.name)
                assertNotNull(crashId)
                log.attributes?.assertMatches(mapOf(
                    "emb.android.react_native_crash.js_exception" to expectedJsException,
                    "emb.android.crash_number" to 1,
                    "emb.android.crash.exception_cause" to expectedExceptionCause,
                    LogIncubatingAttributes.LOG_RECORD_UID.key to crashId
                ))
                assertNotNull(log.attributes?.findAttributeValue("emb.android.threads"))
            }
        )
    }

    private fun Envelope<SessionPayload>.getCrashedId(): String {
        val crashId = checkNotNull(getSessionSpan()?.attributes?.findAttributeValue(embCrashId.name))
        assertFalse(crashId.isBlank())
        return crashId
    }

    private fun Log.assertCrash(
        crashId: String,
        state: String = "background",
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

        attributes?.assertMatches(mapOf(
            embState.attributeKey.key to state,
            "emb.android.crash_number" to 1,
            "emb.android.crash.exception_cause" to expectedExceptionCause,
            LogIncubatingAttributes.LOG_RECORD_UID.key to crashId
        ))
        assertNotNull(attributes?.findAttributeValue("emb.android.threads"))
    }
}
