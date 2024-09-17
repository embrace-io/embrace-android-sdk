@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.LogType
import io.embrace.android.embracesdk.fakes.createNetworkBehavior
import io.embrace.android.embracesdk.findEventOfType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.findSpansByName
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.semconv.HttpAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
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

            recordNetworkRequest(
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

            logComposeTap(Pair(0.0f, 0.0f), "")
            assertFalse(shouldCaptureNetworkBody("", ""))
            setProcessStartedByNotification()
            assertFalse(isNetworkSpanForwardingEnabled())
            getSdkCurrentTime()
            assertFalse(isInternalNetworkCaptureDisabled())
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

                embrace.internalInterface.recordNetworkRequest(
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

            val spans = checkNotNull(session?.data?.spans)
            val requests = checkNotNull(spans.filter { it.attributes?.findAttributeValue(HttpAttributes.HTTP_REQUEST_METHOD.key) != null })
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
            val session = checkNotNull(harness.recordSession {
                embrace.internalInterface.logComposeTap(Pair(expectedX, expectedY), expectedElementName)
            })

            val tapBreadcrumb = session.findSessionSpan().findEventOfType(EmbType.Ux.Tap)
            val attrs = checkNotNull(tapBreadcrumb.attributes)
            assertEquals("button", attrs.findAttributeValue("view.name"))
            assertEquals("10,99", attrs.findAttributeValue("tap.coords"))
            assertEquals("tap", attrs.findAttributeValue("tap.type"))
        }
    }

    @Test
    fun `access check methods work as expected`() {
        with(testRule) {
            harness.overriddenConfigService.networkBehavior =
                createNetworkBehavior(remoteCfg = {
                    RemoteConfig(
                        disabledUrlPatterns = setOf("dontlogmebro.pizza"),
                        networkCaptureRules = setOf(
                            NetworkCaptureRuleRemoteConfig(
                                id = "test",
                                duration = 10000,
                                method = "GET",
                                urlRegex = "capture.me",
                                expiresIn = 10000
                            )
                        )
                    )
                })

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
            harness.recordSession(simulateActivityCreation = true) { }
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
        with(testRule) {
            assertFalse(embrace.internalInterface.isInternalNetworkCaptureDisabled())
            startSdk(context = harness.overriddenCoreModule.context)
            assertFalse(embrace.internalInterface.isInternalNetworkCaptureDisabled())
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

            val unfilteredSpans = checkNotNull(sessionPayload?.data?.spans)
            val spans = checkNotNull(unfilteredSpans.filter { checkNotNull(it.name).startsWith("tz-") }.associateBy { it.name })
            assertEquals(4, spans.size)
            with(checkNotNull(spans["tz-parent-span"])) {
                assertEquals("testvalue", attributes?.findAttributeValue("testkey"))
            }
            with(checkNotNull(spans["tz-child-span"])) {
                val spanEvent = checkNotNull(events)[0]
                val spanAttrs = checkNotNull(spanEvent.attributes)
                assertEquals("cool event bro", spanEvent.name)
                assertEquals("value", spanAttrs.findAttributeValue("key"))
                assertEquals(Span.Status.ERROR, status)
            }
            with(checkNotNull(spans["tz-another-span"])) {
                assertEquals(spans["tz-parent-span"]?.spanId, parentSpanId)
            }
            assertNotNull(spans["tz-old-span"])
        }
    }

    @Test
    fun `span logging across sessions`() {
        with(testRule) {
            startSdk(context = harness.overriddenCoreModule.context)
            val internalInterface = checkNotNull(embrace.internalInterface)
            var stoppedParentId = ""
            var activeParentId = ""
            val s1 = checkNotNull(harness.recordSession {
                stoppedParentId = checkNotNull(internalInterface.startSpan("parent"))
                activeParentId = checkNotNull(internalInterface.startSpan("active-parent"))
                assertTrue(
                    internalInterface.stopSpan(
                        checkNotNull(
                            internalInterface.startSpan(
                                name = "child",
                                parentSpanId = stoppedParentId
                            )
                        )
                    )
                )
                assertTrue(internalInterface.stopSpan(stoppedParentId))
            })

            val s2 = checkNotNull(harness.recordSession {
                assertTrue(
                    internalInterface.stopSpan(
                        checkNotNull(
                            internalInterface.startSpan(
                                name = "parent"
                            )
                        )
                    )
                )
                assertNull(internalInterface.startSpan(name = "stopped-parent-child", parentSpanId = stoppedParentId))
                assertTrue(
                    internalInterface.stopSpan(
                        checkNotNull(
                            internalInterface.startSpan(
                                name = "active-parent-child",
                                parentSpanId = activeParentId
                            )
                        )
                    )
                )
                assertTrue(internalInterface.stopSpan(activeParentId))
            })

            assertEquals(1, s1.findSpansByName("parent").size)
            assertEquals(1, s1.findSpansByName("child").size)
            assertEquals(0, s1.findSpansByName("active-parent").size)

            // spans stopped in a previous session cannot be a valid parent
            assertEquals(0, s2.findSpansByName("stopped-parent-child").size)

            // active spans started in a previous session is a valid parent
            assertEquals(1, s2.findSpansByName("parent").size)
            assertEquals(1, s2.findSpansByName("active-parent-child").size)
            assertEquals(1, s2.findSpansByName("active-parent").size)
        }
    }

    companion object {
        private const val URL = "https://embrace.io"
        private const val START_TIME = 1692201601000L
        private const val END_TIME = 1692201603000L
    }
}