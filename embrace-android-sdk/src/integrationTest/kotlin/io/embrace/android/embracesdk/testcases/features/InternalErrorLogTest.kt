package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLogWithAttributeValue
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class InternalErrorLogTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `internal error log delivered`() {
        lateinit var logger: InternalLogger

        testRule.runTest(
            setupAction = {
                getEmbLogger().throwOnInternalError = false
                logger = getEmbLogger()
            },
            testCaseAction = {
                recordSession {
                    logger.trackInternalError(InternalErrorType.INTERNAL_INTERFACE_FAIL, RuntimeException("Some error message"))
                }
            },
            assertAction = {
                with(getSingleLogEnvelope().getLogWithAttributeValue("exception.message", "Some error message")) {
                    assertEquals("ERROR", severityText)
                    assertEquals("", body)

                    val attrs = checkNotNull(attributes)
                    assertEquals("sys.internal", attrs.findAttributeValue("emb.type"))
                    assertEquals(
                        "Some error message",
                        attrs.findAttributeValue("exception.message")
                    )
                    assertEquals(
                        "java.lang.RuntimeException",
                        attrs.findAttributeValue("exception.type")
                    )
                    assertNotNull(attrs.findAttributeValue("log.record.uid"))
                    assertNotNull(attrs.findAttributeValue("session.id"))
                    checkNotNull(attrs.findAttributeValue("exception.stacktrace"))
                }
            }
        )
    }
}
