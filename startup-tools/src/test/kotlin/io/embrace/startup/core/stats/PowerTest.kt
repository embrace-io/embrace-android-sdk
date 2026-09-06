package io.embrace.startup.core.stats

import io.embrace.startup.Goldens
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sizing arithmetic and the inverse normal against `stats_synthetic.json`.
 *
 * Integer results (`ceil` outputs) are exact - the manifest warns that an off-by-one here means the
 * pre-ceil value sits within an ulp of an integer and deserves a look, not a tolerance. `ndtri`,
 * `minDetectableEffect` and `sigmaFromQuantileRatio` each end in one libm call and are compared at
 * relative 1e-12.
 */
class PowerTest {

    private val golden = Goldens.json("stats_synthetic.json")

    @Test
    fun `ndtri matches the Acklam approximation at every branch and both boundaries`() {
        val g = golden.getValue("_ndtri").jsonObject
        g.forEach { (key, want) ->
            Goldens.assertClose("ndtri($key)", want.jsonPrimitive.double, Normal.ndtri(key.toDouble()), Goldens.LIBM_TOLERANCE)
        }
        assertEquals(g.size, 15)
    }

    @Test
    fun `required n and minimum detectable effect match, including the design-effect and power variants`() {
        val rn = golden.getValue("required_n").jsonObject
        assertEquals(rn.getValue("cv15_effect5").jsonPrimitive.int, Power.requiredN(15.0, 5.0))
        assertEquals(rn.getValue("cv15_effect5_deff0.5").jsonPrimitive.int, Power.requiredN(15.0, 5.0, deff = 0.5))
        assertEquals(rn.getValue("cv15_effect5_deff3.2").jsonPrimitive.int, Power.requiredN(15.0, 5.0, deff = 3.2))
        assertEquals(rn.getValue("cv15_effect5_power0.5").jsonPrimitive.int, Power.requiredN(15.0, 5.0, power = 0.5))
        assertEquals(rn.getValue("cv30_effect2").jsonPrimitive.int, Power.requiredN(30.0, 2.0))
        assertNull(Power.requiredN(15.0, 0.0))

        val mde = golden.getValue("min_detectable_effect").jsonObject
        assertNull(Power.minDetectableEffect(0, 15.0))
        Goldens.assertClose(
            "n200_cv15",
            mde.getValue("n200_cv15").jsonPrimitive.double,
            Power.minDetectableEffect(200, 15.0)!!,
            Goldens.LIBM_TOLERANCE,
        )
        Goldens.assertClose(
            "n200_cv15_deff5",
            mde.getValue("n200_cv15_deff5").jsonPrimitive.double,
            Power.minDetectableEffect(200, 15.0, deff = 5.0)!!,
            Goldens.LIBM_TOLERANCE,
        )
        Goldens.assertClose(
            "n50_cv20_power0.5",
            mde.getValue("n50_cv20_power0.5").jsonPrimitive.double,
            Power.minDetectableEffect(50, 20.0, power = 0.5)!!,
            Goldens.LIBM_TOLERANCE,
        )
    }

    @Test
    fun `tail sizing formulas land on the same integers`() {
        val nq = golden.getValue("n_for_quantile").jsonObject
        assertEquals(nq.getValue("sigma0.3_p0.5_rel0.02").jsonPrimitive.int, Power.nForQuantile(0.3, 0.5, 0.02))
        assertEquals(nq.getValue("sigma0.4_p0.9_rel0.05").jsonPrimitive.int, Power.nForQuantile(0.4, 0.9, 0.05))
        assertEquals(
            nq.getValue("sigma0.4_p0.99_rel0.05_deff2").jsonPrimitive.int,
            Power.nForQuantile(0.4, 0.99, 0.05, deff = 2.0),
        )
        assertEquals(
            nq.getValue("sigma0.6_p0.95_rel0.1_z1.645").jsonPrimitive.int,
            Power.nForQuantile(0.6, 0.95, 0.1, confidenceZ = 1.645),
        )

        val ne = golden.getValue("n_for_exceedance_rate").jsonObject
        assertEquals(ne.getValue("rate0.05_abs0.01").jsonPrimitive.int, Power.nForExceedanceRate(0.05, 0.01))
        assertEquals(
            ne.getValue("rate0.05_abs0.01_deff3").jsonPrimitive.int,
            Power.nForExceedanceRate(0.05, 0.01, deff = 3.0),
        )
        assertEquals(ne.getValue("rate0.5_abs0.05").jsonPrimitive.int, Power.nForExceedanceRate(0.5, 0.05))
    }

    @Test
    fun `CUPED, sigma from quantile ratio, dilution and practical match`() {
        val cuped = golden.getValue("cuped_variance_reduction").jsonObject
        listOf(-0.5, -1.5, 0.0, 0.7, 1.5).forEach { rho ->
            val want = cuped.getValue(rho.toString()).jsonObject
            val got = Power.cupedVarianceReduction(rho)
            assertEquals("rho $rho removed", want.getValue("variance_removed").jsonPrimitive.double, got.varianceRemoved, 0.0)
            assertEquals("rho $rho multiplier", want.getValue("n_multiplier").jsonPrimitive.double, got.nMultiplier, 0.0)
        }

        val sigma = golden.getValue("sigma_from_quantile_ratio").jsonObject
        listOf(1.0, 1.5, 2.0, 3.7).forEach { ratio ->
            Goldens.assertClose(
                "sigma($ratio)",
                sigma.getValue(ratio.toString()).jsonPrimitive.double,
                Power.sigmaFromQuantileRatio(ratio),
                Goldens.LIBM_TOLERANCE,
            )
        }

        val dilution = golden.getValue("dilution").jsonObject
        assertEquals(dilution.getValue("share100_change3").jsonPrimitive.double, Power.dilution(100.0, 3.0), 0.0)
        assertEquals(dilution.getValue("share35_change-10").jsonPrimitive.double, Power.dilution(35.0, -10.0), 0.0)
        assertEquals(dilution.getValue("share4_change25").jsonPrimitive.double, Power.dilution(4.0, 25.0), 0.0)

        val practical = golden.getValue("practical").jsonObject
        assertEquals(practical.getValue("-4_vs_4").jsonPrimitive.boolean, Power.practical(-4.0, 4.0))
        assertEquals(practical.getValue("0_vs_0").jsonPrimitive.boolean, Power.practical(0.0, 0.0))
        assertEquals(practical.getValue("3.9_vs_4").jsonPrimitive.boolean, Power.practical(3.9, 4.0))
        assertEquals(practical.getValue("5_vs_4").jsonPrimitive.boolean, Power.practical(5.0, 4.0))
    }
}
