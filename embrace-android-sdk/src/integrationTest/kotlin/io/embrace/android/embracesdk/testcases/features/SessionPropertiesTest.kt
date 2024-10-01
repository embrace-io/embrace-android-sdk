package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.checkNextSavedBackgroundActivity
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentBackgroundActivities
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.getSessionId
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        IntegrationTestRule.Harness(startImmediately = false)
    }

    @Test
    fun `session properties additions and removal works at all stages app state transition`() {
        testRule.runTest(
            testCaseAction = {
                startSdk()
                embrace.addSessionProperty(PERM_KEY, PERM_VAL, true)
                embrace.addSessionProperty(PERM_KEY_2, PERM_VAL, true)
                harness.recordSession {
                    embrace.addSessionProperty(TEMP_KEY, TEMP_VAL, false)
                    embrace.addSessionProperty(PERM_KEY_3, PERM_VAL, true)
                    embrace.removeSessionProperty(PERM_KEY_2)
                    embrace.removeSessionProperty(TEMP_KEY)
                }
                embrace.addSessionProperty(PERM_KEY_4, PERM_VAL, true)
                embrace.removeSessionProperty(PERM_KEY_3)
                embrace.removeSessionProperty(PERM_KEY_4)

                harness.recordSession()
            },
            assertAction = {
                val sessions = harness.getSentSessions(2)
                val bas = harness.getSentBackgroundActivities(2)

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
                harness.recordSession {
                    embrace.addSessionProperty(PERM_KEY_3, PERM_VAL, true)
                    embrace.removeSessionProperty(PERM_KEY_2)
                }

                embrace.addSessionProperty(TEMP_KEY_2, TEMP_VAL, false)
                embrace.removeSessionProperty(PERM_KEY_3)

                harness.recordSession {
                    embrace.addSessionProperty(PERM_KEY_4, PERM_VAL, true)
                }
            },
            assertAction = {
                val sessions = harness.getSentSessions(2)
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
                harness.recordSession {
                    embrace.addSessionProperty(TEMP_KEY, TEMP_VAL, false)
                }
                harness.recordSession()
            },
            assertAction = {
                val sessions = harness.getSentSessions(2)
                val firstSession = sessions[0]
                val secondSession = sessions[1]

                val bgActivities = harness.getSentBackgroundActivities(2)
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
        with(testRule) {
            startSdk()
            harness.recordSession()
            harness.checkNextSavedBackgroundActivity(
                action = {
                    embrace.addSessionProperty("temp", "value", false)
                },
                validationFn = { envelope ->
                    checkNotNull(envelope.getSessionSpan()).assertPropertyExistence(exist = listOf("temp"))
                }
            )

            harness.recordSession()
            val session = harness.getSentSessions(2).last()
            checkNotNull(session.getSessionSpan()).assertPropertyExistence(missing = listOf("temp"))

            val bg = harness.getSentBackgroundActivities(2).last()
            checkNotNull(bg.getSessionSpan()).assertPropertyExistence(exist = listOf("temp"))
        }
    }

    @Test
    fun `permanent properties are persisted in cached payloads`() {
        with(testRule) {
            startSdk()
            harness.recordSession()
            var session = harness.getSingleSession()
            var lastSessionId = session.getSessionId()

            harness.checkNextSavedBackgroundActivity(
                action = {
                    embrace.addSessionProperty("perm", "value", true)
                },
                validationFn = { envelope ->
                    assertNotEquals(lastSessionId, envelope.getSessionId())
                    with(checkNotNull(envelope.getSessionSpan())) {
                        assertPropertyExistence(
                            exist = listOf("perm")
                        )
                        lastSessionId = checkNotNull(embrace.currentSessionId)
                    }
                }
            )

            harness.recordSession {
                embrace.addSessionProperty("perm2", "value", true)
            }
            session = harness.getSentSessions(2).last()
            checkNotNull(session.getSessionSpan()).assertPropertyExistence(
                exist = listOf("perm", "perm2")
            )

            harness.checkNextSavedBackgroundActivity(
                action = {
                    embrace.addSessionProperty("perm3", "value", true)
                },
                validationFn = { envelope ->
                    assertNotEquals(lastSessionId, envelope.getSessionId())
                    with(checkNotNull(envelope.getSessionSpan())) {
                        assertPropertyExistence(
                            exist = listOf("perm", "perm2", "perm3")
                        )
                        lastSessionId = checkNotNull(embrace.currentSessionId)
                    }
                }
            )

            harness.recordSession()
            val session2 = harness.getSentSessions(3).last()
            checkNotNull(session2.getSessionSpan()).assertPropertyExistence(
                exist = listOf("perm", "perm2", "perm3")
            )
        }
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
                harness.recordSession {
                    embrace.addSessionProperty("perm2", "value", true)
                }
                harness.recordSession {
                    embrace.addSessionProperty("perm3", "value", true)
                }
            },
            assertAction = {
                val sessions = harness.getSentSessions(2)
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