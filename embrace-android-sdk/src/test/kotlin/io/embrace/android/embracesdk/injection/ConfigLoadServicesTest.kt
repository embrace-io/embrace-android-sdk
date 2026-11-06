package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FilesDirApplication
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import io.embrace.android.embracesdk.internal.store.file.FilePersistedSdkStateStore
import io.embrace.android.embracesdk.internal.worker.Worker
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class ConfigLoadServicesTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @get:Rule
    val fsRule = FakeFileSystemRule()

    @Test
    fun `background work only begins once started`() {
        val configService = FakeConfigService()
        val workerThreadPool = FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker))
        val configLoadServices = ConfigLoadServices(
            context = FilesDirApplication(tempDir.newFolder()),
            preStartServices = PreStartServices(errorHandler = FakeErrorHandler(throwOnSdkError = false)),
            workerThreadPoolSupplier = { workerThreadPool },
            configServiceSupplier = { _, _, _, _, _, _ -> configService },
        )
        val ioWorker = workerThreadPool.executorFor(Worker.Background.IoRegWorker)
        assertEquals(0, ioWorker.submitCount)
        assertEquals(0, configService.scheduleConfigRequestsCount)

        configLoadServices.start()
        assertEquals(1, ioWorker.submitCount)
        assertEquals(1, configService.scheduleConfigRequestsCount)
    }

    @Test
    fun `the superior storage system reads the persisted SDK state from install state`() {
        val fs = fsRule.fileSystem
        val context = FilesDirApplication(tempDir.newFolder())
        val stateFile = context.noBackupFilesDir.toOkioPath() / SdkStorageLayout.ROOT_DIR_NAME / "install_state" /
            SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME
        fs.createDirectories(checkNotNull(stateFile.parent))
        FilePersistedSdkStateStore.create(fs, stateFile, PersistedSdkState("device-id", 1L))

        val configLoadServices = ConfigLoadServices(
            context = context,
            preStartServices = PreStartServices(errorHandler = FakeErrorHandler(throwOnSdkError = false)),
            workerThreadPoolSupplier = { FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker)) },
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            superiorStorageSystemEnabled = true,
            fileSystem = fs,
        )
        assertEquals("device-id", configLoadServices.persistedSdkStateStore.value.snapshot().deviceId)
    }
}
