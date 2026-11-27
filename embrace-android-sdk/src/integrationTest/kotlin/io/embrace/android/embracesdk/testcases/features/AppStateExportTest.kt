package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.assertions.getLogs
import io.embrace.android.embracesdk.fakes.TEST_STATE_DECLARED_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.TestStateValue
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.PackedStates
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceOtelExportAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * What an app's own OpenTelemetry exporters get from the app state data model: a log record for each change and for the start of
 * each session part, and the numbers (or the values as well) on its spans.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class AppStateExportTest(otelSdkMode: OtelSdkMode) {

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
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.StateExportWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
        }
    }

    private val generatedValue = TestStateValue.SystemValue("sys")

    private val appStateOn = RemoteConfig(
        pctStateCaptureEnabledV2 = 100f,
        pctSharedStateSpanEnabled = 100f,
        pctStateAssociationEnabled = 100f,
        pctMultiFilePersistenceEnabled = 100f,
        backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
    )

    private val appStateOnWithoutBackgroundActivity = appStateOn.copy(backgroundActivityConfig = null)

    private val appStateVerbose = appStateOn.copy(stateExportMode = "verbose")

    @Test
    fun `a change made in a session part reaches the log exporter as a change event with every field`() {
        val changeTimes = mutableListOf<Long>()
        var changeLogSeqs = emptyList<Long>()
        var processIdentifier: String? = null
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    listOf("foo", generatedValue, "bar").forEach { value ->
                        changeTimes.add(clock.tick(100L))
                        state.onStateChange(newState = value, transitionTimeMs = clock.now())
                    }
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                changeLogSeqs = envelope.changeLog().stateEvents("test").map { checkNotNull(it.attribute(AppStateKeys.SEQ)).toLong() }
                processIdentifier = envelope.findSessionPartSpan().attribute(AppStateKeys.PROCESS_IDENTIFIER)
            },
            otelExportAssertion = {
                val declaration = awaitLogs(4) { it.isChangeOf("test") }.first().attributes.toStringMap()
                assertEquals("UNKNOWN", declaration[AppStateKeys.NEW_VALUE])
                assertNull(declaration[AppStateKeys.PREVIOUS_VALUE])
                val events = awaitChangesOf("test", 3)
                assertEquals(3, changeLogSeqs.size)
                assertEquals(changeLogSeqs, events.map { it.longAttribute(AppStateKeys.SEQ) })
                assertEquals(changeTimes.map { it.millisToNanos() }, events.map { it.timestampEpochNanos })

                events.forEach { event ->
                    val attributes = event.attributes.toStringMap()
                    assertEquals(AppStateKeys.CHANGE_EVENT_NAME, event.eventName)
                    assertEquals("state", attributes[EmbType.State.key])
                    assertEquals(AppStateKeys.ENTITY_TYPE_PROCESS, attributes[AppStateKeys.ENTITY_TYPE])
                    assertEquals(processIdentifier, attributes[AppStateKeys.ENTITY_ID])
                    assertEquals(
                        processIdentifier,
                        event.resource.attributes.toStringMap()[AppStateKeys.PROCESS_IDENTIFIER]
                    )
                    assertEquals("test", attributes[AppStateKeys.NAME])
                    assertEquals(Severity.INFO, event.severity)
                    assertEquals(9, event.severity.severityNumber)
                    assertNull(event.bodyValue)
                    assertFalse(attributes.containsKey("emb.session_part_id"))
                    assertFalse(attributes.containsKey("emb.user_session_id"))
                    assertFalse(attributes.containsKey("emb.state"))
                    assertFalse(attributes.containsKey("log.record.uid"))
                    assertNotNull(processIdentifier)
                }

                val (first, second, third) = events.map { it.attributes.toStringMap() }
                // the first change after the state's default has that default as its previous value
                assertEquals("foo", first[AppStateKeys.NEW_VALUE])
                assertEquals("UNKNOWN", first[AppStateKeys.PREVIOUS_VALUE])
                assertNull(first[AppStateKeys.NEW_VALUE_TYPE])
                assertNull(first[AppStateKeys.PREVIOUS_VALUE_TYPE])

                // a generated value is marked as one, both as the new value and later as the previous value
                assertEquals("$generatedValue", second[AppStateKeys.NEW_VALUE])
                assertEquals("system", second[AppStateKeys.NEW_VALUE_TYPE])
                assertEquals("foo", second[AppStateKeys.PREVIOUS_VALUE])
                assertNull(second[AppStateKeys.PREVIOUS_VALUE_TYPE])

                assertEquals("bar", third[AppStateKeys.NEW_VALUE])
                assertNull(third[AppStateKeys.NEW_VALUE_TYPE])
                assertEquals("$generatedValue", third[AppStateKeys.PREVIOUS_VALUE])
                assertEquals("system", third[AppStateKeys.PREVIOUS_VALUE_TYPE])
            },
        )
    }

    @Test
    fun `a session part starting gives the log exporter a snapshot of every state and the last change number`() {
        val changeLogs = mutableListOf<Span>()
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                }
                recordSession { }
            },
            assertAction = {
                getSessionEnvelopes(2).forEach { changeLogs.add(it.changeLog()) }
            },
            otelExportAssertion = {
                // a snapshot for each part that started with a state to list: a foreground and a background part for each session.
                // The part open when the SDK starts begins before any state is declared, so it has none.
                val snapshots = awaitLogs(2 * 2) { it.eventName == AppStateKeys.SNAPSHOT_EVENT_NAME }
                snapshots.forEach { snapshot ->
                    val attributes = snapshot.attributes.toStringMap()
                    assertNotNull(attributes[AppStateKeys.VALUE_PREFIX + "test"])
                    assertNotNull(snapshot.longAttribute(AppStateKeys.SEQ))
                }

                // each foreground part's snapshot has the values and the number its change log opens with
                changeLogs.forEach { changeLog ->
                    val initialSeq = checkNotNull(changeLog.attribute(AppStateKeys.INITIAL_SEQ)).toLong()
                    val matching = snapshots.filter { it.longAttribute(AppStateKeys.SEQ) == initialSeq }
                    assertTrue("no snapshot with the number $initialSeq", matching.isNotEmpty())
                    val initialValues = changeLog.attributes.orEmpty()
                        .filter { checkNotNull(it.key).startsWith(AppStateKeys.INITIAL_VALUE_PREFIX) }
                        .associate { checkNotNull(it.key).removePrefix(AppStateKeys.INITIAL_VALUE_PREFIX) to it.data }
                    assertTrue(initialValues.containsKey("test"))
                    assertTrue(initialValues.size > 1)
                    matching.forEach { snapshot ->
                        val attributes = snapshot.attributes.toStringMap()
                        initialValues.forEach { (name, value) ->
                            assertEquals(value, attributes[AppStateKeys.VALUE_PREFIX + name])
                        }
                    }
                }

                // the second part opens after the change to foo
                val secondInitialSeq = checkNotNull(changeLogs[1].attribute(AppStateKeys.INITIAL_SEQ)).toLong()
                assertEquals("foo", changeLogs[1].attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "test"))
                assertTrue(
                    snapshots.any {
                        it.longAttribute(AppStateKeys.SEQ) == secondInitialSeq &&
                            it.attributes.toStringMap()[AppStateKeys.VALUE_PREFIX + "test"] == "foo"
                    }
                )
            },
        )
    }

    @Test
    fun `neither the change events nor the snapshots are in an Embrace log envelope`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    embrace.logInfo("ordinary")
                    findDataSource<TestStateDataSource>().onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    embrace.logInfo("after the change")
                }
            },
            assertAction = {
                val logs = getSingleLogEnvelope().getLogs { true }
                assertTrue(logs.map { it.body }.containsAll(listOf("ordinary", "after the change")))
                logs.forEach { log ->
                    assertNotEquals("state", log.attributes.orEmpty().findAttributeValue(EmbType.State.key))
                }
            },
            otelExportAssertion = {
                // the exporter did get them
                assertEquals(1, awaitChangesOf("test", 1).size)
                assertEquals(2, awaitLogs(2) { it.eventName == AppStateKeys.SNAPSHOT_EVENT_NAME }.size)
            },
        )
    }

    @Test
    fun `a change event has the declared attribute of the change and not an undeclared one`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(
                        newState = "foo",
                        transitionTimeMs = clock.tick(100L),
                        transitionAttributes = mapOf(
                            TEST_STATE_DECLARED_ATTRIBUTE_KEY to "declared-value",
                            "test.undeclared" to "undeclared-value",
                        ),
                    )
                }
            },
            otelExportAssertion = {
                val attributes = awaitChangesOf("test", 1).single().attributes.toStringMap()
                assertEquals("declared-value", attributes[TEST_STATE_DECLARED_ATTRIBUTE_KEY])
                assertFalse(attributes.containsKey("test.undeclared"))
            },
        )
    }

    @Test
    fun `an app's own log named like a change event is an ordinary log, stamped, delivered to Embrace and exported once`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    embrace.getOpenTelemetryKotlin().loggerProvider.getLogger("app-logger").emit(
                        body = "app's own",
                        eventName = AppStateKeys.CHANGE_EVENT_NAME,
                    ) {
                        setStringAttribute(EmbType.State.key, "state")
                    }
                }
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogs { it.body == "app's own" }.single()
                val attributes = checkNotNull(log.attributes)
                assertEquals("foo" to false, attributes.packedState("test"))
                assertNotNull(attributes.findAttributeValue(AppStateKeys.SEQ))
                assertNotNull(attributes.findAttributeValue("log.record.uid"))
            },
            otelExportAssertion = {
                val exported = awaitLogs(1) { it.bodyValue?.asString() == "app's own" }.single()
                assertEquals(AppStateKeys.CHANGE_EVENT_NAME, exported.eventName)
                assertEquals("foo", exported.attributes.toStringMap()[AppStateKeys.VALUE_PREFIX + "test"])
                assertNotNull(exported.longAttribute(AppStateKeys.SEQ))

                // the replayed change of the test state is still the one record of that change
                assertEquals(1, awaitChangesOf("test", 1).size)
            },
        )
    }

    @Test
    fun `a change made with no session part has no change event and leaves a gap in the numbers`() {
        var firstSeq = 0L
        var lastSeq = 0L
        var secondPart: Span? = null
        testRule.runTest(
            persistedRemoteConfig = appStateOnWithoutBackgroundActivity,
            testCaseAction = {
                val state = findDataSource<TestStateDataSource>()
                recordSession {
                    state.onStateChange(newState = "in-first-part", transitionTimeMs = clock.tick(100L))
                }
                // the app is in the background and background activity is off, so no session part is open
                state.onStateChange(newState = "in-no-part", transitionTimeMs = clock.tick(100L))
                recordSession {
                    state.onStateChange(newState = "in-second-part", transitionTimeMs = clock.tick(100L))
                }
            },
            assertAction = {
                val envelopes = getSessionEnvelopes(2)
                firstSeq = checkNotNull(envelopes[0].changeLog().stateEvents("test").single().attribute(AppStateKeys.SEQ)).toLong()
                secondPart = envelopes[1].changeLog()
                lastSeq = checkNotNull(secondPart?.stateEvents("test")?.single()?.attribute(AppStateKeys.SEQ)).toLong()
            },
            otelExportAssertion = {
                val events = awaitLogs(2) { it.isChangeOf("test") }
                assertEquals(
                    listOf("in-first-part", "in-second-part"),
                    events.map { it.attributes.toStringMap()[AppStateKeys.NEW_VALUE] },
                )
                assertEquals(listOf(firstSeq, lastSeq), events.map { it.longAttribute(AppStateKeys.SEQ) })

                // the change with no part took a number, which nobody got
                assertTrue(lastSeq > firstSeq + 1)
                assertEquals(
                    "1",
                    checkNotNull(secondPart).attribute(AppStateKeys.NOT_IN_SESSION_COUNT_PREFIX + "test"),
                )
                assertEquals(
                    "in-no-part",
                    checkNotNull(secondPart).attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "test"),
                )
            },
        )
    }

    @Test
    fun `association rendering puts only the two numbers on a span and no values on a change event`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    val span = checkNotNull(embrace.startSpan("changed-under"))
                    state.onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    state.onStateChange(newState = generatedValue, transitionTimeMs = clock.tick(100L))
                    span.stop()
                }
            },
            otelExportAssertion = {
                val span = awaitSpans(1) { it.name == "changed-under" }.single()
                val seq = checkNotNull(span.attributes.get(AttributeKey.longKey(AppStateKeys.SEQ)))
                assertEquals(seq + 2, span.attributes.get(AttributeKey.longKey(AppStateKeys.END_SEQ)))
                assertTrue(span.attributes.toStringMap().keys.none(AppStateKeys::isCustomerValueKey))

                val events = awaitChangesOf("test", 2)
                events.forEach { assertTrue(it.attributes.toStringMap().keys.none(AppStateKeys::isCustomerValueKey)) }
            },
        )
    }

    @Test
    fun `verbose rendering puts the values at the start and the end on a span, and every value on a change event`() {
        var payloadSpan: Span? = null
        var generatedSpan: Span? = null
        testRule.runTest(
            persistedRemoteConfig = appStateVerbose,
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    state.onStateChange(newState = "before", transitionTimeMs = clock.tick(100L))
                    val changedUnder = checkNotNull(embrace.startSpan("changed-under"))
                    state.onStateChange(newState = generatedValue, transitionTimeMs = clock.tick(100L))
                    changedUnder.stop()

                    val startedGenerated = checkNotNull(embrace.startSpan("started-generated"))
                    state.onStateChange(newState = "plain", transitionTimeMs = clock.tick(100L))
                    startedGenerated.stop()
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                payloadSpan = envelope.findSpanByName("changed-under")
                generatedSpan = envelope.findSpanByName("started-generated")
            },
            otelExportAssertion = {
                val changedUnder = awaitSpans(1) { it.name == "changed-under" }.single().attributes.toStringMap()
                assertEquals("before", changedUnder[AppStateKeys.VALUE_PREFIX + "test"])
                assertNull(changedUnder[AppStateKeys.VALUE_TYPE_PREFIX + "test"])
                assertEquals("$generatedValue", changedUnder[AppStateKeys.END_VALUE_PREFIX + "test"])
                assertEquals("system", changedUnder[AppStateKeys.END_VALUE_TYPE_PREFIX + "test"])

                // every other state holds its value, so only the start has it
                val otherStates = changedUnder.keys
                    .filter { it.startsWith(AppStateKeys.VALUE_PREFIX) }
                    .map { it.removePrefix(AppStateKeys.VALUE_PREFIX) }
                    .filter { it != "test" }
                assertTrue(otherStates.isNotEmpty())
                otherStates.forEach { name ->
                    assertFalse(changedUnder.containsKey(AppStateKeys.END_VALUE_PREFIX + name))
                    assertFalse(changedUnder.containsKey(AppStateKeys.END_VALUE_TYPE_PREFIX + name))
                }

                val startedGenerated = awaitSpans(1) { it.name == "started-generated" }.single().attributes.toStringMap()
                assertEquals("$generatedValue", startedGenerated[AppStateKeys.VALUE_PREFIX + "test"])
                assertEquals("system", startedGenerated[AppStateKeys.VALUE_TYPE_PREFIX + "test"])
                assertEquals("plain", startedGenerated[AppStateKeys.END_VALUE_PREFIX + "test"])
                assertNull(startedGenerated[AppStateKeys.END_VALUE_TYPE_PREFIX + "test"])

                // a change event has every state's value as of after the change
                val events = awaitChangesOf("test", 3).map { it.attributes.toStringMap() }
                assertEquals(listOf("before", "$generatedValue", "plain"), events.map { it[AppStateKeys.NEW_VALUE] })
                assertEquals(
                    listOf("before", "$generatedValue", "plain"),
                    events.map { it[AppStateKeys.VALUE_PREFIX + "test"] },
                )
                assertEquals(listOf(null, "system", null), events.map { it[AppStateKeys.VALUE_TYPE_PREFIX + "test"] })
                events.forEach { event ->
                    otherStates.forEach { name -> assertNotNull(event[AppStateKeys.VALUE_PREFIX + name]) }
                }

                // Embrace's payload has the two numbers as text and never the values
                listOf(payloadSpan, generatedSpan).forEach { span ->
                    assertNotNull(checkNotNull(span).attribute(AppStateKeys.SEQ))
                    assertTrue(span.attributes.orEmpty().none { AppStateKeys.isCustomerValueKey(checkNotNull(it.key)) })
                }
                assertNotNull(checkNotNull(payloadSpan).attribute(AppStateKeys.END_SEQ))
            },
        )
    }

    @Test
    fun `the exported resource has the process identifier of the session part span`() {
        var processIdentifier: String? = null
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    embrace.startSpan("a-span")?.stop()
                    embrace.logInfo("a-log")
                }
            },
            assertAction = {
                processIdentifier = getSingleSessionEnvelope().findSessionPartSpan().attribute(AppStateKeys.PROCESS_IDENTIFIER)
                assertNotNull(processIdentifier)
            },
            otelExportAssertion = {
                val span = awaitSpans(1) { it.name == "a-span" }.single()
                val log = awaitLogs(1) { it.bodyValue?.asString() == "a-log" }.single()
                assertEquals(processIdentifier, span.resource.attributes.toStringMap()[AppStateKeys.PROCESS_IDENTIFIER])
                assertEquals(processIdentifier, log.resource.attributes.toStringMap()[AppStateKeys.PROCESS_IDENTIFIER])
            },
        )
    }

    @Test
    fun `no exporter gets the change log span or a span for a state`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    embrace.startSpan("ordinary")?.stop()
                }
            },
            otelExportAssertion = {
                assertEquals(1, awaitSpans(1) { it.name == "ordinary" }.size)
                assertTrue(
                    awaitSpans(0) {
                        it.name == AppStateKeys.CHANGE_LOG_SPAN_NAME ||
                            it.name == "emb-state-test" ||
                            it.attributes.toStringMap()[EmbType.State.key] == "state"
                    }.isEmpty()
                )
            },
        )
    }

    private fun OtelJavaLogRecordData.isChangeOf(state: String): Boolean =
        eventName == AppStateKeys.CHANGE_EVENT_NAME && attributes.toStringMap()[AppStateKeys.NAME] == state

    private fun OtelJavaLogRecordData.longAttribute(key: String): Long? = attributes.get(AttributeKey.longKey(key))

    /**
     * The change events of [state] after its declaration, which was made while the first session part was open and so is the
     * first event, with no previous value.
     */
    private fun EmbraceOtelExportAssertionInterface.awaitChangesOf(state: String, count: Int): List<OtelJavaLogRecordData> {
        val events = awaitLogs(count + 1) { it.isChangeOf(state) }
        assertNull(events.first().attributes.toStringMap()[AppStateKeys.PREVIOUS_VALUE])
        return events.drop(1)
    }

    private fun List<Attribute>.packedState(state: String): Pair<String, Boolean>? =
        PackedStates.unpack(checkNotNull(findAttributeValue(AppStateKeys.PACKED)))
            .singleOrNull { it.name == state }
            ?.let { it.value to it.system }

    private fun Envelope<SessionPartPayload>.changeLog(): Span = findSpanByName(AppStateKeys.CHANGE_LOG_SPAN_NAME)

    private fun Span.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun SpanEvent.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun Span.stateEvents(state: String): List<SpanEvent> =
        events.orEmpty().filter { it.name == AppStateKeys.CHANGE_LOG_EVENT_PREFIX + state }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
