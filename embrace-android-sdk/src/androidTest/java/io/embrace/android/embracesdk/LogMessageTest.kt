package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.comms.delivery.PendingApiCalls
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import java.io.File
import java.io.IOException
import logTestMessage
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
    fun failTest() {
        fail("This test should fail")
    }

    @Test
    fun logInfoTest() {
        logTestMessage("Adding info log to Embrace.")
        Embrace.getInstance().logInfo("Test log info")

        waitForRequest(RequestValidator(EmbraceEndpoint.LOGGING) { request ->
            validateMessageAgainstGoldenFile(request, "log-info-event.json")
        })
    }

    @Test
    fun logInfoWithPropertyTest() {
        logTestMessage("Adding info log to Embrace.")

        val properties = HashMap<String, Any>()
        properties["info"] = "test property"
        Embrace.getInstance().logMessage("Test log info with property", Severity.INFO, properties)

        waitForRequest(RequestValidator(EmbraceEndpoint.LOGGING) { request ->
            validateMessageAgainstGoldenFile(request, "log-info-with-property-event.json")
        })
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
            throw IllegalStateException("Failed to validate file context", e)
        }
    }

    @Test
    fun logErrorTest() {
        logTestMessage("Adding error log to Embrace.")
        Embrace.getInstance().logError("Test log error")

        waitForRequest(RequestValidator(EmbraceEndpoint.LOGGING) { request ->
            validateMessageAgainstGoldenFile(request, "log-error-event.json")
        })
    }

    @Test
    fun logErrorWithPropertyTest() {
        logTestMessage("Adding error log to Embrace.")
        val properties = HashMap<String, Any>()
        properties["error"] = "test property"

        Embrace.getInstance().logMessage("Test log error", Severity.ERROR, properties)

        waitForRequest(RequestValidator(EmbraceEndpoint.LOGGING) { request ->
            validateMessageAgainstGoldenFile(request, "log-error-with-property-event.json")
        })
    }

    @Test
    fun logExceptionTest() {
        logTestMessage("Adding exception log to Embrace.")
        Embrace.getInstance().logException(Exception("Another log error"))

        waitForRequest(RequestValidator(EmbraceEndpoint.LOGGING) { request ->
            validateMessageAgainstGoldenFile(request, "log-error-with-exception-event.json")
        })
    }

    @Test
    fun logErrorWithExceptionAndMessageTest() {
        logTestMessage("Adding exception log to Embrace.")
        val exception = java.lang.NullPointerException("Exception message")
        Embrace.getInstance().logException(exception, Severity.ERROR, mapOf(), "log message")

        waitForRequest(RequestValidator(EmbraceEndpoint.LOGGING) { request ->
            validateMessageAgainstGoldenFile(request, "log-error-with-exception-and-message-event.json")
        })
    }

    @Test
    fun logWarningTest() {
        logTestMessage("Adding warning log to Embrace.")
        Embrace.getInstance().logWarning("Test log warning")

        waitForRequest(RequestValidator(EmbraceEndpoint.LOGGING) { request ->
            validateMessageAgainstGoldenFile(request, "log-warning-event.json")
        })
    }
}
