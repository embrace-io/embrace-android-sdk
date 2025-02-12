package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
import io.embrace.android.embracesdk.testframework.assertions.getOtelSeverity
import io.embrace.android.embracesdk.testframework.server.FormPart
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `log message with user hosted file attachment`() {
        val url = "https://example.com/my-file.txt"
        val id = attachmentId.toString()
        testRule.runTest(testCaseAction = {
            recordSession {
                logWithUserHostedAttachment(id, url)
            }
        }, assertAction = {
            val log = getSingleLogEnvelope().getLastLog()
            assertUserHostedLogSent(log, url, id)
            assertNull(log.attributes?.findAttributeValue(ATTR_KEY_ERR_CODE))
        })
    }

    @Test
    fun `user hosted file attachment exceeding session limit`() {
        val url = "https://example.com/another-file.txt"
        val id = attachmentId.toString()
        val limit = 5
        testRule.runTest(testCaseAction = {
            repeat(2) {
                recordSession {
                    repeat(limit + 1) {
                        logWithUserHostedAttachment(id, url)
                    }
                }
            }
        }, assertAction = {
            val logs = getLogEnvelopes(2).flatMap { checkNotNull(it.data.logs) }
            assertEquals((limit * 2) + 2, logs.size)

            logs.forEachIndexed { k, log ->
                assertUserHostedLogSent(log, url, id)

                val errCode = log.attributes?.findAttributeValue(ATTR_KEY_ERR_CODE)
                val expectedCode = when (k) {
                    limit, limit + limit + 1 -> "OVER_MAX_ATTACHMENTS"
                    else -> null
                }
                assertEquals(expectedCode, errCode)
            }
        })
    }

    @Test
    fun `log message with embrace hosted file attachment`() {
        val byteArray = "Hello, world!".toByteArray()
        testRule.runTest(testCaseAction = {
            recordSession {
                logWithEmbraceHostedAttachment(byteArray)
            }
        }, assertAction = {
            val parts = getSingleAttachment()
            verifyAttachment(parts, byteArray)

            val log = getSingleLogEnvelope().getLastLog()
            assertEmbraceHostedLogSent(log, byteArray.size.toLong())
        })
    }

    @Test
    fun `embrace hosted file attachment exceeding session limit`() {
        val byteArray = ByteArray(1024 * 16)
        val limit = 5
        testRule.runTest(testCaseAction = {
            repeat(2) {
                recordSession {
                    repeat(limit + 1) {
                        logWithEmbraceHostedAttachment(byteArray)
                    }
                }
            }
        }, assertAction = {
            val logs = getLogEnvelopes(2).flatMap { checkNotNull(it.data.logs) }
            val logSize = (limit * 2) + 2
            assertEquals(logSize, logs.size)

            logs.forEachIndexed { k, log ->
                assertEmbraceHostedLogSent(log, byteArray.size.toLong())

                val errCode = log.attributes?.findAttributeValue(ATTR_KEY_ERR_CODE)
                val expectedCode = when (k) {
                    limit, limit + limit + 1 -> "OVER_MAX_ATTACHMENTS"
                    else -> null
                }
                assertEquals(expectedCode, errCode)
            }

            val attachments = getAttachments(10)
            attachments.forEach { parts ->
                verifyAttachment(parts, byteArray)
            }
        })
    }

    @Test
    fun `embrace hosted file attachment equalling size limit`() {
        val byteArray = ByteArray(1024 * 1024)
        testRule.runTest(testCaseAction = {
            recordSession {
                logWithEmbraceHostedAttachment(byteArray)
            }
        }, assertAction = {
            val parts = getSingleAttachment()
            verifyAttachment(parts, byteArray)

            val log = getSingleLogEnvelope().getLastLog()
            assertEmbraceHostedLogSent(log, byteArray.size.toLong())
        })
    }

    @Test
    fun `embrace hosted file attachment exceeding size limit`() {
        val byteArray = ByteArray(2 * 1024 * 1024)
        testRule.runTest(testCaseAction = {
            recordSession {
                logWithEmbraceHostedAttachment(byteArray)
            }
        }, assertAction = {
            val log = getSingleLogEnvelope().getLastLog()
            assertEmbraceHostedLogSent(log, byteArray.size.toLong())
            assertTrue(getAttachments(0).isEmpty())
        })
    }

    @Test
    fun `embrace multiple file attachments`() {
        val a = "apple".toByteArray()
        val b = "banana".toByteArray()
        val c = "cherry".toByteArray()

        testRule.runTest(testCaseAction = {
            recordSession {
                logWithEmbraceHostedAttachment(a)
                logWithEmbraceHostedAttachment(b)
                logWithEmbraceHostedAttachment(c)
            }
        }, assertAction = {
            val expectedSize = 3
            val logs = getLogEnvelopes(1).flatMap { checkNotNull(it.data.logs) }
            assertEquals(expectedSize, logs.size)

            val attachments = getAttachments(expectedSize)
            assertEquals(expectedSize, attachments.size)

            val aId = logs[0].attributes?.findAttributeValue(ATTR_KEY_ID)
            val bId = logs[1].attributes?.findAttributeValue(ATTR_KEY_ID)
            val cId = logs[2].attributes?.findAttributeValue(ATTR_KEY_ID)

            val attachmentA = attachments.single { it[1].data == aId }
            val attachmentB = attachments.single { it[1].data == bId }
            val attachmentC = attachments.single { it[1].data == cId }

            verifyAttachment(attachmentA, a)
            verifyAttachment(attachmentB, b)
            verifyAttachment(attachmentC, c)

            val uniqueIds = logs.distinctBy { it.attributes?.findAttributeValue(ATTR_KEY_ID) }
            assertEquals(expectedSize, uniqueIds.size)
        })
    }

    private fun verifyAttachment(
        parts: List<FormPart>,
        byteArray: ByteArray,
        appId: String = "abcde",
    ) {
        assertEquals("form-data; name=\"app_id\"", parts[0].contentDisposition)
        assertEquals(appId, parts[0].data)
        assertEquals("form-data; name=\"attachment_id\"", parts[1].contentDisposition)
        checkNotNull(UUID.fromString(parts[1].data))
        assertEquals(
            "form-data; name=\"file\"; filename=\"file\"", parts[2].contentDisposition
        )
        assertEquals(String(byteArray), parts[2].data)
    }

    private fun assertUserHostedLogSent(
        log: Log,
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
                ATTR_KEY_URL to url,
                ATTR_KEY_ID to id,
            ),
        )
    }

    private fun assertEmbraceHostedLogSent(
        log: Log,
        size: Long,
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
            ),
        )
        val id = log.attributes?.findAttributeValue(ATTR_KEY_ID)
        checkNotNull(UUID.fromString(id))
    }

    private fun EmbraceActionInterface.logWithUserHostedAttachment(
        id: String,
        url: String,
    ) {
        embrace.logMessage(
            message = "test message",
            severity = Severity.INFO,
            properties = mapOf("key" to "value"),
            attachmentId = id,
            attachmentUrl = url,
        )
    }

    private fun EmbraceActionInterface.logWithEmbraceHostedAttachment(
        byteArray: ByteArray,
    ) {
        embrace.logMessage(
            message = "test message",
            severity = Severity.INFO,
            properties = mapOf("key" to "value"),
            attachment = byteArray
        )
    }
}
