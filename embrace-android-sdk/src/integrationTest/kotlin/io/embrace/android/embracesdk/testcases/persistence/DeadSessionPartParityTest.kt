package io.embrace.android.embracesdk.testcases.persistence

import io.embrace.android.embracesdk.PropertyScope
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_PERIODIC_CACHE_INTERVAL_MS
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testcases.features.createNativeSymbolsForCurrentArch
import io.embrace.android.embracesdk.testcases.persistence.MultiFilePersistenceParityTest.PersistenceMode
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.actions.StoredNativeCrashData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Verifies what the next launch delivers for a foreground session part that its process died in,
 * under both persistence modes.
 *
 * Neither mode ends the part at the death of the process: a user idle in the foreground before a kill
 * does not lengthen the session. The multi-file mode ends it when the part's files were last written,
 * which follows the last change by up to one write interval, whether the change records a time of its
 * own, such as a breadcrumb, or not, such as a session property. The single-file mode has no such
 * record, so it ends the part at the latest time a span in it started or ended, which neither change
 * moves.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class DeadSessionPartParityTest(
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
    fun `a breadcrumb moves the end only under the multi-file mode`() {
        var lastWriteMs = 0L
        killActiveSessionPart {
            clock.tick(5_000)
            embrace.addBreadcrumb("last-thing-the-user-did")
            persistAfterOneInterval()
            lastWriteMs = clock.now()
        }
        relaunch {
            assertDeliveredPartEnd(multiFileEndMs = lastWriteMs)
        }
    }

    @Test
    fun `an update that records no time of its own moves the end only under the multi-file mode`() {
        var lastWriteMs = 0L
        killActiveSessionPart {
            clock.tick(5_000)
            embrace.addBreadcrumb("last-thing-the-user-did")
            persistAfterOneInterval()

            clock.tick(10_000)
            embrace.addUserSessionProperty("late", "value", PropertyScope.USER_SESSION)
            persistAfterOneInterval()
            lastWriteMs = clock.now()
        }
        relaunch {
            assertDeliveredPartEnd(multiFileEndMs = lastWriteMs)
        }
    }

    @Test
    fun `a native crash in the part the process died in is attached to it during the next launch`() {
        lateinit var deadPart: SessionIdsSnapshot
        killActiveSessionPart(instrumentedConfig = nativeCrashConfig) {
            deadPart = testRule.bootstrapper.essentialServiceModule.sessionIdsProvider.getActiveSessionIds()
        }
        val crash = NativeCrashData(
            nativeCrashId = "dead-process-crash",
            sessionPartId = deadPart.sessionPartId,
            userSessionId = deadPart.userSessionId,
            timestamp = 0L,
            crash = "crash",
            symbols = null,
        )

        relaunch(
            instrumentedConfig = nativeCrashConfig,
            setupAction = { setupFakeNativeCrash(TestPlatformSerializer(), storedNativeCrash(crash, getClock().now())) },
        ) {
            val sessionPartSpan = getSessionEnvelopes(1).single().findSessionPartSpan()
            assertEquals(crash.nativeCrashId, sessionPartSpan.attributes?.findAttributeValue(EmbSessionAttributes.EMB_CRASH_ID))
            val crashLog = getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash)
            assertEquals(deadPart.sessionPartId, crashLog.attributes?.findAttributeValue(EmbSessionAttributes.EMB_SESSION_PART_ID))
        }
    }

    @Test
    fun `every part left by processes that died before resurrecting anything is delivered by the next launch`() {
        val deadProcesses = 3
        val deadPartIds = (1..deadProcesses).map { process ->
            if (process > 1) {
                testRule.bootstrapper.stop()
            }
            lateinit var deadPart: SessionIdsSnapshot
            killActiveSessionPart(killBeforeResurrection = true) {
                deadPart = testRule.bootstrapper.essentialServiceModule.sessionIdsProvider.getActiveSessionIds()
            }
            deadPart.sessionPartId
        }

        relaunch {
            val deliveredPartIds = getSessionEnvelopes(deadProcesses).map { envelope ->
                envelope.findSessionPartSpan().attributes?.findAttributeValue(EmbSessionAttributes.EMB_SESSION_PART_ID)
            }
            assertEquals(deadPartIds.toSet(), deliveredPartIds.toSet())
        }
    }

    /**
     * Records a foreground part that the process dies in the middle of some time after [action] runs in it
     * during which nothing happens. If [killBeforeResurrection], the process dies before it resurrects
     * anything an earlier process left, like in an app crash early in startup.
     */
    private fun killActiveSessionPart(
        instrumentedConfig: FakeInstrumentedConfig = FakeInstrumentedConfig(),
        killBeforeResurrection: Boolean = false,
        action: EmbraceActionInterface.() -> Unit,
    ) {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            persistedRemoteConfig = remoteConfig(),
            setupAction = {
                if (killBeforeResurrection) {
                    getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = true
                }
            },
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
     * Starts the next process, which delivers the part the previous one died in, and runs
     * [assertAction] against what it delivers.
     */
    private fun relaunch(
        instrumentedConfig: FakeInstrumentedConfig = FakeInstrumentedConfig(),
        setupAction: EmbraceSetupInterface.() -> Unit = {},
        assertAction: EmbracePayloadAssertionInterface.() -> Unit,
    ) {
        testRule.bootstrapper.stop()
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            persistedRemoteConfig = remoteConfig(),
            setupAction = setupAction,
            testCaseAction = {},
            assertAction = assertAction,
        )
    }

    /**
     * Asserts the delivered part's session span ended at [multiFileEndMs] under the multi-file mode, or
     * when it started under the single-file mode, as no other span in the part starts or ends later.
     */
    private fun EmbracePayloadAssertionInterface.assertDeliveredPartEnd(multiFileEndMs: Long) {
        val sessionSpan = getSessionEnvelopes(1).single().findSessionPartSpan()
        val expectedEndMs = when (persistenceMode) {
            PersistenceMode.SINGLE_FILE -> sessionSpan.startTimeNanos?.nanosToMillis()
            PersistenceMode.MULTI_FILE -> multiFileEndMs
        }
        assertEquals(expectedEndMs, sessionSpan.endTimeNanos?.nanosToMillis())
    }

    /**
     * Lets one write interval pass and runs the writes due by then, so both modes persist what
     * changed: the multi-file mode's debounced writes and the single-file mode's periodic cache tick.
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
            PersistenceMode.SINGLE_FILE -> 0.0f
        },
        backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
    )

    /**
     * Store a native crash that happened at [nowMs] using [crash] as the backing data.
     */
    private fun storedNativeCrash(crash: NativeCrashData, nowMs: Long) = StoredNativeCrashData(
        sessionMetadata = null,
        crashMetadata = StoredTelemetryMetadata(
            timestamp = nowMs,
            uuid = "EB96C6A8AF09449A8547C7703CE6BDAE",
            processIdentifier = "8115ec91-3e5e-4d8a-816d-cc40306f9822",
            envelopeType = SupportedEnvelopeType.CRASH,
            complete = false,
            payloadType = PayloadType.NATIVE_CRASH,
        ),
        cachedCrashEnvelopeMetadata = null,
        nativeCrash = crash,
        partEnvelope = null,
        cachedCrashEnvelope = null,
    )

    internal companion object {
        // the multi-file mode debounces its writes by no more than the single-file mode's cache interval
        private const val WRITE_INTERVAL_MS = DEFAULT_PERIODIC_CACHE_INTERVAL_MS

        private val persistenceWorkers = listOf(
            Worker.Background.SessionPersistenceWorker,
            Worker.Background.PeriodicCacheWorker,
        )

        private val nativeCrashConfig = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
            symbols = createNativeSymbolsForCurrentArch(mapOf("libfoo.so" to "symbol_content")),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun modes(): List<Array<Any>> = PersistenceMode.entries.flatMap { persistenceMode ->
            OtelSdkMode.entries.map { otelSdkMode -> arrayOf(persistenceMode, otelSdkMode) }
        }
    }
}
