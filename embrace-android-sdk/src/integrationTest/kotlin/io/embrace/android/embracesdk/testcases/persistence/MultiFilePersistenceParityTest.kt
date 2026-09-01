package io.embrace.android.embracesdk.testcases.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that a minimal session payload reaches the server whether it was persisted by the legacy
 * single-file layer or by the multi-file layer.
 */
@RunWith(AndroidJUnit4::class)
internal class MultiFilePersistenceParityTest {

    private val multiFileEnabled = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f)

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            workersToFake = listOf(
                Worker.Background.SessionPersistenceWorker,
                Worker.Background.PeriodicCacheWorker,
                Worker.Background.NonIoRegWorker,
                Worker.Background.IoRegWorker,
            ),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
        }
    }

    @Test
    fun `legacy single-file persistence delivers the session payload`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertMinimalSessionPayload(getSingleSessionEnvelope())
            },
        )
    }

    @Test
    fun `multi-file persistence delivers the session payload once its writes complete`() {
        testRule.runTest(
            persistedRemoteConfig = multiFileEnabled,
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                // one payload comes from the legacy writer, the other after the multi-file writes have completed.
                // this assertion waits for both.
                val envelopes = getSessionEnvelopes(2, assertOrdering = false)
                assertEquals(1, envelopes.map(Envelope<SessionPartPayload>::getSessionPartId).distinct().size)
                envelopes.forEach(::assertMinimalSessionPayload)
            },
        )
    }

    /**
     * Asserts the basic shape of a delivered session payload. Both persistence paths must satisfy
     * this identically.
     */
    private fun assertMinimalSessionPayload(envelope: Envelope<SessionPartPayload>) {
        assertEquals("spans", envelope.type)
        assertEquals("2.5.1", envelope.resource?.appVersion)

        val sessionSpan = envelope.findSessionPartSpan()
        assertEquals("emb-session", sessionSpan.name)
        assertNotNull(sessionSpan.endTimeNanos)
        assertEquals("foreground", sessionSpan.attributes?.findAttributeValue(EmbSessionAttributes.EMB_STATE))
    }
}
