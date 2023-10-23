package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.EmbraceEvent
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.LogType
import io.embrace.android.embracesdk.assertions.assertLogMessageReceived
import io.embrace.android.embracesdk.getSentLogMessages
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.net.SocketException

/**
 * Validation of the internal API
 */
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class EmbraceInternalInterfaceTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.newHarness(startImmediately = false)
        }
    )

    @Test
    fun `no NPEs when SDK not started`() {
        assertFalse(testRule.embrace.isStarted)
        with(testRule.embrace.internalInterface) {
            logInfo("", null)
            logWarning("", null, null)
            logError("", null, null, false)
            logHandledException(NullPointerException(), LogType.ERROR, null, null)
            recordCompletedNetworkRequest(
                url = "",
                httpMethod = "GET",
                startTime = 0L,
                endTime = 1L,
                bytesSent = 0L,
                bytesReceived = 0L,
                statusCode = 200,
                traceId = null,
                networkCaptureData = null
            )

            recordIncompleteNetworkRequest(
                url = "",
                httpMethod = "GET",
                startTime = 0L,
                endTime = 1L,
                error = null,
                traceId = null,
                networkCaptureData = null
            )

            recordIncompleteNetworkRequest(
                url = "",
                httpMethod = "GET",
                startTime = 0L,
                endTime = 1L,
                errorType = null,
                errorMessage = null,
                traceId = null,
                networkCaptureData = null
            )

            recordAndDeduplicateNetworkRequest(
                callId = "",
                embraceNetworkRequest = EmbraceNetworkRequest.fromCompletedRequest(
                    "",
                    HttpMethod.GET,
                    0L,
                    1L,
                    0L,
                    0L,
                    200,
                    null
                )
            )

            logComposeTap(android.util.Pair.create(0.0f, 0.0f), "")
            assertFalse(shouldCaptureNetworkBody("", ""))
            setProcessStartedByNotification()
            assertFalse(isNetworkSpanForwardingEnabled())
        }
    }

    @Test
    fun `internal logging methods work as expected`() {
        with(testRule) {
            embrace.start(harness.fakeCoreModule.context)
            val expectedProperties = mapOf(Pair("key", "value"))
            harness.recordSession {
                embrace.internalInterface.logInfo("info", expectedProperties)
                embrace.internalInterface.logWarning("warning", expectedProperties, null)
                embrace.internalInterface.logError("error", expectedProperties, null, false)
                embrace.internalInterface.logHandledException(NullPointerException(), LogType.ERROR, expectedProperties, null)
                val logs = harness.getSentLogMessages(4)

                assertLogMessageReceived(
                    logs[0],
                    message = "info",
                    eventType = EmbraceEvent.Type.INFO_LOG,
                    properties = expectedProperties
                )
                assertLogMessageReceived(
                    logs[1],
                    message = "warning",
                    eventType = EmbraceEvent.Type.WARNING_LOG,
                    properties = expectedProperties
                )
                assertLogMessageReceived(
                    logs[2],
                    message = "error",
                    eventType = EmbraceEvent.Type.ERROR_LOG,
                    properties = expectedProperties
                )
                assertLogMessageReceived(
                    logs[3],
                    message = "",
                    eventType = EmbraceEvent.Type.ERROR_LOG,
                    properties = expectedProperties
                )
            }
        }
    }

    @Test
    fun `network recording methods work as expected`() {
        with(testRule) {
            embrace.start(harness.fakeCoreModule.context)
            val session = harness.recordSession {
                harness.fakeClock.tick()
                harness.fakeConfigService.updateListeners()
                harness.fakeClock.tick()
                embrace.internalInterface.recordCompletedNetworkRequest(
                    url = URL,
                    httpMethod = "GET",
                    startTime = START_TIME,
                    endTime = END_TIME,
                    bytesSent = 0L,
                    bytesReceived = 0L,
                    statusCode = 500,
                    traceId = null,
                    networkCaptureData = null
                )

                embrace.internalInterface.recordIncompleteNetworkRequest(
                    url = URL,
                    httpMethod = "GET",
                    startTime = START_TIME,
                    endTime = END_TIME,
                    error = NullPointerException(),
                    traceId = null,
                    networkCaptureData = null
                )

                embrace.internalInterface.recordIncompleteNetworkRequest(
                    url = URL,
                    httpMethod = "GET",
                    startTime = START_TIME,
                    endTime = END_TIME,
                    errorType = SocketException::class.java.canonicalName,
                    errorMessage = "",
                    traceId = null,
                    networkCaptureData = null
                )

                embrace.internalInterface.recordAndDeduplicateNetworkRequest(
                    callId = "",
                    embraceNetworkRequest = EmbraceNetworkRequest.fromCompletedRequest(
                        URL,
                        HttpMethod.POST,
                        START_TIME,
                        END_TIME,
                        99L,
                        301L,
                        200,
                        null
                    )
                )
            }

            val requests = checkNotNull(session.performanceInfo?.networkRequests?.networkSessionV2?.requests)
            assertEquals(
                "Unexpected number of requests in sent session: ${requests.size}",
                4,
                requests.size
            )
        }
    }

    @Test
    fun `compose tap logging works as expected`() {
        val expectedX = 10.0f
        val expectedY = 99f
        val expectedElementName = "button"

        with(testRule) {
            embrace.start(harness.fakeCoreModule.context)
            val session = harness.recordSession {
                embrace.internalInterface.logComposeTap(android.util.Pair.create(expectedX, expectedY), expectedElementName)
            }

            val tapBreadcrumb = checkNotNull(session.breadcrumbs?.tapBreadcrumbs?.last())
            assertEquals("10,99", tapBreadcrumb.location)
            assertEquals(expectedElementName, tapBreadcrumb.tappedElementName)
        }
    }

    @Test
    fun `access check methods work as expected`() {
        with(testRule) {
            embrace.start(harness.fakeCoreModule.context)
            harness.recordSession {
                assertTrue(embrace.internalInterface.shouldCaptureNetworkBody("capture.me", "GET"))
                assertFalse(embrace.internalInterface.shouldCaptureNetworkBody("capture.me", "POST"))
                assertFalse(embrace.internalInterface.shouldCaptureNetworkBody(URL, "GET"))
                assertTrue(embrace.internalInterface.isNetworkSpanForwardingEnabled())
            }
        }
    }

    @Test
    fun `set process as started by notification works as expected`() {
        with(testRule) {
            embrace.start(harness.fakeCoreModule.context)
            embrace.internalInterface.setProcessStartedByNotification()
            harness.recordSession(simulateAppStartup = true) { }
            assertEquals(EmbraceEvent.Type.START, harness.fakeDeliveryModule.deliveryService.lastEventSentAsync?.event?.type)
        }
    }

    companion object {
        private const val URL = "https://embrace.io"
        private const val START_TIME = 1692201601L
        private const val END_TIME = 1692202600L
    }
}