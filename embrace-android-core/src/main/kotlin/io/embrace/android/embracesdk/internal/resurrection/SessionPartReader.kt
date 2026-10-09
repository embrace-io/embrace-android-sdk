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
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Reads any session parts that were persisted on disk in multi-file persistence mode,
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
 *
 * An intake that times out is treated the same way: the pass stops, the next pass waits on that
 * same intake once more, and if it still hasn't finished, the intake is cancelled and the part is
 * deleted.
 *
 * A part left by a process that has since died is resurrected by [deadPartResurrector] before it is
 * sent to the intake service. A resurrected part is complete and can be intaken by any intake pass.
 * This means that if the initial intake pass at SDK start is aborted, the only intake pass that
 * will look for incomplete, dead session parts to be resurrected, a resurrected part can be picked
 * up by any subsquent pass because it is just a regular, completed session part to be delivered.
 */
class SessionPartReader(
    private val sessionsDir: Lazy<File>,
    private val directoryStore: SessionPartDirectoryStore,
    private val reconstructionService: SessionReconstructionService,
    private val intakeService: IntakeService,
    private val writeTracker: SessionPartWriteTracker,
    private val deadPartResurrector: MultiFileDeadPartResurrector,
    private val processIdProvider: () -> String,
    private val configService: ConfigService,
    private val logger: InternalLogger,
) {
    /**
     * The session parts whose intake attempt has failed at least once.
     */
    private val failed: MutableSet<SessionPartDirectory> = Collections.newSetFromMap(ConcurrentHashMap())

    /**
     * The session parts whose intake started but didn't finish, either because of timeout or thread interruption.
     * These should not be counted as failures. A part whose intake times out is waited on once more,
     * and an interrupted wait doesn't count towards that.
     */
    private val incomplete: MutableMap<SessionPartDirectory, PendingIntake> = ConcurrentHashMap()

    /**
     * Reads every completed session part on disk, returning only once each one has been handed to
     * the [IntakeService] and stored. Never throws: callers run this inline on their own critical
     * path and have to keep going regardless of what the read layer does.
     *
     * This performs disk I/O and waits on intake, so the caller is responsible for already being on
     * a background thread, and for not calling it from a thread that intake itself runs on.
     *
     * [performingResurrection] is set by the pass at launch, before this process has persisted any
     * part of its own. Only then is a part that does not record its process treated as dead.
     */
    fun readPersistedSessionParts(performingResurrection: Boolean = false) {
        if (!configService.config.persistence.multiFileEnabled) {
            deletePersistedSessionParts()
            return
        }
        EmbTrace.trace("mf-read-session-parts") {
            runCatching {
                val stored = directoryStore.storedDirectories()

                // Forget failed and incomplete parts that have since left the disk some other way, so these only hold
                // parts that can still be retried
                val storedSet = stored.toSet()
                failed.retainAll(storedSet)
                incomplete.keys.retainAll(storedSet)

                val directories = stored
                    .filterNot(writeTracker::isWriting)
                    .sortedWith(SessionPartDirectory.comparator)

                // the last part of each user session on disk, which is the one a terminated user
                // session's final-part attributes go on
                val lastPartsOfUserSessions = stored
                    .groupBy(SessionPartDirectory::userSessionId)
                    .values
                    .mapNotNullTo(HashSet()) { parts -> parts.maxWithOrNull(SessionPartDirectory.comparator) }

                for (directory in directories) {
                    val proceed = runCatching {
                        deliver(
                            directory = directory,
                            isLastPartOfUserSession = directory in lastPartsOfUserSessions,
                            performingResurrection = performingResurrection,
                        )
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

    /**
     * The session part ids of every part on disk, which includes the dead parts no pass has
     * delivered yet.
     */
    fun storedSessionPartIds(): Set<String> =
        runCatching {
            directoryStore.storedDirectories().mapTo(HashSet(), SessionPartDirectory::sessionPartId)
        }.getOrDefault(emptySet())

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
     * - Storage not attempted or intake thread interrupted: data kept
     * - Intake cancelled: data kept, intake pass stopped
     * - First recoverable failure or timeout: data kept, intake pass stopped, retried on next intake
     * - Unrecoverable failure or second failure/timeout: data deleted
     *
     * A dead part that cannot be resurrected is deleted, as is one that cannot be reconstructed.
     */
    private fun deliver(
        directory: SessionPartDirectory,
        isLastPartOfUserSession: Boolean,
        performingResurrection: Boolean,
    ): Boolean {
        val intake = incomplete.remove(directory) ?: run {
            val envelope = reconstructionService.reconstruct(directory)
            if (envelope == null) {
                // No need to log as the failed construction is recorded elsewhere
                directoryStore.delete(directory)
                return true
            }
            val deliverable = deliverable(envelope, directory, isLastPartOfUserSession, performingResurrection)
                ?: return true
            val task = intakeService.take(
                intake = deliverable.envelope,
                metadata = directory.createMetadata(deliverable.processIdentifier),
                onStored = { directoryStore.delete(directory) },
            )
            PendingIntake(task, deliverable.afterStored)
        }

        val result = try {
            intake.task.get(INTAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (exc: TimeoutException) {
            logger.trackInternalError(InternalErrorType.IntakeFail, exc)
            return if (intake.timedOut) {
                // The intake has already timed out once, so stop blocking newer parts on it. Cancel it
                // first, so that one still queued can't store the part after newer parts. One already
                // running isn't interrupted, which could cut a write off halfway: it finishes, and the
                // part it stores is only delivered late.
                intake.task.cancel(false)
                deleteSessionPartData(directory, "intake timed out on retry")
                true
            } else {
                // Update the pending intake to note that it has timed out, and so should be
                // deleted if it times out the next time.
                incomplete[directory] = PendingIntake(
                    task = intake.task,
                    afterStored = intake.afterStored,
                    timedOut = true,
                )
                false
            }
        } catch (_: InterruptedException) {
            // An interrupt says nothing about the intake, so keep it as it was without counting it
            Thread.currentThread().interrupt()
            incomplete[directory] = intake
            return false
        } catch (_: CancellationException) {
            // A cancelled intake should stop the intake pass
            return false
        }

        runAfterStored(intake, result)

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

    /**
     * If [intake] stored its part, does what waited on that, such as sending the part's native crash.
     */
    private fun runAfterStored(intake: PendingIntake, result: IntakeResult) {
        if (result == IntakeResult.STORED) {
            runCatching(intake.afterStored).onFailure {
                logger.trackInternalError(InternalErrorType.PayloadResurrectionPayloadFail, it)
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

    /**
     * What to hand over for the part [envelope] was reconstructed from: the envelope itself for a part
     * this process persisted, or the resurrected envelope for a dead part. Returns null if a dead part
     * could not be resurrected, in which case it has been deleted.
     */
    private fun deliverable(
        envelope: Envelope<SessionPartPayload>,
        directory: SessionPartDirectory,
        isLastPartOfUserSession: Boolean,
        performingResurrection: Boolean,
    ): Deliverable? {
        val recordedProcessIdentifier = envelope.findProcessIdentifier()
        val processIdentifier = recordedProcessIdentifier ?: processIdProvider()
        val isDead = if (recordedProcessIdentifier == null) {
            performingResurrection
        } else {
            recordedProcessIdentifier != processIdProvider()
        }
        return if (isDead) {
            resurrect(envelope, directory, processIdentifier, isLastPartOfUserSession)?.let {
                Deliverable(it.envelope, processIdentifier, it.afterStored)
            }
        } else {
            Deliverable(envelope, processIdentifier)
        }
    }

    /**
     * Resurrects [deadPart], or deletes it and returns null if it cannot be resurrected.
     */
    private fun resurrect(
        deadPart: Envelope<SessionPartPayload>,
        directory: SessionPartDirectory,
        processIdentifier: String,
        isLastPartOfUserSession: Boolean,
    ): MultiFileDeadPartResurrector.ResurrectedPart? {
        val resurrected = runCatching {
            deadPartResurrector.resurrect(
                deadPart = deadPart,
                directory = directory,
                processIdentifier = processIdentifier,
                isLastPartOfUserSession = isLastPartOfUserSession,
                lastUpdatedMs = reconstructionService.lastUpdatedMs(directory),
            )
        }.onFailure {
            logger.trackInternalError(InternalErrorType.PayloadResurrectionPayloadFail, it)
        }.getOrNull()
        if (resurrected == null) {
            deleteSessionPartData(directory, "dead session part could not be resurrected")
        }
        return resurrected
    }

    private fun SessionPartDirectory.createMetadata(processIdentifier: String): StoredTelemetryMetadata = StoredTelemetryMetadata(
        timestamp = timestamp,
        uuid = uuid,
        processIdentifier = processIdentifier,
        envelopeType = SupportedEnvelopeType.SESSION,
        complete = true,
        payloadType = PayloadType.SESSION,
        userSessionId = userSessionId,
        sessionPartId = sessionPartId,
    )

    private fun Envelope<SessionPartPayload>.findProcessIdentifier(): String? =
        getSessionPartSpan()?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER)

    /**
     * An intake of a session part, the work for the reader to do once it has stored the part, and
     * whether the reader has already timed out waiting on it.
     */
    private class PendingIntake(
        val task: Future<IntakeResult>,
        val afterStored: () -> Unit,
        val timedOut: Boolean = false,
    )

    /**
     * The envelope to hand over for a session part, the process that persisted the part, and the work
     * for the reader to do once the part has been stored.
     */
    private class Deliverable(
        val envelope: Envelope<SessionPartPayload>,
        val processIdentifier: String,
        val afterStored: () -> Unit = {},
    )

    private companion object {
        private const val INTAKE_TIMEOUT_MS = 5_000L
    }
}
