package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.assertions.findSpanSnapshotsOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.internal.worker.Worker.Background.PeriodicCacheWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that the session is periodically cached.
 */
@RunWith(AndroidJUnit4::class)
internal class PeriodicSessionCacheTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        val clock = FakeClock(IntegrationTestRule.DEFAULT_SDK_START_TIME_MS)
        val fakeInitModule = FakeInitModule(clock = clock)
        EmbraceSetupInterface(
            overriddenClock = clock,
            overriddenInitModule = fakeInitModule,
            overriddenWorkerThreadModule = FakeWorkerThreadModule(fakeInitModule = fakeInitModule, testWorkerName = PeriodicCacheWorker)
        )
    }

    @Test
    fun `session is periodically cached`() {
        with(testRule) {
            val executor = (setup.overriddenWorkerThreadModule as FakeWorkerThreadModule).executor
            val deliveryService = setup.overriddenDeliveryModule.deliveryService

            action.recordSession {
                executor.runCurrentlyBlocked()
                action.embrace.addSessionProperty("Test", "Test", true)

                val endMessage = checkNotNull(deliveryService.savedSessionEnvelopes.last().first)
                val span = endMessage.findSpanSnapshotOfType(EmbType.Ux.Session)
                val attrs = checkNotNull(span.attributes)
                assertEquals(false, attrs.findAttributeValue("emb.clean_exit").toBoolean())
                assertEquals(true, attrs.findAttributeValue("emb.terminated").toBoolean())
                assertNull(span.getSessionProperty("Test"))

                // trigger another periodic cache
                executor.moveForwardAndRunBlocked(2000)

                val nextMessage = checkNotNull(deliveryService.savedSessionEnvelopes.last().first)
                val nextSpan = nextMessage.findSpanSnapshotOfType(EmbType.Ux.Session)
                val nextAttrs = checkNotNull(nextSpan.attributes)
                assertEquals(false, nextAttrs.findAttributeValue("emb.clean_exit").toBoolean())
                assertEquals(true, nextAttrs.findAttributeValue("emb.terminated").toBoolean())
                assertEquals("Test", nextSpan.getSessionProperty("Test"))
            }

            val endMessage = assertion.getSingleSessionEnvelope()
            val span = endMessage.findSessionSpan()
            val attrs = checkNotNull(span.attributes)
            assertEquals(true, attrs.findAttributeValue("emb.clean_exit").toBoolean())
            assertEquals(false, attrs.findAttributeValue("emb.terminated").toBoolean())
            assertEquals("Test", span.getSessionProperty("Test"))
        }
    }
}
