package io.embrace.android.embracesdk.internal.injection

import android.os.Build
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.config.ConfigServiceImpl
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.source.OkHttpRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.worker.Worker
import okhttp3.OkHttpClient

internal fun createConfigService(
    preStartServices: PreStartServices,
    instrumentedConfig: InstrumentedConfig,
    serializer: PlatformSerializer,
    okHttpClient: Lazy<OkHttpClient>,
    workerThreadPool: WorkerThreadPool,
    persistedConfig: PersistedConfig,
    remoteConfigSource: RemoteConfigSource = OkHttpRemoteConfigSource(
        okhttpClient = okHttpClient,
        serializer = serializer,
        instrumentedConfig = instrumentedConfig,
        deviceIdProvider = { persistedConfig.deviceId },
        sdkVersion = BuildConfig.VERSION_NAME,
        apiLevel = Build.VERSION.SDK_INT,
    ),
): ConfigServiceImpl = ConfigServiceImpl(
    instrumentedConfig = instrumentedConfig,
    persistedConfig = persistedConfig,
    worker = workerThreadPool.backgroundWorker(Worker.Background.HttpRequestWorker),
    serializer = serializer,
    remoteConfigSource = remoteConfigSource,
    hasConfiguredOtlpExport = preStartServices.externalOtelConfig::hasConfiguredOtlpExport,
    primaryAbi = preStartServices.systemInfo.primaryAbi,
    errorHandler = preStartServices.errorHandler,
)
