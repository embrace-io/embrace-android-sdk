package io.embrace.android.embracesdk.benchmark

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.arch.store.DeliveredAeiStore
import io.embrace.android.embracesdk.internal.arch.store.NetworkCaptureRuleStore
import io.embrace.android.embracesdk.internal.capture.state.StateRecorder
import io.embrace.android.embracesdk.internal.capture.state.StateSnapshot
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.PersistenceConfig
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.network.http.HttpRequestInfoModifierChain
import io.embrace.android.embracesdk.internal.network.parent.NetworkSpanParents
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartWriterImpl
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.storage.SdkStorage
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.io.File

/**
 * [stateCount] states capturing through one session part after another, each recorded into a span of its own or, when
 * [journaled], into the journal of state changes.
 *
 * It runs what the SDK runs when a session part's telemetry is persisted as multiple files: [StateDataSource]s held by the
 * [InstrumentationRegistryImpl], the harness's destination and span stack, and the [SessionPartWriterImpl] writing every
 * part's files under [storageDir] on a worker of its own. [changeSessionPart] makes the calls a session part change makes
 * of them, in the order it makes them. It does not deliver a part, and unless [keepParts] is set it deletes each part once
 * it has been written, as delivery would.
 */
internal class StateCaptureFixture(
    val stateCount: Int,
    private val journaled: Boolean,
    private val storageDir: File,
    private val clock: Clock = NormalizedIntervalClock(),
    private val keepParts: Boolean = false,
) : AutoCloseable {

    private val harness = TelemetryDestinationHarness(useKotlinSdk = true, clock = clock)
    private val configService: ConfigService = object : ConfigService by harness.configService {
        override val config: EmbraceConfig = EmbraceConfig(
            autoDataCapture = { AutoDataCaptureConfig(sharedStateSpanEnabled = { journaled }) },
            persistence = { PersistenceConfig(multiFileEnabled = { true }) },
        )
    }
    private val eventBus = EventBus(harness.errorHandler)
    private val recorder: StateRecorder? = if (journaled) {
        StateRecorder(clock).also { eventBus.addHandler(StateUpdate.KEY, it) }
    } else {
        null
    }
    private val parts = SessionPartFiles(harness, configService, storageDir, clock, keepParts, recorder)
    private val registry = InstrumentationRegistryImpl(harness.errorHandler)
    private val states: List<BenchmarkStateDataSource>
    private val transitionCounts = IntArray(stateCount)

    init {
        val args = StateCaptureInstrumentationArgs(
            configService = configService,
            destination = harness.destination,
            errorHandler = harness.errorHandler,
            clock = clock,
            telemetryService = harness.telemetryService,
            eventBus = eventBus,
            sessionPartId = parts::currentPartId,
        )
        states = List(stateCount) { index ->
            checkNotNull(registry.add { BenchmarkStateDataSource(args, STATE_NAMES[index]) }) {
                "state $index was not registered"
            }
        }
    }

    /**
     * Ends the current session part and starts the next, as the SDK does when the app changes between foreground and
     * background, and returns the id of the part that ended.
     */
    fun changeSessionPart(): String = parts.changeSessionPart(
        beforeEnd = registry::onPreSessionEnd,
        afterChange = registry::onPostSessionChange,
    )

    /**
     * Changes the value of the state at [stateIndex].
     */
    fun transition(stateIndex: Int) {
        val count = ++transitionCounts[stateIndex]
        states[stateIndex].onStateChange(
            newState = STATE_VALUES[count % STATE_VALUES.size],
            transitionTimeMs = clock.now(),
        )
    }

    /**
     * Changes the value of every state [transitionsPerState] times, one state after another so that their transitions
     * interleave as they would in an app.
     */
    fun transitionEveryState(transitionsPerState: Int) {
        repeat(transitionsPerState) {
            repeat(stateCount, ::transition)
        }
    }

    /**
     * The attributes every log record is stamped with to say what value each state had: one for each state, or the one
     * snapshot of them all and its place in the order of state changes.
     */
    fun stampLog(): Map<String, String> = buildMap {
        val snapshot = recorder?.snapshot()
        if (snapshot == null) {
            registry.getCurrentStates().forEach { put(it.key, it.value.toString()) }
        } else if (!snapshot.isEmpty()) {
            put(StateSnapshot.ATTRIBUTE, snapshot.encoded)
            put(StateSnapshot.SEQUENCE_ATTRIBUTE, snapshot.sequence.toString())
        }
    }

    /**
     * The span in progress that the state at [stateIndex] is recorded into. Only a state that is not journaled has one.
     */
    fun stateSpan(stateIndex: Int): EmbraceSdkSpan {
        val name = "emb-state-${STATE_NAMES[stateIndex]}"
        return harness.spanRepository.getActiveEmbraceSpans().single { it.snapshot()?.name == name }
    }

    /**
     * Waits for everything queued for the worker so far to be written.
     */
    fun awaitWrites() = parts.awaitWrites()

    /**
     * Waits for everything queued for the worker so far to be written, then keeps the worker from running anything more
     * until [releaseWrites]. What is done in between is then the work of the calling thread alone: nothing the SDK does on
     * a thread that reports a state change or changes the session part waits for the worker.
     */
    fun holdWrites() = parts.holdWrites()

    /**
     * Lets the worker run again after [holdWrites], and waits for it to write everything queued meanwhile.
     */
    fun releaseWrites() = parts.releaseWrites()

    /**
     * Where the session part with [sessionPartId] is on disk, once everything queued for the worker has been written.
     */
    fun partDirectory(sessionPartId: String): SessionPartDirectory = parts.partDirectory(sessionPartId)

    /**
     * Reads the session part in [directory] back from disk as the envelope that would be delivered for it, which for
     * journaled states is where their span is made. The part must have ended, and the fixture must keep its parts.
     */
    fun readPart(directory: SessionPartDirectory): Envelope<SessionPartPayload> = parts.readPart(directory)

    /**
     * The directory holding the files of the session part with [sessionPartId].
     */
    fun partDir(sessionPartId: String): File = parts.partDir(sessionPartId)

    override fun close() = parts.close()

    companion object {
        /**
         * The most states the fixture can hold.
         */
        const val MAX_STATE_COUNT: Int = 20

        /**
         * The most times one state can change value in a session part before the fixture's own limit drops the change.
         */
        const val MAX_TRANSITIONS_PER_STATE: Int = 10_000

        /**
         * The names of the states, in the order they are added.
         */
        val STATE_NAMES: List<String> = listOf(
            "network",
            "power",
            "screen-automatic",
            "orientation",
            "thermal",
            "battery",
            "memory-pressure",
            "dark-mode",
            "locale",
            "connectivity-type",
            "audio-route",
            "bluetooth",
            "location-permission",
            "notifications",
            "data-saver",
            "vpn",
            "roaming",
            "airplane-mode",
            "foldable-posture",
            "multi-window",
        )

        /**
         * The value every state has until it first changes.
         */
        const val INITIAL_STATE_VALUE: String = "state-value-initial"

        /**
         * The values a state takes in turn as it changes, after the one it starts with.
         */
        val STATE_VALUES: List<String> = listOf("state-value-a", "state-value-b")

        init {
            check(STATE_NAMES.size == MAX_STATE_COUNT)
        }
    }
}

