package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.getLogs
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.TestStateValue
import io.embrace.android.embracesdk.fakes.config.FakeAppStateLocalConfig
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_PERIODIC_CACHE_INTERVAL_MS
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.PackedStates
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceOtelExportAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.actions.StoredNativeCrashData
import io.opentelemetry.api.common.AttributeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * What each of the two steps of the app state data model does on its own: the first step is the recorder, the change log and the
 * stamp on every log, and the second step is the link on every span and what is replayed to the app's exporters. The second
 * step does nothing without the first.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class AppStateStepsTest(otelSdkMode: OtelSdkMode) {

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

    private val generatedValue = TestStateValue.SystemValue("sys")

    private val firstStepOnly = RemoteConfig(
        pctStateCaptureEnabledV2 = 100f,
        pctSharedStateSpanEnabled = 100f,
        pctStateAssociationEnabled = 0f,
        pctMultiFilePersistenceEnabled = 100f,
        backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
    )

    private val firstStepOnlyVerbose = firstStepOnly.copy(stateExportMode = "verbose")

    private val bothSteps = firstStepOnly.copy(pctStateAssociationEnabled = 100f)

    private val secondStepOnly = firstStepOnly.copy(pctSharedStateSpanEnabled = 0f, pctStateAssociationEnabled = 100f)

    @Test
    fun `with the first step alone the change log, the stamp and the replay are there and no span is linked to state`() {
        runFirstStepOnly(firstStepOnly, verbose = false)
    }

    @Test
    fun `with the first step alone the verbose rendering puts every value on a replayed change and none on a span`() {
        runFirstStepOnly(firstStepOnlyVerbose, verbose = true)
    }

    @Test
    fun `with the first step alone a native crash's log still gets its stamp and a span the next launch completes gets no end number`() {
        lateinit var deadPart: SessionIdsSnapshot
        firstLaunch(instrumentedConfig = nativeCrashConfig) {
            deadPart = testRule.services.sessionIdsProvider.getActiveSessionIds()
            embrace.startSpan("left-running")
            findDataSource<TestStateDataSource>().onStateChange(newState = "checkout;step=2", transitionTimeMs = clock.tick(100L))
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
            },
        ) {
            val deadEnvelope = getSessionEnvelopes(1).single()
            val changeLog = deadEnvelope.findSpanByName(AppStateKeys.CHANGE_LOG_SPAN_NAME)
            val lastSeq = checkNotNull(changeLog.events).last().attribute(AppStateKeys.SEQ)

            val crashLog = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash).attributes)
            assertEquals("checkout;step=2" to false, crashLog.packedState("test"))
            assertEquals(lastSeq, crashLog.findAttributeValue(AppStateKeys.SEQ))

            // the span was left running, and the first step alone gives no span a link to complete
            val leftRunning = deadEnvelope.findSpanByName("left-running")
            assertNull(leftRunning.attribute(AppStateKeys.SEQ))
            assertNull(leftRunning.attribute(AppStateKeys.END_SEQ))
            assertNull(deadEnvelope.findSessionPartSpan().attribute(AppStateKeys.END_SEQ))
            assertTrue(deadEnvelope.allSpans().none { it.hasLink() })
        }
    }

    @Test
    fun `a part that died with the first step alone gets no end number from a launch with both steps on, and its native crash is still stamped`() {
        val crash = killedInANativeCrashedPart(firstLaunchConfig = firstStepOnly)
        relaunch(
            instrumentedConfig = nativeCrashConfig,
            remoteConfig = bothSteps,
            setupAction = { setupFakeNativeCrash(TestPlatformSerializer(), storedNativeCrash(crash, getClock().now())) },
        ) {
            val deadEnvelope = getSessionEnvelopes(1).single()
            // none of what the dead process left has a link to complete, the session part span included
            assertTrue(deadEnvelope.allSpans().none { it.attribute(AppStateKeys.END_SEQ) != null })
            assertNull(deadEnvelope.findSpanByName("left-running").attribute(AppStateKeys.SEQ))

            val crashLog = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash).attributes)
            assertEquals("checkout;step=2" to false, crashLog.packedState("test"))
        }
    }

    @Test
    fun `a part that died with both steps on gets its end numbers from a launch with the first step alone`() {
        val crash = killedInANativeCrashedPart(firstLaunchConfig = bothSteps)
        relaunch(
            instrumentedConfig = nativeCrashConfig,
            remoteConfig = firstStepOnly,
            setupAction = { setupFakeNativeCrash(TestPlatformSerializer(), storedNativeCrash(crash, getClock().now())) },
        ) {
            val deadEnvelope = getSessionEnvelopes(1).single()
            val changeLog = deadEnvelope.findSpanByName(AppStateKeys.CHANGE_LOG_SPAN_NAME)
            val lastSeq = checkNotNull(changeLog.events).last().attribute(AppStateKeys.SEQ)

            val leftRunning = deadEnvelope.findSpanByName("left-running")
            assertNotNull(leftRunning.attribute(AppStateKeys.SEQ))
            assertEquals(lastSeq, leftRunning.attribute(AppStateKeys.END_SEQ))
            assertEquals(lastSeq, deadEnvelope.findSessionPartSpan().attribute(AppStateKeys.END_SEQ))
        }
    }

    @Test
    fun `with the second step on and the first off nothing of the model is present`() {
        testRule.runTest(
            persistedRemoteConfig = secondStepOnly,
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    embrace.logInfo("ordinary")
                    embrace.startSpan("ordinary")?.stop()
                    embrace.startSpan("left-running")
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                assertTrue(envelope.findSpansByName(AppStateKeys.CHANGE_LOG_SPAN_NAME).isEmpty())
                assertTrue(envelope.allSpans().none { it.hasLink() })
                assertTrue(envelope.findSessionPartSpan().attributes.orEmpty().none { it.isStateKey() })

                val log = getSingleLogEnvelope().getLogs { it.body == "ordinary" }.single()
                assertTrue(log.attributes.orEmpty().none { it.key?.startsWith(AppStateKeys.STEM) == true })
            },
            otelExportAssertion = {
                val span = awaitSpans(1) { it.name == "ordinary" }.single()
                assertTrue(span.attributes.toStringMap().keys.none { it.startsWith(AppStateKeys.STEM) })
                val log = awaitLogs(1) { it.bodyValue?.asString() == "ordinary" }.single()
                assertTrue(log.attributes.toStringMap().keys.none { it.startsWith(AppStateKeys.STEM) })
                assertFalse(log.resource.attributes.toStringMap().containsKey(AppStateKeys.PROCESS_IDENTIFIER))
                assertTrue(awaitLogs(0) { it.eventName?.startsWith(AppStateKeys.STEM) == true }.isEmpty())
            },
        )
    }

    @Test
    fun `local keys alone turn on both steps as the remote percentages would`() {
        testRule.runTest(
            // no remote value for the feature's own switches: only what lets the SDK keep a change log
            persistedRemoteConfig = RemoteConfig(
                pctStateCaptureEnabledV2 = 100f,
                pctMultiFilePersistenceEnabled = 100f,
                backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
            ),
            setupAction = { appStateLocalConfig = FakeAppStateLocalConfig(enabled = true, associationEnabled = true) },
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    val span = checkNotNull(embrace.startSpan("changed-under"))
                    state.onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    span.stop()
                    embrace.logInfo("after")
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                val changeLog = envelope.findSpanByName(AppStateKeys.CHANGE_LOG_SPAN_NAME)
                assertEquals(listOf("foo"), changeLog.stateEvents("test").map { it.attribute(AppStateKeys.NEW_VALUE) })
                val span = envelope.findSpanByName("changed-under")
                assertNotNull(span.attribute(AppStateKeys.SEQ))
                assertNotNull(span.attribute(AppStateKeys.END_SEQ))
                val log = checkNotNull(getSingleLogEnvelope().getLogs { it.body == "after" }.single().attributes)
                assertEquals("foo" to false, log.packedState("test"))
            },
            otelExportAssertion = {
                assertEquals(1, awaitLogs(2) { it.eventName == AppStateKeys.CHANGE_EVENT_NAME && it.isOf("test") }.drop(1).size)
            },
        )
    }

    @Test
    fun `local keys alone with the first step only give a change log and no link`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                pctStateCaptureEnabledV2 = 100f,
                pctMultiFilePersistenceEnabled = 100f,
                backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
            ),
            setupAction = { appStateLocalConfig = FakeAppStateLocalConfig(enabled = true, associationEnabled = false) },
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    val span = checkNotNull(embrace.startSpan("changed-under"))
                    state.onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    span.stop()
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                val changeLog = envelope.findSpanByName(AppStateKeys.CHANGE_LOG_SPAN_NAME)
                assertEquals(listOf("foo"), changeLog.stateEvents("test").map { it.attribute(AppStateKeys.NEW_VALUE) })
                assertTrue(envelope.allSpans().none { it.hasLink() })
            },
            otelExportAssertion = {
                // the change is replayed, after the declaration of the state, and no span carries a link
                val changes = awaitLogs(2) { it.eventName == AppStateKeys.CHANGE_EVENT_NAME && it.isOf("test") }
                assertEquals("foo", changes.last().attributes.toStringMap()[AppStateKeys.NEW_VALUE])
                assertEquals(2, awaitLogs(2) { it.eventName == AppStateKeys.SNAPSHOT_EVENT_NAME }.size)
                val span = awaitSpans(1) { it.name == "changed-under" }.single().attributes.toStringMap()
                assertTrue(span.keys.none { it == AppStateKeys.SEQ || it == AppStateKeys.END_SEQ })
            },
        )
    }

    /**
     * Records one part on [remoteConfig], which has the first step on and the second off, and asserts what the first step alone
     * gives Embrace's payload and the app's exporters, which includes the replay of changes and part snapshots.
     */
    private fun runFirstStepOnly(remoteConfig: RemoteConfig, verbose: Boolean) {
        var changeSeqs = emptyList<Long>()
        var initialSeq = 0L
        testRule.runTest(
            persistedRemoteConfig = remoteConfig,
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    embrace.logInfo("before")
                    state.onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    embrace.startSpan("left-running")
                    val changedUnder = checkNotNull(embrace.startSpan("changed-under"))
                    state.onStateChange(newState = generatedValue, transitionTimeMs = clock.tick(100L))
                    changedUnder.stop()
                    embrace.logInfo("after")
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                val changeLog = envelope.findSpanByName(AppStateKeys.CHANGE_LOG_SPAN_NAME)
                assertEquals(listOf("foo", "$generatedValue"), changeLog.stateEvents("test").map { it.attribute(AppStateKeys.NEW_VALUE) })
                assertEquals(listOf(null, "system"), changeLog.stateEvents("test").map { it.attribute(AppStateKeys.NEW_VALUE_TYPE) })
                changeSeqs = changeLog.stateEvents("test").map { checkNotNull(it.attribute(AppStateKeys.SEQ)).toLong() }
                initialSeq = checkNotNull(changeLog.attribute(AppStateKeys.INITIAL_SEQ)).toLong()

                val logs = getSingleLogEnvelope().getLogs { it.body in setOf("before", "after") }.associateBy { it.body }
                val before = checkNotNull(logs.getValue("before").attributes)
                val after = checkNotNull(logs.getValue("after").attributes)
                assertEquals("UNKNOWN" to false, before.packedState("test"))
                assertEquals("$generatedValue" to true, after.packedState("test"))
                assertTrue(after.none { it.key?.startsWith(AppStateKeys.VALUE_PREFIX) == true })

                // the spans a part has, finished and still running (a snapshot), carry no number
                assertTrue(checkNotNull(envelope.data.spans).any { it.name == "changed-under" })
                assertTrue(envelope.data.spanSnapshots.orEmpty().any { it.name == "left-running" })
                envelope.allSpans().filter { it.name != AppStateKeys.CHANGE_LOG_SPAN_NAME }.forEach { span ->
                    assertFalse(span.name, span.hasLink())
                    assertTrue(span.name, span.attributes.orEmpty().none { AppStateKeys.isCustomerValueKey(checkNotNull(it.key)) })
                }
            },
            otelExportAssertion = {
                val exported = awaitLogs(2) { it.bodyValue?.asString() in setOf("before", "after") }.associateBy { it.bodyValue?.asString() }
                assertEquals("UNKNOWN", exported.getValue("before").attributes.toStringMap()[AppStateKeys.VALUE_PREFIX + "test"])
                val after = exported.getValue("after").attributes.toStringMap()
                assertEquals("$generatedValue", after[AppStateKeys.VALUE_PREFIX + "test"])
                assertEquals("system", after[AppStateKeys.VALUE_TYPE_PREFIX + "test"])
                assertFalse(after.containsKey(AppStateKeys.PACKED))

                val span = awaitSpans(1) { it.name == "changed-under" }.single().attributes
                assertNull(span.get(AttributeKey.longKey(AppStateKeys.SEQ)))
                assertNull(span.get(AttributeKey.longKey(AppStateKeys.END_SEQ)))
                assertTrue(span.toStringMap().keys.none { it == AppStateKeys.SEQ || it == AppStateKeys.END_SEQ })
                assertTrue(span.toStringMap().keys.none(AppStateKeys::isCustomerValueKey))

                // the changes are replayed, the first of them being the declaration of the state
                val changes = awaitLogs(3) { it.isChangeOf("test") }.drop(1)
                assertEquals(listOf("foo", "$generatedValue"), changes.map { it.attributes.toStringMap()[AppStateKeys.NEW_VALUE] })
                assertEquals(changeSeqs, changes.map { it.attributes.get(AttributeKey.longKey(AppStateKeys.SEQ)) })
                changes.forEach { change ->
                    val attributes = change.attributes.toStringMap()
                    assertEquals(verbose, attributes.keys.any { it.startsWith(AppStateKeys.VALUE_PREFIX) })
                }
                if (verbose) {
                    val values = changes.last().attributes.toStringMap()
                    assertEquals("$generatedValue", values[AppStateKeys.VALUE_PREFIX + "test"])
                    assertTrue(values.keys.count { it.startsWith(AppStateKeys.VALUE_PREFIX) } > 1)
                }

                // and the snapshot of the part's start, with the number its change log opens with
                val snapshots = awaitLogs(2) { it.eventName == AppStateKeys.SNAPSHOT_EVENT_NAME }
                assertTrue(snapshots.any { it.attributes.get(AttributeKey.longKey(AppStateKeys.SEQ)) == initialSeq })
            },
        )
    }

    /**
     * Runs a launch on [firstLaunchConfig] in which a span is left running and a state changes, then kills the process, and returns
     * the native crash that the launch left for the next one.
     */
    private fun killedInANativeCrashedPart(firstLaunchConfig: RemoteConfig): NativeCrashData {
        lateinit var deadPart: SessionIdsSnapshot
        firstLaunch(instrumentedConfig = nativeCrashConfig, remoteConfig = firstLaunchConfig) {
            deadPart = testRule.services.sessionIdsProvider.getActiveSessionIds()
            embrace.startSpan("left-running")
            findDataSource<TestStateDataSource>().onStateChange(newState = "checkout;step=2", transitionTimeMs = clock.tick(100L))
            persistAfterOneInterval()
        }
        return NativeCrashData(
            nativeCrashId = "dead-process-crash",
            sessionPartId = deadPart.sessionPartId,
            userSessionId = deadPart.userSessionId,
            timestamp = 0L,
            crash = "crash",
            symbols = null,
        )
    }

    /**
     * Records a foreground part that [action] runs in, which the process is then killed in the middle of.
     */
    private fun firstLaunch(
        instrumentedConfig: FakeInstrumentedConfig = FakeInstrumentedConfig(),
        remoteConfig: RemoteConfig = firstStepOnly,
        action: EmbraceActionInterface.() -> Unit,
    ) {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            persistedRemoteConfig = remoteConfig,
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
     * Starts the next process, which delivers the part the previous one died in, and runs [assertAction] against what it delivers.
     */
    private fun relaunch(
        instrumentedConfig: FakeInstrumentedConfig = FakeInstrumentedConfig(),
        remoteConfig: RemoteConfig = firstStepOnly,
        setupAction: EmbraceSetupInterface.() -> Unit = {},
        otelExportAssertion: EmbraceOtelExportAssertionInterface.() -> Unit = {},
        assertAction: EmbracePayloadAssertionInterface.() -> Unit,
    ) {
        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            persistedRemoteConfig = remoteConfig,
            setupAction = setupAction,
            testCaseAction = {},
            assertAction = assertAction,
            otelExportAssertion = otelExportAssertion,
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

    private fun Envelope<SessionPartPayload>.allSpans(): List<Span> =
        checkNotNull(data.spans).plus(data.spanSnapshots.orEmpty())

    private fun Span.hasLink(): Boolean =
        attribute(AppStateKeys.SEQ) != null || attribute(AppStateKeys.END_SEQ) != null

    private fun Span.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun SpanEvent.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun Span.stateEvents(state: String): List<SpanEvent> =
        events.orEmpty().filter { it.name == AppStateKeys.CHANGE_LOG_EVENT_PREFIX + state }

    private fun Attribute.isStateKey(): Boolean = checkNotNull(key).let { it.startsWith(AppStateKeys.STEM) }

    private fun io.opentelemetry.kotlin.aliases.OtelJavaLogRecordData.isOf(state: String): Boolean =
        attributes.toStringMap()[AppStateKeys.NAME] == state

    private fun io.opentelemetry.kotlin.aliases.OtelJavaLogRecordData.isChangeOf(state: String): Boolean =
        eventName == AppStateKeys.CHANGE_EVENT_NAME && isOf(state)

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
