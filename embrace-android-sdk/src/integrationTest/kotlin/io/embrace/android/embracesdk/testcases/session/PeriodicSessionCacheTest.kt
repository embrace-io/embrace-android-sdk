package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.internal.worker.Worker.Background.PeriodicCacheWorker
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
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
            overriddenWorkerThreadModule = FakeWorkerThreadModule(
                fakeInitModule = fakeInitModule,
                testWorkerName = PeriodicCacheWorker
            )
        )
    }

    @Test
    fun `session is periodically cached`() {
        testRule.runTest(
            testCaseAction = {
                val executor =
                    (testRule.bootstrapper.workerThreadModule as FakeWorkerThreadModule).executor

                recordSession {
                    executor.runCurrentlyBlocked()
                    embrace.addSessionProperty("Test", "Test", true)

                    // trigger another periodic cache
                    executor.moveForwardAndRunBlocked(2000)
                }
            },
            assertAction = {
                val envelopes = getCachedSessionEnvelopes(2)
                val endMessage = envelopes[0]
                val span = endMessage.findSpanSnapshotOfType(EmbType.Ux.Session)
                assertNull(span.getSessionProperty("Test"))
                span.attributes?.assertMatches {
                    "emb.clean_exit" to false
                    "emb.terminated" to true
                }

                val nextMessage = envelopes[1]
                val nextSpan = nextMessage.findSpanSnapshotOfType(EmbType.Ux.Session)
                assertEquals("Test", nextSpan.getSessionProperty("Test"))
                nextSpan.attributes?.assertMatches {
                    "emb.clean_exit" to false
                    "emb.terminated" to true
                }

                val completedMessage = getSingleSessionEnvelope()
                val completedSpan = completedMessage.findSessionSpan()
                assertEquals("Test", completedSpan.getSessionProperty("Test"))
                completedSpan.attributes?.assertMatches {
                    "emb.clean_exit" to true
                    "emb.terminated" to false
                }
            }
        )
    }
}
