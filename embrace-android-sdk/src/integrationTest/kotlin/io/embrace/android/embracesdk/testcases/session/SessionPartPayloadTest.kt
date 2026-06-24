package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.assertNoPreviousSessionPart
import io.embrace.android.embracesdk.assertions.assertPreviousSessionPart
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
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
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
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
                val span = sessions[0].findSessionPartSpan()
                assertEquals("bar", span.getSessionProperty("foo"))

                // confirm info not added to next session
                val nextSpan = sessions[1].findSessionPartSpan()
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
                assertEquals(100, session.getSessionPartSpan()?.events?.size)
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
                val firstSessionPartSpan = checkNotNull(firstSession.getSessionPartSpan())
                val startAndEndInSession = checkNotNull(firstSession.data.spans?.single { it.name == "startAndEndInSession"})

                assertTrue(firstSessionPartSpan.hasLinkToEmbraceSpan(startAndEndInSession, LinkType.EndedIn))
                assertFalse(firstSessionPartSpan.hasLinkToEmbraceSpan(firstSessionPartSpan, LinkType.EndedIn))
                assertTrue(startAndEndInSession.hasLinkToEmbraceSpan(firstSessionPartSpan, LinkType.EndSessionPart))

                val secondSessionPartSpan = checkNotNull(secondSession.getSessionPartSpan())
                val startInSessionEndInBackground =
                    checkNotNull(secondSession.data.spans?.single { it.name == "startInSessionEndInBackground"})
                val startAndEndInDifferentSessions =
                    checkNotNull(secondSession.data.spans?.single { it.name == "startAndEndInDifferentSessions"})
                assertTrue(secondSessionPartSpan.hasLinkToEmbraceSpan(startAndEndInDifferentSessions, LinkType.EndedIn))
                assertFalse(secondSessionPartSpan.hasLinkToEmbraceSpan(secondSessionPartSpan, LinkType.EndedIn))
                assertTrue(startAndEndInDifferentSessions.hasLinkToEmbraceSpan(secondSessionPartSpan, LinkType.EndSessionPart))
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
                first.findSessionPartSpan().attributes?.assertMatches(mapOf(
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

                val firstBaSessionPartSpan = firstBa.getValidatedSessionPartSpan()

                val firstSessionPartSpan = firstSession.getValidatedSessionPartSpan(
                    previousSessionPartSpan = firstBaSessionPartSpan,
                    previousSessionPartId = firstBa.getSessionPartId()
                )

                val secondBaSessionPartSpan = secondBa.getValidatedSessionPartSpan(
                    isColdStart = false,
                    previousSessionPartSpan = firstSessionPartSpan,
                    previousSessionPartId = firstSession.getSessionPartId()
                )

                val secondSessionPartSpan = secondSession.getValidatedSessionPartSpan(
                    isColdStart = false,
                    previousSessionPartSpan = secondBaSessionPartSpan,
                    previousSessionPartId = secondBa.getSessionPartId()
                )

                val thirdBaSessionPartSpan = thirdBa.getValidatedSessionPartSpan(
                    isColdStart = false,
                    previousSessionPartSpan = secondSessionPartSpan,
                    previousSessionPartId = secondSession.getSessionPartId()
                )

                thirdSession.getValidatedSessionPartSpan(
                    isColdStart = false,
                    previousSessionPartSpan = thirdBaSessionPartSpan,
                    previousSessionPartId = thirdBa.getSessionPartId()
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

                val firstSessionPartSpan = first.getValidatedSessionPartSpan()

                val secondSessionPartSpan = second.getValidatedSessionPartSpan(
                    isColdStart = false,
                    previousSessionPartSpan = firstSessionPartSpan,
                    previousSessionPartId = first.getSessionPartId()
                )

                third.getValidatedSessionPartSpan(
                    isColdStart = false,
                    previousSessionPartSpan = secondSessionPartSpan,
                    previousSessionPartId = second.getSessionPartId()
                )
            }
        )
    }

    private fun Envelope<SessionPartPayload>.getValidatedSessionPartSpan(
        isColdStart: Boolean = true,
        previousSessionPartSpan: Span? = null,
        previousSessionPartId: String? = null,
    ): Span {
        val sessionPartSpan = findSessionPartSpan()
        assertFalse(hasSpanSnapshotsOfType(EmbType.Ux.Session))
        with(sessionPartSpan) {
            checkNotNull(attributes).assertMatches(
                mapOf(
                    EmbSessionAttributes.EMB_COLD_START to isColdStart
                )
            )

            if (previousSessionPartSpan != null && previousSessionPartId != null) {
                assertPreviousSessionPart(previousSessionPartSpan, previousSessionPartId)
            } else {
                assertNoPreviousSessionPart()
            }
        }
        return sessionPartSpan
    }
}
