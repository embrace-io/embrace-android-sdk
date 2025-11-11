package io.embrace.android.embracesdk.internal.instrumentation.crash

import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeInstrumentationModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class CrashModuleImplTest {

    @Test
    fun testDefaultImplementations() {
        val module = createCrashModule(
            FakeInitModule(),
            FakeConfigModule(),
            FakeInstrumentationModule(mockk())
        )
        assertNotNull(module.jvmCrashDataSource)
    }
}
