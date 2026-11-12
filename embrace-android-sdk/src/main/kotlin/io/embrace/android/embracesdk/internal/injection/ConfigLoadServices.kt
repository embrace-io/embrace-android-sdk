package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.arch.store.DeliveredAeiStore
import io.embrace.android.embracesdk.internal.arch.store.NetworkCaptureRuleStore
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.prefs.PrefsMigrationGate
import io.embrace.android.embracesdk.internal.prefs.createSharedPrefsMigrator
import io.embrace.android.embracesdk.internal.prefs.startFreshInstallState
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.storage.OrphanSweeper
import io.embrace.android.embracesdk.internal.storage.SdkDir
import io.embrace.android.embracesdk.internal.storage.SdkStorage
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.storage.migration.LegacyTelemetryMigration
import io.embrace.android.embracesdk.internal.storage.migration.MigrationHandle
import io.embrace.android.embracesdk.internal.storage.migration.MigrationWorker
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.file.FileDeliveredAeiStore
import io.embrace.android.embracesdk.internal.store.file.FileNetworkCaptureRuleStore
import io.embrace.android.embracesdk.internal.store.file.FilePersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.file.MmapOrdinalStore
import io.embrace.android.embracesdk.internal.store.file.ScopedOrdinalStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.UuidSourceImpl
import io.embrace.android.embracesdk.internal.worker.Worker
import okhttp3.OkHttpClient
import okio.FileSystem
import java.io.Closeable

/**
 * Constructs the objects needed to load config and decide whether the SDK is disabled. This is constructed before
 * the SDK's services and decides whether to no-op or continue SDK initialization. Construction has no side
 * effects: [start] must be called to begin background work, and [close] must be called if the SDK's services
 * are never constructed.
 *
 * Each supplier is a test seam and is null in production.
 */
