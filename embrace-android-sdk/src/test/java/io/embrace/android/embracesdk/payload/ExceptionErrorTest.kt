package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.fakes.FakeClock
import io.mockk.unmockkAll
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

internal class ExceptionErrorTest {

    companion object {
        private lateinit var exceptionError: ExceptionError
        private lateinit var clock: FakeClock

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            clock = FakeClock()
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    @Test
    fun `serialize then deserialize default object`() {
        val obj = ExceptionError(false)
        assertJsonMatchesGoldenFile("exception_error_expected.json", obj)
        val other = deserializeJsonFromResource<ExceptionError>("exception_error_expected.json")
        assertEquals(obj, other)
    }

    @Test
    fun `test addException with strict mode disabled has a limit of 5 exceptions`() {
        exceptionError = ExceptionError(false)
        val throwable = Throwable("exceptions")
        exceptionError.addException(throwable, "state", clock)
        exceptionError.addException(throwable, "state", clock)
        exceptionError.addException(throwable, "state", clock)
        exceptionError.addException(throwable, "state", clock)
        exceptionError.addException(throwable, "state", clock)
        exceptionError.addException(throwable, "state", clock)

        assertEquals(exceptionError.exceptionErrors.size, 5)
        assertEquals(exceptionError.occurrences, 6)
    }

    @Test
    fun `test addException with strict mode enabled has a limit of 50 exceptions`() {
        exceptionError = ExceptionError(true)
        val throwable = Throwable("exceptions")

        repeat(50) {
            exceptionError.addException(throwable, "state", clock)
        }

        assertEquals(exceptionError.exceptionErrors.size, 50)
        assertEquals(exceptionError.occurrences, 50)
        exceptionError.addException(throwable, "state", clock)
        assertEquals(exceptionError.exceptionErrors.size, 50)
        assertEquals(exceptionError.occurrences, 51)
    }
}
