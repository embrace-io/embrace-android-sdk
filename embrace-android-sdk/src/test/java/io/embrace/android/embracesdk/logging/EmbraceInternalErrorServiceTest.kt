package io.embrace.android.embracesdk.logging

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.internal.clock.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class EmbraceInternalErrorServiceTest {

    private lateinit var service: InternalErrorService
    private lateinit var cfgService: ConfigService
    private lateinit var activityService: FakeProcessStateService
    private lateinit var cfg: RemoteConfig
    private val clock = Clock { 1509234092L }

    @Before
    fun setUp() {
        activityService = FakeProcessStateService()
        service = EmbraceInternalErrorService(clock)
        cfg = RemoteConfig()
        cfgService =
            FakeConfigService(dataCaptureEventBehavior = fakeDataCaptureEventBehavior { cfg })
    }

    @Test
    fun testExceptionReportingEnabled() {
        cfg = cfg.copy(internalExceptionCaptureEnabled = true)
        service.configService = cfgService
        service.handleInternalError(RuntimeException("Whoops!"))

        val error = checkNotNull(service.getCapturedData())
        assertEquals(1, error.occurrences)
        with(error.exceptionErrors.single()) {
            assertEquals(clock.now(), timestamp)

            // verify exc object
            val exc = exceptions?.single()
            assertEquals("Whoops!", exc?.message)
            assertEquals("java.lang.RuntimeException", exc?.name)
        }
    }

    @Test
    fun testExceptionReportingDisabled() {
        cfg = cfg.copy(internalExceptionCaptureEnabled = false)
        service.configService = cfgService
        service.handleInternalError(RuntimeException())
        assertNull(service.getCapturedData())
    }

    @Test
    fun testExceptionReportingUnknown() {
        service.handleInternalError(RuntimeException())
        val error = checkNotNull(service.getCapturedData())
        assertEquals(1, error.occurrences)
    }

    @Test
    fun testExceptionReset() {
        cfg = cfg.copy(internalExceptionCaptureEnabled = true)
        service.configService = cfgService
        service.handleInternalError(RuntimeException())

        val error = checkNotNull(service.getCapturedData())
        assertEquals(1, error.occurrences)

        service.cleanCollections()
        assertNull(service.getCapturedData())
    }

    @Test
    fun testIsInBackground() {
        activityService.isInBackground = true
        service.handleInternalError(RuntimeException("Whoops!"))

        val error = checkNotNull(service.getCapturedData())
        assertEquals(1, error.occurrences)
    }

    @Test
    fun testMultipleExceptionTypes() {
        service.handleInternalError(RuntimeException("Whoops!"))
        service.handleInternalError(IllegalStateException("Another!"))
        service.handleInternalError(IllegalStateException("Another 2!"))

        val error = checkNotNull(service.getCapturedData())
        assertEquals(3, error.occurrences)

        assertEquals("Whoops!", error.exceptionErrors[0].exceptions?.single()?.message)
        assertEquals("Another!", error.exceptionErrors[1].exceptions?.single()?.message)
        assertEquals("Another 2!", error.exceptionErrors[2].exceptions?.single()?.message)
    }

    @Test
    fun testExceptionMaxLimit() {
        repeat(12) { k ->
            service.handleInternalError(RuntimeException("Oh no $k"))
        }
        val err = checkNotNull(service.getCapturedData())
        assertEquals(12, err.occurrences)
        assertEquals(10, err.exceptionErrors.size)
    }
}
