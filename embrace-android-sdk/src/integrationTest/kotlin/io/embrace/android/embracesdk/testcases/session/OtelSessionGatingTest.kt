package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.IntegrationTestRule.Harness
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.createSessionBehavior
import io.embrace.android.embracesdk.fakes.fakeCompletedAnrInterval
import io.embrace.android.embracesdk.fakes.fakeInProgressAnrInterval
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.hasEventOfType
import io.embrace.android.embracesdk.hasSpanOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that OTel parts of sessions are gated.
 */
@RunWith(AndroidJUnit4::class)
internal class OtelSessionGatingTest {

    private var gatingConfig = SessionRemoteConfig(
        fullSessionEvents = setOf(),
        sessionComponents = setOf()
    )

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        Harness(
            overriddenConfigService = FakeConfigService(
                sessionBehavior = createSessionBehavior(remoteCfg = { RemoteConfig(sessionConfig = gatingConfig) })
            )
        )
    }

    @Test
    fun `session sent in full without gating`() {
        gatingConfig = SessionRemoteConfig()

        testRule.runTest(
            testCaseAction = {
                simulateSession()
            },
            assertAction = {
                val payload = harness.getSingleSession()
                assertSessionGating(payload, gated = false)
            }
        )
    }

    @Test
    fun `session gated`() {
        gatingConfig = SessionRemoteConfig(
            sessionComponents = emptySet(),
            fullSessionEvents = setOf(
                "crashes",
                "errors"
            )
        )
        testRule.runTest(
            testCaseAction = {
                simulateSession()
            },
            assertAction = {
                val payload = harness.getSingleSession()
                assertSessionGating(payload, gated = true)
            }
        )
    }

    private fun assertSessionGating(
        payload: Envelope<SessionPayload>,
        gated: Boolean
    ) {
        val sessionSpan = payload.findSessionSpan()
        assertNotNull(sessionSpan)
        assertEquals(!gated, sessionSpan.hasEventOfType(EmbType.System.Breadcrumb))
        assertEquals(!gated, payload.hasSpanOfType(EmbType.Ux.View))
        assertEquals(!gated, sessionSpan.hasEventOfType(EmbType.Ux.Tap))
        assertEquals(!gated, sessionSpan.hasEventOfType(EmbType.Ux.WebView))

        val spans = checkNotNull(payload.data.spans)
        val anrSpans = spans.filter { it.name == "emb-thread-blockage" }
        val expectedCount = when (gated) {
            true -> 0
            false -> 2
        }
        assertEquals(expectedCount, anrSpans.size)
    }

    private fun IntegrationTestRule.simulateSession(action: () -> Unit = {}) {
        harness.recordSession {
            embrace.addBreadcrumb("Hello, world!")
            embrace.startView("MyActivity")
            embrace.internalInterface.logComposeTap(Pair(10f, 20f), "MyButton")
            embrace.endView("MyActivity")
            harness.logWebView("https://example.com")

            // simulate ANR intervals
            val anrService = bootstrapper.anrModule.anrService as FakeAnrService
            anrService.data = listOf(fakeCompletedAnrInterval, fakeInProgressAnrInterval)

            harness.overriddenClock.tick(10000) // enough to trigger new session
            action()
        }
    }
}
