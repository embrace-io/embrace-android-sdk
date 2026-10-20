package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import okhttp3.OkHttpClient

typealias ConfigServiceSupplier = (
    preStartServices: PreStartServices,
    instrumentedConfig: InstrumentedConfig,
    serializer: PlatformSerializer,
    okHttpClient: Lazy<OkHttpClient>,
    workerThreadPool: WorkerThreadPool,
    persistedConfig: PersistedConfig,
) -> ConfigService

typealias CoreModuleSupplier = (
    context: Context,
    initModule: InitModule,
    keyValueStore: Lazy<KeyValueStore>,
) -> CoreModule

typealias InitModuleSupplier = (
    preStartServices: PreStartServices,
) -> InitModule

typealias OpenTelemetryModuleSupplier = (
    initModule: InitModule,
    configService: ConfigService,
    externalOtelConfig: ExternalOtelConfig,
    sessionIdsProvider: () -> SessionIdsProvider?,
    userIdProvider: () -> String?,
    eventMetadataProvider: () -> Map<String, String>,
) -> OpenTelemetryModule

typealias StorageServiceSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadPool: WorkerThreadPool,
) -> StorageService

typealias WorkerThreadPoolSupplier = () -> WorkerThreadPool
