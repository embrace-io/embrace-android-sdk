package io.embrace.android.embracesdk.internal.storage.migration

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.storage.LegacyStorageMigrator
import io.embrace.android.embracesdk.internal.storage.OrphanSweeper
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Moves the 9.x telemetry into the layout on [worker] once [move] is called, and deletes the rest of the 9.x install
 * state behind that move once [start] is called. [sweepDelayMs] after [sweepOrphans] is called, it deletes any 9.x
 * state still left and runs [orphanSweeper], so neither competes with the app's startup. Anything that reads the
 * previous launch's telemetry calls [await] first. Without a [migrator], or after [skipMove], there is nothing to move
 * and [await] returns at once.
 */
class LegacyTelemetryMigration(
    private val worker: MigrationWorker,
    private val migrator: LegacyStorageMigrator?,
    private val errorHandler: ErrorHandler,
    private val orphanSweeper: OrphanSweeper? = null,
    private val sweepDelayMs: Long = SWEEP_DELAY_MS,
    private val awaitTimeoutMs: Long = AWAIT_TIMEOUT_MS,
) {
    private val handle = MigrationHandle(NAME, Unit, errorHandler)
    private val cancelled = AtomicBoolean(false)
    private val moveSubmitted = AtomicBoolean(false)
    private val awaitTimedOut = AtomicBoolean(false)

    init {
        if (migrator == null) {
            handle.complete()
        }
    }

    /**
     * Moves the telemetry on [worker] and blocks until it finishes or [budget] runs out, or completes at once if there is
     * no 9.x telemetry to move.
     */
    fun move(budget: MigrationBudget) {
        val migrator = migrator ?: return
        if (cancelled.get() || handle.isComplete) {
            return
        }
        if (!migrator.hasLegacyTelemetry()) {
            handle.complete()
            return
        }
        if (!moveSubmitted.compareAndSet(false, true)) {
            return
        }
        worker.submit(handle, SdkErrorType.StorageMigrationFail) {
            if (!cancelled.get()) {
                migrator.moveTelemetry()
            }
        }
        budget.await(handle, SdkErrorType.StorageMigrationFail)
    }

    /**
     * Completes the migration without checking for 9.x telemetry
     */
    fun skipMove() = handle.complete()

    /**
     * Queues the deletion of the rest of the 9.x install state behind the [move].
     */
    fun start() {
        val migrator = migrator ?: return
        if (!moveSubmitted.get()) {
            return
        }
        worker.submit(DELETE_INSTALL_STATE_NAME, Unit, SdkErrorType.StorageMigrationFail) {
            if (!cancelled.get()) {
                migrator.deleteLegacyInstallState()
            }
        }
    }

    /**
     * Also deletes any 9.x state a previous launch left behind, e.g. if it ended before its [move].
     */
    fun sweepOrphans(scheduler: BackgroundWorker) {
        val sweeper = orphanSweeper ?: return
        scheduler.schedule<Unit>(
            {
                worker.submit(SWEEP_NAME, Unit, SdkErrorType.OrphanSweepFail) {
                    if (!cancelled.get()) {
                        migrator?.deleteLegacyInstallState()
                        sweeper.sweep()
                    }
                }
            },
            sweepDelayMs,
            TimeUnit.MILLISECONDS,
        )
    }

    /**
     * Blocks until the migration finishes or is cancelled, or for at most [awaitTimeoutMs]. After a timeout the caller
     * proceeds with a stale view of the previous launch's telemetry, and later calls return at once.
     */
    fun await() {
        if (migrator != null && !handle.isComplete && !awaitTimedOut.get()) {
            handle.await(awaitTimeoutMs) {
                awaitTimedOut.set(true)
                errorHandler.trackSdkError(
                    SdkErrorType.StorageMigrationFail,
                    TimeoutException("migration '$NAME' not done after $awaitTimeoutMs ms"),
                )
            }
        }
    }

    /**
     * True once the migration has finished or been cancelled, or when there is nothing to migrate.
     */
    val isComplete: Boolean
        get() = handle.isComplete

    /**
     * Stops a migration that hasn't begun and releases anything waiting in [await]. A migration already running
     * finishes.
     */
    fun cancel() {
        cancelled.set(true)
        handle.complete()
    }

    /**
     * Cancels the migration, then queues the deletion of the 9.x telemetry behind any migration still running, and
     * returns without waiting for it. [afterDeletion] then runs on the worker, even if the deletion fails. Without a
     * migrator nothing was moved, so neither runs.
     */
    fun deleteLegacyTelemetry(afterDeletion: () -> Unit) {
        cancel()
        val migrator = migrator ?: return
        worker.submit(DELETE_NAME, Unit, SdkErrorType.Ignored) {
            try {
                migrator.deleteLegacyTelemetry()
            } finally {
                afterDeletion()
            }
        }
    }

    companion object {
        const val SWEEP_DELAY_MS: Long = 10_000L
        private const val NAME = "telemetry-move"
        private const val AWAIT_TIMEOUT_MS = 10_000L
        private const val DELETE_INSTALL_STATE_NAME = "delete-legacy-install-state"
        private const val DELETE_NAME = "delete-legacy-telemetry"
        private const val SWEEP_NAME = "orphan-sweep"
    }
}
