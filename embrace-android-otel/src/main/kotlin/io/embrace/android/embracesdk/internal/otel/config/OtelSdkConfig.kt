package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.logs.DefaultLogRecordExporter
import io.embrace.android.embracesdk.internal.otel.logs.DefaultLogRecordProcessor
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.sdk.IdGenerator
import io.embrace.android.embracesdk.internal.otel.spans.DefaultSpanExporter
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanProcessor
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.semconv.AndroidAttributes
import io.opentelemetry.kotlin.semconv.DeviceAttributes
import io.opentelemetry.kotlin.semconv.OsAttributes
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import io.opentelemetry.kotlin.semconv.TelemetryAttributes
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

class OtelSdkConfig(
    spanSink: SpanSink,
    logSink: LogSink,
    val sdkName: String,
    val sdkVersion: String,
    val appVersion: String,
    val packageName: String,
    private val systemInfo: SystemInfo,
    private val sessionIdsProvider: () -> SessionIdsProvider? = { null },
    private val userIdProvider: () -> String? = { null },
    private val resourceAttributeOverrideEnabled: () -> Boolean = { false },
    private val processIdentifierProvider: () -> String = IdGenerator.Companion::generateLaunchInstanceId,
) {
    val resourceAction: AttributesMutator.() -> Unit
        get() = {
            getResourceAttributes().forEach { (key, value) ->
                setStringAttribute(key, value)
            }
        }

    /**
     * Unique ID generated for an instance of the app process and not related to the actual process ID assigned by the OS.
     * This allows us to explicitly relate all the sessions associated with a particular app launch rather than having the backend figure
     * this out by proximity for stitched sessions.
     */
    val processIdentifier: String by lazy {
        EmbTrace.trace("process-identifier-init", processIdentifierProvider)
    }

    val spanProcessor: SpanProcessor by lazy {
        EmbraceSpanProcessor(
            sessionIdsProvider,
            userIdProvider,
            processIdentifier,
            spanExporter,
        )
    }

    val logRecordProcessor: LogRecordProcessor by lazy {
        DefaultLogRecordProcessor(logRecordExporter)
    }

    /**
     * App-supplied resource attributes, in the order they were set
     */
    private val customAttributes = LinkedHashMap<String, String>()

    /**
     * The semantic-convention resource attributes that Embrace itself sets. These are authoritative and
     * are kept separate from app-supplied [customAttributes] so the two can be distinguished.
     */
    private val embraceResourceAttributes: Map<String, String>
        get() = linkedMapOf(
            ServiceAttributes.SERVICE_NAME to packageName,
            ServiceAttributes.SERVICE_VERSION to appVersion,
            OsAttributes.OS_NAME to systemInfo.osName,
            OsAttributes.OS_VERSION to systemInfo.osVersion,
            OsAttributes.OS_TYPE to systemInfo.osType,
            OsAttributes.OS_BUILD_ID to systemInfo.osBuild,
            AndroidAttributes.ANDROID_OS_API_LEVEL to systemInfo.androidOsApiLevel,
            DeviceAttributes.DEVICE_MANUFACTURER to systemInfo.deviceManufacturer,
            DeviceAttributes.DEVICE_MODEL_IDENTIFIER to systemInfo.deviceModel,
            DeviceAttributes.DEVICE_MODEL_NAME to systemInfo.deviceModel,
            TelemetryAttributes.TELEMETRY_DISTRO_NAME to sdkName,
            TelemetryAttributes.TELEMETRY_DISTRO_VERSION to sdkVersion,
        )

    /** Keys the SDK sets itself; app attributes that clash with these are overrides and don't count toward the cap. */
    private val embraceResourceAttributeKeys: Set<String> by lazy { embraceResourceAttributes.keys }

    private val externalSpanExporters = mutableListOf<SpanExporter>()
    private val externalSpanProcessors = mutableListOf<SpanProcessor>()
    private val externalLogExporters = mutableListOf<LogRecordExporter>()
    private val externalLogRecordProcessors = mutableListOf<LogRecordProcessor>()

    private var exportEnabled: Boolean = true
    private val exportCheck: () -> Boolean = { exportEnabled }

    private val spanExporter: SpanExporter by lazy {
        DefaultSpanExporter(
            spanSink = spanSink,
            externalExporters = externalSpanExporters.toList(),
            exportCheck = exportCheck,
        )
    }

    private val logRecordExporter: LogRecordExporter by lazy {
        DefaultLogRecordExporter(
            logSink = logSink,
            externalExporters = externalLogExporters.toList(),
            exportCheck = exportCheck,
        )
    }

    /**
     * The resource attributes used for the OTel SDK instance that combines both the ones set by Embrace and the app.
     * In case of a key clash, depending on value of the override feature flag, either the Embrace or the app set ones will be used.
     */
    fun getResourceAttributes(): Map<String, String> =
        if (resourceAttributeOverrideEnabled()) {
            embraceResourceAttributes + customAttributes
        } else {
            customAttributes + embraceResourceAttributes
        }

    /**
     * Records an app-supplied resource attribute if it's allowed
     */
    fun setResourceAttribute(key: String, value: String) {
        // Custom resource attribute keys can't start with the reserved prefix for internal Embrace attributes
        if (key.startsWith(EMB_ATTRIBUTE_PREFIX)) {
            return
        }

        // Allow if one of these conditions are true:
        // - Only updating the value of an existing custom attribute
        // - Attribute will be set by the Embrace SDK, so it's not a new attribute
        // - The cap has not been reached
        if (customAttributes.containsKey(key) ||
            key in embraceResourceAttributeKeys ||
            customResourceAttributeCount() < MAX_CUSTOM_RESOURCE_ATTRIBUTES
        ) {
            customAttributes[key] = value
        }
    }

    private fun customResourceAttributeCount(): Int = customAttributes.keys.count { it !in embraceResourceAttributeKeys }

    fun addSpanExporter(spanExporter: SpanExporter) {
        externalSpanExporters.add(spanExporter)
    }

    fun addSpanProcessor(spanProcessor: SpanProcessor) {
        externalSpanProcessors.add(spanProcessor)
    }

    fun getExternalSpanProcessors(): List<SpanProcessor> = externalSpanProcessors.toList()

    fun addLogExporter(logExporter: LogRecordExporter) {
        externalLogExporters.add(logExporter)
    }

    fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) {
        externalLogRecordProcessors.add(logRecordProcessor)
    }

    fun getExternalLogRecordProcessors(): List<LogRecordProcessor> = externalLogRecordProcessors.toList()

    fun hasConfiguredOtlpExport(): Boolean = externalLogExporters.isNotEmpty() || externalLogRecordProcessors.isNotEmpty() ||
        externalSpanExporters.isNotEmpty() || externalSpanProcessors.isNotEmpty()

    fun disableDataExport() {
        exportEnabled = false
    }

    private companion object {
        private const val EMB_ATTRIBUTE_PREFIX = "emb."
        private const val MAX_CUSTOM_RESOURCE_ATTRIBUTES = 20
    }
}
