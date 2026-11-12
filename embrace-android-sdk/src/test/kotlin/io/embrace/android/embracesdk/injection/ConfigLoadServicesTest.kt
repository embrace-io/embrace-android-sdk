package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FilesDirApplication
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import io.embrace.android.embracesdk.internal.store.file.FilePersistedSdkStateStore
import io.embrace.android.embracesdk.internal.worker.Worker
import okio.FileMetadata
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class ConfigLoadServicesTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @get:Rule
    val fsRule = FakeFileSystemRule()

    @Test
    fun `config requests only begin once started`() {
        val configService = FakeConfigService()
        val configLoadServices =
            createServices(FilesDirApplication(tempDir.newFolder()), configService)
        assertEquals(0, configService.scheduleConfigRequestsCount)

        configLoadServices.start()
        assertEquals(1, configService.scheduleConfigRequestsCount)
    }

    @Test
    fun `reads the persisted SDK state from install state off the init thread`() {
        val context = FilesDirApplication(tempDir.newFolder())
        val stateFile = context.noBackupFilesDir.toOkioPath() / SdkStorageLayout.ROOT_DIR_NAME / "install_state" /
            SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME
        fsRule.fileSystem.createDirectories(checkNotNull(stateFile.parent))
        FilePersistedSdkStateStore.create(fsRule.fileSystem, stateFile, PersistedSdkState("device-id", 1L))
        val loaded = CountDownLatch(1)
        val loadThread = AtomicReference<String>()
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun source(file: Path): Source {
                if (file == stateFile) {
                    loadThread.set(Thread.currentThread().name)
                    loaded.countDown()
                }
                return super.source(file)
            }
        }

        val configLoadServices = ConfigLoadServices(
            context = context,
            preStartServices = PreStartServices(errorHandler = FakeErrorHandler(throwOnSdkError = false)),
            workerThreadPoolSupplier = { FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker)) },
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            fileSystem = fs,
        )

        assertTrue(loaded.await(5, TimeUnit.SECONDS))
        assertEquals("emb-migration", loadThread.get())
        assertEquals("device-id", configLoadServices.persistedSdkStateStore.value.snapshot().deviceId)
    }

    @Test
    fun `moves the 9x config into place during construction`() {
        val fs = fsRule.fileSystem
        val context = FilesDirApplication(tempDir.newFolder())
        val legacy = context.filesDir.toOkioPath() / "embrace_remote_config" / PersistedConfig.CONFIG_RESPONSE_FILE_NAME
        fs.createDirectories(checkNotNull(legacy.parent))
        fs.write(legacy) { writeUtf8("{}") }

        createServices(context, FakeConfigService())

        val migrated = context.noBackupFilesDir.toOkioPath() / SdkStorageLayout.ROOT_DIR_NAME / "remote_config" /
            PersistedConfig.CONFIG_RESPONSE_FILE_NAME
        assertTrue(fs.exists(migrated))
        assertFalse(fs.exists(legacy))
    }

    @Test
    fun `keeps ordinals in install state`() {
        val context = FilesDirApplication(tempDir.newFolder())
        val configLoadServices = ConfigLoadServices(
            context = context,
            preStartServices = PreStartServices(errorHandler = FakeErrorHandler()),
            workerThreadPoolSupplier = { FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker)) },
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            fileSystem = FileSystem.SYSTEM,
        )
        val store = configLoadServices.ordinalStore.value
        assertEquals(1, store.incrementAndGet(Ordinal.SESSION))
        assertEquals(2, store.incrementAndGet(Ordinal.SESSION))

        val installState = File(context.noBackupFilesDir, "${SdkStorageLayout.ROOT_DIR_NAME}/install_state")
        assertTrue(File(installState, SdkStorageLayout.ORDINALS_FILE_NAME).exists())
    }

    @Test
    fun `recovers ordinals from a corrupt ordinals file`() {
        val context = FilesDirApplication(tempDir.newFolder())
        val ordinals = File(
            context.noBackupFilesDir,
            "${SdkStorageLayout.ROOT_DIR_NAME}/install_state/${SdkStorageLayout.ORDINALS_FILE_NAME}",
        )
        checkNotNull(ordinals.parentFile).mkdirs()
        ordinals.writeBytes(ByteArray(32) { 0x7f })
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val configLoadServices = ConfigLoadServices(
            context = context,
            preStartServices = PreStartServices(errorHandler = errorHandler),
            workerThreadPoolSupplier = { FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker)) },
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            fileSystem = FileSystem.SYSTEM,
        )
        val store = configLoadServices.ordinalStore.value
        assertEquals(1, store.incrementAndGet(Ordinal.SESSION))
        assertEquals(2, store.incrementAndGet(Ordinal.SESSION))
        assertEquals(
            listOf(SdkErrorType.OrdinalStoreOpenFail.toString()),
            errorHandler.sdkErrorMessages.map { it.msg },
        )
    }

    @Test
    fun `keeps delivered AEI and network capture counts in install state`() {
        val context = FilesDirApplication(tempDir.newFolder())
        val rule = NetworkCaptureRuleRemoteConfig(id = "rule", duration = null, method = "GET", urlRegex = "embrace.io")
        val configService = FakeConfigService(networkBehavior = FakeNetworkBehavior(rules = setOf(rule)))
        val workers = listOf(Worker.Background.NonIoRegWorker, Worker.Background.PersistenceWorker)
        val workerThreadPool = FakeWorkerThreadPool(testWorkers = workers)
        val first = createServices(context, configService, workerThreadPool)
        first.deliveredAeiStore.value.add("aei")
        first.networkCaptureRuleStore.value.decrement(rule.id, 5)
        workers.forEach { workerThreadPool.executorFor(it).runCurrentlyBlocked() }

        val second = createServices(context, configService, FakeWorkerThreadPool(testWorkers = workers))
        assertEquals(setOf("aei"), second.deliveredAeiStore.value.ids())
        assertEquals(4, second.networkCaptureRuleStore.value.remaining(rule.id, 5))
    }

    @Test
    fun `the stores don't wait behind a config move that timed out`() {
        val configMoveReleased = CountDownLatch(1)
        val fs = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun metadataOrNull(path: Path): FileMetadata? {
                if (path.name == "embrace_remote_config") {
                    configMoveReleased.await()
                }
                return super.metadataOrNull(path)
            }
        }
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val configLoadServices = ConfigLoadServices(
            context = FilesDirApplication(tempDir.newFolder()),
            preStartServices = PreStartServices(errorHandler = errorHandler),
            workerThreadPoolSupplier = { FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker)) },
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            fileSystem = fs,
            configMigrationTimeoutMs = 1,
        )
        try {
            val ordinals = Executors.newSingleThreadExecutor().submit<OrdinalStore> {
                configLoadServices.ordinalStore.value
            }
            assertEquals(1, ordinals.get(5, TimeUnit.SECONDS).incrementAndGet(Ordinal.SESSION))
            assertEquals("StorageMigrationFail", errorHandler.sdkErrorMessages.single().msg)
        } finally {
            configMoveReleased.countDown()
        }
    }

    /**
     * Blocks until the prefs migration and any in-progress state prefetch are done, so no file is left open.
     */
    private fun ConfigLoadServices.awaitStatePrefetch() {
        persistedSdkStateStore.value.snapshot()
    }

    private fun createServices(
        context: FilesDirApplication,
        configService: FakeConfigService,
        workerThreadPool: FakeWorkerThreadPool =
            FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.PersistenceWorker)),
    ) = ConfigLoadServices(
        context = context,
        preStartServices = PreStartServices(errorHandler = FakeErrorHandler()),
        workerThreadPoolSupplier = { workerThreadPool },
        configServiceSupplier = { _, _, _, _, _, _ -> configService },
        fileSystem = fsRule.fileSystem,
    ).also { it.awaitStatePrefetch() }
}
