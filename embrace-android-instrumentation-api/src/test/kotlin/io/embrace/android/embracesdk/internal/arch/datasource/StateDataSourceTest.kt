package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.TEST_STATE_DECLARED_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fakes.TestState
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.TestStateValue
import io.embrace.android.embracesdk.fakes.appStateEnabledConfigService
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.state.StateDeclaration
import io.embrace.android.embracesdk.internal.state.StateDrops
import io.embrace.android.embracesdk.internal.state.StateEvents
import io.embrace.android.embracesdk.internal.state.StateRecorder
import io.embrace.android.embracesdk.internal.state.StateReport
import io.embrace.android.embracesdk.internal.state.StateUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class StateDataSourceTest {

    private lateinit var args: FakeInstrumentationArgs
    private lateinit var dataSource: TestStateDataSource
    private val declarations = mutableListOf<StateDeclaration>()
    private val updates = mutableListOf<StateUpdate>()
    private val drops = mutableListOf<StateDrops>()

    @Before
    fun setUp() {
        args = FakeInstrumentationArgs(sessionPartIdSupplier = { "part-id" })
        dataSource = TestStateDataSource(args)
        listenToBus(args)
    }

    @Test
    fun `unrecorded transitions are only reported on the part where they happened`() {
        dataSource.onDataCaptureEnabled()
        dataSource.onStateChange("a", 1L)
        dataSource.onStateChange("a", 2L)
        dataSource.onPreSessionEnd()
        dataSource.onPostSessionChange()
        dataSource.onStateChange("b", 3L)
        dataSource.onPreSessionEnd()

        val (first, second) = args.destination.createdStateTokens
        assertEquals(UnrecordedTransitions(droppedByInstrumentation = 1), first.endUnrecordedTransitions)
        assertEquals(listOf(UnrecordedTransitions()), second.updateUnrecordedTransitions)
        assertEquals(UnrecordedTransitions(), second.endUnrecordedTransitions)
    }

    @Test
    fun `transitions over the limit are counted as dropped`() {
        dataSource.onDataCaptureEnabled()
        repeat(MAX_TRANSITIONS + 3) {
            dataSource.onStateChange("value-$it", it.toLong())
        }
        dataSource.onPreSessionEnd()

        val token = args.destination.createdStateTokens.single()
        assertEquals(MAX_TRANSITIONS, token.transitions.size)
        assertEquals(UnrecordedTransitions(droppedByInstrumentation = 3), token.endUnrecordedTransitions)
    }

    @Test
    fun `a state span still open when another is started is ended`() {
        dataSource.onDataCaptureEnabled()
        // a part change that starts its span before the previous part's span was taken
        dataSource.onPostSessionChange()

        val tokens = args.destination.createdStateTokens
        assertEquals(2, tokens.size)
        assertNotEquals(0L, tokens[0].endTimeMs)
        assertEquals(0L, tokens[1].endTimeMs)
    }

    /**
     * The first capture of an async source runs on a worker. A part change while it is starting its span waits for it, then
     * ends that span and starts the new part's, so the span left open is the new part's.
     */
    @Test
    fun `a part change during the first capture leaves the new part's span open`() {
        val starters = Collections.synchronizedList(mutableListOf<String>())
        val capturing = CountDownLatch(1)
        val release = CountDownLatch(1)
        // recorded after any hold, so the names line up with the tokens in the order they are created
        args.destination.onStartSessionPartStateCapture = {
            if (Thread.currentThread().name == CAPTURE_THREAD) {
                capturing.countDown()
                release.await(AWAIT_SECONDS, TimeUnit.SECONDS)
            }
            starters.add(Thread.currentThread().name)
        }

        val capture = thread(name = CAPTURE_THREAD) { dataSource.onDataCaptureEnabled() }
        capturing.await(AWAIT_SECONDS, TimeUnit.SECONDS)
        val partChange = thread(name = PART_CHANGE_THREAD) {
            dataSource.onPreSessionEnd()
            dataSource.onPostSessionChange()
        }
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(AWAIT_SECONDS)
        while (partChange.isAlive && partChange.state != Thread.State.BLOCKED && System.currentTimeMillis() < deadline) {
            Thread.yield()
        }
        release.countDown()
        capture.join()
        partChange.join()

        val tokens = args.destination.createdStateTokens
        val openStarters = tokens.indices.filter { tokens[it].endTimeMs == 0L }.map { starters[it] }
        assertEquals(listOf(PART_CHANGE_THREAD), openStarters)
    }

    /**
     * A part change ends the part's span, builds the ending part's payload while that part is still the active one, then starts
     * the next part's span. A first capture in between leaves the span to the part change.
     */
    @Test
    fun `a first capture between a part's end and the next part's start leaves only the next part's span`() {
        dataSource.onPreSessionEnd()
        dataSource.onDataCaptureEnabled()
        dataSource.onPostSessionChange()

        val tokens = args.destination.createdStateTokens
        assertEquals(1, tokens.size)
        assertEquals(0L, tokens.single().endTimeMs)
    }

    @Test
    fun `with the model on enabling declares the state with its definition and default value`() {
        val modelArgs = modelOnArgs()
        TestStateDataSource(modelArgs).onDataCaptureEnabled()

        val declaration = declarations.single()
        assertEquals("test", declaration.definition.name)
        assertEquals(MAX_TRANSITIONS, declaration.definition.changeLimit)
        assertEquals(setOf(TEST_STATE_DECLARED_ATTRIBUTE_KEY), declaration.definition.attributeKeys)
        assertEquals("UNKNOWN", declaration.initialValue)
        assertEquals(false, declaration.system)
        assertEquals(modelArgs.clock.now(), declaration.timeMs)
    }

    @Test
    fun `with the model on a generated default is declared as generated with its own limit and keys`() {
        val modelArgs = modelOnArgs()
        val generated = TestStateValue.SystemValue("start")
        object : StateDataSource<Any>(
            args = modelArgs,
            stateTypeFactory = ::TestState,
            defaultValue = generated,
            maxTransitions = 7,
            changeAttributeKeys = setOf("kept", "emb.app.state.reserved", "emb.state.legacy"),
        ) {}.onDataCaptureEnabled()

        val declaration = declarations.single()
        assertEquals(7, declaration.definition.changeLimit)
        assertEquals(setOf("kept"), declaration.definition.attributeKeys)
        assertEquals("$generated", declaration.initialValue)
        assertEquals(true, declaration.system)
    }

    @Test
    fun `with the model on a change emits an update with the value, the time and the attributes`() {
        val modelArgs = modelOnArgs()
        val source = TestStateDataSource(modelArgs).apply { onDataCaptureEnabled() }

        source.onStateChange("a", 5000L, mapOf(TEST_STATE_DECLARED_ATTRIBUTE_KEY to "x"))

        val update = updates.single()
        assertEquals("test", update.state)
        assertEquals("a", update.value)
        assertEquals(false, update.system)
        assertEquals(5000L, update.timeMs)
        assertEquals(mapOf(TEST_STATE_DECLARED_ATTRIBUTE_KEY to "x"), update.attributes)
        assertEquals("a", source.getCurrentStateValue())
    }

    @Test
    fun `with the model on a generated value is emitted as generated`() {
        val source = TestStateDataSource(modelOnArgs()).apply { onDataCaptureEnabled() }
        val generated = TestStateValue.SystemValue("x")

        source.onStateChange(generated, 5000L)
        source.onStateChange(TestStateValue.NonSystemValue("x"), 6000L)

        assertEquals(listOf("$generated", "${TestStateValue.NonSystemValue("x")}"), updates.map { it.value })
        assertEquals(listOf(true, false), updates.map { it.system })
    }

    @Test
    fun `with the model on the attributes of the value being left go with the transition's`() {
        val modelArgs = modelOnArgs()
        val source = object : StateDataSource<Any>(
            args = modelArgs,
            stateTypeFactory = ::TestState,
            defaultValue = "UNKNOWN",
            changeAttributeKeys = setOf("outgoing", "transition", "shared"),
        ) {
            override fun outgoingValueAttributes(value: Any): Map<String, String> =
                mapOf("outgoing" to value.toString(), "shared" to "from-outgoing")
        }.apply { onDataCaptureEnabled() }

        source.onStateChange("a", 1L)
        source.onStateChange("b", 2L, mapOf("transition" to "t", "shared" to "from-transition"))

        assertEquals(
            listOf(
                mapOf("outgoing" to "UNKNOWN", "shared" to "from-outgoing"),
                mapOf("outgoing" to "a", "shared" to "from-transition", "transition" to "t"),
            ),
            updates.map { it.attributes },
        )
    }

    @Test
    fun `with the model on a repeated value emits nothing`() {
        val source = TestStateDataSource(modelOnArgs()).apply { onDataCaptureEnabled() }

        source.onStateChange("UNKNOWN", 1L)
        source.onStateChange("a", 2L)
        source.onStateChange("a", 3L)

        assertEquals(listOf("a"), updates.map { it.value })
        assertEquals(listOf(2L), updates.map { it.timeMs })
    }

    @Test
    fun `with the model on dropped transitions are reported as dropped`() {
        val source = TestStateDataSource(modelOnArgs()).apply { onDataCaptureEnabled() }

        source.onStateChange("a", 1L, droppedTransitions = 3)
        source.onStateChange("a", 2L, droppedTransitions = 2)
        source.onStateChange("b", 3L)

        assertEquals(listOf("test" to 3, "test" to 2), drops.map { it.state to it.count })
        assertEquals(listOf("a", "b"), updates.map { it.value })
    }

    @Test
    fun `with the model on no span is started and nothing happens at a part boundary`() {
        val modelArgs = modelOnArgs()
        val source = TestStateDataSource(modelArgs).apply { onDataCaptureEnabled() }

        source.onStateChange("a", 1L)
        source.onPreSessionEnd()
        source.onPostSessionChange()
        source.onStateChange("b", 2L)
        source.onPreSessionEnd()

        assertTrue(modelArgs.destination.createdStateTokens.isEmpty())
        assertTrue(modelArgs.destination.createdSpans.isEmpty())
        assertEquals(emptyMap<String, Any>(), source.currentStateAttributes())
        assertEquals(listOf("a", "b"), updates.map { it.value })
    }

    @Test
    fun `with the model off nothing reaches the bus and the state still describes itself`() {
        dataSource.onDataCaptureEnabled()
        dataSource.onStateChange("a", 1L, droppedTransitions = 2)

        assertTrue(declarations.isEmpty())
        assertTrue(updates.isEmpty())
        assertTrue(drops.isEmpty())
        assertEquals(mapOf<String, Any>("emb.state.test" to "a"), dataSource.currentStateAttributes())
        assertEquals(1, args.destination.createdStateTokens.size)
    }

    @Test
    fun `with the model on two typed values that render to the same text are one value`() {
        val modelArgs = modelOnArgs()
        val recorder = recorderOn(modelArgs)
        val source = TestStateDataSource(modelArgs).apply { onDataCaptureEnabled() }

        // a String and a StringBuilder are different typed values with the same text
        source.onStateChange("x", 1L)
        source.onStateChange(StringBuilder("x"), 2L)

        assertEquals(listOf("x"), updates.map { it.value })
        assertEquals(2L, recorder.current().seq)
        assertEquals("test=x", recorder.current().packed())
    }

    @Test
    fun `with the model on values that differ only past the length limit are one value with no attributes gathered`() {
        val modelArgs = modelOnArgs()
        val recorder = recorderOn(modelArgs)
        var gathered = 0
        val source = object : StateDataSource<Any>(
            args = modelArgs,
            stateTypeFactory = ::TestState,
            defaultValue = "UNKNOWN",
        ) {
            override fun outgoingValueAttributes(value: Any): Map<String, String> {
                gathered++
                return emptyMap()
            }
        }.apply { onDataCaptureEnabled() }

        source.onStateChange("a".repeat(2_500) + "b", 1L)
        assertEquals(1, gathered)
        source.onStateChange("a".repeat(2_500) + "c", 2L)

        assertEquals(1, updates.size)
        assertEquals(1, gathered)
        assertEquals(2L, recorder.current().seq)
        assertEquals(2_000, recorder.current().value(0).length)
    }

    @Test
    fun `with the model on the latest report stands whoever made it`() {
        val modelArgs = modelOnArgs()
        val recorder = recorderOn(modelArgs)
        val source = TestStateDataSource(modelArgs).apply { onDataCaptureEnabled() }

        source.onStateChange("A", 1L)
        modelArgs.eventBus.emit(StateEvents.REPORTED, StateReport("test", "B", 2L))
        source.onStateChange("A", 3L)

        assertEquals(listOf("A", "B", "A"), updates.map { it.value })
        assertEquals("A", recorder.current().value(0))
        assertEquals(4L, recorder.current().seq)
    }

    @Test
    fun `with the model on a report from outside that repeats the native value is no change`() {
        val modelArgs = modelOnArgs()
        val recorder = recorderOn(modelArgs)
        val source = TestStateDataSource(modelArgs).apply { onDataCaptureEnabled() }

        source.onStateChange("A", 1L)
        modelArgs.eventBus.emit(StateEvents.REPORTED, StateReport("test", "A", 2L))

        assertEquals(listOf("A"), updates.map { it.value })
        assertEquals(2L, recorder.current().seq)
    }

    @Test
    fun `with the model on a report from outside is not generated, has the reporter's time and the outgoing attributes`() {
        val modelArgs = modelOnArgs()
        val source = attributedSource(modelArgs).apply { onDataCaptureEnabled() }

        val generated = TestStateValue.SystemValue("generated")
        source.onStateChange(generated, 1L)
        modelArgs.eventBus.emit(StateEvents.REPORTED, StateReport("test", "$generated", 7L))

        assertEquals(listOf(true, false), updates.map { it.system })
        val outside = updates.last()
        assertEquals("$generated", outside.value)
        assertEquals(7L, outside.timeMs)
        assertEquals(mapOf(OUTGOING_KEY to "typed:$generated"), outside.attributes)
    }

    @Test
    fun `with the model on the change after an outside report carries the reported value's outgoing attributes`() {
        val modelArgs = modelOnArgs()
        val source = attributedSource(modelArgs).apply { onDataCaptureEnabled() }

        source.onStateChange("a", 1L)
        modelArgs.eventBus.emit(StateEvents.REPORTED, StateReport("test", "b", 2L))
        source.onStateChange("c", 3L)

        assertEquals(
            listOf(
                mapOf(OUTGOING_KEY to "typed:UNKNOWN"),
                mapOf(OUTGOING_KEY to "typed:a"),
                mapOf(OUTGOING_KEY to "generated:false"),
            ),
            updates.map { it.attributes },
        )
    }

    @Test
    fun `with the model on a report from outside for another state is ignored`() {
        val modelArgs = modelOnArgs()
        val recorder = recorderOn(modelArgs)
        TestStateDataSource(modelArgs).onDataCaptureEnabled()

        modelArgs.eventBus.emit(StateEvents.REPORTED, StateReport("other", "B", 2L))

        assertTrue(updates.isEmpty())
        assertEquals("test=UNKNOWN", recorder.current().packed())
    }

    @Test
    fun `with the model on a report from outside before the data source is enabled changes nothing, then or later`() {
        val modelArgs = modelOnArgs()
        val recorder = recorderOn(modelArgs)
        val source = TestStateDataSource(modelArgs)

        modelArgs.eventBus.emit(StateEvents.REPORTED, StateReport("test", "B", 1L))
        source.onDataCaptureEnabled()

        assertTrue(updates.isEmpty())
        assertEquals("test=UNKNOWN", recorder.current().packed())
        assertEquals(1L, recorder.current().seq)
        assertEquals("UNKNOWN", source.getCurrentStateValue())
        source.onStateChange("UNKNOWN", 2L)
        assertEquals(1L, recorder.current().seq)
    }

    @Test
    fun `with the model on a state whose name cannot name one reports an SDK error once and declares and reports nothing`() {
        val modelArgs = modelOnArgs()
        modelArgs.errorHandler.throwOnSdkError = false
        val recorder = recorderOn(modelArgs)
        val declarations = mutableListOf<StateDeclaration>()
        modelArgs.eventBus.addHandler(StateEvents.DECLARED) { declarations.add(it) }
        val source = object : StateDataSource<Any>(
            args = modelArgs,
            stateTypeFactory = ::BadlyNamedState,
            defaultValue = "UNKNOWN",
        ) {}

        source.onDataCaptureEnabled()
        source.onStateChange("hot", 1L)
        modelArgs.eventBus.emit(StateEvents.REPORTED, StateReport("thermal_state", "cool", 2L))

        val error = modelArgs.errorHandler.sdkErrorMessages.single()
        assertTrue(checkNotNull(error.throwable?.message).contains("thermal_state"))
        assertTrue(declarations.isEmpty())
        assertTrue(updates.isEmpty())
        assertEquals(0L, recorder.current().seq)
    }

    @Test
    fun `with the model off a report from outside does nothing`() {
        dataSource.onDataCaptureEnabled()
        dataSource.onStateChange("a", 1L)

        args.eventBus.emit(StateEvents.REPORTED, StateReport("test", "B", 2L))
        dataSource.onStateChange("a", 3L)

        assertTrue(updates.isEmpty())
        assertEquals("a", dataSource.getCurrentStateValue())
        assertEquals(mapOf<String, Any>("emb.state.test" to "a"), dataSource.currentStateAttributes())
        assertEquals(listOf("a"), args.destination.createdStateTokens.single().transitions.map { it.second })
    }

    private fun attributedSource(modelArgs: InstrumentationArgs) = object : StateDataSource<Any>(
        args = modelArgs,
        stateTypeFactory = ::TestState,
        defaultValue = "UNKNOWN",
        changeAttributeKeys = setOf(OUTGOING_KEY),
    ) {
        override fun outgoingValueAttributes(value: Any): Map<String, String> = mapOf(OUTGOING_KEY to "typed:$value")
        override fun outgoingAttributes(
            generated: Boolean,
        ): Map<String, String> = mapOf(OUTGOING_KEY to "generated:$generated")
    }

    private fun recorderOn(modelArgs: FakeInstrumentationArgs) = StateRecorder(modelArgs.clock).also {
        it.attach(modelArgs.eventBus)
    }

    private fun modelOnArgs(): FakeInstrumentationArgs = FakeInstrumentationArgs(
        sessionPartIdSupplier = { "part-id" },
        configService = appStateEnabledConfigService(),
    ).also(::listenToBus)

    private fun listenToBus(args: InstrumentationArgs) {
        args.eventBus.addHandler(StateEvents.DECLARED) { declarations.add(it) }
        args.eventBus.addHandler(StateEvents.UPDATED) { updates.add(it) }
        args.eventBus.addHandler(StateEvents.DROPPED) { drops.add(it) }
    }

    private class BadlyNamedState(initialValue: Any) : SchemaType.State<Any>(initialValue, "thermal_state")

    private companion object {
        const val MAX_TRANSITIONS = 100
        const val OUTGOING_KEY = "outgoing"
        const val CAPTURE_THREAD = "first-capture"
        const val PART_CHANGE_THREAD = "part-change"
        const val AWAIT_SECONDS = 5L
    }
}
