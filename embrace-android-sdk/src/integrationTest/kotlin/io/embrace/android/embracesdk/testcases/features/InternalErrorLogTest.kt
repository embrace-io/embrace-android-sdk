package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.getLogWithAttributeValue
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertSessionIdsConsistent
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class InternalErrorLogTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = otelSdkMode)

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
                    logger.trackInternalError(InternalErrorType.InternalInterfaceFail, RuntimeException("Some error message"))
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
                    assertSessionIdsConsistent()
                    checkNotNull(attrs.findAttributeValue(ExceptionAttributes.EXCEPTION_STACKTRACE))
                }
            }
        )
    }

    @Test
    fun `internal error tracked before sdk start delivered`() {
        testRule.runTest(
            setupAction = {
                getEmbLogger().throwOnInternalError = false
                getEmbLogger().trackInternalError(InternalErrorType.InternalInterfaceFail, RuntimeException("Early error"))
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogWithAttributeValue(ExceptionAttributes.EXCEPTION_MESSAGE, "Early error")
                assertEquals("sys.internal", checkNotNull(log.attributes).findAttributeValue("emb.type"))
            }
        )
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
