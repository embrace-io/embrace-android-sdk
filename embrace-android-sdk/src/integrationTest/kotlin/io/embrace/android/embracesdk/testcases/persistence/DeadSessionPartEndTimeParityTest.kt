package io.embrace.android.embracesdk.testcases.persistence

import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_PERIODIC_CACHE_INTERVAL_MS
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testcases.persistence.MultiFilePersistenceParityTest.PersistenceMode
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Verifies when a foreground session part that its process died in is ended once the next launch
 * delivers it, under both persistence layers.
 *
 * Neither layer ends the part at the death of the process: a user idle in the foreground before a kill
 * does not lengthen the session. The multi-file layer ends it when the part's files were last written,
 * which follows the last change by up to one write interval, whether the change records a time of its
 * own, such as a breadcrumb, or not, such as a session property. The legacy layer has no such record,
 * so it ends the part at the latest time a span in it started or ended, which neither change moves.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class DeadSessionPartEndTimeParityTest(
    private val persistenceMode: PersistenceMode,
    otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
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
    fun `a breadcrumb moves the end only under the multi-file layer`() {
        var lastWriteMs = 0L
        killMidPart {
            clock.tick(5_000)
            embrace.addBreadcrumb("last-thing-the-user-did")
            persistAfterOneInterval()
            lastWriteMs = clock.now()
        }
        assertDeliveredPartEnd(multiFileEndMs = lastWriteMs)
    }

    @Test
    fun `an update that records no time of its own moves the end only under the multi-file layer`() {
        var lastWriteMs = 0L
        killMidPart {
            clock.tick(5_000)
            embrace.addBreadcrumb("last-thing-the-user-did")
            persistAfterOneInterval()

            clock.tick(10_000)
            embrace.addUserSessionProperty("late", "value", PropertyScope.USER_SESSION)
            persistAfterOneInterval()
            lastWriteMs = clock.now()
        }
        assertDeliveredPartEnd(multiFileEndMs = lastWriteMs)
    }

    /**
     * Records a foreground part that the process dies in the middle of some time after [action] runs in it
     * during which nothing happens.
     */
    private fun killMidPart(action: EmbraceActionInterface.() -> Unit) {
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {
                recordSession(endInBackground = false) {
                    persistDueWrites()
                    action()
                }
                killProcess()
            },
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
            },
        )
    }

    /**
     * Starts the next process, which delivers the part the previous one died in, and asserts its session
     * span ended at [multiFileEndMs] under the multi-file layer, or when it started under the legacy layer,
     * as no other span in the part starts or ends later.
     */
    private fun assertDeliveredPartEnd(multiFileEndMs: Long) {
        testRule.bootstrapper.stop()
        testRule.runTest(
            persistedRemoteConfig = remoteConfig(),
            testCaseAction = {},
            assertAction = {
                val sessionSpan = getSessionEnvelopes(1).single().findSessionPartSpan()
                val expectedEndMs = when (persistenceMode) {
                    PersistenceMode.LEGACY -> sessionSpan.startTimeNanos?.nanosToMillis()
                    PersistenceMode.MULTI_FILE -> multiFileEndMs
                }
                assertEquals(expectedEndMs, sessionSpan.endTimeNanos?.nanosToMillis())
            },
        )
    }

    /**
     * Lets one write interval pass and runs the writes due by then, so both layers persist what
     * changed: the multi-file layer's debounced writes and the legacy layer's periodic cache tick.
     */
    private fun EmbraceActionInterface.persistAfterOneInterval() {
        clock.tick(WRITE_INTERVAL_MS)
        persistDueWrites()
    }

    private fun persistDueWrites() {
        persistenceWorkers.forEach { worker ->
            testRule.setup.getFakedWorkerExecutor(worker).runCurrentlyBlocked()
        }
    }

    /**
     * Kills the process: every write still waiting on a debounce or a cache tick dies with it.
     */
    private fun killProcess() {
        persistenceWorkers.forEach { worker ->
            testRule.setup.getFakedWorkerExecutor(worker).apply {
                blockingMode = true
                shutdownNow()
            }
        }
        checkNotNull(testRule.bootstrapper.deliveryModule).intakeService.shutdown()
    }

    private fun remoteConfig() = RemoteConfig(
        pctMultiFilePersistenceEnabled = when (persistenceMode) {
            PersistenceMode.MULTI_FILE -> 100.0f
            PersistenceMode.LEGACY -> 0.0f
        },
        backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
    )

    internal companion object {
        // the multi-file layer debounces its writes by no more than the legacy layer's cache interval
        private const val WRITE_INTERVAL_MS = DEFAULT_PERIODIC_CACHE_INTERVAL_MS

        private val persistenceWorkers = listOf(
            Worker.Background.SessionPersistenceWorker,
            Worker.Background.PeriodicCacheWorker,
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun modes(): List<Array<Any>> = PersistenceMode.entries.flatMap { persistenceMode ->
            OtelSdkMode.entries.map { otelSdkMode -> arrayOf(persistenceMode, otelSdkMode) }
        }
    }
}