private class BenchmarkState(initialValue: String, name: String) : SchemaType.State<String>(initialValue, name)

private class BenchmarkStateDataSource(
    args: InstrumentationArgs,
    name: String,
) : StateDataSource<String>(
    args = args,
    stateTypeFactory = { BenchmarkState(it, name) },
    defaultValue = StateCaptureFixture.INITIAL_STATE_VALUE,
    maxTransitions = StateCaptureFixture.MAX_TRANSITIONS_PER_STATE,
)

/**
 * The few things a [StateDataSource] reads from its [InstrumentationArgs]. Everything else throws, as a benchmark that reached
 * it would no longer be measuring state capture alone.
 */
private class StateCaptureInstrumentationArgs(
    override val configService: ConfigService,
    override val destination: TelemetryDestination,
    override val errorHandler: ErrorHandler,
    override val clock: Clock,
    override val telemetryService: InternalTelemetryService,
    override val eventBus: EventBus,
    private val sessionPartId: () -> String,
) : InstrumentationArgs {

    override fun sessionPartId(): String = sessionPartId.invoke()

    override val processStateTracker: ProcessStateTracker get() = unused()
    override val versionChecker: VersionChecker get() = unused()
    override val systemInfo: SystemInfo get() = unused()
    override val httpRequestInfoModifierChain: HttpRequestInfoModifierChain get() = unused()
    override val networkSpanParents: NetworkSpanParents get() = unused()
    override val context: Context get() = unused()
    override val application: Application get() = unused()
    override val deliveredAeiStore: DeliveredAeiStore get() = unused()
    override val networkCaptureRuleStore: NetworkCaptureRuleStore get() = unused()
    override val serializer: PlatformSerializer get() = unused()
    override val uuidSource: UuidSource get() = unused()
    override val ordinalStore: OrdinalStore get() = unused()
    override val packageInfo: PackageInfo get() = unused()
    override val processIdentifier: String get() = unused()
    override val crashMarkerFile: File get() = unused()
    override val sdkStorage: SdkStorage get() = unused()
    override val navigationTrackingService: NavigationTrackingService get() = unused()

    override fun backgroundWorker(worker: Worker.Background): BackgroundWorker = unused()
    override fun <T> priorityWorker(worker: Worker.Priority): PriorityWorker<T> = unused()
    override fun <T> systemService(name: String): T = unused()
    override fun userSessionId(): String = unused()
    override fun activeSessionIds(): SessionIdsSnapshot = unused()
    override fun userSessionProperties(): Map<String, String> = unused()
    override fun registerSessionPartChangeListener(listener: SessionPartChangeListener): Unit = unused()
    override fun registerSessionPartEndListener(listener: SessionPartEndListener): Unit = unused()

    private fun unused(): Nothing = error("not used by state capture")
}
