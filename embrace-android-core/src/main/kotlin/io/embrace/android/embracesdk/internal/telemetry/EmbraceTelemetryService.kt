package io.embrace.android.embracesdk.internal.telemetry

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.isEmulator
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceUsageAttributeName
import io.embrace.android.embracesdk.semconv.EmbTelemetryAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for tracking usage of public APIs, and different internal metrics about the app.
 */
internal class EmbraceTelemetryService(
    private val systemInfo: SystemInfo,
) : TelemetryService {

    private val okHttpReflectionFacade: OkHttpReflectionFacade = OkHttpReflectionFacade()
    private val usageCountMap = ConcurrentHashMap<String, Int>()
    private val storageTelemetryMap = ConcurrentHashMap<String, String>()
    private val appliedLimitCountMap = ConcurrentHashMap<String, Int>()
    private val appAttributes: Map<String, String> by lazy { computeAppAttributes() }

    override fun onPublicApiCalled(name: String) {
        synchronized(usageCountMap) {
            usageCountMap[name] = (usageCountMap[name] ?: 0) + 1
        }
    }

    override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {
        this.storageTelemetryMap.putAll(storageTelemetry)
    }

    override fun trackAppliedLimit(telemetryType: String, limitType: AppliedLimitType) {
        val id = "applied_limit.$telemetryType.${limitType.attributeName}"
        val key = "emb.private.$id"

        do {
            val current = appliedLimitCountMap[key]
            val updated = if (current == null) {
                appliedLimitCountMap.putIfAbsent(key, 1) == null
            } else {
                appliedLimitCountMap.replace(key, current, current + 1)
            }
        } while (!updated)
    }

    override fun getAndClearTelemetryAttributes(): Map<String, String> {
        return getAndClearUsageCountTelemetry()
            .plus(getAndClearStorageTelemetry())
            .plus(getAndClearAppliedLimitTelemetry())
            .plus(appAttributes)
    }

    private fun getAndClearUsageCountTelemetry(): Map<String, String> {
        synchronized(usageCountMap) {
            val usageCountTelemetryMap = usageCountMap.entries.associate {
                it.key.toEmbraceUsageAttributeName() to it.value.toString()
            }
            usageCountMap.clear()
            return usageCountTelemetryMap
        }
    }

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
     * Removes every entry, returning them with values mapped by [transform]. Each key is removed atomically rather than the
     * map being copied then cleared, so an entry written mid-drain is either returned now or left for the next drain.
     */
    private inline fun <V : Any> ConcurrentHashMap<String, V>.drain(transform: (V) -> String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        keys.forEach { key ->
            remove(key)?.let { value ->
                result[key] = transform(value)
            }
        }
        return result
    }
}
