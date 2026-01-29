package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.hasLinkToEmbraceSpan
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SessionSpanTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `there is always a valid session when background activity is enabled`() {
        val ids = mutableListOf<String?>()

        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = true)),
            testCaseAction = {
                recordSession {
                    ids.add(embrace.currentSessionId)
                }
                ids.add(embrace.currentSessionId)
                recordSession {
                    ids.add(embrace.currentSessionId)
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
}
