package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.fakes.FakeAttributesMutator
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.export.immediateExportDispatcher
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.state.StateExportMode
import io.embrace.android.embracesdk.internal.otel.state.StateTelemetry
import io.embrace.android.embracesdk.internal.otel.state.markedAsStateReplay
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.StateRecorder
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.semconv.AndroidAttributes
import io.opentelemetry.kotlin.semconv.DeviceAttributes
import io.opentelemetry.kotlin.semconv.LogAttributes
import io.opentelemetry.kotlin.semconv.OsAttributes
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import io.opentelemetry.kotlin.semconv.TelemetryAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            logSink = LogSinkImpl(FakeErrorHandler()),
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
        val context = NoopOpenTelemetry.context.current()
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

    @Test
    fun `the process identifier is in the resource only when state telemetry is supplied`() {
        fun resource(stateTelemetry: StateTelemetry?) = FakeAttributesMutator().apply(
            OtelSdkConfig(
                spanRepository = SpanRepository(FakeErrorHandler()),
                logSink = LogSinkImpl(FakeErrorHandler()),
                sdkName = "sdk",
                sdkVersion = "1.0",
                appVersion = "2.5.1",
                packageName = "com.test.app",
                systemInfo = SystemInfo(),
                uuidSource = TestUuidSource(),
                processIdentifierProvider = { "process-id" },
                externalExportDispatcher = immediateExportDispatcher(),
                stateTelemetry = stateTelemetry,
            ).resourceAction,
        ).attributes

        val telemetry = StateTelemetry(StateRecorder(FakeClock()), exportMode = { StateExportMode.ASSOCIATION })

        assertEquals("process-id", resource(telemetry)[AppStateKeys.PROCESS_IDENTIFIER])
        assertFalse(resource(null).containsKey(AppStateKeys.PROCESS_IDENTIFIER))
    }

    @Test
    fun `a replayed record skips Embrace's store whenever the model is on and the app receives logs, whichever steps are on`() {
        fun emitReplay(stateTelemetry: StateTelemetry?, receivesLogs: Boolean = true): Triple<Int, Int, Boolean> {
            val sink = LogSinkImpl(FakeErrorHandler())
            val external = FakeLogRecordExporter()
            val configuration = OtelSdkConfig(
                spanRepository = SpanRepository(FakeErrorHandler()),
                logSink = sink,
                sdkName = "sdk",
                sdkVersion = "1.0",
                appVersion = "2.5.1",
                packageName = "com.test.app",
                systemInfo = SystemInfo(),
                uuidSource = TestUuidSource(),
                externalExportDispatcher = immediateExportDispatcher(),
                externalConfig = ExternalOtelConfig().apply {
                    if (receivesLogs) {
                        addLogExporter(external)
                    }
                },
                stateTelemetry = stateTelemetry,
            )
            val log = FakeReadWriteLogRecord(eventName = AppStateKeys.CHANGE_EVENT_NAME)
            val replayContext = fakeOpenTelemetry(OtelSdkMode.REGULAR).context.root().markedAsStateReplay()

            configuration.logRecordProcessor.onEmit(log, replayContext)

            return Triple(
                sink.logsForNextBatch().size,
                external.exportedLogs.size,
                log.attributes.containsKey(LogAttributes.LOG_RECORD_UID),
            )
        }

        fun telemetry(associationEnabled: Boolean) = StateTelemetry(
            StateRecorder(FakeClock()),
            exportMode = { StateExportMode.ASSOCIATION },
            associationEnabled = associationEnabled,
        )

        // replayed: only the app's exporter gets it, and it is not given a log id, whichever steps are on
        assertEquals(Triple(0, 1, false), emitReplay(telemetry(associationEnabled = true)))
        assertEquals(Triple(0, 1, false), emitReplay(telemetry(associationEnabled = false)))
        // the model is off: the mark means nothing, so it is an ordinary log that is stored and exported
        assertEquals(Triple(1, 1, true), emitReplay(null))
        // nothing receives logs, so nothing is replayed and no log's context is looked at: it is an ordinary log
        assertEquals(Triple(1, 0, true), emitReplay(telemetry(associationEnabled = true), receivesLogs = false))
    }

    private fun config(externalConfig: ExternalOtelConfig) = OtelSdkConfig(
        spanRepository = SpanRepository(FakeErrorHandler()),
        logSink = LogSinkImpl(FakeErrorHandler()),
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
