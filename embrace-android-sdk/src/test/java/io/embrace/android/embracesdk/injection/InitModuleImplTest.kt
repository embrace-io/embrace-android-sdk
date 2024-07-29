package io.embrace.android.embracesdk.injection

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.injection.InitModuleImpl
import io.embrace.android.embracesdk.internal.telemetry.EmbraceTelemetryService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class InitModuleImplTest {

    @Test
    fun testInitModuleImplDefaults() {
        val initModule = InitModuleImpl()
        assertNotNull(initModule.clock)
        assertTrue(initModule.telemetryService is EmbraceTelemetryService)
        assertEquals(initModule.systemInfo, SystemInfo())
        assertEquals(initModule.processIdentifier.length, 16)
        assertNotNull(initModule.jsonSerializer)
    }

    @Test
    fun testInitModuleImplOverrideComponents() {
        val clock = FakeClock()
        val systemInfo = SystemInfo()
        val initModule = InitModuleImpl(
            clock = clock,
            systemInfo = systemInfo
        )
        assertSame(clock, initModule.clock)
        assertSame(systemInfo, initModule.systemInfo)
    }
}
