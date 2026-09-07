package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectoryStore
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTracker
import io.embrace.android.embracesdk.internal.session.persistence.SessionReconstructionService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes

/**
 * Reads any session parts that were persisted on disk by the multi-file persistence layer,
 * reconstructs each one into an envelope, and hands it to the [IntakeService] for delivery. A
 * session part is deleted once intake has accepted it.
 */
class SessionPartReader(
    private val directoryStore: SessionPartDirectoryStore,
    private val reconstructionService: SessionReconstructionService,
    private val intakeService: IntakeService,
    private val writeTracker: SessionPartWriteTracker,
    private val processIdProvider: () -> String,
    private val configService: ConfigService,
    private val logger: InternalLogger,
    private val worker: BackgroundWorker,
) {

    /**
     * Queues a read of every completed session part on disk. The work runs on [worker] so that it
     * does not hold up telemetry queued on the session persistence worker.
     */
    fun readPersistedSessionParts() {
        if (!configService.persistenceBehavior.isMultiFilePersistenceEnabled()) {
            return
        }
        worker.submit {
            directoryStore.storedDirectories()
                .filterNot { writeTracker.isWriting(it.sessionPartId) }
                .sortedWith(SessionPartDirectory.comparator)
                .forEach { directory ->
                    runCatching {
                        deliver(directory)
                    }.onFailure {
                        logger.trackInternalError(InternalErrorType.SessionPartReadFail, it)
                    }
                }
        }
    }

    /**
     * Hands the session part's telemetry to the intake service, then removes it from disk. Session
     * parts that cannot be reconstructed are deleted rather than retried.
     */
    private fun deliver(directory: SessionPartDirectory) {
        val envelope = reconstructionService.reconstruct(directory)
        if (envelope != null) {
            intakeService.take(
                intake = envelope,
                metadata = directory.createMetadata(envelope),
            )
        }
        directoryStore.delete(directory)
    }

    private fun SessionPartDirectory.createMetadata(
        envelope: Envelope<SessionPartPayload>,
    ): StoredTelemetryMetadata = StoredTelemetryMetadata(
        timestamp = timestamp,
        uuid = uuid,
        processIdentifier = envelope.findProcessIdentifier() ?: processIdProvider(),
        envelopeType = SupportedEnvelopeType.SESSION,
        complete = true,
        payloadType = PayloadType.SESSION,
        userSessionId = userSessionId,
        sessionPartId = sessionPartId,
    )

    private fun Envelope<SessionPartPayload>.findProcessIdentifier(): String? =
        getSessionPartSpan()?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER)
}
