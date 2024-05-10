package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.fakes.FakeClock
import io.mockk.unmockkAll
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

internal class LegacyExceptionErrorTest {

    companion object {
        private lateinit var exceptionError: LegacyExceptionError
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
        val obj = LegacyExceptionError()
        assertJsonMatchesGoldenFile("exception_error_expected.json", obj)
        val other = deserializeJsonFromResource<LegacyExceptionError>("exception_error_expected.json")
        assertEquals(obj, other)
    }

    @Test
    fun `test addException has a limit of 10 exceptions`() {
        exceptionError = LegacyExceptionError()
        val max = 10
        repeat(max + 1) {
            exceptionError.addException(Throwable("exceptions"), "state", clock)
        }

        assertEquals(exceptionError.exceptionErrors.size, max)
        assertEquals(exceptionError.occurrences, max + 1)
    }
}
