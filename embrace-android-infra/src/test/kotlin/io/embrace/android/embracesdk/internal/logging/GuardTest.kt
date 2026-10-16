package io.embrace.android.embracesdk.internal.logging

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class GuardTest {

    private val type = InternalErrorType.PublicApiFail
    private val exc = IllegalStateException("whoops")
    private lateinit var logger: FakeInternalLogger

    @Before
    fun setUp() {
        logger = FakeInternalLogger(throwOnInternalError = false)
    }

    @Test
    fun `guardSdkCode returns value when action succeeds`() {
        assertEquals(1, logger.guardSdkCode(type, 0) { 1 })
        assertTrue(logger.internalErrorMessages.isEmpty())
    }

    @Test
    fun `guardSdkCode tracks SDK failure as internal error`() {
        assertEquals(0, logger.guardSdkCode(type, 0) { throw exc })
        assertSame(exc, logger.internalErrorMessages.single().throwable)
        assertTrue(logger.userCodeErrorMessages.isEmpty())
    }

    @Test
    fun `guardSdkCode reports guardUserCode failure as user code error`() {
        logger.guardSdkCode(type) {
            guardUserCode { throw exc }
        }
        assertSame(exc, logger.userCodeErrorMessages.single().throwable)
        assertTrue(logger.internalErrorMessages.isEmpty())
    }

    @Test
    fun `Ignored type reports nothing`() {
        assertEquals(0, logger.guardSdkCode(InternalErrorType.Ignored, 0) { throw exc })
        logger.guardSdkCode(InternalErrorType.Ignored) { guardUserCode { throw exc } }
        assertTrue(logger.internalErrorMessages.isEmpty())
        assertTrue(logger.userCodeErrorMessages.isEmpty())
    }

    @Test
    fun `nested guardUserCode is unwrapped once`() {
        logger.guardSdkCode(type) {
            guardUserCode { guardUserCode { throw exc } }
        }
        assertSame(exc, logger.userCodeErrorMessages.single().throwable)
    }
}
