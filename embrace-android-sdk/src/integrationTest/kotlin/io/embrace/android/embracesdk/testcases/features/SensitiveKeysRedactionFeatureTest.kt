package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SensitiveKeysRedactionFeatureTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    private val sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(
        listOf("password"),
 InstrumentedConfigImpl
    )

    @Test
    fun `custom span properties are redacted if they are sensitive`() {
        testRule.runTest(
            setupAction = {
                overriddenConfigService.sensitiveKeysBehavior = sensitiveKeysBehavior
            },
            testCaseAction = {
                recordSession {
                    embrace.startSpan("test span")?.apply {
                        addAttribute("password", "1234")
                        addAttribute("not a password", "1234")
                        stop()
                    }
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val recordedSpan = session.findSpanByName("test span")
                recordedSpan.attributes?.assertMatches {
                    "password" to REDACTED_LABEL
                    "not a password" to "1234"
                }
            }
        )
    }

    @Test
    fun `custom span events are redacted if they are sensitive`() {
        testRule.runTest(
            setupAction = {
                overriddenConfigService.sensitiveKeysBehavior = sensitiveKeysBehavior
            },
            testCaseAction = {
                recordSession {
                    embrace.startSpan("test span")?.apply {
                        addEvent("event", null, mapOf("password" to "123456", "status" to "ok"))
                        addEvent("anotherEvent", null, mapOf("password" to "654321", "someKey" to "someValue"))
                        stop()
                    }
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val recordedSpan = session.findSpanByName("test span")

                val event = recordedSpan.events?.first { it.name == "event" }
                val anotherEvent = recordedSpan.events?.first { it.name == "anotherEvent" }
                event?.attributes?.assertMatches {
                    "password" to REDACTED_LABEL
                    "status" to "ok"
                }
                anotherEvent?.attributes?.assertMatches {
                    "password" to REDACTED_LABEL
                    "someKey" to "someValue"
                }
            }
        )
    }
}
