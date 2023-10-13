package io.embrace.android.embracesdk.anr

import android.os.strictmode.Violation
import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.capture.strictmode.EmbraceStrictModeService
import io.embrace.android.embracesdk.clock.Clock
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceStrictModeServiceTest {

    private lateinit var configService: ConfigService
    private lateinit var service: EmbraceStrictModeService
    private val clock = Clock { 16900000000 }

    @Before
    fun setUp() {
        configService = FakeConfigService()
        service =
            EmbraceStrictModeService(configService, MoreExecutors.newDirectExecutorService(), clock)
    }

    @Test
    fun testCleanCollections() {
        service.addViolation(mockk(relaxed = true))
        service.cleanCollections()
        assertEquals(0, service.getCapturedData().size)
    }

    @Test
    fun testSessionEnd() {
        val violation = mockk<Violation>(relaxed = true) {
            every { message } returns "Whoops!"
        }
        service.addViolation(violation)
        val violations = service.getCapturedData()
        val obj = violations.single()
        assertTrue(obj.exceptionInfo.name.startsWith("android.os.strictmode.Violation"))
        assertEquals("Whoops!", obj.exceptionInfo.message)
        assertEquals(16900000000, obj.timestamp)
    }

    @Test
    fun testMaxSize() {
        repeat(200) {
            service.addViolation(mockk(relaxed = true))
        }
        assertEquals(25, service.getCapturedData().size)
    }
}
