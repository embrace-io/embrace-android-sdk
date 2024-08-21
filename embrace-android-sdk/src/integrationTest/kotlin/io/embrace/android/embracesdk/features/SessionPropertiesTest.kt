package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getLastSavedBackgroundActivity
import io.embrace.android.embracesdk.getLastSavedSession
import io.embrace.android.embracesdk.getLastSentBackgroundActivity
import io.embrace.android.embracesdk.getSentBackgroundActivities
import io.embrace.android.embracesdk.getSessionId
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
        with(testRule) {
            startSdk()
            embrace.addSessionProperty(PERM_KEY, PERM_VAL, true)
            embrace.addSessionProperty(PERM_KEY_2, PERM_VAL, true)
            val session1 = checkNotNull(harness.recordSession {
                embrace.addSessionProperty(TEMP_KEY, TEMP_VAL, false)
                embrace.addSessionProperty(PERM_KEY_3, PERM_VAL, true)
                embrace.removeSessionProperty(PERM_KEY_2)
                embrace.removeSessionProperty(TEMP_KEY)
            })
            val ba1 = checkNotNull(harness.getLastSentBackgroundActivity())
            embrace.addSessionProperty(PERM_KEY_4, PERM_VAL, true)
            embrace.removeSessionProperty(PERM_KEY_3)
            embrace.removeSessionProperty(PERM_KEY_4)

            val session2 = checkNotNull(harness.recordSession())
            val ba2 = checkNotNull(harness.getLastSentBackgroundActivity())

            ba1.findSessionSpan().assertPropertyExistence(
                exist = listOf(PERM_KEY, PERM_KEY_2)
            )
            session1.findSessionSpan().assertPropertyExistence(
                exist = listOf(PERM_KEY, PERM_KEY_3),
                missing = listOf(TEMP_KEY, PERM_KEY_2)
            )
            ba2.findSessionSpan().assertPropertyExistence(
                exist = listOf(PERM_KEY),
                missing = listOf(TEMP_KEY, PERM_KEY_2, PERM_KEY_3, PERM_KEY_4)
            )
            session2.findSessionSpan().assertPropertyExistence(
                exist = listOf(PERM_KEY),
                missing = listOf(TEMP_KEY, TEMP_KEY_2, PERM_KEY_2, PERM_KEY_3)
            )
        }
    }

    @Test
    fun `session properties work with background activity disabled`() {
        with(testRule) {
            harness.overriddenConfigService.backgroundActivityCaptureEnabled = false
            startSdk()
            embrace.addSessionProperty(PERM_KEY, PERM_VAL, true)
            embrace.addSessionProperty(TEMP_KEY, TEMP_VAL, false)
            embrace.addSessionProperty(PERM_KEY_2, PERM_VAL, true)
            val session1 = checkNotNull(harness.recordSession {
                embrace.addSessionProperty(PERM_KEY_3, PERM_VAL, true)
                embrace.removeSessionProperty(PERM_KEY_2)
            })

            embrace.addSessionProperty(TEMP_KEY_2, TEMP_VAL, false)
            embrace.removeSessionProperty(PERM_KEY_3)

            val session2 = checkNotNull(harness.recordSession {
                embrace.addSessionProperty(PERM_KEY_4, PERM_VAL, true)
            })

            session1.findSessionSpan().assertPropertyExistence(
                exist = listOf(TEMP_KEY, PERM_KEY, PERM_KEY_3),
                missing = listOf(PERM_KEY_2)
            )

            session2.findSessionSpan().assertPropertyExistence(
                exist = listOf(TEMP_KEY_2, PERM_KEY, PERM_KEY_4),
                missing = listOf(TEMP_KEY, PERM_KEY_2, PERM_KEY_3)
            )
        }
    }

    @Test
    fun `temp properties are cleared in next session`() {
        with(testRule) {
            startSdk()
            embrace.addSessionProperty(PERM_KEY, PERM_VAL, true)
            val firstSession = checkNotNull(harness.recordSession {
                embrace.addSessionProperty(TEMP_KEY, TEMP_VAL, false)
            })
            val secondSession = checkNotNull(harness.recordSession())
            val bgActivities = harness.getSentBackgroundActivities()
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
    }

    @Test
    fun `adding properties in bg activity modifications change the cached payload`() {
        with(testRule) {
            startSdk()
            harness.recordSession()
            embrace.addSessionProperty("temp", "value", false)
            val bgSnapshot = checkNotNull(harness.getLastSavedBackgroundActivity())
            checkNotNull(bgSnapshot.getSessionSpan()).assertPropertyExistence(exist = listOf("temp"))

            val session = checkNotNull(harness.recordSession())
            checkNotNull(session.getSessionSpan()).assertPropertyExistence(missing = listOf("temp"))

            val bg = checkNotNull(harness.getLastSentBackgroundActivity())
            checkNotNull(bg.getSessionSpan()).assertPropertyExistence(exist = listOf("temp"))
        }
    }

    @Test
    fun `permanent properties are persisted in cached payloads`() {
        with(testRule) {
            startSdk()
            var lastSessionId = checkNotNull(harness.recordSession()).getSessionId()
            embrace.addSessionProperty("perm", "value", true)
            with(checkNotNull(harness.getLastSavedBackgroundActivity()?.getSessionSpan())) {
                assertNotEquals(lastSessionId, embrace.currentSessionId)
                assertPropertyExistence(
                    exist = listOf("perm")
                )
                lastSessionId = checkNotNull(embrace.currentSessionId)
            }

            harness.recordSession {
                with(checkNotNull(harness.getLastSavedSession()?.getSessionSpan())) {
                    assertNotEquals(lastSessionId, embrace.currentSessionId)
                    assertPropertyExistence(
                        exist = listOf("perm")
                    )
                    lastSessionId = checkNotNull(embrace.currentSessionId)
                }
                embrace.addSessionProperty("perm2", "value", true)
                checkNotNull(harness.getLastSavedSession()?.getSessionSpan()).assertPropertyExistence(
                    exist = listOf("perm", "perm2")
                )
            }

            with(checkNotNull(harness.getLastSavedBackgroundActivity()?.getSessionSpan())) {
                assertNotEquals(lastSessionId, embrace.currentSessionId)
                assertPropertyExistence(
                    exist = listOf("perm", "perm2")
                )
                lastSessionId = checkNotNull(embrace.currentSessionId)
            }

            embrace.addSessionProperty("perm3", "value", true)
            checkNotNull(harness.getLastSavedBackgroundActivity()?.getSessionSpan()).assertPropertyExistence(
                exist = listOf("perm", "perm2", "perm3")
            )

            harness.recordSession {
                with(checkNotNull(harness.getLastSavedSession()?.getSessionSpan())) {
                    assertNotEquals(lastSessionId, embrace.currentSessionId)
                    assertPropertyExistence(
                        exist = listOf("perm", "perm2", "perm3")
                    )
                    lastSessionId = checkNotNull(embrace.currentSessionId)
                }
            }
        }
    }

    @Test
    fun `permanent properties are persisted in cached payloads when bg activities are disabled`() {
        with(testRule) {
            harness.overriddenConfigService.backgroundActivityCaptureEnabled = false
            startSdk()
            embrace.addSessionProperty("perm", "value", true)
            var lastSessionId = checkNotNull(harness.recordSession()).getSessionId()
            harness.recordSession {
                with(checkNotNull(harness.getLastSavedSession()?.getSessionSpan())) {
                    assertNotEquals(lastSessionId, embrace.currentSessionId)
                    assertPropertyExistence(
                        exist = listOf("perm")
                    )
                    lastSessionId = checkNotNull(embrace.currentSessionId)
                }
                embrace.addSessionProperty("perm2", "value", true)
                checkNotNull(harness.getLastSavedSession()?.getSessionSpan()).assertPropertyExistence(
                    exist = listOf("perm", "perm2")
                )
            }
            harness.recordSession {
                with(checkNotNull(harness.getLastSavedSession()?.getSessionSpan())) {
                    assertNotEquals(lastSessionId, embrace.currentSessionId)
                    assertPropertyExistence(
                        exist = listOf("perm", "perm2")
                    )
                    lastSessionId = checkNotNull(embrace.currentSessionId)
                }
                embrace.addSessionProperty("perm3", "value", true)
                checkNotNull(harness.getLastSavedSession()?.getSessionSpan()).assertPropertyExistence(
                    exist = listOf("perm", "perm2", "perm3")
                )
            }
        }
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