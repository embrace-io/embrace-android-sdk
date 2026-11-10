package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FilesDirApplication
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import io.embrace.android.embracesdk.internal.store.file.FilePersistedSdkStateStore
import io.embrace.android.embracesdk.internal.worker.Worker
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

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

    @Test
    fun `the superior storage system keeps ordinals in install state`() {
        val context = FilesDirApplication(tempDir.newFolder())
        val configLoadServices = ConfigLoadServices(
            context = context,
            preStartServices = PreStartServices(errorHandler = FakeErrorHandler()),
            workerThreadPoolSupplier = { FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker)) },
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            superiorStorageSystemEnabled = true,
            fileSystem = FileSystem.SYSTEM,
        )
        val store = configLoadServices.ordinalStore.value
        assertEquals(1, store.incrementAndGet(Ordinal.SESSION))
        assertEquals(2, store.incrementAndGet(Ordinal.SESSION))

        val installState = File(context.noBackupFilesDir, "${SdkStorageLayout.ROOT_DIR_NAME}/install_state")
        assertTrue(File(installState, SdkStorageLayout.ORDINALS_FILE_NAME).exists())
    }

    @Test
    fun `the superior storage system skips the prefs prewarm`() {
        val workerThreadPool = FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker))
        val configLoadServices = ConfigLoadServices(
            context = FilesDirApplication(tempDir.newFolder()),
            preStartServices = PreStartServices(errorHandler = FakeErrorHandler()),
            workerThreadPoolSupplier = { workerThreadPool },
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            superiorStorageSystemEnabled = true,
            fileSystem = fsRule.fileSystem,
        )
        configLoadServices.start()
        assertEquals(0, workerThreadPool.executorFor(Worker.Background.IoRegWorker).submitCount)
    }

    @Test
    fun `the superior storage system keeps delivered AEI and network capture counts in install state`() {
        val context = FilesDirApplication(tempDir.newFolder())
        val rule = NetworkCaptureRuleRemoteConfig(id = "rule", duration = null, method = "GET", urlRegex = "embrace.io")
        val configService = FakeConfigService(networkBehavior = FakeNetworkBehavior(rules = setOf(rule)))
        val workers = listOf(Worker.Background.NonIoRegWorker, Worker.Background.PersistenceWorker)
        val workerThreadPool = FakeWorkerThreadPool(testWorkers = workers)
        val first = createSuperiorStorageServices(context, configService, workerThreadPool)
        first.deliveredAeiStore.value.add("aei")
        first.networkCaptureRuleStore.value.decrement(rule.id, 5)
        workers.forEach { workerThreadPool.executorFor(it).runCurrentlyBlocked() }

        val second = createSuperiorStorageServices(context, configService, FakeWorkerThreadPool(testWorkers = workers))
        assertEquals(setOf("aei"), second.deliveredAeiStore.value.ids())
        assertEquals(4, second.networkCaptureRuleStore.value.remaining(rule.id, 5))
    }

    private fun createSuperiorStorageServices(
        context: FilesDirApplication,
        configService: FakeConfigService,
        workerThreadPool: FakeWorkerThreadPool,
    ) = ConfigLoadServices(
        context = context,
        preStartServices = PreStartServices(errorHandler = FakeErrorHandler()),
        workerThreadPoolSupplier = { workerThreadPool },
        configServiceSupplier = { _, _, _, _, _, _ -> configService },
        superiorStorageSystemEnabled = true,
        fileSystem = fsRule.fileSystem,
    )
}
