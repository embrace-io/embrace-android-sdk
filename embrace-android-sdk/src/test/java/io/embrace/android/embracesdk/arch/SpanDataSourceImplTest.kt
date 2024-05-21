package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.arch.limits.LimitStrategy
import io.embrace.android.embracesdk.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SpanDataSourceImplTest {

    @Test
    fun `capture data successfully`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst)
        val success = source.alterSessionSpan(inputValidation = { true }) {
            createSpan("test")
        }
        assertTrue(success)
        assertEquals(1, dst.createdSpans.size)
    }

    @Test
    fun `capture data threw exception`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst)
        val success = source.alterSessionSpan(inputValidation = { true }) {
            error("Whoops!")
        }
        assertFalse(success)
        assertEquals(0, dst.createdSpans.size)
    }

    @Test
    fun `capture data respects limits`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy { 2 })

        var count = 0
        repeat(4) {
            source.alterSessionSpan(inputValidation = { true }) {
                count++
            }
        }
        assertEquals(2, count)
    }

    @Test
    fun `capture data respects validation`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy { 2 })

        var count = 0
        repeat(4) {
            source.alterSessionSpan(inputValidation = { false }) {
                count++
            }
        }
        assertEquals(0, count)
    }

    @Test
    fun `start span succeeds`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst)
        val success = source.captureSpanData(true, inputValidation = { true }) {
            createSpan("test")
        }
        assertTrue(success)
        assertEquals(1, dst.createdSpans.size)
    }

    @Test
    fun `start span data threw exception`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst)
        val success = source.captureSpanData(true, inputValidation = { true }) {
            error("Whoops!")
        }
        assertFalse(success)
        assertEquals(0, dst.createdSpans.size)
    }

    @Test
    fun `start span respects limits`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy { 2 })

        var count = 0
        repeat(4) {
            source.captureSpanData(true, inputValidation = { true }) {
                count++
            }
        }
        assertEquals(2, count)
    }

    @Test
    fun `start span respects validation`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy { 2 })

        var count = 0
        repeat(4) {
            source.captureSpanData(true, inputValidation = { false }) {
                count++
            }
        }
        assertEquals(0, count)
    }

    @Test
    fun `stop span succeeeds`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst)
        val success = source.captureSpanData(false, inputValidation = { true }) {
            createSpan("test")
        }
        assertTrue(success)
        assertEquals(1, dst.createdSpans.size)
    }

    @Test
    fun `stop span data threw exception`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst)
        val success = source.captureSpanData(false, inputValidation = { true }) {
            error("Whoops!")
        }
        assertFalse(success)
        assertEquals(0, dst.createdSpans.size)
    }

    @Test
    fun `stop span does not increment limits`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy { 2 })

        var count = 0
        repeat(4) {
            source.captureSpanData(false, inputValidation = { true }) {
                count++
            }
        }
        assertEquals(4, count)
    }

    @Test
    fun `stop span respects validation`() {
        val dst = FakeSpanService()
        val source = FakeDataSourceImpl(dst, UpToLimitStrategy { 2 })

        var count = 0
        repeat(4) {
            source.captureSpanData(false, inputValidation = { false }) {
                count++
            }
        }
        assertEquals(0, count)
    }

    private class FakeDataSourceImpl(
        dst: FakeSpanService,
        limitStrategy: LimitStrategy = NoopLimitStrategy
    ) : SpanDataSourceImpl(dst, EmbLoggerImpl(), limitStrategy)
}
