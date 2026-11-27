package io.embrace.android.embracesdk.testcases.features

import android.app.ApplicationExitInfo
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.fakes.TestAeiData
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.setupFakeAeiData
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_PERIODIC_CACHE_INTERVAL_MS
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.PackedStates
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSpanAttributes
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceOtelExportAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.actions.StoredNativeCrashData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

/**
 * What the next launch delivers of state for a session part whose process died: what the journal on disk holds, never what was
 * only in memory, and never the state of the process that delivers it.
 */
@Config(shadows = [ShadowActivityManager::class])
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class AppStateProcessDeathTest(otelSdkMode: OtelSdkMode) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface(
            workersToFake = listOf(
                Worker.Background.SessionPersistenceWorker,
                Worker.Background.PeriodicCacheWorker,
                Worker.Background.NonIoRegWorker,
                Worker.Background.StateExportWorker,
                Worker.Background.IoRegWorker,
            ),
        ).apply {
            getFakeErrorHandler().throwOnSdkError = false
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.StateExportWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
        }
    }

    private val appStateOn = RemoteConfig(
        pctStateCaptureEnabledV2 = 100f,
        pctSharedStateSpanEnabled = 100f,
        pctStateAssociationEnabled = 100f,
        pctMultiFilePersistenceEnabled = 100f,
        backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
    )

    @Test
    fun `a part whose process was killed delivers the changes that reached the journal, marked as cut short`() {
        firstLaunch {
            val state = findDataSource<TestStateDataSource>()
            embrace.startSpan("left-running")
            state.onStateChange(newState = "first", transitionTimeMs = clock.tick(100L))
            state.onStateChange(newState = "second", transitionTimeMs = clock.tick(100L))
            persistAfterOneInterval()

            // made just before the kill, while the worker that writes the journal had not yet run: it replaced the value in memory
            // and never reached the disk
            testRule.setup.getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = true
            state.onStateChange(newState = "lost-with-the-process", transitionTimeMs = clock.tick(100L))
        }
        relaunch {
            val deadPart = getSessionEnvelopes(1).single()
            val changeLog = deadPart.findSpanByName(AppStateKeys.CHANGE_LOG_SPAN_NAME)

            // what survived is the start of the truth with no hole in it, and it says it is incomplete
            assertEquals("true", changeLog.attribute(AppStateKeys.TRUNCATED))
            val events = changeLog.stateEvents("test")
            assertEquals(listOf("first", "second"), events.map { it.attribute(AppStateKeys.NEW_VALUE) })
            val seqs = checkNotNull(changeLog.events).map { checkNotNull(it.attribute(AppStateKeys.SEQ)).toLong() }
            val initialSeq = checkNotNull(changeLog.attribute(AppStateKeys.INITIAL_SEQ)).toLong()
            assertEquals((initialSeq + 1..initialSeq + seqs.size).toList(), seqs)
            assertEquals("UNKNOWN", changeLog.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "test"))

            // the end of the part was never recorded, so nothing is said of counts or final values
            assertTrue(changeLog.attributes.orEmpty().none { checkNotNull(it.key).startsWith(AppStateKeys.DROPPED_COUNT_PREFIX) })
            assertTrue(changeLog.attributes.orEmpty().none { checkNotNull(it.key).startsWith(AppStateKeys.FINAL_VALUE_PREFIX) })
            assertNull(changeLog.attribute(AppStateKeys.DROPPED_CHANGES))

            // the next launch completes it as it completes any other span the dead process left running
            val sessionSpan = deadPart.findSessionPartSpan()
            assertEquals(Span.Status.ERROR, changeLog.status)
            assertEquals("failure", changeLog.attribute(EmbSpanAttributes.EMB_ERROR_CODE))
            assertEquals(sessionSpan.endTimeNanos, changeLog.endTimeNanos)
            assertTrue(deadPart.data.spanSnapshots.isNullOrEmpty())

            // and gives each one the log's last number as its end, so that none reads as a span nothing changed under
            val lastSeq = seqs.last().toString()
            val leftRunning = deadPart.findSpanByName("left-running")
            val beforeFirstChange = checkNotNull(events.first().attribute(AppStateKeys.SEQ)).toLong() - 1
            assertEquals(beforeFirstChange.toString(), leftRunning.attribute(AppStateKeys.SEQ))
            assertEquals(lastSeq, leftRunning.attribute(AppStateKeys.END_SEQ))
            assertEquals(lastSeq, sessionSpan.attribute(AppStateKeys.END_SEQ))
            assertNull(changeLog.attribute(AppStateKeys.END_SEQ))
        }
    }

    @Test
    fun `a native crash's log carries the state the dead process recorded and not that of the launch that writes it`() {
        lateinit var deadPart: SessionIdsSnapshot
        firstLaunch(instrumentedConfig = nativeCrashConfig) {
            deadPart = testRule.services.sessionIdsProvider.getActiveSessionIds()
            val state = findDataSource<TestStateDataSource>()
            state.onStateChange(newState = "checkout;step=2", transitionTimeMs = clock.tick(100L))
            persistAfterOneInterval()
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
            otelExportAssertion = {
                val exported = awaitLogs(1) { it.attributes.toStringMap()[EmbType.System.NativeCrash.key] == EmbType.System.NativeCrash.value }
                    .single().attributes.toStringMap()
                assertEquals("checkout;step=2", exported[AppStateKeys.VALUE_PREFIX + "test"])
                assertFalse(exported.containsKey(AppStateKeys.PACKED))
            },
        ) {
            val changeLog = getSessionEnvelopes(1).single().findSpanByName(AppStateKeys.CHANGE_LOG_SPAN_NAME)
            val lastSeq = checkNotNull(changeLog.events).last().attribute(AppStateKeys.SEQ)

            val crashLog = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash).attributes)
            assertEquals("checkout;step=2" to false, crashLog.packedState("test"))
            assertEquals(lastSeq, crashLog.findAttributeValue(AppStateKeys.SEQ))

            // the new process's own value for the same state is its default, and is not what the log says
            assertFalse(crashLog.packedState("test")?.first == "UNKNOWN")
            assertTrue(crashLog.none { checkNotNull(it.key).startsWith(AppStateKeys.VALUE_PREFIX) })
        }
    }

    @Test
    fun `a part that ends in a JVM crash delivers its whole change log, and the crash's log carries the state at the crash`() {
        firstLaunch(killAfter = false) {
            val state = findDataSource<TestStateDataSource>()
            state.onStateChange(newState = "about-to-crash", transitionTimeMs = clock.tick(100L))
            clock.tick(100L)
            simulateJvmUncaughtException(RuntimeException("Boom!"))
        }
        relaunch {
            val crashedPart = getSessionEnvelopes(1, assertOrdering = false).single()
            val changeLog = crashedPart.findSpanByName(AppStateKeys.CHANGE_LOG_SPAN_NAME)

            // the SDK had time to react, so the end of the part was recorded and nothing is missing
            assertNull(changeLog.attribute(AppStateKeys.TRUNCATED))
            assertEquals(listOf("about-to-crash"), changeLog.stateEvents("test").map { it.attribute(AppStateKeys.NEW_VALUE) })
            assertEquals(crashedPart.findSessionPartSpan().endTimeNanos, changeLog.endTimeNanos)

            val crashLog = checkNotNull(
                getLogEnvelopes(1, logsOrderedByTimestamp = false).single().getLogOfType(EmbType.System.Crash).attributes,
            )
            assertEquals("about-to-crash" to false, crashLog.packedState("test"))
            assertEquals(changeLog.stateEvents("test").single().attribute(AppStateKeys.SEQ), crashLog.findAttributeValue(AppStateKeys.SEQ))
        }
    }

    @Test
    fun `the record of why the last process exited is not stamped with the state of the process that writes it`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            setupAction = {
                setupFakeAeiData(listOf(TestAeiData(ApplicationExitInfo.REASON_CRASH, 1, "jvmCrash", "details").toAeiObject()))
            },
            testCaseAction = {
                recordSession {
                    embrace.logInfo("a log about this process")
                }
            },
            assertAction = {
                val logs = getLogEnvelopes(2, logsOrderedByTimestamp = false).flatMap { it.data.logs.orEmpty() }
                val ordinary = checkNotNull(logs.single { it.body == "a log about this process" }.attributes)
                assertEquals("UNKNOWN" to false, ordinary.packedState("test"))

                val exitRecord = checkNotNull(logs.single { it.attributes?.findAttributeValue(EmbType.System.Exit.key) == EmbType.System.Exit.value }.attributes)
                assertTrue(exitRecord.none { checkNotNull(it.key).startsWith(AppStateKeys.STEM) })
            },
            otelExportAssertion = {
                val exported = awaitLogs(1) { it.attributes.toStringMap()[EmbType.System.Exit.key] == EmbType.System.Exit.value }
                    .single().attributes.toStringMap()
                assertTrue(exported.keys.none { it.startsWith(AppStateKeys.STEM) })
            },
        )
    }

    /**
     * Records a foreground part that [action] runs in, which the process is then killed in the middle of unless [killAfter] is
     * false, as when the action ended the process itself.
     */
    private fun firstLaunch(
        instrumentedConfig: FakeInstrumentedConfig = FakeInstrumentedConfig(),
        killAfter: Boolean = true,
        action: EmbraceActionInterface.() -> Unit,
    ) {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession(endInBackground = false) {
                    persistDueWrites()
                    action()
                }
                if (killAfter) {
                    killProcess()
                }
            },
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
            },
        )
    }

    /**
     * Starts the next process, which delivers the part the previous one died in, and runs [assertAction] against what it delivers.
     */
    private fun relaunch(
        instrumentedConfig: FakeInstrumentedConfig = FakeInstrumentedConfig(),
        setupAction: EmbraceSetupInterface.() -> Unit = {},
        otelExportAssertion: EmbraceOtelExportAssertionInterface.() -> Unit = {},
        assertAction: EmbracePayloadAssertionInterface.() -> Unit,
    ) {
        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            persistedRemoteConfig = appStateOn,
            setupAction = setupAction,
            testCaseAction = {},
            assertAction = assertAction,
            otelExportAssertion = otelExportAssertion,
        )
    }

    /**
     * Lets one write interval pass and runs the writes due by then, so that what changed so far is on disk.
     */
    private fun EmbraceActionInterface.persistAfterOneInterval() {
        clock.tick(DEFAULT_PERIODIC_CACHE_INTERVAL_MS)
        persistDueWrites()
    }

    private fun persistDueWrites() {
        persistenceWorkers.forEach { worker ->
            testRule.setup.getFakedWorkerExecutor(worker).runCurrentlyBlocked()
        }
    }

    /**
     * Kills the process: every write still waiting to run dies with it.
     */
    private fun killProcess() {
        persistenceWorkers.forEach { worker ->
            testRule.setup.getFakedWorkerExecutor(worker).apply {
                blockingMode = true
                shutdownNow()
            }
        }
        testRule.services.intakeService.shutdown()
    }

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

    private fun Span.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun SpanEvent.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun Span.stateEvents(state: String): List<SpanEvent> =
        events.orEmpty().filter { it.name == AppStateKeys.CHANGE_LOG_EVENT_PREFIX + state }

    /**
     * The value the packed stamp among these log attributes has for [state], and whether it is marked as generated.
     */
    private fun List<Attribute>.packedState(state: String): Pair<String, Boolean>? =
        findAttributeValue(AppStateKeys.PACKED)
            ?.let(PackedStates::unpack)
            ?.singleOrNull { it.name == state }
            ?.let { it.value to it.system }

    internal companion object {
        private val persistenceWorkers = listOf(
            Worker.Background.SessionPersistenceWorker,
            Worker.Background.PeriodicCacheWorker,
        )

        private val nativeCrashConfig = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
            symbols = createNativeSymbolsForCurrentArch(mapOf("libfoo.so" to "symbol_content")),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
