package io.embrace.android.embracesdk.internal.injection

import android.os.Build
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.config.ConfigServiceImpl
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.source.OkHttpRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.worker.Worker

/**
 * Constructs the [ConfigServiceImpl] the SDK runs with. Tests that must not reach the network pass
 * their own [remoteConfigSource].
 */
internal fun createConfigService(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    persistedConfig: PersistedConfig,
    remoteConfigSource: RemoteConfigSource = OkHttpRemoteConfigSource(
        okhttpClient = initModule.okHttpClient,
        serializer = initModule.jsonSerializer,
        instrumentedConfig = initModule.instrumentedConfig,
        deviceIdProvider = { persistedConfig.deviceId },
        sdkVersion = BuildConfig.VERSION_NAME,
        apiLevel = Build.VERSION.SDK_INT,
    ),
): ConfigServiceImpl = ConfigServiceImpl(
    instrumentedConfig = initModule.instrumentedConfig,
    persistedConfig = persistedConfig,
    worker = workerThreadModule.backgroundWorker(Worker.Background.HttpRequestWorker),
    serializer = initModule.jsonSerializer,
    remoteConfigSource = remoteConfigSource,
    hasConfiguredOtlpExport = openTelemetryModule.otelSdkConfig.externalConfig::hasConfiguredOtlpExport,
    primaryAbi = initModule.systemInfo.primaryAbi,
    logger = initModule.logger,
)
