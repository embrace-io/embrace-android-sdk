package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import okhttp3.OkHttpClient

typealias ConfigServiceSupplier = (
    preStartServices: PreStartServices,
    instrumentedConfig: InstrumentedConfig,
    serializer: PlatformSerializer,
    okHttpClient: Lazy<OkHttpClient>,
    workerThreadPool: WorkerThreadPool,
    persistedConfig: PersistedConfig,
) -> ConfigService

typealias WorkerThreadPoolSupplier = () -> WorkerThreadPool
