package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.fakes.FakeAttributesMutator
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class OTelApiDelegateTest(
    private val otelSdkMode: OtelSdkMode,
) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }

    private lateinit var otelModule: FakeOpenTelemetryModule
    private lateinit var delegate: OTelApiDelegate
    private lateinit var cfg: OtelSdkConfig

    @Before
    fun setUp() {
        otelModule = FakeOpenTelemetryModule(otelSdkMode = otelSdkMode)
        cfg = otelModule.otelSdkConfig

        delegate = OTelApiDelegate(lazy { otelModule.otelSdkWrapper })
    }

    @Test
    fun `add exporters after start`() {
        delegate.addSpanExporter(FakeSpanExporter())
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertFalse(otelModule.otelSdkConfig.externalConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `add processors after start`() {
        delegate.addSpanProcessor(FakeSpanProcessor())
        delegate.addLogRecordProcessor(FakeLogRecordProcessor())
        assertFalse(otelModule.otelSdkConfig.externalConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `get opentelemetry kotlin returns sdk instance`() {
        assertSame(
            otelModule.otelSdkWrapper.openTelemetryKotlin,
            delegate.getOpenTelemetryKotlin(),
        )
    }

    @Test
    fun `set resource attribute after sdk starts`() {
        delegate.setResourceAttribute("test", "foo")
        val attrs = FakeAttributesMutator().apply(cfg.resourceAction).attributes
        assertNull(attrs["test"])
    }
}
