package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.android.embracesdk.fakes.FakeOtelJavaSpanExporter
import org.junit.Test

class KotlinSpanExportWrapperTest {

    @Test
    fun export() {
        val impl = FakeOtelJavaSpanExporter()
        val wrapper = KotlinSpanExportWrapper(impl)
        TODO("Not yet implemented")
    }
}
