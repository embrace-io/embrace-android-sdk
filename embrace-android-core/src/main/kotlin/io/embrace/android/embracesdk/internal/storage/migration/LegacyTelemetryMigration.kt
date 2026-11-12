package io.embrace.android.embracesdk.internal.storage.migration

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.storage.LegacyStorageMigrator
import io.embrace.android.embracesdk.internal.storage.OrphanSweeper
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Moves the 9.x telemetry into the layout on [worker] once [start] is called, and runs [orphanSweeper]
 * [sweepDelayMs] after [sweepOrphans] is called, so it doesn't compete with the app's startup. Anything that reads
 * the previous launch's telemetry calls [await] first. Without a [migrator] there is nothing to move and [await]
 * returns at once.
 */
class LegacyTelemetryMigration(
    private val worker: MigrationWorker,
    private val migrator: LegacyStorageMigrator?,
    errorHandler: ErrorHandler,
    private val orphanSweeper: OrphanSweeper? = null,
    private val sweepDelayMs: Long = SWEEP_DELAY_MS,
) {
    private val handle = MigrationHandle(NAME, Unit, errorHandler)
    private val cancelled = AtomicBoolean(false)

    init {
        if (migrator == null) {
            handle.complete()
        }
    }

    /**
     * Queues the migration. Does nothing once [cancel] has been called.
     */
    fun start() {
        val migrator = migrator ?: return
        worker.submit(handle, SdkErrorType.StorageMigrationFail) {
            if (!cancelled.get()) {
                migrator.migrateTelemetry()
            }
        }
    }

    fun sweepOrphans(scheduler: BackgroundWorker) {
        val sweeper = orphanSweeper ?: return
        scheduler.schedule<Unit>(
            {
                worker.submit(SWEEP_NAME, Unit, SdkErrorType.OrphanSweepFail) {
                    if (!cancelled.get()) {
                        sweeper.sweep()
                    }
                }
            },
            sweepDelayMs,
            TimeUnit.MILLISECONDS,
        )
    }

    /**
     * Blocks until the migration finishes or is cancelled.
     */
    fun await() {
        if (migrator != null) {
            handle.await()
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
        private const val NAME = "telemetry"
        private const val DELETE_NAME = "delete-legacy-telemetry"
        private const val SWEEP_NAME = "orphan-sweep"
    }
}
