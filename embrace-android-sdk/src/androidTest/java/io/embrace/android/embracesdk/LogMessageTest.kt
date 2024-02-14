package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.comms.delivery.PendingApiCalls
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import java.io.File
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

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

    private fun validateFileContent(file: File) {
        try {
            assertTrue(file.exists() && !file.isDirectory)
            readFile(file, "/v1/log/logging")
            val serializer = EmbraceSerializer()
            val obj = serializer.fromJson(file.inputStream(), PendingApiCalls::class.java)
            val pendingApiCall = obj.pollNextPendingApiCall()
            checkNotNull(pendingApiCall)
            val pendingApiCallFileName = pendingApiCall.cachedPayloadFilename
            assert(pendingApiCallFileName.isNotBlank())
            readFileContent("Test log info fail", pendingApiCallFileName)
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
