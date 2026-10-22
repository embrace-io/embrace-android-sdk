package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.fakes.FakeAttributesMutator
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.export.immediateExportDispatcher
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.semconv.AndroidAttributes
import io.opentelemetry.kotlin.semconv.DeviceAttributes
import io.opentelemetry.kotlin.semconv.OsAttributes
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import io.opentelemetry.kotlin.semconv.TelemetryAttributes
import org.junit.Assert.assertEquals
import org.junit.Test

internal class OtelSdkConfigTest {

    @Test
    fun `check resource attributes`() {
        val systemInfo = SystemInfo(
            osName = "testOS",
            osType = "testOsType",
            osBuild = "testBuild",
            osVersion = "testOsVersionName",
            androidOsApiLevel = "99",
            deviceManufacturer = "testManufacturer",
            deviceModel = "testModel",
        )

        val configuration = OtelSdkConfig(
            spanRepository = SpanRepository(FakeErrorHandler()),
            logSink = LogSinkImpl(),
            sdkName = "sdk",
            sdkVersion = "1.0",
            appVersion = "2.5.1",
            packageName = "com.test.app",
            systemInfo = systemInfo,
            uuidSource = TestUuidSource(),
            externalExportDispatcher = immediateExportDispatcher(),
        )

        val attrs = FakeAttributesMutator().apply(configuration.resourceAction).attributes
        val expected = mapOf(
            ServiceAttributes.SERVICE_NAME to configuration.packageName,
            ServiceAttributes.SERVICE_VERSION to configuration.appVersion,
            TelemetryAttributes.TELEMETRY_DISTRO_NAME to configuration.sdkName,
            TelemetryAttributes.TELEMETRY_DISTRO_VERSION to configuration.sdkVersion,
            OsAttributes.OS_NAME to systemInfo.osName,
            OsAttributes.OS_VERSION to systemInfo.osVersion,
            OsAttributes.OS_TYPE to systemInfo.osType,
            OsAttributes.OS_BUILD_ID to systemInfo.osBuild,
            AndroidAttributes.ANDROID_OS_API_LEVEL to systemInfo.androidOsApiLevel,
            DeviceAttributes.DEVICE_MANUFACTURER to systemInfo.deviceManufacturer,
            DeviceAttributes.DEVICE_MODEL_IDENTIFIER to systemInfo.deviceModel,
            DeviceAttributes.DEVICE_MODEL_NAME to systemInfo.deviceModel,
        ).mapKeys { it.key }
        assertEquals(expected, attrs)
    }

    @Test
    fun `external processors are gated by disableDataExport`() {
        val spanProcessor = FakeSpanProcessor()
        val logProcessor = FakeLogRecordProcessor()
        val configuration = config(
            ExternalOtelConfig().apply {
                addSpanProcessor(spanProcessor)
                addLogRecordProcessor(logProcessor)
            },
        )
        val span = FakeReadWriteSpan(FakeSpan(name = "span"))
        val log = FakeReadWriteLogRecord(body = "log")
        val context = NoopOpenTelemetry.context.implicit()
        configuration.externalSpanProcessors.single().onEnd(span)
        configuration.externalLogRecordProcessors.single().onEmit(log, context)
        configuration.disableDataExport()
        configuration.externalSpanProcessors.single().onEnd(span)
        configuration.externalLogRecordProcessors.single().onEmit(log, context)
        assertEquals(listOf("span"), spanProcessor.endedSpanNames)
        assertEquals(listOf("log"), logProcessor.processedLogBodies)
    }

    @Test
    fun `close shuts down external exporters and processors`() {
        val spanExporter = FakeSpanExporter()
        val logExporter = FakeLogRecordExporter()
        val spanProcessor = FakeSpanProcessor()
        val logProcessor = FakeLogRecordProcessor()
        val configuration = config(
            ExternalOtelConfig().apply {
                addSpanExporter(spanExporter)
                addLogExporter(logExporter)
                addSpanProcessor(spanProcessor)
                addLogRecordProcessor(logProcessor)
            },
        )
        configuration.close()
        assertEquals(1, spanExporter.shutdownCount)
        assertEquals(1, logExporter.shutdownCount)
        assertEquals(1, spanProcessor.shutdownCount)
        assertEquals(1, logProcessor.shutdownCount)
    }

    private fun config(externalConfig: ExternalOtelConfig) = OtelSdkConfig(
        spanRepository = SpanRepository(FakeErrorHandler()),
        logSink = LogSinkImpl(),
        sdkName = "sdk",
        sdkVersion = "1.0",
        appVersion = "2.5.1",
        packageName = "com.test.app",
        systemInfo = SystemInfo(),
        uuidSource = TestUuidSource(),
        externalExportDispatcher = immediateExportDispatcher(),
        externalConfig = externalConfig,
    )
}
