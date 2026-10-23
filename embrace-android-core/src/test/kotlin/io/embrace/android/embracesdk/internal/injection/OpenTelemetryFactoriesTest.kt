package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.config.resolved.SensitiveKeysConfig
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpanImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OpenTelemetryFactoriesTest {

    private val initServices = TestInitServices(otelSdkMode = OtelSdkMode.COMPAT)
    private val configService = FakeConfigService()

    @Test
    fun `factory defaults`() {
        val spanRepository = SpanRepository(FakeErrorHandler())
        assertNotNull(
            createOtelSdkConfig(
                spanRepository = spanRepository,
                logSink = LogSinkImpl(),
                instrumentedConfig = initServices.instrumentedConfig,
                systemInfo = initServices.systemInfo,
                uuidSource = initServices.uuidSource,
                errorHandler = initServices.errorHandler,
            ),
        )
        val span = createCurrentSessionPartSpan(
            openTelemetryClock = EmbClock(initServices.clock),
            telemetryService = initServices.telemetryService,
            spanRepository = spanRepository,
            tracerSupplier = { error("unused") },
            openTelemetrySupplier = { error("unused") },
            embraceSpanFactorySupplier = { error("unused") },
            uuidSource = initServices.uuidSource,
            otelBehaviorSupplier = { configService.otelBehavior },
            customBreadcrumbLimitSupplier = { 100 },
        )
        assertTrue(span is CurrentSessionPartSpanImpl)
    }

    @Test
    fun `redaction function redacts sensitive keys`() {
        val redact =
            createRedactionFunction(SensitiveKeysBehavior(SensitiveKeysConfig(denylist = { listOf("password") })))
        assertEquals(REDACTED_LABEL, redact("password", "secret"))
        assertEquals("value", redact("other", "value"))
    }
}
