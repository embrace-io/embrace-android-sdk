package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.assertions.waitUntilConditionMet
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FilesDirApplication
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fs.FakeFileSystemRule
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.storage.SdkDir
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.store.Ordinal
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

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
    fun `reads the persisted SDK state off the init thread, skipping the 9x dirs once migrated`() {
        val context = FilesDirApplication(tempDir.newFolder())
        val stateFile = context.noBackupFilesDir.toOkioPath() / SdkStorageLayout.ROOT_DIR_NAME / "install_state" /
            SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME
        fsRule.fileSystem.createDirectories(checkNotNull(stateFile.parent))
        FilePersistedSdkStateStore.create(fsRule.fileSystem, stateFile, PersistedSdkState("device-id", 1L))
        val loadThreads = CopyOnWriteArrayList<String>()
        val filesDirProbes = CopyOnWriteArrayList<Path>()
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun metadataOrNull(path: Path): FileMetadata? {
                if (path.toString().startsWith("${context.filesDir}/")) {
                    filesDirProbes.add(path)
                }
                return super.metadataOrNull(path)
            }

            override fun source(file: Path): Source {
                if (file == stateFile) {
                    loadThreads.add(Thread.currentThread().name)
                }
                return super.source(file)
            }
        }
        val launch = {
            ConfigLoadServices(
                context = context,
                preStartServices = PreStartServices(errorHandler = FakeErrorHandler(throwOnSdkError = false)),
                workerThreadPoolSupplier = { FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker)) },
                configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
                fileSystem = fs,
            ).also { it.legacyTelemetryMigration.move() }
        }

        launch()
        waitUntilConditionMet(waitTimeMs = 5_000, dataProvider = { loadThreads.toList() }, condition = { it.isNotEmpty() })
        assertEquals(listOf("emb-prefs-migration"), loadThreads)
        assertTrue(filesDirProbes.isNotEmpty())
        filesDirProbes.clear()

        val migrated = launch()
        waitUntilConditionMet(waitTimeMs = 5_000, dataProvider = { loadThreads.size }, condition = { it == 2 })
        assertEquals("emb-persistence", loadThreads.last())
        assertEquals("device-id", migrated.persistedSdkStateStore.value.snapshot().deviceId)
        assertTrue(migrated.legacyTelemetryMigration.isComplete)
        assertEquals(emptyList<Path>(), filesDirProbes)
    }

    @Test
    fun `serves the 9x config in place until the telemetry move moves it`() {
        val context = FilesDirApplication(tempDir.newFolder())
        File(context.filesDir, "embrace").mkdirs()
        val legacy = File(context.filesDir, "embrace_remote_config").apply { mkdirs() }
        File(legacy, PersistedConfig.CONFIG_RESPONSE_FILE_NAME).writeText(
            """{"network_capture":[{"id":"rule","duration":null,"method":"GET","url":"embrace.io","max_count":3}]}""",
        )

        val first = createSystemServices(context)
        assertEquals(mapOf("rule" to 3), first.persistedConfig.networkCaptureRuleMaxCounts())
        assertFalse(first.sdkStorage.fileSystem.exists(first.sdkStorage.path(SdkDir.REMOTE_CONFIG)))

        first.legacyTelemetryMigration.move()
        assertFalse(legacy.exists())
        val second = createSystemServices(context)
        assertEquals(mapOf("rule" to 3), second.persistedConfig.networkCaptureRuleMaxCounts())
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
    fun `an empty config dir left by a failed write doesn't hide the 9x config`() {
        val context = FilesDirApplication(tempDir.newFolder())
        File(context.filesDir, "embrace").mkdirs()
        val legacy = File(context.filesDir, "embrace_remote_config").apply { mkdirs() }
        File(legacy, PersistedConfig.CONFIG_RESPONSE_FILE_NAME).writeText(
            """{"network_capture":[{"id":"rule","duration":null,"method":"GET","url":"embrace.io","max_count":3}]}""",
        )
        File(context.noBackupFilesDir, "${SdkStorageLayout.ROOT_DIR_NAME}/remote_config").mkdirs()

        val first = createSystemServices(context)
        assertEquals(mapOf("rule" to 3), first.persistedConfig.networkCaptureRuleMaxCounts())

        first.legacyTelemetryMigration.move()
        assertFalse(legacy.exists())
        val second = createSystemServices(context)
        assertEquals(mapOf("rule" to 3), second.persistedConfig.networkCaptureRuleMaxCounts())
    }

    @Test
    fun `a response written before the telemetry move supersedes the 9x config`() {
        val context = FilesDirApplication(tempDir.newFolder())
        File(context.filesDir, "embrace").mkdirs()
        val legacy = File(context.filesDir, "embrace_remote_config").apply { mkdirs() }
        File(legacy, PersistedConfig.ETAG_FILE_NAME).writeText("legacy")
        File(legacy, PersistedConfig.CACHED_CONFIG_FILE_NAME).writeText("legacy")
        val configLoadServices = createSystemServices(context)
        val fs = configLoadServices.sdkStorage.fileSystem
        val config = configLoadServices.sdkStorage.dir(SdkDir.REMOTE_CONFIG)
        fs.write(config / PersistedConfig.CONFIG_RESPONSE_FILE_NAME) { writeUtf8("fresh") }

        configLoadServices.legacyTelemetryMigration.move()

        val contents = fs.list(config).associate { it.name to fs.read(it) { readUtf8() } }
        assertEquals(mapOf(PersistedConfig.CONFIG_RESPONSE_FILE_NAME to "fresh"), contents)
        assertFalse(legacy.exists())
    }

    @Test
    fun `flushing the persisted SDK state writes a pending update without the worker`() {
        val context = FilesDirApplication(tempDir.newFolder())
        val workers = listOf(Worker.Background.PersistenceWorker)
        val first = createServices(context, FakeConfigService(), FakeWorkerThreadPool(testWorkers = workers))
        first.persistedSdkStateStore.value.update { userId = "flushed" }
        first.flushPersistedSdkState()

        val second = createServices(context, FakeConfigService(), FakeWorkerThreadPool(testWorkers = workers))
        assertEquals("flushed", second.persistedSdkStateStore.value.snapshot().userId)
    }

    @Test
    fun `flushing the persisted SDK state doesn't open the store`() {
        // holds the prefs migration so the state prefetch queued behind it can't open the store
        val migrationReleased = CountDownLatch(1)
        val fs = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun metadataOrNull(path: Path): FileMetadata? {
                if (Thread.currentThread().name == "emb-migration") {
                    migrationReleased.await()
                }
                return super.metadataOrNull(path)
            }
        }
        val configLoadServices = ConfigLoadServices(
            context = FilesDirApplication(tempDir.newFolder()),
            preStartServices = PreStartServices(errorHandler = FakeErrorHandler(throwOnSdkError = false)),
            workerThreadPoolSupplier = { FakeWorkerThreadPool() },
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            fileSystem = fs,
        )
        try {
            configLoadServices.flushPersistedSdkState()
            assertFalse(configLoadServices.persistedSdkStateStore.isInitialized())
        } finally {
            migrationReleased.countDown()
        }
    }

    @Test
    fun `without 9x config only the init thread checks for it`() {
        val threads = CopyOnWriteArrayList<String>()
        val fs = object : ForwardingFileSystem(fsRule.fileSystem) {
            override fun metadataOrNull(path: Path): FileMetadata? {
                if (path.name == "embrace_remote_config") {
                    threads.add(Thread.currentThread().name)
                }
                return super.metadataOrNull(path)
            }
        }
        val errorHandler = FakeErrorHandler()
        val configLoadServices = ConfigLoadServices(
            context = FilesDirApplication(tempDir.newFolder()),
            preStartServices = PreStartServices(errorHandler = errorHandler),
            workerThreadPoolSupplier = { FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker)) },
            configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
            fileSystem = fs,
        )
        configLoadServices.awaitStatePrefetch()

        assertEquals(listOf(Thread.currentThread().name), threads)
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    /**
     * Blocks until the prefs migration and any in-progress state prefetch are done, so no file is left open.
     */
    private fun ConfigLoadServices.awaitStatePrefetch() {
        persistedSdkStateStore.value.snapshot()
    }

    private fun createSystemServices(context: FilesDirApplication) = ConfigLoadServices(
        context = context,
        preStartServices = PreStartServices(errorHandler = FakeErrorHandler()),
        instrumentedConfig = FakeInstrumentedConfig(),
        workerThreadPoolSupplier = { FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.PersistenceWorker)) },
        configServiceSupplier = { _, _, _, _, _, _ -> FakeConfigService() },
        fileSystem = FileSystem.SYSTEM,
    ).also { it.awaitStatePrefetch() }

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