internal class ConfigLoadServices(
    private val context: Context,
    preStartServices: PreStartServices,
    val instrumentedConfig: InstrumentedConfig = InstrumentedConfigImpl,
    val jsonSerializer: PlatformSerializer = EmbraceSerializer(),
    val okHttpClient: Lazy<OkHttpClient> = lazyOkHttpClient(),
    val uuidSource: UuidSource = UuidSourceImpl(),
    workerThreadPoolSupplier: WorkerThreadPoolSupplier? = null,
    configServiceSupplier: ConfigServiceSupplier? = null,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val configMigrationTimeoutMs: Long = CONFIG_MIGRATION_TIMEOUT_MS,
    private val prefsMigrationTimeoutMs: Long = PREFS_MIGRATION_TIMEOUT_MS,
    orphanSweepDelayMs: Long = LegacyTelemetryMigration.SWEEP_DELAY_MS,
) : Closeable {

    // only sweep orphaned files from previous processes
    private val sweepCutoffMs: Long = System.currentTimeMillis().let { it - it % MS_PER_SECOND }

    val sdkStorage: SdkStorage = createSdkStorage(context, preStartServices.errorHandler, fileSystem)

    private val migrationWorker = MigrationWorker(preStartServices.errorHandler)

    private val legacyStorageMigrator = createLegacyStorageMigrator(context, preStartServices.errorHandler, fileSystem)

    val legacyTelemetryMigration: LegacyTelemetryMigration = LegacyTelemetryMigration(
        worker = migrationWorker,
        migrator = legacyStorageMigrator,
        errorHandler = preStartServices.errorHandler,
        orphanSweeper = OrphanSweeper(
            storage = sdkStorage,
            layoutVersion = SdkStorageLayout.LAYOUT_VERSION,
            processStartMs = sweepCutoffMs,
            wallClock = System::currentTimeMillis,
            errorHandler = preStartServices.errorHandler,
        ),
        sweepDelayMs = orphanSweepDelayMs,
    )

    private val appFramework: AppFramework =
        AppFramework.fromString(instrumentedConfig.project.getAppFramework()) ?: AppFramework.NATIVE

    /**
     * Submitted once [persistedConfig] exists, as the migration needs its network capture rules. It's queued behind the
     * config move, so if that move times out it completes as a fresh install instead of waiting behind it.
     */
    private val prefsMigration = MigrationHandle(PREFS_MIGRATION_NAME, false, preStartServices.errorHandler)

    private val prefsMigrationGate = PrefsMigrationGate()

    /**
     * Every file-backed install-state store touches this before opening its file, so it sees the migrated prefs. A
     * migration that misses the deadline is abandoned and the install starts fresh.
     */
    private val installState: Lazy<Unit> = lazy {
        prefsMigration.await(prefsMigrationTimeoutMs, SdkErrorType.PrefsMigrationFail)
        if (!prefsMigration.isComplete) {
            preStartServices.errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail) {
                prefsMigrationGate.abandon {
                    startFreshInstallState(sdkStorage.fileSystem, sdkStorage.dir(SdkDir.INSTALL_STATE))
                }
            }
        }
    }

    val persistedSdkStateStore: Lazy<PersistedSdkStateStore> = lazy {
        installState.value
        FilePersistedSdkStateStore(
            fileSystem = sdkStorage.fileSystem,
            path = sdkStorage.dir(SdkDir.INSTALL_STATE) / SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME,
            worker = workerThreadPool.backgroundWorker(Worker.Background.PersistenceWorker),
            errorHandler = preStartServices.errorHandler,
            clock = preStartServices.clock,
            uuidSource = uuidSource,
        )
    }

    val ordinalStore: Lazy<OrdinalStore> = lazy {
        EmbTrace.trace("ordinal-store-init") {
            createFileOrdinalStore(preStartServices.errorHandler)
        }
    }

    val deliveredAeiStore: Lazy<DeliveredAeiStore> = lazy {
        installState.value
        FileDeliveredAeiStore(
            fileSystem = sdkStorage.fileSystem,
            path = sdkStorage.dir(SdkDir.INSTALL_STATE) / SdkStorageLayout.AEI_DELIVERED_FILE_NAME,
            worker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
            errorHandler = preStartServices.errorHandler,
        )
    }

    val networkCaptureRuleStore: Lazy<NetworkCaptureRuleStore> = lazy {
        installState.value
        FileNetworkCaptureRuleStore(
            fileSystem = sdkStorage.fileSystem,
            path = sdkStorage.dir(SdkDir.INSTALL_STATE) / SdkStorageLayout.NETWORK_CAPTURE_FILE_NAME,
            worker = workerThreadPool.backgroundWorker(Worker.Background.PersistenceWorker),
            errorHandler = preStartServices.errorHandler,
            liveRuleIds = { configService.networkBehavior.getNetworkCaptureRules().mapTo(HashSet()) { it.id } },
        )
    }

    val workerThreadPool: WorkerThreadPool = EmbTrace.trace("worker-thread-pool-init") {
        workerThreadPoolSupplier?.invoke() ?: WorkerThreadPoolImpl(preStartServices.errorHandler)
    }

    val persistedConfig: PersistedConfig = EmbTrace.trace(
        sectionName = "persisted-config-init",
        recordDuration = true,
    ) {
        val configMoved = migrationWorker
            .submit("config", Unit, SdkErrorType.StorageMigrationFail, legacyStorageMigrator::migrateConfig)
            .also { it.await(configMigrationTimeoutMs, SdkErrorType.StorageMigrationFail) }
            .isComplete
        PersistedConfig(
            serializer = jsonSerializer,
            storageDir = sdkStorage.path(SdkDir.REMOTE_CONFIG).toFile(),
            instrumentedConfig = instrumentedConfig,
            stateStore = persistedSdkStateStore,
            clock = preStartServices.clock,
            errorHandler = preStartServices.errorHandler,
        ).also { config ->
            if (configMoved) {
                submitPrefsMigration(config, preStartServices.errorHandler)
            } else {
                prefsMigration.complete()
            }
        }
    }

    val configService: ConfigService = EmbTrace.trace(sectionName = "config-service-init", recordDuration = true) {
        configServiceSupplier?.invoke(
            preStartServices,
            instrumentedConfig,
            jsonSerializer,
            okHttpClient,
            workerThreadPool,
            persistedConfig,
        ) ?: createConfigService(
            preStartServices,
            instrumentedConfig,
            jsonSerializer,
            okHttpClient,
            workerThreadPool,
            persistedConfig,
        )
    }

    /**
     * Begins the background work needed during SDK init. Call this as early as possible after construction.
     */
    fun start() {
        // kick off config HTTP request early so the SDK can't get in a permanently disabled state.
        configService.scheduleConfigRequests()
    }

    fun flushPersistedSdkState() {
        if (persistedSdkStateStore.isInitialized()) {
            persistedSdkStateStore.value.flush()
        }
    }

    override fun close() {
        workerThreadPool.backgroundWorker(Worker.Background.HttpRequestWorker).submit {
            okHttpClient.evictConnections()
            workerThreadPool.close()
        }
    }

    private fun submitPrefsMigration(config: PersistedConfig, errorHandler: ErrorHandler) {
        migrationWorker.submit(prefsMigration, SdkErrorType.PrefsMigrationFail) {
            createSharedPrefsMigrator(
                context = context,
                fileSystem = sdkStorage.fileSystem,
                installStateDir = sdkStorage.dir(SdkDir.INSTALL_STATE),
                serializer = jsonSerializer,
                errorHandler = errorHandler,
                appFramework = appFramework,
                gate = prefsMigrationGate,
                liveNetworkCaptureRules = config::networkCaptureRuleMaxCounts,
            ).migrate()
        }
    }

    private fun createFileOrdinalStore(errorHandler: ErrorHandler): OrdinalStore {
        installState.value
        val path = sdkStorage.dir(SdkDir.INSTALL_STATE) / SdkStorageLayout.ORDINALS_FILE_NAME
        errorHandler.guardSdkCode(SdkErrorType.OrdinalStoreOpenFail, Unit) {
            if (!sdkStorage.fileSystem.exists(path)) {
                MmapOrdinalStore.create(sdkStorage.fileSystem, path, emptyMap())
            }
        }
        val ordinals = MmapOrdinalStore.open(path.toFile(), errorHandler) ?: return UnavailableOrdinalStore
        return ScopedOrdinalStore(ordinals, persistedSdkStateStore.value, errorHandler)
    }
}

private const val CONFIG_MIGRATION_TIMEOUT_MS = 3_000L

private const val PREFS_MIGRATION_TIMEOUT_MS = 3_000L

private const val MS_PER_SECOND = 1_000L

private const val PREFS_MIGRATION_NAME = "prefs"
