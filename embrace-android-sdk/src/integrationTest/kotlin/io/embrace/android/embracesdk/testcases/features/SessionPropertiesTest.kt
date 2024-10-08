package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SessionPropertiesTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        EmbraceSetupInterface(startImmediately = false)
    }

    @Test
    fun `session properties additions and removal works at all stages app state transition`() {
        testRule.runTest(
            testCaseAction = {
                startSdk()
                embrace.addSessionProperty(PERM_KEY, PERM_VAL, true)
                embrace.addSessionProperty(PERM_KEY_2, PERM_VAL, true)
                recordSession {
                    embrace.addSessionProperty(TEMP_KEY, TEMP_VAL, false)
                    embrace.addSessionProperty(PERM_KEY_3, PERM_VAL, true)
                    embrace.removeSessionProperty(PERM_KEY_2)
                    embrace.removeSessionProperty(TEMP_KEY)
                }
                embrace.addSessionProperty(PERM_KEY_4, PERM_VAL, true)
                embrace.removeSessionProperty(PERM_KEY_3)
                embrace.removeSessionProperty(PERM_KEY_4)

                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val bas = getSessionEnvelopes(2, ApplicationState.BACKGROUND)

                bas[0].findSessionSpan().assertPropertyExistence(
                    exist = listOf(PERM_KEY, PERM_KEY_2)
                )
                sessions[0].findSessionSpan().assertPropertyExistence(
                    exist = listOf(PERM_KEY, PERM_KEY_3),
                    missing = listOf(TEMP_KEY, PERM_KEY_2)
                )
                bas[1].findSessionSpan().assertPropertyExistence(
                    exist = listOf(PERM_KEY),
                    missing = listOf(TEMP_KEY, PERM_KEY_2, PERM_KEY_3, PERM_KEY_4)
                )
                sessions[1].findSessionSpan().assertPropertyExistence(
                    exist = listOf(PERM_KEY),
                    missing = listOf(TEMP_KEY, TEMP_KEY_2, PERM_KEY_2, PERM_KEY_3)
                )
            }
        )
    }

    @Test
    fun `session properties work with background activity disabled`() {
        testRule.runTest(
            setupAction = {
                overriddenConfigService.backgroundActivityCaptureEnabled = false
            },
            testCaseAction = {
                startSdk()
                embrace.addSessionProperty(PERM_KEY, PERM_VAL, true)
                embrace.addSessionProperty(TEMP_KEY, TEMP_VAL, false)
                embrace.addSessionProperty(PERM_KEY_2, PERM_VAL, true)
                recordSession {
                    embrace.addSessionProperty(PERM_KEY_3, PERM_VAL, true)
                    embrace.removeSessionProperty(PERM_KEY_2)
                }

                embrace.addSessionProperty(TEMP_KEY_2, TEMP_VAL, false)
                embrace.removeSessionProperty(PERM_KEY_3)

                recordSession {
                    embrace.addSessionProperty(PERM_KEY_4, PERM_VAL, true)
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val session1 = sessions[0]
                val session2 = sessions[1]

                session1.findSessionSpan().assertPropertyExistence(
                    exist = listOf(TEMP_KEY, PERM_KEY, PERM_KEY_3),
                    missing = listOf(PERM_KEY_2)
                )

                session2.findSessionSpan().assertPropertyExistence(
                    exist = listOf(TEMP_KEY_2, PERM_KEY, PERM_KEY_4),
                    missing = listOf(TEMP_KEY, PERM_KEY_2, PERM_KEY_3)
                )
            }
        )
    }

    @Test
    fun `temp properties are cleared in next session`() {
        testRule.runTest(
            testCaseAction = {
                startSdk()
                embrace.addSessionProperty(PERM_KEY, PERM_VAL, true)
                recordSession {
                    embrace.addSessionProperty(TEMP_KEY, TEMP_VAL, false)
                }
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val firstSession = sessions[0]
                val secondSession = sessions[1]

                val bgActivities = getSessionEnvelopes(2, ApplicationState.BACKGROUND)
                assertEquals(2, bgActivities.size)
                val firstBg = bgActivities.first()
                val secondBg = bgActivities.last()
                // check perm property is in all payloads
                assertEquals(PERM_VAL, firstBg.findSessionSpan().getSessionProperty(PERM_KEY))
                assertEquals(PERM_VAL, firstSession.findSessionSpan().getSessionProperty(PERM_KEY))
                assertEquals(PERM_VAL, secondBg.findSessionSpan().getSessionProperty(PERM_KEY))
                assertEquals(PERM_VAL, secondSession.findSessionSpan().getSessionProperty(PERM_KEY))
                // check temp property is only in first session payload
                assertNull(firstBg.findSessionSpan().getSessionProperty(TEMP_KEY))
                assertEquals(TEMP_VAL, firstSession.findSessionSpan().getSessionProperty(TEMP_KEY))
                assertNull(secondBg.findSessionSpan().getSessionProperty(TEMP_KEY))
                assertNull(secondSession.findSessionSpan().getSessionProperty(TEMP_KEY))
            }
        )
    }

    @Test
    fun `adding properties in bg activity modifications change the cached payload`() {
        testRule.runTest(
            testCaseAction = {
                startSdk()
                recordSession()
                embrace.addSessionProperty("temp", "value", false)
                recordSession()
            },
            assertAction = {
                val session = getSessionEnvelopes(2)[0]
                checkNotNull(session.getSessionSpan()).assertPropertyExistence(missing = listOf("temp"))

                val bg = getSessionEnvelopes(2, ApplicationState.BACKGROUND).last()
                checkNotNull(bg.getSessionSpan()).assertPropertyExistence(exist = listOf("temp"))
            }
        )
    }

    @Test
    fun `permanent properties are persisted in cached payloads`() {
        testRule.runTest(
            testCaseAction = {
                startSdk()
                recordSession()
                embrace.addSessionProperty("perm", "value", true)

                recordSession {
                    embrace.addSessionProperty("perm2", "value", true)
                }
                embrace.addSessionProperty("perm3", "value", true)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(3)
                checkNotNull(sessions[1].findSessionSpan()).assertPropertyExistence(
                    exist = listOf("perm", "perm2")
                )
                checkNotNull(sessions[2].findSessionSpan()).assertPropertyExistence(
                    exist = listOf("perm", "perm2", "perm3")
                )

                val bas = getSessionEnvelopes(3, ApplicationState.BACKGROUND)
                checkNotNull(bas[0].findSessionSpan()).assertPropertyExistence(
                    exist = listOf()
                )
                checkNotNull(bas[1].findSessionSpan()).assertPropertyExistence(
                    exist = listOf("perm", "perm2")
                )
                checkNotNull(bas[2].findSessionSpan()).assertPropertyExistence(
                    exist = listOf("perm", "perm2", "perm3")
                )
            }
        )
    }

    @Test
    fun `permanent properties are persisted in cached payloads when bg activities are disabled`() {
        testRule.runTest(
            setupAction = {
                overriddenConfigService.backgroundActivityCaptureEnabled = false
            },
            testCaseAction = {
                startSdk()
                embrace.addSessionProperty("perm", "value", true)
                recordSession {
                    embrace.addSessionProperty("perm2", "value", true)
                }
                recordSession {
                    embrace.addSessionProperty("perm3", "value", true)
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                checkNotNull(sessions[0].getSessionSpan()).assertPropertyExistence(
                    exist = listOf("perm", "perm2")
                )
                checkNotNull(sessions[1].getSessionSpan()).assertPropertyExistence(
                    exist = listOf("perm", "perm2", "perm3")
                )
            }
        )
    }

    private fun Span.assertPropertyExistence(exist: List<String> = emptyList(), missing: List<String> = emptyList()) {
        exist.forEach {
            assertNotNull(getSessionProperty(it), "Missing session property with key: $it")
        }
        missing.forEach {
            assertNull(getSessionProperty(it))
        }
    }

    private companion object {
        const val PERM_KEY = "perm"
        const val PERM_KEY_2 = "perm2"
        const val PERM_KEY_3 = "perm3"
        const val PERM_KEY_4 = "perm4"
        const val TEMP_KEY = "temp"
        const val TEMP_KEY_2 = "temp2"
        const val PERM_VAL = "permVal"
        const val TEMP_VAL = "tempVal"
    }
}
