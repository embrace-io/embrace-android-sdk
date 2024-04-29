package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.config.remote.OTelRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.fakeOTelBehavior
import io.embrace.android.embracesdk.findLogAttribute
import io.embrace.android.embracesdk.getLastSentLog
import io.embrace.android.embracesdk.getLastSentSessionMessage
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.verifySessionHappened
import io.opentelemetry.api.logs.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class CrashTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    private val testException = RuntimeException("Boom!")

    @Before
    fun setup() {
        assertTrue(testRule.harness.getSentSessionMessages().isEmpty())
        testRule.harness.overriddenConfigService.oTelBehavior = fakeOTelBehavior {
            RemoteConfig(oTelConfig = OTelRemoteConfig(isBetaEnabled = true))
        }
    }

    @Test
    fun `app crash generates an OTel Log and matches the crashId in the session`() {
        testRule.harness.recordSession {
            val handler = checkNotNull(Thread.getDefaultUncaughtExceptionHandler())
            handler.uncaughtException(Thread.currentThread(), testException)
        }

        val log = testRule.harness.getLastSentLog()
        assertOtelLogReceived(
            log,
            expectedMessage = "",
            expectedSeverityNumber = Severity.ERROR.severityNumber,
            expectedSeverityText = Severity.ERROR.name,
            expectedExceptionName = testException.javaClass.canonicalName,
            expectedExceptionMessage = checkNotNull(testException.message),
            expectedStacktrace = testException.getSafeStackTrace()?.toList(),
            expectedProperties = emptyMap(),
            expectedEmbType = "sys.android.crash"
        )

        val message = checkNotNull(testRule.harness.getLastSentSessionMessage())
        verifySessionHappened(message)
        assertNotNull(message.session.crashReportId)
        assertEquals(message.session.crashReportId, log?.findLogAttribute("log.record.uid"))
    }

    @Test
    fun `React Native crash generates an OTel Log and matches the crashId in the session`() {
        with(testRule) {
            embrace.start(harness.overriddenCoreModule.context, false, Embrace.AppFramework.REACT_NATIVE)

            testRule.harness.recordSession {
                embrace.reactNativeInternalInterface?.logUnhandledJsException(
                    "name",
                    "message",
                    "type",
                    "stacktrace"
                )
                val handler = checkNotNull(Thread.getDefaultUncaughtExceptionHandler())
                handler.uncaughtException(Thread.currentThread(), testException)
            }
        }

        val log = testRule.harness.getLastSentLog()
        assertOtelLogReceived(
            log,
            expectedMessage = "",
            expectedSeverityNumber = Severity.ERROR.severityNumber,
            expectedSeverityText = Severity.ERROR.name,
            expectedExceptionName = testException.javaClass.canonicalName,
            expectedExceptionMessage = checkNotNull(testException.message),
            expectedStacktrace = testException.getSafeStackTrace()?.toList(),
            expectedProperties = emptyMap(),
            expectedEmbType = "sys.android.react_native_crash"
        )
        val expectedJsException = "{\"n\":\"name\",\"m\":\"message\",\"t\":\"type\",\"st\":\"stacktrace\"}"
        assertEquals(expectedJsException, log?.findLogAttribute("emb.android.react_native_crash.js_exception"))

        val message = checkNotNull(testRule.harness.getLastSentSessionMessage())
        verifySessionHappened(message)
        assertNotNull(message.session.crashReportId)
        assertEquals(message.session.crashReportId, log?.findLogAttribute("log.record.uid"))
    }
}
