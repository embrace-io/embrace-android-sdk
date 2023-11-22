package io.embrace.android.embracesdk

import com.google.gson.stream.JsonReader
import io.embrace.android.embracesdk.comms.delivery.DeliveryFailedApiCalls
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@Suppress("DEPRECATION")
internal class LogMessageTest : BaseTest() {

    @Before
    fun setup() {
        startEmbraceInForeground()
    }

    @After
    fun tearDown() {
        sendBackground()
    }

    @Test
    fun logInfoTest() {
        Embrace.getInstance().logInfo("Test log info")

        waitForRequest { request ->
            validateMessageAgainstGoldenFile(request, "log-info-event.json")
        }
    }

    @Test
    fun logInfoWithPropertyTest() {
        val properties = HashMap<String, Any>()
        properties["info"] = "test property"

        Embrace.getInstance().logMessage("Test log info with property", Severity.INFO, properties)

        waitForRequest { request ->
            validateMessageAgainstGoldenFile(request, "log-info-with-property-event.json")
        }
    }

    @Test
    fun logInfoFailRequestTest() {
        waitForFailedRequest(
            endpoint = EmbraceEndpoint.LOGGING,
            request = { Embrace.getInstance().logInfo("Test log info fail") },
            action = {
                waitForRequest { request ->
                    validateMessageAgainstGoldenFile(request, "log-info-fail-event.json")
                }
            },
            validate = { file -> validateFileContent(file) }
        )
    }

    private fun validateFileContent(file: File) {
        try {
            assertTrue(file.exists() && !file.isDirectory)
            readFile(file, "/v1/log/logging")
            val serializer = EmbraceSerializer()
            file.bufferedReader().use { bufferedReader ->
                JsonReader(bufferedReader).use { jsonreader ->
                    jsonreader.isLenient = true
                    val obj = serializer.loadObject(jsonreader, DeliveryFailedApiCalls::class.java)
                    if (obj != null) {
                        val failedCallFileName = obj.element().cachedPayloadFilename
                        assert(failedCallFileName.isNotBlank())
                        readFileContent("Test log info fail", failedCallFileName)
                    } else {
                        fail("Null object")
                    }
                }
            }
        } catch (e: IOException) {
            fail("IOException error: ${e.message}")
        }
    }

    @Test
    fun logErrorTest() {
        Embrace.getInstance().logError("Test log error")

        waitForRequest { request ->
            validateMessageAgainstGoldenFile(request, "log-error-event.json")
        }
    }

    @Test
    fun logErrorWithPropertyTest() {
        val properties = HashMap<String, Any>()
        properties["error"] = "test property"

        Embrace.getInstance().logMessage("Test log error", Severity.ERROR, properties)

        waitForRequest { request ->
            validateMessageAgainstGoldenFile(request, "log-error-with-property-event.json")
        }
    }

    @Test
    fun logExceptionTest() {
        Embrace.getInstance().logException(Exception("Another log error"))

        waitForRequest { request ->
            validateMessageAgainstGoldenFile(request, "log-error-with-exception-event.json")
        }
    }

    @Test
    fun logErrorWithExceptionAndMessageTest() {
        val exception = java.lang.NullPointerException("Exception message")
        Embrace.getInstance().logException(exception, Severity.ERROR, mapOf(), "log message")

        waitForRequest { request ->
            validateMessageAgainstGoldenFile(
                request,
                "log-error-with-exception-and-message-event.json"
            )
        }
    }

    @Test
    fun logWarningTest() {
        Embrace.getInstance().logWarning("Test log warning")

        waitForRequest { request ->
            validateMessageAgainstGoldenFile(request, "log-warning-event.json")
        }
    }
}
