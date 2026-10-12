package io.embrace.android.embracesdk.internal.resurrection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeResult
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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Reads any session parts that were persisted on disk by the multi-file persistence layer,
 * reconstructs each one into an envelope, and hands it to the [IntakeService] for delivery. A
 * session part is deleted once intake has stored it.
 *
 * Parts are delivered in order, and a part that can't be stored in the storage layer will block
 * subsequent parts from being processed. Therefore, we delete any part for which intake failure
 * is unrecoverable, and for those that may succeed on a retry, we keep it and stop the pass so
 * the next intake pass tries it again (but just once). The pass stops so newer session parts
 * won't be processed until this failed one has a chance to retry. A second failure is treated
 * like it were unrecoverable, biasing to unblocking the delivery queue for new session parts
 * over trying to reprocess a part that may never succeed.
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
     * The session parts whose intake attempt has failed at least once.
     */
    private val failed: MutableSet<SessionPartDirectory> = Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * Reads every completed session part on disk, returning only once each one has been handed to
     * the [IntakeService] and stored. Never throws: callers run this inline on their own critical
     * path and have to keep going regardless of what the read layer does.
     *
     * This performs disk I/O and waits on intake, so the caller is responsible for already being on
     * a background thread, and for not calling it from a thread that intake itself runs on.
     */
    fun readPersistedSessionParts(performingResurrection: Boolean = false) {
        if (!configService.config.persistence.multiFileEnabled) {
            deletePersistedSessionParts()
            return
        }
        EmbTrace.trace("mf-read-session-parts") {
            runCatching {
                val stored = directoryStore.storedDirectories()

                // Forget failed parts that have since left the disk some other way, so the set only holds parts that
                // can still be retried
                failed.retainAll(stored.toSet())

                val directories = stored
                    .filterNot(writeTracker::isWriting)
                    .sortedWith(SessionPartDirectory.comparator)

                for (directory in directories) {
                    val proceed = runCatching {
                        deliver(directory, performingResurrection)
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
     * has stored it. Returns whether the pass may move on to the next part.
     *
     * In the case the storage layer fails to store the session part, the specific outcome from the
     * intake attempt determines what we do with the session part data:
     *
     * - Storage not attempted: data kept
     * - Intake timed out or thread interrupted: data kept, intake pass stopped
     * - First recoverable failure: data kept, intake pass stopped, retried on next intake
     * - Unrecoverable failure or second failure: data deleted
     */
    private fun deliver(directory: SessionPartDirectory, performingResurrection: Boolean): Boolean {
        val envelope = reconstructionService.reconstruct(directory)
        if (envelope == null) {
            // No need to log as the failed construction is recorded elsewhere
            directoryStore.delete(directory)
            return true
        }
        val task = intakeService.take(
            intake = envelope,
            metadata = directory.createMetadata(envelope, performingResurrection),
            onStored = { directoryStore.delete(directory) },
        )

        val result = try {
            task.get(INTAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (exc: TimeoutException) {
            logger.trackInternalError(InternalErrorType.IntakeFail, exc)
            return false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }

        return when (result) {
            // No failures mean the intake was successful or not attempted.
            // Either way, we clean up and move onto the next part.
            IntakeResult.STORED, IntakeResult.NOT_ATTEMPTED -> {
                failed.remove(directory)
                true
            }
            IntakeResult.PERMANENT_FAILURE -> {
                deleteSessionPartData(directory, "intake failure unrecoverable")
                true
            }
            IntakeResult.RETRYABLE_FAILURE -> {
                if (failed.add(directory)) {
                    // If a session part has a recoverable failure that hasn't been retried, abort the
                    // pass so it can be retried later
                    false
                } else {
                    // If a session part intake has already been retried, delete it and move on.
                    deleteSessionPartData(directory, "failed intake retry failed again")
                    true
                }
            }
        }
    }

    private fun deleteSessionPartData(directory: SessionPartDirectory, reason: String) {
        failed.remove(directory)
        directoryStore.delete(directory)
        logger.trackInternalError(
            InternalErrorType.SessionPartReadFail,
            IllegalStateException("Session part data deleted: $reason"),
        )
    }

    private fun SessionPartDirectory.createMetadata(
        envelope: Envelope<SessionPartPayload>,
        performingResurrection: Boolean,
    ): StoredTelemetryMetadata = StoredTelemetryMetadata(
        timestamp = timestamp,
        uuid = uuid,
        processIdentifier = envelope.findProcessIdentifier() ?: processIdProvider(),
        envelopeType = SupportedEnvelopeType.SESSION,
        complete = !performingResurrection,
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
