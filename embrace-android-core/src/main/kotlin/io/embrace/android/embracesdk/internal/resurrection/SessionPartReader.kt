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
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Reads any session parts that were persisted on disk by the multi-file persistence layer,
 * reconstructs each one into an envelope, and hands it to the [IntakeService] for delivery. A
 * session part is deleted once intake has stored it.
 */
class SessionPartReader(
    private val sessionsDir: Lazy<File>,
    private val directoryStore: SessionPartDirectoryStore,
    private val reconstructionService: SessionReconstructionService,
    private val intakeService: IntakeService,
    private val writeTracker: SessionPartWriteTracker,
    private val processIdProvider: () -> String,
    private val configService: ConfigService,
    private val logger: InternalLogger,
) {

    /**
     * Reads every completed session part on disk, returning only once each one has been handed to
     * the [IntakeService] and stored. Never throws: callers run this inline on their own critical
     * path and have to keep going regardless of what the read layer does.
     *
     * This performs disk I/O and waits on intake, so the caller is responsible for already being on
     * a background thread, and for not calling it from a thread that intake itself runs on.
     */
    fun readPersistedSessionParts() {
        if (!configService.persistenceBehavior.isMultiFilePersistenceEnabled()) {
            deletePersistedSessionParts()
            return
        }
        EmbTrace.trace("mf-read-session-parts") {
            runCatching {
                val directories = directoryStore.storedDirectories()
                    .filterNot { writeTracker.isWriting(it.sessionPartId) }
                    .sortedWith(SessionPartDirectory.comparator)

                for (directory in directories) {
                    val proceed = runCatching {
                        deliver(directory)
                    }.onFailure {
                        logger.trackInternalError(InternalErrorType.SessionPartReadFail, it)
                    }.getOrDefault(true)

                    if (!proceed) {
                        break
                    }
                }
            }.onFailure {
                logger.trackInternalError(InternalErrorType.SessionPartReadFail, it)
            }
        }
    }

    private fun deletePersistedSessionParts() {
        EmbTrace.trace("mf-delete-session-parts") {
            runCatching {
                sessionsDir.value.deleteRecursively()
            }.onFailure {
                logger.trackInternalError(InternalErrorType.SessionPartReadFail, it)
            }
        }
    }

    /**
     * Hands the session part's telemetry to the intake service, and removes it from disk once intake
     * has stored it. A part that intake drops or fails to store is left alone so that the next launch
     * can retry it, as this is the only copy of the telemetry. Session parts that cannot be
     * reconstructed are deleted rather than retried.
     */
    private fun deliver(directory: SessionPartDirectory): Boolean {
        val envelope = reconstructionService.reconstruct(directory)
        if (envelope == null) {
            directoryStore.delete(directory)
            return true
        }
        val task = intakeService.take(
            intake = envelope,
            metadata = directory.createMetadata(envelope),
            onStored = { directoryStore.delete(directory) },
        )
        return try {
            task.get(INTAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            true
        } catch (exc: TimeoutException) {
            logger.trackInternalError(InternalErrorType.IntakeFail, exc)
            false
        } catch (exc: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
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

    private companion object {
        private const val INTAKE_TIMEOUT_MS = 5_000L
    }
}
