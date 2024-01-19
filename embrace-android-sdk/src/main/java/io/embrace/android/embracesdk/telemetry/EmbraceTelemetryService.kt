package io.embrace.android.embracesdk.telemetry

import io.embrace.android.embracesdk.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.internal.spans.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.spans.toEmbraceStorageAttributeName
import io.embrace.android.embracesdk.internal.spans.toEmbraceUsageAttributeName
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for tracking usage of public APIs, and different internal metrics about the app.
 */
internal class EmbraceTelemetryService(
    private val okHttpReflectionFacade: OkHttpReflectionFacade = OkHttpReflectionFacade()
) : TelemetryService {

    private val usageCountMap = ConcurrentHashMap<String, Int>()
    private val fileToSizeMap = ConcurrentHashMap<String, Int>()
    private val appAttributes: Map<String, String> by lazy { computeAppAttributes() }

    override fun onPublicApiCalled(name: String) {
        synchronized(usageCountMap) {
            usageCountMap[name] = (usageCountMap[name] ?: 0) + 1
        }
    }

    override fun logStorageTelemetry(fileToSizeMap: Map<String, Int>) {
        this.fileToSizeMap.putAll(fileToSizeMap)
    }

    override fun getAndClearTelemetryAttributes(): Map<String, String> {
        return getAndClearUsageCountTelemetry()
            .plus(getAndClearStorageTelemetry())
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

    private fun getAndClearStorageTelemetry(): Map<String, String> {
        val storageTelemetryMap = fileToSizeMap.entries.associate {
            it.key.toEmbraceStorageAttributeName() to it.value.toString()
        }
        fileToSizeMap.clear()
        return storageTelemetryMap
    }

    /**
     * Interesting attributes about the running app environment. These should be the same for every session, so we only compute them once.
     */
    private fun computeAppAttributes(): Map<String, String> {
        val appAttributesMap = mutableMapOf<String, String>()

        appAttributesMap["okhttp3".toEmbraceAttributeName()] = okHttpReflectionFacade.hasOkHttp3().toString()

        val okhttp3Version = okHttpReflectionFacade.getOkHttp3Version()
        if (okhttp3Version.isNotEmpty()) {
            appAttributesMap["okhttp3_on_classpath".toEmbraceAttributeName()] = okhttp3Version
        }

        appAttributesMap["kotlin_on_classpath".toEmbraceAttributeName()] =
            runCatching { KotlinVersion.CURRENT.toString() }.getOrDefault("unknown")

        appAttributesMap["is_emulator".toEmbraceAttributeName()] =
            runCatching { EmbraceMetadataService.isEmulator().toString() }.getOrDefault("unknown")

        return appAttributesMap
    }
}
