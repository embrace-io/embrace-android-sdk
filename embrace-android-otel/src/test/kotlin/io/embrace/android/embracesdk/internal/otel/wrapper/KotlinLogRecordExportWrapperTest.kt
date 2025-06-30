package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.android.embracesdk.fakes.FakeOtelJavaLogRecordExporter
import org.junit.Test

class KotlinLogRecordExportWrapperTest {

    @Test
    fun export() {
        val impl = FakeOtelJavaLogRecordExporter()
        val wrapper = KotlinLogRecordExportWrapper(impl)
        TODO("Not yet implemented")
    }
}
