package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SdkCallCheckerTest {

    private val action = "foo"
    private lateinit var logger: FakeEmbLogger
    private lateinit var telemetryService: FakeTelemetryService
    private lateinit var checker: SdkCallChecker

    @Before
    fun setUp() {
        logger = FakeEmbLogger()
        telemetryService = FakeTelemetryService()
        checker = SdkCallChecker(logger, telemetryService)
    }

    @Test
    fun `test not started`() {
        logger.throwOnInternalError = false
        assertFalse(checker.started.get())
        assertFalse(checker.check(action))
        assertEquals(action, logger.sdkNotInitializedMessages.single().msg)
        assertEquals(action, telemetryService.apiCalls.single())
    }

    @Test
    fun `error message can be suppressed`() {
        logger.throwOnInternalError = false
        assertFalse(checker.started.get())
        assertFalse(checker.check(action, false))
        assertTrue(logger.sdkNotInitializedMessages.isEmpty())
        assertEquals(action, telemetryService.apiCalls.single())
    }

    @Test
    fun `test started`() {
        checker.started.set(true)
        assertTrue(checker.check(action))
        assertTrue(logger.sdkNotInitializedMessages.isEmpty())
        assertEquals(action, telemetryService.apiCalls.single())
    }
}
