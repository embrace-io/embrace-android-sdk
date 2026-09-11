package io.embrace.analysis.fixtures

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Goldens]' own comparison idioms, since every other test in the suite trusts them: the relative
 * tolerance boundary in [Goldens.assertClose], its NaN-vs-NaN equality, and the JSON null / Python
 * `"NaN"` string mapping in [Goldens.doubleOrNull].
 */
class GoldensTest {

    @Test
    fun `assertClose passes exactly at the relative tolerance and fails just beyond it`() {
        val want = 8.0
        val rel = 0.125

        // tol = rel * want = 1.0 exactly (both are exact binary fractions), so want + 1.0 sits exactly
        // on the boundary and nextUp of that is the smallest possible step beyond it.
        Goldens.assertClose("at tolerance", want, want + 1.0, rel)

        val error = runCatching {
            Goldens.assertClose("beyond tolerance", want, Math.nextUp(want + 1.0), rel)
        }.exceptionOrNull()
        assertTrue(error is AssertionError)
    }

    @Test
    fun `assertClose treats NaN vs NaN as equal`() {
        Goldens.assertClose("nan", Double.NaN, Double.NaN)
    }

    @Test
    fun `doubleOrNull maps JSON null and the string NaN as documented, and reads a number otherwise`() {
        assertNull(Goldens.doubleOrNull(null))
        assertNull(Goldens.doubleOrNull(JsonNull))
        assertTrue(Goldens.doubleOrNull(JsonPrimitive("NaN"))!!.isNaN())
        assertEquals(3.5, Goldens.doubleOrNull(JsonPrimitive(3.5))!!, 0.0)
    }
}
