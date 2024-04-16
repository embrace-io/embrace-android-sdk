@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.LogType
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.assertions.assertLogMessageReceived
import io.embrace.android.embracesdk.findEventOfType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentLogMessages
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before
    fun setup() {
        ApkToolsConfig.IS_NETWORK_CAPTURE_DISABLED = false
    }

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
                ),
            )

            logComposeTap(Pair(0.0f, 0.0f), "")
            assertFalse(shouldCaptureNetworkBody("", ""))
            setProcessStartedByNotification()
            assertFalse(isNetworkSpanForwardingEnabled())
            getSdkCurrentTime()
            assertFalse(isInternalNetworkCaptureDisabled())
        }
    }

    @Test
    fun `internal logging methods work as expected`() {
        with(testRule) {
            startSdk(context = harness.overriddenCoreModule.context)
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
                    eventType = EventType.INFO_LOG,
                    properties = expectedProperties
                )
                assertLogMessageReceived(
                    logs[1],
                    message = "warning",
                    eventType = EventType.WARNING_LOG,
                    properties = expectedProperties
                )
                assertLogMessageReceived(
                    logs[2],
                    message = "error",
                    eventType = EventType.ERROR_LOG,
                    properties = expectedProperties
                )
                assertLogMessageReceived(
                    logs[3],
                    message = "",
                    eventType = EventType.ERROR_LOG,
                    properties = expectedProperties
                )
            }
        }
    }

    @Test
    fun `network recording methods work as expected`() {
        with(testRule) {
            startSdk(context = harness.overriddenCoreModule.context)
            val session = harness.recordSession {
                harness.overriddenClock.tick()
                harness.overriddenConfigService.updateListeners()
                harness.overriddenClock.tick()
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
                    ),
                )
            }

            val requests = checkNotNull(session?.performanceInfo?.networkRequests?.networkSessionV2?.requests)
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
            startSdk(context = harness.overriddenCoreModule.context)
            val session = checkNotNull( harness.recordSession {
                embrace.internalInterface.logComposeTap(Pair(expectedX, expectedY), expectedElementName)
            })

            val tapBreadcrumb = session.findSessionSpan().findEventOfType(EmbType.Ux.Tap)
            assertEquals("button", tapBreadcrumb.attributes["view.name"])
            assertEquals("10,99", tapBreadcrumb.attributes["tap.coords"])
            assertEquals("tap", tapBreadcrumb.attributes["tap.type"])
        }
    }

    @Test
    fun `access check methods work as expected`() {
        with(testRule) {
            startSdk(context = harness.overriddenCoreModule.context)
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
            startSdk(context = harness.overriddenCoreModule.context)
            embrace.internalInterface.setProcessStartedByNotification()
            harness.recordSession(simulateAppStartup = true) { }
            assertEquals(EventType.START, harness.overriddenDeliveryModule.deliveryService.lastEventSentAsync?.event?.type)
        }
    }

    @Test
    fun `test sdk time`() {
        with(testRule) {
            startSdk(context = harness.overriddenCoreModule.context)
            assertEquals(harness.overriddenClock.now(), embrace.internalInterface.getSdkCurrentTime())
            harness.overriddenClock.tick()
            assertEquals(harness.overriddenClock.now(), embrace.internalInterface.getSdkCurrentTime())
        }
    }

    @Test
    fun `test isInternalNetworkCaptureDisabled`() {
        ApkToolsConfig.IS_NETWORK_CAPTURE_DISABLED = true
        with(testRule) {
            assertFalse(embrace.internalInterface.isInternalNetworkCaptureDisabled())
            startSdk(context = harness.overriddenCoreModule.context)
            assertTrue(embrace.internalInterface.isInternalNetworkCaptureDisabled())
        }
    }

    @Test
    fun `internal tracing APIs work as expected`() {
        with(testRule) {
            startSdk(context = harness.overriddenCoreModule.context)
            val sessionPayload = harness.recordSession {
                with(embrace.internalInterface) {
                    val parentSpanId = checkNotNull(startSpan(name = "tz-parent-span"))
                    harness.overriddenClock.tick(10)
                    val childSpanId = checkNotNull(startSpan(name = "tz-child-span", parentSpanId = parentSpanId))
                    addSpanAttribute(spanId = parentSpanId, "testkey", "testvalue")
                    addSpanEvent(spanId = childSpanId, name = "cool event bro", attributes = mapOf("key" to "value"))
                    recordSpan(name = "tz-another-span", parentSpanId = parentSpanId) { }
                    recordCompletedSpan(
                        name = "tz-old-span",
                        startTimeMs = harness.overriddenClock.now() - 1L,
                        endTimeMs = embrace.internalInterface.getSdkCurrentTime(),
                    )
                    stopSpan(spanId = childSpanId, errorCode = ErrorCode.USER_ABANDON)
                    stopSpan(parentSpanId)
                }
            }

            val spans = checkNotNull(sessionPayload?.spans?.filter { it.name.startsWith("tz-") }?.associateBy { it.name })
            assertEquals(4, spans.size)
            with(checkNotNull(spans["tz-parent-span"])) {
                assertEquals("testvalue", attributes["testkey"])
            }
            with(checkNotNull(spans["tz-child-span"])) {
                assertEquals("cool event bro", events[0].name)
                assertEquals("value", events[0].attributes["key"])
                assertEquals(StatusCode.ERROR, status)
            }
            with(checkNotNull(spans["tz-another-span"])) {
                assertEquals(spans["tz-parent-span"]?.spanId, parentSpanId)
            }
            assertNotNull(spans["tz-old-span"])
        }
    }

    companion object {
        private const val URL = "https://embrace.io"
        private const val START_TIME = 1692201601000L
        private const val END_TIME = 1692201603000L
    }
}