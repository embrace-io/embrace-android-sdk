package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.hasEventOfType
import io.embrace.android.embracesdk.assertions.hasSpanOfType
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.fakeCompletedAnrInterval
import io.embrace.android.embracesdk.fakes.fakeInProgressAnrInterval
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
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

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `session sent in full without gating`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(sessionConfig = SessionRemoteConfig()),
            testCaseAction = {
                simulateSession()
            },
            assertAction = {
                val payload = getSingleSessionEnvelope()
                assertSessionGating(payload, gated = false)
            }
        )
    }

    @Test
    fun `session gated`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                sessionConfig = SessionRemoteConfig(
                    sessionComponents = emptySet(),
                    fullSessionEvents = setOf(
                        "crashes",
                        "errors"
                    )
                )
            ),
            testCaseAction = {
                simulateSession()
            },
            assertAction = {
                val payload = getSingleSessionEnvelope()
                assertSessionGating(payload, gated = true)
            }
        )
    }

    private fun assertSessionGating(
        payload: Envelope<SessionPayload>,
        gated: Boolean,
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

    private fun EmbraceActionInterface.simulateSession(action: () -> Unit = {}) {
        recordSession {
            embrace.addBreadcrumb("Hello, world!")
            embrace.startView("MyActivity")
            EmbraceInternalApi.getInstance().internalInterface.logComposeTap(Pair(10f, 20f), "MyButton")
            embrace.endView("MyActivity")
            embrace.logWebView("https://example.com")

            // simulate ANR intervals
            val anrService = testRule.bootstrapper.anrModule.anrService as FakeAnrService
            anrService.data = listOf(fakeCompletedAnrInterval, fakeInProgressAnrInterval)

            clock.tick(10000) // enough to trigger new session
            action()
        }
    }
}
