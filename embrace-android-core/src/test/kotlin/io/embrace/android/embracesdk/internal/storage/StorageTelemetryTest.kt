package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import io.embrace.android.embracesdk.semconv.EmbTelemetryAttributes
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

internal class StorageTelemetryTest {

    @get:Rule
    val fsRule = FakeFileSystemRule()

    private val dataDir = "/data".toPath()
    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private val telemetryService = FakeInternalTelemetryService()

    @Test
    fun `counts nested telemetry and ignores files outside the sdk dirs`() {
        val storage = createSdkStorage(fsRule.fileSystem, dataDir / "no_backup", errorHandler)
        write(storage.dir(SdkDir.REMOTE_CONFIG) / "config", 3)
        write(storage.dir(SdkDir.INSTALL_STATE) / "crash_marker", 5)
        write(storage.dir(SdkDir.TELEMETRY_READY) / "payload", 7)
        write(storage.dir(SdkDir.TELEMETRY_PENDING_SESSION_PARTS) / "part" / "spans" / "span", 11)
        write(storage.dir(SdkDir.TELEMETRY_LOG_ENVELOPES) / "envelope", 13)
        write(storage.root / "layout_version", 100)
        write(dataDir / "no_backup" / "app_file", 1000)
        write(dataDir / "cache" / "app_cache", 10000)

        logTelemetry(storage)

        assertEquals(39L, storageUsed())
        assertEquals(1000L, storageAvailable())
    }

    @Test
    fun `counts each legacy dir once and ignores the rest of the files dir`() {
        val filesDir = dataDir / "files"
        val storage = LegacySdkStorage(fsRule.fileSystem, filesDir, { dataDir / "cache" }, errorHandler)
        write(storage.dir(SdkDir.REMOTE_CONFIG) / "config", 3)
        write(storage.dir(SdkDir.INSTALL_STATE) / "embrace_crash_marker", 5)
        write(storage.dir(SdkDir.TELEMETRY_PENDING_SNAPSHOTS) / "snapshot", 7)
        write(storage.dir(SdkDir.TELEMETRY_PENDING_SESSION_PARTS) / "part" / "span", 11)
        write(filesDir / "app_file", 1000)
        write(dataDir / "cache" / "app_cache", 10000)

        logTelemetry(storage)

        assertEquals(26L, storageUsed())
    }

    @Test
    fun `reports zero when no dir exists`() {
        val storage = SdkStorageImpl(fsRule.fileSystem, dataDir / "missing", errorHandler)
        logTelemetry(storage)
        assertEquals(0L, storageUsed())
    }

    private fun logTelemetry(storage: SdkStorage) {
        StorageTelemetry(storage, telemetryService, FakeStorageAvailabilityChecker()).log()
    }

    private fun storageUsed(): Long? =
        telemetryService.storageTelemetryMap[EmbTelemetryAttributes.EMB_STORAGE_USED]?.toLong()

    private fun storageAvailable(): Long? =
        telemetryService.storageTelemetryMap[EmbTelemetryAttributes.EMB_STORAGE_AVAILABLE]?.toLong()

    private fun write(path: Path, bytes: Int) {
        val fs = fsRule.fileSystem
        path.parent?.let(fs::createDirectories)
        fs.write(path) { write(ByteArray(bytes)) }
    }
}
