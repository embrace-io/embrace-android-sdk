package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation of the internal API
 */
@RunWith(AndroidJUnit4::class)
internal class EmbraceInternalInterfaceTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `no NPEs when SDK not started`() {
        testRule.runTest(
            startSdk = false,
            testCaseAction = {
                assertFalse(embrace.isStarted)
                with(EmbraceInternalApi.internalInterface) {
                    assertFalse(shouldCaptureNetworkBody("", ""))
                    assertFalse(isNetworkSpanForwardingEnabled())
                }
                assertFalse(embrace.isStarted)
            }
        )
    }

    @Test
    fun `access check methods work as expected`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
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
            ),
            testCaseAction = {
                recordSession {
                    assertTrue(
                        EmbraceInternalApi.internalInterface.shouldCaptureNetworkBody(
                            "capture.me",
                            "GET"
                        )
                    )
                    assertFalse(
                        EmbraceInternalApi.internalInterface.shouldCaptureNetworkBody(
                            "capture.me",
                            "POST"
                        )
                    )
                    assertFalse(EmbraceInternalApi.internalInterface.shouldCaptureNetworkBody(URL, "GET"))
                    assertFalse(EmbraceInternalApi.internalInterface.isNetworkSpanForwardingEnabled())
                }
            }
        )
    }

    @Test
    fun `internal tracing APIs work as expected`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    with(EmbraceInternalApi.internalInterface) {
                        val parentSpanId = checkNotNull(startSpan(name = "tz-parent-span"))
                        clock.tick(10)
                        val childSpanId =
                            checkNotNull(startSpan(name = "tz-child-span", parentSpanId = parentSpanId))
                        addSpanAttribute(spanId = parentSpanId, "testkey", "testvalue")
                        addSpanEvent(
                            spanId = childSpanId,
                            name = "cool event bro",
                            attributes = mapOf("key" to "value")
                        )
                        recordSpan(name = "tz-another-span", parentSpanId = parentSpanId) { }
                        recordCompletedSpan(
                            name = "tz-old-span",
                            startTimeMs = clock.now() - 1L,
                            endTimeMs = embrace.getSdkCurrentTimeMs(),
                        )
                        stopSpan(spanId = childSpanId, errorCode = ErrorCode.USER_ABANDON)
                        stopSpan(parentSpanId)
                    }
                }
            },
            assertAction = {
                val sessionPayload = getSingleSessionEnvelope()
                val unfilteredSpans = checkNotNull(sessionPayload.data.spans)
                val spans =
                    checkNotNull(unfilteredSpans.filter { checkNotNull(it.name).startsWith("tz-") }
                        .associateBy { it.name })
                assertEquals(4, spans.size)
                with(checkNotNull(spans["tz-parent-span"])) {
                    attributes?.assertMatches(
                        mapOf(
                            "testkey" to "testvalue",
                        )
                    )
                }
                with(checkNotNull(spans["tz-child-span"])) {
                    val spanEvent = checkNotNull(events)[0]
                    spanEvent.attributes?.assertMatches(
                        mapOf(
                            "key" to "value",
                        )
                    )
                    assertEquals("cool event bro", spanEvent.name)
                    assertEquals(Span.Status.ERROR, status)
                }
                with(checkNotNull(spans["tz-another-span"])) {
                    assertEquals(spans["tz-parent-span"]?.spanId, parentSpanId)
                }
                assertNotNull(spans["tz-old-span"])
            }
        )
    }

    @Test
    fun `SDK will not start if feature flag has it being disabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(threshold = 0),
            expectSdkToStart = false,
            testCaseAction = {
                assertFalse(embrace.isStarted)
            }
        )
    }

    @Test
    fun `span logging across sessions`() {
        testRule.runTest(
            testCaseAction = {
                val internalInterface = checkNotNull(EmbraceInternalApi.internalInterface)
                var stoppedParentId = ""
                var activeParentId = ""
                recordSession {
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
                }

                recordSession {
                    assertTrue(
                        internalInterface.stopSpan(
                            checkNotNull(
                                internalInterface.startSpan(
                                    name = "parent"
                                )
                            )
                        )
                    )
                    assertNull(
                        internalInterface.startSpan(
                            name = "stopped-parent-child",
                            parentSpanId = stoppedParentId
                        )
                    )
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
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val s1 = sessions[0]
                val s2 = sessions[1]

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
        )
    }

    companion object {
        private const val URL = "https://embrace.io"
    }
}
