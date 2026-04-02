package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLogWithAttributeValue
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
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
                with(getSingleLogEnvelope().getLogWithAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE, "Some error message")) {
                    assertEquals("ERROR", severityText)
                    assertEquals("", body)

                    val attrs = checkNotNull(attributes)
                    assertEquals("sys.internal", attrs.findAttributeValue("emb.type"))
                    assertEquals(
                        "Some error message",
                        attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE)
                    )
                    assertEquals(
                        "java.lang.RuntimeException",
                        attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_TYPE)
                    )
                    assertNotNull(attrs.findAttributeValue("log.record.uid"))
                    assertNotNull(attrs.findAttributeValue("session.id"))
                    checkNotNull(attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_STACKTRACE))
                }
            }
        )
    }
}
