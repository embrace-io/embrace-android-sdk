package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.fakes.FakeAttributesMutator
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.OpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class OTelApiDelegateTest(
    private val otelSdkMode: OtelSdkMode,
) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }

    private lateinit var bootstrapper: ModuleInitBootstrapper
    private lateinit var delegate: OTelApiDelegate
    private lateinit var cfg: OtelSdkConfig
    private lateinit var started: AtomicBoolean
    private lateinit var openTelemetryKotlin: OpenTelemetry

    @Before
    fun setUp() {
        bootstrapper = ModuleInitBootstrapper(
            FakeInitModule(otelSdkMode = OtelSdkMode.COMPAT),
            FakeOpenTelemetryModule(otelSdkMode = otelSdkMode),
        )
        bootstrapper.init(ApplicationProvider.getApplicationContext())
        cfg = bootstrapper.openTelemetryModule.otelSdkConfig

        started = AtomicBoolean(true)
        openTelemetryKotlin = LateBindingOpenTelemetry {
            if (started.get()) {
                bootstrapper.openTelemetryModule.otelSdkWrapper.openTelemetryKotlin
            } else {
                NoopOpenTelemetry
            }
        }
        delegate = OTelApiDelegate(openTelemetryKotlin)
    }

    @Test
    fun `add exporters after start`() {
        delegate.addSpanExporter(FakeSpanExporter())
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertFalse(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `add processors after start`() {
        delegate.addSpanProcessor(FakeSpanProcessor())
        delegate.addLogRecordProcessor(FakeLogRecordProcessor())
        assertFalse(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `get opentelemetry kotlin binds late`() {
        started.set(false)
        val otel = delegate.getOpenTelemetryKotlin()
        assertSame(openTelemetryKotlin, otel)
        val tracer = otel.tracerProvider.getTracer("test")
        assertFalse(tracer.startSpan("before").isRecording())

        started.set(true)
        assertTrue(tracer.startSpan("after").isRecording())
    }

    @Test
    fun `context captured before start binds late`() {
        started.set(false)
        val otel = delegate.getOpenTelemetryKotlin()
        val context = otel.context

        started.set(true)
        val span = otel.tracerProvider.getTracer("test").startSpan("span")
        val scope = context.root().storeSpan(span).attach()
        try {
            assertEquals(span.spanContext.traceId, context.implicit().extractSpan().spanContext.traceId)
        } finally {
            scope.detach()
        }
    }

    @Test
    fun `set resource attribute after sdk starts`() {
        delegate.setResourceAttribute("test", "foo")
        val attrs = FakeAttributesMutator().apply(cfg.resourceAction).attributes
        assertNull(attrs["test"])
    }
}
