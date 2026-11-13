package io.embrace.android.embracesdk.internal.storage.migration

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.storage.LegacyStorageMigrator
import io.embrace.android.embracesdk.internal.storage.OrphanSweeper
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.storage.createSdkStorage
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class LegacyTelemetryMigrationTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val data = "/data".toPath()
    private val legacyPayload = data / "files" / "embrace_payloads" / "payload"
    private val legacyInstallState = data / "files" / "embrace"
    private val legacyCrashMarker = legacyInstallState / "embrace_crash_marker"
    private val migratedInstallState = data / "no_backup" / SdkStorageLayout.ROOT_DIR_NAME / "install_state"
    private val migratedPayload = data / "no_backup" / SdkStorageLayout.ROOT_DIR_NAME / "telemetry" / "ready" / "payload"
    private val orphan = data / "no_backup" / SdkStorageLayout.ROOT_DIR_NAME / "orphan"
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private val worker = MigrationWorker(errorHandler)
    private val schedulerExecutor = BlockingScheduledExecutorService(FakeClock())
    private val scheduler = BackgroundWorker(schedulerExecutor)

    @Test
    fun `await returns at once without a migrator`() {
        LegacyTelemetryMigration(worker, null, errorHandler).await()
    }

    @Test
    fun `move moves the legacy telemetry before it returns`() {
        val fs = fsRule.fileSystem
        fs.seedLegacyTelemetry()
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler)

        migration.move()

        assertTrue(migration.isComplete)
        assertTrue(fs.exists(migratedPayload))
        assertFalse(fs.exists(legacyPayload))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `move leaves the rest of the install state for start to delete`() {
        val fs = fsRule.fileSystem
        fs.writeText(legacyInstallState / "embrace_crash_marker")
        fs.writeText(legacyInstallState / "other")
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler)

        migration.move()

        assertTrue(fs.exists(migratedInstallState / SdkStorageLayout.CRASH_MARKER_FILE_NAME))
        assertTrue(fs.exists(legacyInstallState / "other"))

        migration.start()
        drainWorker()

        assertFalse(fs.exists(legacyInstallState))
    }

    @Test
    fun `a move past its timeout returns and completes once it finishes`() {
        val fs = fsRule.fileSystem
        fs.seedLegacyTelemetry()
        val release = blockWorker()
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler)

        migration.move(timeoutMs = 10)

        assertFalse(migration.isComplete)
        assertTrue(fs.exists(legacyPayload))
        assertEquals(listOf("StorageMigrationFail"), errorHandler.sdkErrorMessages.map { it.msg })
        release.countDown()
        migration.await()

        assertTrue(migration.isComplete)
        assertTrue(fs.exists(migratedPayload))
    }

    @Test
    fun `start without a move deletes nothing`() {
        val fs = fsRule.fileSystem
        fs.writeText(legacyInstallState / "other")
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler)

        migration.start()
        drainWorker()

        assertTrue(fs.exists(legacyInstallState / "other"))
    }

    @Test
    fun `move without the 9x install state completes at once and moves nothing`() {
        val fs = fsRule.fileSystem
        fs.writeText(legacyPayload)
        val release = blockWorker()
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler)

        migration.move()
        assertTrue(migration.isComplete)
        release.countDown()
        drainWorker()

        assertTrue(fs.exists(legacyPayload))
        assertFalse(fs.exists(migratedPayload))
    }

    @Test
    fun `cancel releases await and stops a migration that hasn't begun`() {
        val fs = fsRule.fileSystem
        fs.seedLegacyTelemetry()
        val release = blockWorker()
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler)

        migration.move(timeoutMs = 0)
        migration.cancel()
        migration.await()
        release.countDown()
        drainWorker()

        assertTrue(fs.exists(legacyPayload))
        assertFalse(fs.exists(migratedPayload))
    }

    @Test
    fun `deleteLegacyTelemetry deletes without moving`() {
        val fs = fsRule.fileSystem
        fs.seedLegacyTelemetry()
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler)

        migration.deleteLegacyTelemetry {}
        migration.await()
        drainWorker()

        assertFalse(fs.exists(legacyPayload))
        assertFalse(fs.exists(migratedPayload))
    }

    @Test
    fun `deleteLegacyTelemetry returns without waiting for queued work`() {
        val fs = fsRule.fileSystem
        fs.seedLegacyTelemetry()
        val release = blockWorker()
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler)
        val afterDeletion = CountDownLatch(1)

        migration.deleteLegacyTelemetry(afterDeletion::countDown)
        assertTrue(fs.exists(legacyPayload))
        assertEquals(1L, afterDeletion.count)
        release.countDown()

        assertTrue(afterDeletion.await(TIMEOUT_SECS, TimeUnit.SECONDS))
        assertFalse(fs.exists(legacyPayload))
    }

    @Test
    fun `afterDeletion runs once a migration already moving files has finished`() {
        val moving = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun atomicMove(source: Path, target: Path) {
                super.atomicMove(source, target)
                moving.countDown()
                release.await(TIMEOUT_SECS, TimeUnit.SECONDS)
            }
        }
        fs.seedLegacyTelemetry()
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler)
        val movedBeforeAfterDeletion = AtomicBoolean(false)
        val afterDeletion = CountDownLatch(1)

        migration.move(timeoutMs = 0)
        assertTrue(moving.await(TIMEOUT_SECS, TimeUnit.SECONDS))
        migration.deleteLegacyTelemetry {
            movedBeforeAfterDeletion.set(fs.exists(migratedPayload))
            fs.delete(migratedPayload)
            afterDeletion.countDown()
        }
        assertEquals(1L, afterDeletion.count)
        release.countDown()

        assertTrue(afterDeletion.await(TIMEOUT_SECS, TimeUnit.SECONDS))
        assertTrue(movedBeforeAfterDeletion.get())
        assertFalse(fs.exists(legacyPayload))
        assertFalse(fs.exists(migratedPayload))
    }

    @Test
    fun `afterDeletion runs when the legacy dirs can't be resolved`() {
        val migrator = LegacyStorageMigrator(
            fsRule.fileSystem,
            { error("files dir") },
            { error("cache dir") },
            { data / "no_backup" },
            errorHandler,
        )
        val migration = LegacyTelemetryMigration(worker, migrator, errorHandler)
        val afterDeletion = CountDownLatch(1)

        migration.deleteLegacyTelemetry(afterDeletion::countDown)

        assertTrue(afterDeletion.await(TIMEOUT_SECS, TimeUnit.SECONDS))
    }

    @Test
    fun `move after cancel or skipMove doesn't migrate`() {
        val fs = fsRule.fileSystem
        fs.seedLegacyTelemetry()
        listOf(LegacyTelemetryMigration::cancel, LegacyTelemetryMigration::skipMove).forEach { stop ->
            val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler)

            stop(migration)
            migration.move()
            drainWorker()

            assertTrue(migration.isComplete)
            assertTrue(fs.exists(legacyPayload))
            assertFalse(fs.exists(migratedPayload))
        }
    }

    @Test
    fun `start doesn't sweep orphans`() {
        val fs = fsRule.fileSystem
        fs.writeText(orphan)

        LegacyTelemetryMigration(worker, migrator(fs), errorHandler, sweeper(fs)).apply {
            move()
            start()
        }
        drainWorker()

        assertTrue(fs.exists(orphan))
    }

    @Test
    fun `sweepOrphans sweeps once the migration has finished`() {
        val fs = fsRule.fileSystem
        fs.seedLegacyTelemetry()
        fs.writeText(orphan)
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler, sweeper(fs))

        migration.move()
        migration.sweepOrphans(scheduler)
        runSweep()

        assertTrue(fs.exists(migratedPayload))
        assertFalse(fs.exists(orphan))
    }

    @Test
    fun `sweepOrphans deletes orphans and 9x leftovers after the delay`() {
        val fs = fsRule.fileSystem
        fs.writeText(orphan)
        fs.seedLegacyTelemetry()
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler, sweeper(fs))

        migration.sweepOrphans(scheduler)
        schedulerExecutor.moveForwardAndRunBlocked(LegacyTelemetryMigration.SWEEP_DELAY_MS - 1)
        drainWorker()
        assertTrue(fs.exists(orphan))
        assertTrue(fs.exists(legacyPayload))

        schedulerExecutor.moveForwardAndRunBlocked(1)
        drainWorker()
        assertFalse(fs.exists(orphan))
        assertFalse(fs.exists(legacyPayload))
        assertFalse(fs.exists(legacyInstallState))
        assertFalse(fs.exists(migratedPayload))
    }

    @Test
    fun `sweepOrphans sweeps without a migrator`() {
        val fs = fsRule.fileSystem
        fs.writeText(orphan)

        LegacyTelemetryMigration(worker, null, errorHandler, sweeper(fs)).sweepOrphans(scheduler)
        runSweep()

        assertFalse(fs.exists(orphan))
    }

    @Test
    fun `await doesn't wait for the sweep`() {
        val sweeping = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean) {
                if (fileOrDirectory == orphan) {
                    sweeping.countDown()
                    release.await(TIMEOUT_SECS, TimeUnit.SECONDS)
                }
                super.deleteRecursively(fileOrDirectory, mustExist)
            }
        }
        fs.writeText(orphan)
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler, sweeper(fs))

        migration.move()
        migration.sweepOrphans(scheduler)
        schedulerExecutor.moveForwardAndRunBlocked(LegacyTelemetryMigration.SWEEP_DELAY_MS)
        assertTrue(sweeping.await(TIMEOUT_SECS, TimeUnit.SECONDS))
        migration.await()
        release.countDown()
        drainWorker()

        assertFalse(fs.exists(orphan))
    }

    @Test
    fun `sweepOrphans after cancel doesn't sweep`() {
        val fs = fsRule.fileSystem
        fs.writeText(orphan)
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler, sweeper(fs))

        migration.cancel()
        migration.sweepOrphans(scheduler)
        runSweep()

        assertTrue(fs.exists(orphan))
    }

    @Test
    fun `cancel during the delay stops the sweep`() {
        val fs = fsRule.fileSystem
        fs.writeText(orphan)
        val migration = LegacyTelemetryMigration(worker, migrator(fs), errorHandler, sweeper(fs))

        migration.sweepOrphans(scheduler)
        migration.cancel()
        runSweep()

        assertTrue(fs.exists(orphan))
    }

    @Test
    fun `isComplete is false until the migration finishes`() {
        fsRule.fileSystem.seedLegacyTelemetry()
        val release = blockWorker()
        val migration = LegacyTelemetryMigration(worker, migrator(fsRule.fileSystem), errorHandler)

        migration.move(timeoutMs = 0)
        assertFalse(migration.isComplete)
        release.countDown()
        migration.await()

        assertTrue(migration.isComplete)
        assertTrue(LegacyTelemetryMigration(worker, null, errorHandler).isComplete)
    }

    private fun sweeper(fs: FileSystem) = OrphanSweeper(
        storage = createSdkStorage(fs, data / "no_backup", errorHandler),
        layoutVersion = SdkStorageLayout.LAYOUT_VERSION,
        processStartMs = 0,
        wallClock = { 0L },
        errorHandler = errorHandler,
    )

    private fun migrator(fs: FileSystem) =
        LegacyStorageMigrator(fs, { data / "files" }, { data / "cache" }, { data / "no_backup" }, errorHandler)

    private fun blockWorker(): CountDownLatch = CountDownLatch(1).also { release ->
        worker.submit("block", Unit, SdkErrorType.Ignored) {
            release.await(TIMEOUT_SECS, TimeUnit.SECONDS)
        }
    }

    private fun runSweep() {
        schedulerExecutor.moveForwardAndRunBlocked(LegacyTelemetryMigration.SWEEP_DELAY_MS)
        drainWorker()
    }

    private fun drainWorker() {
        worker.submit("drain", Unit, SdkErrorType.Ignored) {}.await()
    }

    private fun FileSystem.seedLegacyTelemetry() {
        writeText(legacyPayload)
        writeText(legacyCrashMarker)
    }

    private fun FileSystem.writeText(path: Path) {
        createDirectories(checkNotNull(path.parent))
        write(path) { writeUtf8("x") }
    }

    private companion object {
        const val TIMEOUT_SECS = 5L
    }
}
