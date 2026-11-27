package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.state.StateRecorder
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartWriter
import io.embrace.android.embracesdk.internal.session.persistence.StateJournalRecord
import io.embrace.android.embracesdk.internal.state.StateExportMode
import io.embrace.android.embracesdk.internal.state.StateRecorderImpl
import io.embrace.android.embracesdk.internal.state.StateReplayImpl
import io.embrace.android.embracesdk.internal.state.StateSnapshot
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.opentelemetry.kotlin.logging.Logger

/**
 * State capture under v2 of the runtime state data model: the one [recorder] every state data source reports to, and
 * what it adds to telemetry when association is on.
 */
class StateCapture internal constructor(
    private val recorderImpl: StateRecorderImpl,
    val associationEnabled: Boolean,
    val exportMode: StateExportMode,
) {

    @Volatile
    private var writer: SessionPartWriter? = null

    val recorder: StateRecorder
        get() = recorderImpl

    /**
     * What a log is stamped with: every state's value and the number of the last change. Every log carries it while v2
     * is on, in place of v1's stamp, so that no log is left without state.
     */
    fun stamp(): StateSnapshot = recorderImpl.current()

    /**
     * Has the recorder learn of every session part boundary. Done before the first part starts.
     */
    fun attach(sessionPartTracker: SessionPartTracker) {
        sessionPartTracker.addSessionPartEndListener(recorderImpl)
        sessionPartTracker.addSessionPartChangeListener(recorderImpl)
    }

    /**
     * Gives the recorder the writer its journal records go to.
     */
    fun onSessionPartWriter(writer: SessionPartWriter) {
        this.writer = writer
    }

    internal fun appendJournal(sessionPartId: String, record: StateJournalRecord) {
        writer?.onStateJournalRecord(sessionPartId, record)
    }
}

/**
 * Creates state capture under v2, or returns null when v1 captures state instead. v2 needs v1's switch, its own switch,
 * and multi-file persistence, through which its journal is written. Association adds the stamp on logs, the link on
 * spans, and the renderings for a customer's exporters.
 */
@Suppress("LongParameterList")
fun createStateCapture(
    configService: ConfigService,
    clock: Clock,
    externalOtelConfig: ExternalOtelConfig,
    sessionPartIdProvider: () -> String?,
    replayWorker: BackgroundWorker,
    loggerSupplier: () -> Logger,
    processIdentifierProvider: () -> String,
): StateCapture? {
    val capture = configService.config.autoDataCapture
    val enabled = capture.stateCaptureEnabled && capture.sharedStateSpanEnabled &&
        configService.config.persistence.multiFileEnabled
    if (!enabled) {
        return null
    }
    val association = capture.stateAssociationEnabled
    // only an exporter gets state rendered as a value for each state and the replayed changes; a processor sees the
    // record Embrace stamped, so it does not count as receiving them
    val receivesLogs = externalOtelConfig.getLogExporters().isNotEmpty()
    val exportMode = when (capture.stateExportMode?.lowercase()) {
        "verbose" -> StateExportMode.VERBOSE
        "association" -> StateExportMode.ASSOCIATION
        else -> {
            val exportsSpans = externalOtelConfig.getSpanExporters().isNotEmpty() ||
                externalOtelConfig.getSpanProcessors().isNotEmpty()
            if (!receivesLogs && exportsSpans) StateExportMode.VERBOSE else StateExportMode.ASSOCIATION
        }
    }
    val replay = if (association && receivesLogs) {
        StateReplayImpl(
            worker = replayWorker,
            loggerSupplier = loggerSupplier,
            processIdentifier = processIdentifierProvider(),
            verbose = exportMode == StateExportMode.VERBOSE,
        )
    } else {
        null
    }
    lateinit var stateCapture: StateCapture
    val recorder = StateRecorderImpl(
        clock = clock,
        sessionPartIdProvider = sessionPartIdProvider,
        journal = { id, record -> stateCapture.appendJournal(id, record) },
        replay = replay,
    )
    stateCapture = StateCapture(recorder, association, exportMode)
    return stateCapture
}
