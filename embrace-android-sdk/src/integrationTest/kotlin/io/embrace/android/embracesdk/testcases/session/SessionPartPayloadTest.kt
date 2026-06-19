package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.assertNoPreviousSession
import io.embrace.android.embracesdk.assertions.assertPreviousSession
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getOtelSessionId
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.hasLinkToEmbraceSpan
import io.embrace.android.embracesdk.assertions.hasSpanSnapshotsOfType
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.getSessionProperty
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts the shape and structure of session part payloads: device/app envelope attributes, session-scoped
 * data clearing at part boundaries, span links, session validity, life-event types, and part sequencing.
 */
@RunWith(AndroidJUnit4::class)
internal class SessionPartPayloadTest {
    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `device and app attributes are present in session envelope`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()

            },
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertEquals("spans", type)
                    with(checkNotNull(resource)) {
                        assertTrue(checkNotNull(appVersion).isNotBlank())
                        assertTrue(checkNotNull(sdkVersion).isNotBlank())
                        assertTrue(checkNotNull(osVersion).isNotBlank())
                        assertTrue(checkNotNull(osName).isNotBlank())
                        assertTrue(checkNotNull(deviceModel).isNotBlank())
                        assertEquals(AppFramework.NATIVE, appFramework)
                    }
                    assertTrue(getOtelSessionId().isNotBlank())
                }
            }
        )
    }

    /**
     * Sets some data on the first session, and asserts that it is not present on the second
     * session.
     *
     * This test case is not exhaustive - it's meant to be a canary in the coalmine that
     * warns if no data at all is being cleared between sessions.
     */
    @Test
    fun `session messages have a clean boundary`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.addUserSessionProperty("foo", "bar", PropertyScope.USER_SESSION)
                }
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)

                // validate info added to first session
                val span = sessions[0].findSessionSpan()
                assertEquals("bar", span.getSessionProperty("foo"))

                // confirm info not added to next session
                val nextSpan = sessions[1].findSessionSpan()
                assertNull(nextSpan.getSessionProperty("foo"))
            }
        )
    }

    @Test
    fun `there is always a valid session when background activity is enabled`() {
        val ids = mutableListOf<String?>()

        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = true)),
            testCaseAction = {
                recordSession {
                    ids.add(embrace.currentUserSessionId)
                }
                ids.add(embrace.currentUserSessionId)
                recordSession {
                    ids.add(embrace.currentUserSessionId)
                }
            },
            assertAction = {
                ids.forEach {
                    assertFalse(it.isNullOrBlank())
                }
            }
        )
    }

    @Test
    fun `session span event limits do not affect logging maximum breadcrumbs`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    repeat(101) {
                        embrace.addBreadcrumb("breadcrumb $it")
                    }
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                assertEquals(100, session.getSessionSpan()?.events?.size)
            }
        )
    }

    @Test
    fun `correct span links created when a span ends`() {
        testRule.runTest(
            testCaseAction = {
                val startInSessionEndInBackground = checkNotNull(embrace.createSpan("startInSessionEndInBackground"))
                val startAndEndInDifferentSessions = checkNotNull(embrace.createSpan("startAndEndInDifferentSessions"))
                recordSession {
                    embrace.recordSpan("startAndEndInSession") {
                        assertTrue(startInSessionEndInBackground.start())
                        assertTrue(startAndEndInDifferentSessions.start())
                    }
                }
                assertTrue(startInSessionEndInBackground.stop())
                recordSession {
                    assertTrue(startAndEndInDifferentSessions.stop())
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val firstSession = sessions[0]
                val secondSession = sessions[1]
                val firstSessionSpan = checkNotNull(firstSession.getSessionSpan())
                val startAndEndInSession = checkNotNull(firstSession.data.spans?.single { it.name == "startAndEndInSession"})

                assertTrue(firstSessionSpan.hasLinkToEmbraceSpan(startAndEndInSession, LinkType.EndedIn))
                assertFalse(firstSessionSpan.hasLinkToEmbraceSpan(firstSessionSpan, LinkType.EndedIn))
                assertTrue(startAndEndInSession.hasLinkToEmbraceSpan(firstSessionSpan, LinkType.EndSession))

                val secondSessionSpan = checkNotNull(secondSession.getSessionSpan())
                val startInSessionEndInBackground =
                    checkNotNull(secondSession.data.spans?.single { it.name == "startInSessionEndInBackground"})
                val startAndEndInDifferentSessions =
                    checkNotNull(secondSession.data.spans?.single { it.name == "startAndEndInDifferentSessions"})
                assertTrue(secondSessionSpan.hasLinkToEmbraceSpan(startAndEndInDifferentSessions, LinkType.EndedIn))
                assertFalse(secondSessionSpan.hasLinkToEmbraceSpan(secondSessionSpan, LinkType.EndedIn))
                assertTrue(startAndEndInDifferentSessions.hasLinkToEmbraceSpan(secondSessionSpan, LinkType.EndSession))
                assertEquals(0, startInSessionEndInBackground.links?.size)
            }
        )
    }

    @Test
    fun `stateful session records correct life-event start and end types with distinct part ids`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.addBreadcrumb("Hello, World!")
                }

                // capture another session
                recordSession()
            },
            assertAction = {
                // verify first session
                val messages = getSessionEnvelopes(2)
                val first = messages[0]
                first.findSessionSpan().attributes?.assertMatches(mapOf(
                    EmbSessionAttributes.EMB_SESSION_START_TYPE to LifeEventType.STATE.name.lowercase(Locale.ENGLISH),
                    EmbSessionAttributes.EMB_SESSION_END_TYPE to LifeEventType.STATE.name.lowercase(Locale.ENGLISH),
                    EmbSessionAttributes.EMB_ERROR_LOG_COUNT to 0
                ))

                assertFalse(first.hasSpanSnapshotsOfType(EmbType.Ux.Session))

                // verify second session
                val second = messages[1]
                assertNotEquals(first.getSessionPartId(), second.getSessionPartId())
            }
        )
    }

    @Test
    fun `session metadata are recorded correctly when background sessions enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)),
            testCaseAction = {
                recordSession()
                recordSession()
                recordSession()
            },
            assertAction = {
                val bas = getSessionEnvelopes(expectedSize = 3, state = AppState.BACKGROUND)
                val sessions = getSessionEnvelopes(3)
                val firstBa = bas[0]
                val secondBa = bas[1]
                val thirdBa = bas[2]
                val firstSession = sessions[0]
                val secondSession = sessions[1]
                val thirdSession = sessions[2]

                val firstBaSessionSpan = firstBa.getValidatedSessionSpan()

                val firstSessionSpan = firstSession.getValidatedSessionSpan(
                    previousSessionSpan = firstBaSessionSpan,
                    previousSessionId = firstBa.getOtelSessionId()
                )

                val secondBaSessionSpan = secondBa.getValidatedSessionSpan(
                    isColdStart = false,
                    previousSessionSpan = firstSessionSpan,
                    previousSessionId = firstSession.getOtelSessionId()
                )

                val secondSessionSpan = secondSession.getValidatedSessionSpan(
                    isColdStart = false,
                    previousSessionSpan = secondBaSessionSpan,
                    previousSessionId = secondBa.getOtelSessionId()
                )

                val thirdBaSessionSpan = thirdBa.getValidatedSessionSpan(
                    isColdStart = false,
                    previousSessionSpan = secondSessionSpan,
                    previousSessionId = secondSession.getOtelSessionId()
                )

                thirdSession.getValidatedSessionSpan(
                    isColdStart = false,
                    previousSessionSpan = thirdBaSessionSpan,
                    previousSessionId = thirdBa.getOtelSessionId()
                )
            }
        )
    }

    @Test
    fun `session metadata are recorded correctly when background sessions disabled`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
                recordSession()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(3)
                val first = sessions[0]
                val second = sessions[1]
                val third = sessions[2]

                val firstSessionSpan = first.getValidatedSessionSpan()

                val secondSessionSpan = second.getValidatedSessionSpan(
                    isColdStart = false,
                    previousSessionSpan = firstSessionSpan,
                    previousSessionId = first.getOtelSessionId()
                )

                third.getValidatedSessionSpan(
                    isColdStart = false,
                    previousSessionSpan = secondSessionSpan,
                    previousSessionId = second.getOtelSessionId()
                )
            }
        )
    }

    private fun Envelope<SessionPartPayload>.getValidatedSessionSpan(
        isColdStart: Boolean = true,
        previousSessionSpan: Span? = null,
        previousSessionId: String? = null,
    ): Span {
        val sessionSpan = findSessionSpan()
        assertFalse(hasSpanSnapshotsOfType(EmbType.Ux.Session))
        with(sessionSpan) {
            checkNotNull(attributes).assertMatches(
                mapOf(
                    EmbSessionAttributes.EMB_COLD_START to isColdStart
                )
            )

            if (previousSessionSpan != null && previousSessionId != null) {
                assertPreviousSession(previousSessionSpan, previousSessionId)
            } else {
                assertNoPreviousSession()
            }
        }
        return sessionSpan
    }
}
