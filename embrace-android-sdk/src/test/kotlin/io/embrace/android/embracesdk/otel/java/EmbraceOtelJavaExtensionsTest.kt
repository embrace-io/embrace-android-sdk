package io.embrace.android.embracesdk.otel.java

import io.embrace.android.embracesdk.fakes.FakeOtelJavaLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeOtelJavaLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeOtelJavaSpanExporter
import io.embrace.android.embracesdk.fakes.FakeOtelJavaSpanProcessor
import io.embrace.android.embracesdk.fakes.ThrowingOTelConfig
import io.embrace.android.embracesdk.fakes.ThrowingOpenTelemetrySdkApi
import io.opentelemetry.api.OpenTelemetry
import org.junit.Assert.assertSame
import org.junit.Test

internal class EmbraceOtelJavaExtensionsTest {

    @Test
    fun `failures adding java exporters and processors are not thrown`() {
        with(ThrowingOTelConfig()) {
            addJavaSpanExporter(FakeOtelJavaSpanExporter())
            addJavaSpanProcessor(FakeOtelJavaSpanProcessor())
            addJavaLogRecordExporter(FakeOtelJavaLogRecordExporter())
            addJavaLogRecordProcessor(FakeOtelJavaLogRecordProcessor())
        }
    }

    @Test
    fun `failure creating java opentelemetry returns noop`() {
        assertSame(OpenTelemetry.noop(), ThrowingOpenTelemetrySdkApi().getJavaOpenTelemetry())
    }
}
