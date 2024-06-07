package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.IntegrationTestRule.Harness
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.config.remote.OTelRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeAnrService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeCompletedAnrInterval
import io.embrace.android.embracesdk.fakes.fakeInProgressAnrInterval
import io.embrace.android.embracesdk.fakes.fakeOTelBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.hasEventOfType
import io.embrace.android.embracesdk.hasSpanOfType
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.verifySessionHappened
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
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

    private val gatingService = EmbraceGatingService(
        FakeConfigService(
            sessionBehavior = fakeSessionBehavior {
                RemoteConfig(sessionConfig = gatingConfig)
            },
        ),
        EmbLoggerImpl()
    )

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        Harness(
            overriddenDeliveryModule = FakeDeliveryModule(
                deliveryService = GatedDeliveryService(gatingService)
            )
        )
    }

    @Before
    fun setUp() {
        assertTrue(testRule.harness.getSentSessions().isEmpty())
    }

    @Test
    fun `session sent in full without gating`() {
        gatingConfig = SessionRemoteConfig()

        with(testRule) {
            simulateSession()
            val payload = harness.getSentSessions().single()
            verifySessionHappened(payload)
            assertSessionGating(payload, gated = false)
        }
    }

    @Test
    fun `session gated`() {
        gatingConfig = SessionRemoteConfig(
            sessionComponents = emptySet(),
            fullSessionEvents = setOf(
                SessionGatingKeys.FULL_SESSION_CRASHES,
                SessionGatingKeys.FULL_SESSION_ERROR_LOGS
            )
        )

        with(testRule) {
            simulateSession()
            val payload = harness.getSentSessions().single()
            verifySessionHappened(payload)
            assertSessionGating(payload, gated = true)
        }
    }

    private fun assertSessionGating(
        payload: SessionMessage,
        gated: Boolean
    ) {
        val sessionSpan = payload.findSessionSpan()
        assertNotNull(sessionSpan)
        assertEquals(!gated, sessionSpan.hasEventOfType(EmbType.System.Breadcrumb))
        assertEquals(!gated, payload.hasSpanOfType(EmbType.Ux.View))
        assertEquals(!gated, sessionSpan.hasEventOfType(EmbType.Ux.Tap))
        assertEquals(!gated, sessionSpan.hasEventOfType(EmbType.Ux.WebView))

        val spans = checkNotNull(payload.data?.spans)
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

    /**
     * Wraps a fake delivery service around the [GatingService] so we can assert against
     * gating behavior.
     */
    private class GatedDeliveryService(
        private val gatingService: GatingService,
    ) : FakeDeliveryService() {

        override fun sendSession(
            sessionMessage: SessionMessage,
            snapshotType: SessionSnapshotType
        ) = super.sendSession(
            gatingService.gateSessionMessage(sessionMessage),
            snapshotType
        )
    }
}
