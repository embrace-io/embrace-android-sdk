package io.embrace.android.embracesdk.internal.telemetry

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.isEmulator
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceUsageAttributeName
import io.embrace.android.embracesdk.semconv.EmbTelemetryAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for tracking usage of public APIs, and different internal metrics about the app.
 */
class InternalTelemetryServiceImpl(
    private val systemInfo: SystemInfo,
) : InternalTelemetryService {

    private val okHttpReflectionFacade: OkHttpReflectionFacade = OkHttpReflectionFacade()
    private val usageCountMap = ConcurrentHashMap<String, Int>()
    private val storageTelemetryMap = ConcurrentHashMap<String, String>()
    private val appliedLimitCountMap = ConcurrentHashMap<String, Int>()
    private val slowCallbackCountMap = ConcurrentHashMap<String, Int>()
    private val slowCallbackMaxMsMap = ConcurrentHashMap<String, Long>()
    private val appAttributes: Map<String, String> by lazy { computeAppAttributes() }

    override fun onPublicApiCalled(name: String) {
        usageCountMap.increment(name)
    }

    override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {
        this.storageTelemetryMap.putAll(storageTelemetry)
    }

    override fun trackAppliedLimit(telemetryType: String, limitType: AppliedLimitType) {
        val id = "applied_limit.$telemetryType.${limitType.attributeName}"
        appliedLimitCountMap.increment("emb.private.$id")
    }

    override fun trackSlowCallback(name: String, durationMs: Long) {
        slowCallbackCountMap.increment("emb.private.slow_callback.$name.count")
        slowCallbackMaxMsMap.max("emb.private.slow_callback.$name.max_ms", durationMs)
    }

    override fun getAndClearTelemetryAttributes(): Map<String, String> {
        return getAndClearUsageCountTelemetry()
            .plus(getAndClearStorageTelemetry())
            .plus(getAndClearAppliedLimitTelemetry())
            .plus(slowCallbackCountMap.drain { it.toString() })
            .plus(slowCallbackMaxMsMap.drain { it.toString() })
            .plus(appAttributes)
    }

    private fun getAndClearUsageCountTelemetry(): Map<String, String> =
        usageCountMap.drain({ it.toEmbraceUsageAttributeName() }) { it.toString() }

    private fun getAndClearStorageTelemetry(): Map<String, String> = storageTelemetryMap.drain { it }

    private fun getAndClearAppliedLimitTelemetry(): Map<String, String> = appliedLimitCountMap.drain { it.toString() }

    /**
     * Interesting attributes about the running app environment. These should be the same for every session, so we only compute them once.
     */
    private fun computeAppAttributes(): Map<String, String> {
        val appAttributesMap = mutableMapOf<String, String>()

        appAttributesMap[EmbTelemetryAttributes.EMB_OKHTTP3] = okHttpReflectionFacade.hasOkHttp3().toString()

        val okhttp3Version = okHttpReflectionFacade.getOkHttp3Version()
        if (okhttp3Version.isNotEmpty()) {
            appAttributesMap[EmbTelemetryAttributes.EMB_OKHTTP3_ON_CLASSPATH] = okhttp3Version
        }

        appAttributesMap[EmbTelemetryAttributes.EMB_KOTLIN_ON_CLASSPATH] =
            runCatching { KotlinVersion.CURRENT.toString() }.getOrDefault("unknown")

        appAttributesMap[EmbTelemetryAttributes.EMB_IS_EMULATOR] =
            runCatching { systemInfo.isEmulator().toString() }.getOrDefault("unknown")

        return appAttributesMap
    }

    /**
     * Increments the count for [key] without locking via CAS. An increment racing a drain
     * lands in either the drained count or the next one, which is acceptable for our purposes.
     */
    private fun ConcurrentHashMap<String, Int>.increment(key: String) {
        do {
            val current = this[key]
            val updated = if (current == null) {
                putIfAbsent(key, 1) == null
            } else {
                replace(key, current, current + 1)
            }
        } while (!updated)
    }

    /**
     * Raises the value for [key] to [value] if it is larger, without locking via CAS.
     */
    private fun ConcurrentHashMap<String, Long>.max(key: String, value: Long) {
        do {
            val current = this[key]
            val updated = when {
                current == null -> putIfAbsent(key, value) == null
                current >= value -> true
                else -> replace(key, current, value)
            }
        } while (!updated)
    }

    /**
     * Removes every entry, returning them with keys mapped by [keyTransform] and values mapped by [transform]. Each key
     * is removed atomically so an entry written mid-drain is either returned now or left for the next drain.
     */
    private inline fun <V : Any> ConcurrentHashMap<String, V>.drain(
        keyTransform: (String) -> String = { it },
        transform: (V) -> String,
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        keys.forEach { key ->
            remove(key)?.let { value ->
                result[keyTransform(key)] = transform(value)
            }
        }
        return result
    }
}
