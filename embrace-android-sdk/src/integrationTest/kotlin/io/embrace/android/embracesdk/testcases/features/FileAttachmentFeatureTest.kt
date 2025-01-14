package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
import io.embrace.android.embracesdk.testframework.assertions.getOtelSeverity
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FileAttachmentFeatureTest {

    private companion object {
        private const val ATTR_KEY_SIZE = "emb.attachment_size"
        private const val ATTR_KEY_URL = "emb.attachment_url"
        private const val ATTR_KEY_ID = "emb.attachment_id"
        private const val ATTR_KEY_ERR_CODE = "emb.attachment_error_code"
    }

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
                    logWithUserHostedAttachment(id, url, size)
                }
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                assertUserHostedLogSent(log, size, url, id)
                assertNull(log.attributes?.findAttributeValue(ATTR_KEY_ERR_CODE))
            }
        )
    }

    @Test
    fun `user hosted file attachment exceeding limit`() {
        val size = 509L
        val url = "https://example.com/my-file.txt"
        val id = attachmentId.toString()
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    repeat(6) {
                        logWithUserHostedAttachment(id, url, size)
                    }
                }
                recordSession {
                    repeat(6) {
                        logWithUserHostedAttachment(id, url, size)
                    }
                }
            },
            assertAction = {
                val logs = getLogEnvelopes(2).flatMap { checkNotNull(it.data.logs) }
                assertEquals(12, logs.size)

                logs.forEachIndexed { k, log ->
                    assertUserHostedLogSent(log, size, url, id)

                    val errCode = log.attributes?.findAttributeValue(ATTR_KEY_ERR_CODE)
                    val expectedCode = when (k) {
                        5, 11 -> "OVER_MAX_ATTACHMENTS"
                        else -> null
                    }
                    assertEquals(expectedCode, errCode)
                }
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

    private fun assertUserHostedLogSent(
        log: Log,
        size: Long,
        url: String,
        id: String,
    ) {
        assertOtelLogReceived(
            logReceived = log,
            expectedMessage = "test message",
            expectedSeverityNumber = getOtelSeverity(Severity.INFO).severityNumber,
            expectedSeverityText = Severity.INFO.name,
            expectedState = "foreground",
            expectedTimeMs = null,
            expectedProperties = mapOf(
                "key" to "value",
                ATTR_KEY_SIZE to size.toString(),
                ATTR_KEY_URL to url,
                ATTR_KEY_ID to id,
            ),
        )
    }

    private fun EmbraceActionInterface.logWithUserHostedAttachment(
        id: String,
        url: String,
        size: Long,
    ) {
        embrace.logMessage(
            message = "test message",
            severity = Severity.INFO,
            properties = mapOf("key" to "value"),
            attachmentId = id,
            attachmentUrl = url,
            attachmentSize = size,
        )
    }
}
