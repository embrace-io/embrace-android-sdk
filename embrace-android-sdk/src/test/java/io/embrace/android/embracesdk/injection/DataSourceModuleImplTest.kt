package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DataSourceModuleImplTest {

    @Test
    fun `test default behavior`() {
        val module = DataSourceModuleImpl(FakeEssentialServiceModule())
        val dataSource = module.placeholderDataSource
        assertNotNull(dataSource)
        assertTrue(module.getDataSources().contains(dataSource))
    }
}
