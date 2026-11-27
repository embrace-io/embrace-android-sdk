package io.embrace.android.embracesdk.testcases.features

import android.app.ApplicationExitInfo
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.assertions.getLogs
import io.embrace.android.embracesdk.fakes.TestAeiData
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.setupFakeAeiData
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_PERIODIC_CACHE_INTERVAL_MS
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceOtelExportAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

/**
 * Behaviour the proof of concept of the app state data model knowingly leaves wrong or unbuilt. Each test pins what happens today
 * and says above it what the right behaviour is, so that fixing the defect fails the test that carries its name, and the fix
 * replaces the assertions with the right ones.
 */
@Config(shadows = [ShadowActivityManager::class])
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class AppStateKnownDefectsTest(otelSdkMode: OtelSdkMode) {

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

    /**
     * Right behaviour: the opening snapshot of the first part has every state the part ends with, or the change log says which were
     * declared after it opened, so that a reader of the log alone knows every state's value at the start.
     *
     * Today the part that is open as the SDK starts opens before the states are declared. Its change log has no opening value for a
     * state declared after, and lists the declaration as a change, with a value and no previous value. Nothing marks the state as
     * declared late.
     */
    @Test
    fun `a state declared after the first part opened has no opening value there and its declaration is listed as a change`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession { }
                recordSession { }
            },
            assertAction = {
                val first = allParts().first().changeLog()
                assertNull(first.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "test"))
                assertNull(first.attribute(AppStateKeys.INITIAL_VALUE_TYPE_PREFIX + "test"))
                val declaration = first.stateEvents("test").single()
                assertEquals("UNKNOWN", declaration.attribute(AppStateKeys.NEW_VALUE))
                assertNull(declaration.attribute(AppStateKeys.PREVIOUS_VALUE))
                assertTrue(first.attributes.orEmpty().none { checkNotNull(it.key).contains("late") || checkNotNull(it.key).contains("declared") })
            },
        )
    }

    /**
     * Right behaviour: an app exit record that the next launch reports carries the state of the process that exited, as of its
     * exit, in the stamp every other log carries.
     *
     * Today it carries no state stamp at all, for Embrace or for the app's exporters, so that nothing says what the dead process's
     * state was.
     */
    @Test
    fun `an app exit record reported by the next launch carries no state stamp`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            setupAction = {
                setupFakeAeiData(listOf(TestAeiData(ApplicationExitInfo.REASON_CRASH, 1, "jvmCrash", "details").toAeiObject()))
            },
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                }
            },
            assertAction = {
                val logs = getLogEnvelopes(1, logsOrderedByTimestamp = false).flatMap { it.data.logs.orEmpty() }
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
     * Right behaviour: a change the dead process journaled and never replayed to the app's exporters is replayed by the next launch,
     * so that the app's exporters see every change of every process.
     *
     * Today the next launch delivers the change in the dead part's change log and replays nothing of it.
     */
    @Test
    fun `a change the dead process journaled but had not replayed is not replayed by the next launch`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession(endInBackground = false) {
                    persistDueWrites()
                    val state = findDataSource<TestStateDataSource>()
                    // the worker that replays to the app's exporters has not run when the process dies
                    testRule.setup.getFakedWorkerExecutor(Worker.Background.StateExportWorker).blockingMode = true
                    state.onStateChange(newState = JOURNALED_NOT_REPLAYED, transitionTimeMs = clock.tick(100L))
                    persistAfterOneInterval()
                }
                killProcess()
            },
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
            },
            otelExportAssertion = {
                assertTrue(awaitLogs(0) { it.isChange(JOURNALED_NOT_REPLAYED) }.isEmpty())
            },
        )
        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {},
            assertAction = {
                val deadPart = getSessionEnvelopes(1, assertOrdering = false).single()
                val listed = deadPart.changeLog().stateEvents("test").map { it.attribute(AppStateKeys.NEW_VALUE) }
                assertEquals(listOf(JOURNALED_NOT_REPLAYED), listed)
            },
            otelExportAssertion = {
                assertTrue(awaitLogs(0) { it.isChange(JOURNALED_NOT_REPLAYED) }.isEmpty())
            },
        )
    }

    /**
     * Right behaviour: an app whose only exporter takes spans loses nothing it is exported with the model off when the first step
     * is turned on. With the model off its exporter gets a span for each state.
     *
     * Today, with the first step alone and no log exporter registered, its exporter gets nothing of state: no span for a state,
     * no change log, no value on a span and no link, because values on spans and links both take the second step.
     */
    @Test
    fun `an app with only a span exporter gets no state of any kind at it with the first step alone`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn.copy(pctStateAssociationEnabled = 0f),
            registerLogExporter = false,
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    embrace.startSpan("ordinary")?.stop()
                }
            },
            otelExportAssertion = {
                val span = awaitSpans(1) { it.name == "ordinary" }.single().attributes.toStringMap()
                assertTrue(span.keys.none { it.startsWith(AppStateKeys.STEM) })
                assertTrue(
                    awaitSpans(0) {
                        it.name == AppStateKeys.CHANGE_LOG_SPAN_NAME ||
                            it.name.startsWith("emb-state") ||
                            it.attributes.toStringMap()[EmbType.State.key] == "state"
                    }.isEmpty()
                )
            },
        )
    }

    /**
     * Right behaviour: a span that started before the recorder's window of recent snapshots, which so has no start number, is not
     * given an end number by the next launch, as there is no start to pair it with.
     *
     * Today the next launch cannot tell it from a span that started before the first change, so in a part whose process linked
     * spans it gives the span an end number though it has no start number.
     */
    @Test
    fun `a span older than the window of snapshots that the process left running gets an end number and has no start number`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession(endInBackground = false) {
                    persistDueWrites()
                    val state = findDataSource<TestStateDataSource>()
                    val startedAt = clock.now()
                    // more changes than the window of 64 snapshots holds, so that what held at the start is no longer known
                    repeat(WINDOW_SIZE + 6) { i ->
                        state.onStateChange(newState = "change-$i", transitionTimeMs = clock.tick(10L))
                    }
                    embrace.startSpan("older-than-window", null, startedAt)
                    persistAfterOneInterval()
                }
                killProcess()
            },
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
            },
        )
        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {},
            assertAction = {
                val deadPart = getSessionEnvelopes(1, assertOrdering = false).single()
                val span = deadPart.findSpansByName("older-than-window").single()
                assertNull(span.attribute(AppStateKeys.SEQ))
                assertNotNull(span.attribute(AppStateKeys.END_SEQ))
                // the process did link other spans
                assertNotNull(deadPart.findSessionPartSpan().attribute(AppStateKeys.SEQ))
            },
        )
    }

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
     * Kills the process: every write and replay still waiting to run dies with it.
     */
    private fun killProcess() {
        (persistenceWorkers + Worker.Background.StateExportWorker).forEach { worker ->
            testRule.setup.getFakedWorkerExecutor(worker).apply {
                blockingMode = true
                shutdownNow()
            }
        }
        testRule.services.intakeService.shutdown()
    }

    private fun OtelJavaLogRecordData.isChange(newValue: String): Boolean =
        eventName == AppStateKeys.CHANGE_EVENT_NAME && attributes.toStringMap()[AppStateKeys.NEW_VALUE] == newValue

    private fun Envelope<SessionPartPayload>.changeLog(): Span = findSpansByName(AppStateKeys.CHANGE_LOG_SPAN_NAME).single()

    private fun Span.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun SpanEvent.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun Span.stateEvents(state: String): List<SpanEvent> =
        events.orEmpty().filter { it.name == AppStateKeys.CHANGE_LOG_EVENT_PREFIX + state }

    private fun EmbracePayloadAssertionInterface.allParts(): List<Envelope<SessionPartPayload>> =
        (getSessionEnvelopes(2) + getSessionEnvelopes(2, state = ProcessState.BACKGROUND).distinctBy { it.findSessionPartSpan().spanId })
            .sortedBy { it.findSessionPartSpan().startTimeNanos }

    internal companion object {
        private const val WINDOW_SIZE = 64
        private const val JOURNALED_NOT_REPLAYED = "journaled-not-replayed"
        private val persistenceWorkers = listOf(
            Worker.Background.SessionPersistenceWorker,
            Worker.Background.PeriodicCacheWorker,
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
