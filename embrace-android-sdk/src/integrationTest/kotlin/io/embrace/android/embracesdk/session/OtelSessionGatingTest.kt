package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.IntegrationTestRule.*
import io.embrace.android.embracesdk.IntegrationTestRule.Harness
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.getSentSessionMessages
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.verifySessionHappened
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            }
        )
    )

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            Harness(
                fakeDeliveryModule = FakeDeliveryModule(
                    deliveryService = GatedDeliveryService(gatingService)
                )
            )
        }
    )

    @Before
    fun setUp() {
        assertTrue(testRule.harness.getSentSessionMessages().isEmpty())
    }

    @Test
    fun `session sent in full without gating`() {
        gatingConfig = SessionRemoteConfig()

        with(testRule) {
            simulateSession()
            val payload = harness.getSentSessionMessages().single()
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
            val payload = harness.getSentSessionMessages().single()
            verifySessionHappened(payload)
            assertSessionGating(payload, gated = true)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun assertSessionGating(
        payload: SessionMessage,
        gated: Boolean
    ) {
        val sessionSpan = payload.findSessionSpan()
        assertNotNull(sessionSpan)
    }

    private fun IntegrationTestRule.simulateSession(action: () -> Unit = {}) {
        harness.recordSession {
            embrace.addBreadcrumb("Hello, world!")
            harness.fakeClock.tick(10000) // enough to trigger new session
            action()
        }
    }

    private fun SessionMessage.findSessionSpan(): EmbraceSpanData =
        checkNotNull(findSpan("emb-session-span")) {
            "Session span not found"
        }

    private fun SessionMessage.findSpan(name: String): EmbraceSpanData? =
        spans?.singleOrNull { it.name == name }

    private fun EmbraceSpanData.findEvent(name: String): EmbraceSpanEvent? =
        events.singleOrNull { it.name == name }

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
