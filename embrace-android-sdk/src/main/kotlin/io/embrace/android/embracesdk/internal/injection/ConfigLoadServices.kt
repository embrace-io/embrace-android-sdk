@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.prefs.createKeyValueStore
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.UuidSourceImpl
import io.embrace.android.embracesdk.internal.worker.Worker
import okhttp3.OkHttpClient
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
) : Closeable {

    val keyValueStore: Lazy<KeyValueStore> = lazy { createKeyValueStore(context, jsonSerializer) }

    val persistedConfig: PersistedConfig = EmbTrace.trace(
        sectionName = "persisted-config-init",
        recordDuration = true,
    ) {
        PersistedConfig(
            serializer = jsonSerializer,
            filesDir = context.filesDir,
            instrumentedConfig = instrumentedConfig,
            keyValueStore = keyValueStore,
            uuidSource = uuidSource,
            clock = preStartServices.clock,
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
        prewarmSharedPreferences()

        // kick off config HTTP request early so the SDK can't get in a permanently disabled state.
        configService.scheduleConfigRequests()
    }

    override fun close() {
        workerThreadPool.backgroundWorker(Worker.Background.HttpRequestWorker).submit {
            okHttpClient.evictConnections()
            workerThreadPool.close()
        }
    }

    /**
     * Touches the default `SharedPreferences` on a background worker as early as possible to speculatively
     * attempt to reduce the amount of time Android blocks with `awaitLoadedLocked` later on in SDK init.
     */
    private fun prewarmSharedPreferences() {
        workerThreadPool.backgroundWorker(Worker.Background.IoRegWorker).submit {
            try {
                PreferenceManager.getDefaultSharedPreferences(context)
            } catch (ignored: Throwable) {
            }
        }
    }
}
