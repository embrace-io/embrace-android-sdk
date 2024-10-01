package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SensitiveKeysRedactionFeatureTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
    }

    @Before
    fun setUp() {
        testRule.harness.overriddenConfigService.sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(
            listOf("password")
        )
    }

    @Test
    fun `custom span properties are redacted if they are sensitive`() {
        testRule.runTest(
            testCaseAction = {
                startSdk()
                harness.recordSession {
                    val span = embrace.startSpan("test span")
                    span?.addAttribute("password", "1234")
                    span?.addAttribute("not a password", "1234")
                    span?.stop()
                }
            },
            assertAction = {
                val session = harness.getSingleSession()
                val recordedSpan = session.data.spans?.find { it.name == "test span" }
                val sensitiveAttribute = recordedSpan?.attributes?.first { it.key == "password" }
                val notSensitiveAttribute = recordedSpan?.attributes?.first { it.key == "not a password" }

                assertEquals(REDACTED_LABEL, sensitiveAttribute?.data)
                assertEquals("1234", notSensitiveAttribute?.data)
            }
        )
    }

    @Test
    fun `custom span events are redacted if they are sensitive`() {
        testRule.runTest(
            testCaseAction = {
                startSdk()
                harness.recordSession {
                    val span = embrace.startSpan("test span")
                    span?.addEvent("event", null, mapOf("password" to "123456", "status" to "ok"))
                    span?.addEvent("anotherEvent", null, mapOf("password" to "654321", "someKey" to "someValue"))
                    span?.stop()
                }
            },
            assertAction = {
                val session = harness.getSingleSession()
                val recordedSpan = session.data.spans?.find { it.name == "test span" }

                val event = recordedSpan?.events?.first { it.name == "event" }
                val anotherEvent = recordedSpan?.events?.first { it.name == "anotherEvent" }
                assertTrue(event?.attributes?.any { it.key == "password" && it.data == REDACTED_LABEL } ?: false)
                assertTrue(event?.attributes?.any { it.key == "status" && it.data == "ok" } ?: false)
                assertTrue(anotherEvent?.attributes?.any { it.key == "password" && it.data == REDACTED_LABEL } ?: false)
                assertTrue(anotherEvent?.attributes?.any { it.key == "someKey" && it.data == "someValue" } ?: false)
            }
        )
    }
}