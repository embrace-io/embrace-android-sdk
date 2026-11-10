@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import android.preference.PreferenceManager
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
import io.embrace.android.embracesdk.internal.prefs.PrefsDeliveredAeiStore
import io.embrace.android.embracesdk.internal.prefs.PrefsNetworkCaptureRuleStore
import io.embrace.android.embracesdk.internal.prefs.PrefsPersistedSdkStateStore
import io.embrace.android.embracesdk.internal.prefs.PrefsStoreWriteBatcher
import io.embrace.android.embracesdk.internal.prefs.createKeyValueStore
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.storage.SdkDir
import io.embrace.android.embracesdk.internal.storage.SdkStorage
import io.embrace.android.embracesdk.internal.storage.SdkStorageLayout
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.OrdinalStoreImpl
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateStore
import io.embrace.android.embracesdk.internal.store.StoreWriteBatcher
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
    val superiorStorageSystemEnabled: Boolean = SUPERIOR_STORAGE_SYSTEM_ENABLED,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : Closeable {

    val sdkStorage: SdkStorage = if (superiorStorageSystemEnabled) {
        createSdkStorage(context, preStartServices.errorHandler, fileSystem)
    } else {
        createLegacySdkStorage(context, preStartServices.errorHandler)
    }

    val keyValueStore: Lazy<KeyValueStore> = lazy {
        createKeyValueStore(context, jsonSerializer, preStartServices.errorHandler)
    }

    val persistedSdkStateStore: Lazy<PersistedSdkStateStore> = lazy {
        if (superiorStorageSystemEnabled) {
            FilePersistedSdkStateStore(
                fileSystem = sdkStorage.fileSystem,
                path = sdkStorage.dir(SdkDir.INSTALL_STATE) / SdkStorageLayout.PERSISTED_SDK_STATE_FILE_NAME,
                worker = workerThreadPool.backgroundWorker(Worker.Background.PersistenceWorker),
                errorHandler = preStartServices.errorHandler,
                clock = preStartServices.clock,
                uuidSource = uuidSource,
            )
        } else {
            PrefsPersistedSdkStateStore(
                keyValueStore.value,
                preStartServices.clock,
                uuidSource,
                AppFramework.fromString(instrumentedConfig.project.getAppFramework()) ?: AppFramework.NATIVE,
            )
        }
    }

    val ordinalStore: Lazy<OrdinalStore> = lazy {
        EmbTrace.trace("ordinal-store-init") {
            if (superiorStorageSystemEnabled) {
                createFileOrdinalStore(preStartServices.errorHandler)
            } else {
                OrdinalStoreImpl(keyValueStore.value, preStartServices.errorHandler)
            }
        }
    }

    val writeBatcher: Lazy<StoreWriteBatcher> = lazy {
        if (superiorStorageSystemEnabled) {
            StoreWriteBatcher { it() }
        } else {
            PrefsStoreWriteBatcher(keyValueStore.value)
        }
    }

    val deliveredAeiStore: Lazy<DeliveredAeiStore> = lazy {
        if (superiorStorageSystemEnabled) {
            FileDeliveredAeiStore(
                fileSystem = sdkStorage.fileSystem,
                path = sdkStorage.dir(SdkDir.INSTALL_STATE) / SdkStorageLayout.AEI_DELIVERED_FILE_NAME,
                worker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
                errorHandler = preStartServices.errorHandler,
            )
        } else {
            PrefsDeliveredAeiStore(keyValueStore.value)
        }
    }

    val networkCaptureRuleStore: Lazy<NetworkCaptureRuleStore> = lazy {
        if (superiorStorageSystemEnabled) {
            FileNetworkCaptureRuleStore(
                fileSystem = sdkStorage.fileSystem,
                path = sdkStorage.dir(SdkDir.INSTALL_STATE) / SdkStorageLayout.NETWORK_CAPTURE_FILE_NAME,
                worker = workerThreadPool.backgroundWorker(Worker.Background.PersistenceWorker),
                errorHandler = preStartServices.errorHandler,
                liveRuleIds = { configService.networkBehavior.getNetworkCaptureRules().mapTo(HashSet()) { it.id } },
            )
        } else {
            PrefsNetworkCaptureRuleStore(keyValueStore.value)
        }
    }

    val persistedConfig: PersistedConfig = EmbTrace.trace(
        sectionName = "persisted-config-init",
        recordDuration = true,
    ) {
        PersistedConfig(
            serializer = jsonSerializer,
            storageDir = sdkStorage.path(SdkDir.REMOTE_CONFIG).toFile(),
            instrumentedConfig = instrumentedConfig,
            stateStore = persistedSdkStateStore,
            clock = preStartServices.clock,
            errorHandler = preStartServices.errorHandler,
        )
    }

    val workerThreadPool: WorkerThreadPool = EmbTrace.trace("worker-thread-pool-init") {
        workerThreadPoolSupplier?.invoke() ?: WorkerThreadPoolImpl(preStartServices.errorHandler)
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
        if (!superiorStorageSystemEnabled) {
            prewarmSharedPreferences()
        }

        // kick off config HTTP request early so the SDK can't get in a permanently disabled state.
        configService.scheduleConfigRequests()
    }

    override fun close() {
        workerThreadPool.backgroundWorker(Worker.Background.HttpRequestWorker).submit {
            okHttpClient.evictConnections()
            workerThreadPool.close()
        }
    }

    private fun createFileOrdinalStore(errorHandler: ErrorHandler): OrdinalStore {
        val path = sdkStorage.dir(SdkDir.INSTALL_STATE) / SdkStorageLayout.ORDINALS_FILE_NAME
        errorHandler.guardSdkCode(SdkErrorType.OrdinalStoreOpenFail, Unit) {
            if (!sdkStorage.fileSystem.exists(path)) {
                MmapOrdinalStore.create(sdkStorage.fileSystem, path, emptyMap())
            }
        }
        val ordinals = MmapOrdinalStore.open(path.toFile(), errorHandler) ?: return UnavailableOrdinalStore
        return ScopedOrdinalStore(ordinals, persistedSdkStateStore.value, errorHandler)
    }

    /**
     * Touches the default `SharedPreferences` on a background worker as early as possible to speculatively
     * attempt to reduce the amount of time Android blocks with `awaitLoadedLocked` later on in SDK init.
     */
    @Suppress("SharedPreferencesUsage")
    private fun prewarmSharedPreferences() {
        workerThreadPool.backgroundWorker(Worker.Background.IoRegWorker).submit {
            try {
                PreferenceManager.getDefaultSharedPreferences(context)
            } catch (ignored: Throwable) {
            }
        }
    }
}

internal const val SUPERIOR_STORAGE_SYSTEM_ENABLED = false
