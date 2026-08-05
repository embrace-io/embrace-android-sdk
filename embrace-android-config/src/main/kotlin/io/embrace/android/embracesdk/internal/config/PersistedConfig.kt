package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehaviorImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStoreImpl
import io.embrace.android.embracesdk.internal.config.store.StoredConfigResponse
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.UuidSource
import java.io.File

/**
 * The remote config that was persisted by a previous launch, loaded from disk at construction time. This
 * is the very first thing the SDK reads and is fundamental for deciding how the SDK should behave.
 *
 * Loading is disk-only and does not touch the network: the HTTP fetch scheduled later by
 * [ConfigServiceImpl] only writes to the store, so a config change from the server takes effect on
 * the next process launch.
 */
class PersistedConfig(
    serializer: PlatformSerializer,
    filesDir: File,
    instrumentedConfig: InstrumentedConfig,
    keyValueStore: Lazy<KeyValueStore>,
    uuidSource: UuidSource,
) {

    /**
     * Without an app ID there is no Embrace backend to fetch config from, so any config left on disk
     * by a previous install must be ignored and the store is never created.
     */
    internal val onlyOtelExportEnabled: Boolean = instrumentedConfig.project.getAppId() == null

    internal val store: RemoteConfigStore? = when {
        onlyOtelExportEnabled -> null
        else -> RemoteConfigStoreImpl(
            serializer = serializer,
            storageDir = File(filesDir, STORAGE_DIR_NAME),
            deviceIdProvider = { deviceId },
        )
    }

    internal val response: StoredConfigResponse? = runCatching { store?.loadResponse() }.getOrNull()

    internal val remoteConfig: RemoteConfig? = response?.cfg

    /**
     * Resolved lazily so that the common case (binary cache) never has to touch the [KeyValueStore].
     */
    val deviceId: String by lazy {
        DeviceIdProvider(keyValueStore, response?.deviceId, uuidSource).deviceId
    }

    internal val thresholdCheck: BehaviorThresholdCheck = BehaviorThresholdCheck(::deviceId)

    val otelBehavior: OtelBehavior = OtelBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)

    companion object {

        /**
         * Subdirectory of the app's files dir where the remote config is persisted.
         */
        const val STORAGE_DIR_NAME: String = "embrace_remote_config"
    }
}
