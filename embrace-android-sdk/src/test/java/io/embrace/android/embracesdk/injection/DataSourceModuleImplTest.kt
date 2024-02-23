package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class DataSourceModuleImplTest {

    @Test
    fun `test default behavior`() {
        val module = DataSourceModuleImpl(
            FakeEssentialServiceModule(),
            FakeInitModule(),
            FakeOpenTelemetryModule()
        )
        assertNotNull(module.getDataSources())
        assertEquals(0, module.getDataSources().size)
    }
}
