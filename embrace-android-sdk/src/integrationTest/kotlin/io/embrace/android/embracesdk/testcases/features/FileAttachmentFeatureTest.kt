package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
import io.embrace.android.embracesdk.testframework.assertions.getOtelSeverity
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FileAttachmentFeatureTest {

    private val attachmentId = UUID.randomUUID()

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `log message with user hosted file attachment`() {
        val size = 509L
        val url = "https://example.com/my-file.txt"
        val id = attachmentId.toString()
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logMessage(
                        message = "test message",
                        severity = Severity.INFO,
                        properties = mapOf("key" to "value"),
                        attachmentId = id,
                        attachmentUrl = url,
                        attachmentSize = size,
                    )
                }
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "test message",
                    expectedSeverityNumber = getOtelSeverity(Severity.INFO).severityNumber,
                    expectedSeverityText = Severity.INFO.name,
                    expectedState = "foreground",
                    expectedProperties = mapOf(
                        "key" to "value",
                    ),
                )
            }
        )
    }

    @Test
    fun `log message with embrace hosted file attachment`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logMessage(
                        message = "test message",
                        severity = Severity.INFO,
                        properties = mapOf("key" to "value"),
                        attachment = "Hello, world!".toByteArray()
                    )
                }
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "test message",
                    expectedSeverityNumber = getOtelSeverity(Severity.INFO).severityNumber,
                    expectedSeverityText = Severity.INFO.name,
                    expectedState = "foreground",
                    expectedProperties = mapOf(
                        "key" to "value",
                    ),
                )
            }
        )
    }
}
