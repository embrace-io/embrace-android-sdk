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
import io.embrace.android.embracesdk.internal.prefs.PrefsMigrationPhase
import io.embrace.android.embracesdk.internal.prefs.createSharedPrefsMigrator
import io.embrace.android.embracesdk.internal.prefs.startFreshInstallState
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.storage.OrphanSweeper
import io.embrace.android.embracesdk.internal.storage.SdkDir
import io.embrace.android.embracesdk.internal.storage.SdkStorage
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.storage.migration.LegacyTelemetryMigration
import io.embrace.android.embracesdk.internal.storage.migration.MigrationBudget
import io.embrace.android.embracesdk.internal.storage.migration.MigrationHandle
import io.embrace.android.embracesdk.internal.storage.migration.MigrationWorker
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.file.FileDeliveredAeiStore
import io.embrace.android.embracesdk.internal.store.file.FileNetworkCaptureRuleStore
import io.embrace.android.embracesdk.internal.store.file.FilePersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.file.MmapOrdinalStore
import io.embrace.android.embracesdk.internal.store.file.ScopedOrdinalStore
import io.embrace.android.embracesdk.internal.store.file.WriteBarrier
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
    val jsonSerializer: PlatformSerializer = EmbTrace.trace("serializer-init") { EmbraceSerializer() },
    val okHttpClient: Lazy<OkHttpClient> = lazyOkHttpClient(),
    val uuidSource: UuidSource = UuidSourceImpl(),
    workerThreadPoolSupplier: WorkerThreadPoolSupplier? = null,
    configServiceSupplier: ConfigServiceSupplier? = null,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    migrationBudgetMs: Long = MigrationBudget.BUDGET_MS,
    orphanSweepDelayMs: Long = LegacyTelemetryMigration.SWEEP_DELAY_MS,
) : Closeable {

    // only sweep orphaned files from previous processes
    private val sweepCutoffMs: Long = System.currentTimeMillis().let { it - it % MS_PER_SECOND }

    val sdkStorage: SdkStorage = EmbTrace.trace("sdk-storage-init") {
        createSdkStorage(context, preStartServices.errorHandler, fileSystem)
    }

    /**
     * Whether a previous launch migrated, in which case the move and prefs migration below are never built. Probed by
     * [persistedConfig].
     */
    private val migrated: Boolean by lazy {
        EmbTrace.trace("migration-marker-probe") {
            preStartServices.errorHandler.guardSdkCode(SdkErrorType.Ignored, false) {
                sdkStorage.fileSystem.exists(sdkStorage.dir(SdkDir.INSTALL_STATE) / SdkStorageLayout.ORDINALS_FILE_NAME)
            }
        }
    }

    private val migrationWorker = lazy {
        EmbTrace.trace("migration-worker-init") { MigrationWorker(preStartServices.errorHandler) }
    }

    private val migrationBudget by lazy {
        EmbTrace.trace("migration-budget-init") {
            MigrationBudget(migrationWorker.value, preStartServices.errorHandler, migrationBudgetMs)
        }
    }

    /**
     * The prefs migration and the state prefetch run on their own thread, so the telemetry move isn't queued behind
     * them: its result is needed before the prefs migration's.
     */
    private val prefsMigrationWorker by lazy {
        EmbTrace.trace("prefs-migration-worker-init") {
            MigrationWorker(preStartServices.errorHandler, { runnable ->
                Thread(runnable, PREFS_MIGRATION_THREAD_NAME).apply { isDaemon = true }
            })
        }
    }

    private val legacyStorageMigrator = lazy {
        EmbTrace.trace("legacy-storage-migrator-init") {
            createLegacyStorageMigrator(context, preStartServices.errorHandler, fileSystem)
        }
    }

    val legacyTelemetryMigration: LegacyTelemetryMigration by lazy {
        LegacyTelemetryMigration(
            worker = migrationWorker,
            migrator = legacyStorageMigrator,
            errorHandler = preStartServices.errorHandler,
            orphanSweeper = lazy {
                OrphanSweeper(
                    storage = sdkStorage,
                    layoutVersion = SdkStorageLayout.LAYOUT_VERSION,
                    processStartMs = sweepCutoffMs,
                    wallClock = System::currentTimeMillis,
                    errorHandler = preStartServices.errorHandler,
                )
            },
            sweepDelayMs = orphanSweepDelayMs,
            alreadyMoved = migrated,
        )
    }

    private val appFramework by lazy {
        EmbTrace.trace("app-framework-init") {
            AppFramework.fromString(instrumentedConfig.project.getAppFramework()) ?: AppFramework.NATIVE
        }
    }

    /**
     * Submitted once [persistedConfig] exists, as the migration needs its network capture rules. Completes once the
     * ordinals are staged and the stores can start from what it decoded, before it writes the files, or when it
     * finishes if staging failed.
     */
    private val prefsMigration by lazy {
        EmbTrace.trace("prefs-migration-handle-init") {
            MigrationHandle(PREFS_MIGRATION_NAME, false, preStartServices.errorHandler)
        }
    }

    /**
     * Null once migrated: the stores then read their files.
     */
    private val prefsMigrationGate: PrefsMigrationGate? by lazy {
        if (migrated) null else EmbTrace.trace("prefs-migration-gate-init") { PrefsMigrationGate() }
    }

    /**
     * Every file-backed install-state store touches this before it starts, so it sees the migrated prefs. Waits up to
     * the [migrationBudget] for the migration to decode the prefs and stage the ordinals. One still decoding is then
     * abandoned and the install starts fresh. Otherwise the stores start from the state it decoded, if it wrote or is
     * still writing it. Once migrated it is [PrefsMigrationPhase.COMMITTED] at once.
     */
    private val installState: Lazy<PrefsMigrationPhase> = lazy {
        val gate = prefsMigrationGate ?: return@lazy PrefsMigrationPhase.COMMITTED
        migrationBudget.await(prefsMigration, SdkErrorType.PrefsMigrationFail)
        gate.settle().also { phase ->
            if (phase == PrefsMigrationPhase.ABANDONED) {
                preStartServices.errorHandler.guardSdkCode(SdkErrorType.PrefsMigrationFail) {
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
            initial = prefsMigrationGate?.migrated?.persistedSdkState,
            writes = prefsMigrationGate?.writes ?: WriteBarrier(open = true),
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
            initialIds = prefsMigrationGate?.migrated?.deliveredAeiIds,
            writes = prefsMigrationGate?.writes ?: WriteBarrier(open = true),
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
            initialUsed = prefsMigrationGate?.migrated?.networkCaptureUsed,
            writes = prefsMigrationGate?.writes ?: WriteBarrier(open = true),
        )
    }

    val workerThreadPool: WorkerThreadPool = EmbTrace.trace("worker-thread-pool-init") {
        workerThreadPoolSupplier?.invoke() ?: WorkerThreadPoolImpl(preStartServices.errorHandler)
    }

    val persistedConfig: PersistedConfig = EmbTrace.trace(
        sectionName = "persisted-config-init",
        recordDuration = true,
    ) {
        val storageDir = sdkStorage.path(SdkDir.REMOTE_CONFIG)
        // until the telemetry move moves it, a 9.x config is read where 9.x left it
        val readDir = if (migrated) {
            storageDir
        } else {
            EmbTrace.trace("migration-config-probe") {
                val hasConfig = preStartServices.errorHandler.guardSdkCode(SdkErrorType.Ignored, true) {
                    sdkStorage.fileSystem.exists(storageDir / PersistedConfig.CONFIG_RESPONSE_FILE_NAME)
                }
                storageDir.takeIf { hasConfig } ?: legacyStorageMigrator.value.legacyConfigDir() ?: storageDir
            }
        }
        PersistedConfig(
            serializer = jsonSerializer,
            storageDir = storageDir.toFile(),
            readDir = readDir.toFile(),
            instrumentedConfig = instrumentedConfig,
            stateStore = persistedSdkStateStore,
            clock = preStartServices.clock,
            errorHandler = preStartServices.errorHandler,
        ).also { config ->
            prefsMigrationGate?.let { submitPrefsMigration(config, it, preStartServices.errorHandler) }
            submitStatePrefetch(preStartServices.errorHandler)
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

    /**
     * Moves the 9.x telemetry, blocking for up to the migration budget, unless a previous launch migrated.
     */
    fun moveLegacyTelemetry() {
        if (!migrated) {
            legacyTelemetryMigration.move(migrationBudget)
        }
    }

    override fun close() {
        workerThreadPool.backgroundWorker(Worker.Background.HttpRequestWorker).submit {
            okHttpClient.evictConnections()
            workerThreadPool.close()
        }
    }

    private fun submitPrefsMigration(config: PersistedConfig, gate: PrefsMigrationGate, errorHandler: ErrorHandler) {
        prefsMigrationWorker.submit(prefsMigration, SdkErrorType.PrefsMigrationFail) {
            createSharedPrefsMigrator(
                context = context,
                fileSystem = sdkStorage.fileSystem,
                installStateDir = sdkStorage.dir(SdkDir.INSTALL_STATE),
                serializer = jsonSerializer,
                errorHandler = errorHandler,
                appFramework = appFramework,
                gate = gate,
                liveNetworkCaptureRules = config::networkCaptureRuleMaxCounts,
                onDecoded = { prefsMigration.complete(true) },
            ).migrate()
        }
    }

    /**
     * Prefetches the persisted SDK state off the init thread which then either finds it already
     * loaded or waits for the in-progress load.
     */
    private fun submitStatePrefetch(errorHandler: ErrorHandler) {
        if (migrated) {
            errorHandler.guardSdkCode(SdkErrorType.PersistedSdkStateLoadFail) {
                workerThreadPool.backgroundWorker(Worker.Background.PersistenceWorker).submit {
                    errorHandler.guardSdkCode(SdkErrorType.PersistedSdkStateLoadFail) {
                        persistedSdkStateStore.value.snapshot()
                    }
                }
            }
            return
        }
        val handle = MigrationHandle(STATE_PREFETCH_NAME, Unit, errorHandler, recordDuration = false)
        prefsMigrationWorker.submit(handle, SdkErrorType.PersistedSdkStateLoadFail) {
            persistedSdkStateStore.value.snapshot()
        }
    }

    private fun createFileOrdinalStore(errorHandler: ErrorHandler): OrdinalStore {
        val phase = installState.value
        prefsMigrationGate?.ordinals?.let {
            return ScopedOrdinalStore(it, persistedSdkStateStore.value, errorHandler)
        }
        if (phase == PrefsMigrationPhase.ADOPTED) {
            migrationBudget.await(prefsMigration, SdkErrorType.PrefsMigrationFail)
            if (!prefsMigration.isComplete) {
                return UnavailableOrdinalStore
            }
        }
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

private const val MS_PER_SECOND = 1_000L

private const val PREFS_MIGRATION_NAME = "prefs"

private const val STATE_PREFETCH_NAME = "sdk-state-prefetch"

private const val PREFS_MIGRATION_THREAD_NAME = "emb-prefs-migration"
