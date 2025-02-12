package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
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
        testRule.runTest(
            setupAction = {
                (overriddenInitModule.logger as FakeEmbLogger).throwOnInternalError = false
            },
            testCaseAction = {
                recordSession {
                    embrace.impl.internalInterface.logInternalError("Some error message", null)
                }
            },
            assertAction = {
                with(getSingleLogEnvelope().getLastLog()) {
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
