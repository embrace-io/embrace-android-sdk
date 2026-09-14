package io.embrace.analysis.campaign

import io.embrace.analysis.common.text.PyFormat
import io.embrace.analysis.device.Adb
import io.embrace.analysis.device.Topology

/**
 * The device is cool enough to measure: the hottest plausible HAL sensor is at or under the cell's gate,
 * unless the cell's thermal level is `hot`, where the heater owns the band. thermalservice only -
 * `dumpsys battery` is not a control input (some devices freeze it).
 *
 * The parser once required its `mValue=` match to START a comma-split part, which the first part never
 * does (it starts with `Temperature{`), so no sensor was ever read and this invariant could never pass.
 * It now matches `mValue=` anywhere in a `Temperature{...}` line.
 */
class Temperature(
    private val adb: Adb,
    private val serial: String,
    private val gateC: Double,
    private val band: String?,
) : Invariant {

    override fun check(): Check {
        val temps = parseTemperatures(adb.shell(serial, "dumpsys", "thermalservice"))
        val plausible = temps.filter { it > PLAUSIBLE_MIN_C && it < PLAUSIBLE_MAX_C }
        if (plausible.isEmpty()) {
            return Check(NAME, false, "no plausible thermalservice sensor values (do not fall back to battery)")
        }
        val hottest = plausible.max()
        val shown = PyFormat.fixed(hottest, 1)
        return when {
            band == "hot" -> Check(NAME, true, "hot cell: hottest sensor $shown C (band enforced by the heater)")
            hottest > gateC -> Check(NAME, false, "hottest sensor $shown C > gate ${pyNum(gateC)} C")
            else -> Check(NAME, true, "hottest sensor $shown C <= gate ${pyNum(gateC)} C")
        }
    }

    companion object {
        const val NAME: String = "temperature"
        private const val PLAUSIBLE_MIN_C = 10.0
        private const val PLAUSIBLE_MAX_C = 100.0
        private val M_VALUE = Regex("mValue=([\\-\\d.]+)")

        /**
         * Every `mValue=` on a `Temperature{...}` line of the "Current temperatures from HAL" block.
         * Scanning the whole dump is wrong - on some devices it also contains a "Cached temperatures"
         * block holding peak values (72 °C at idle), and a gate reading those would never open after a hot pass.
         */
        fun parseTemperatures(dumpsys: String): List<Double> =
            Topology.halTemperatureLines(dumpsys).map { it.trim() }
                .filter { it.startsWith("Temperature{") }
                .flatMap { line -> M_VALUE.findAll(line).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList() }

        /** Python `str()` of the gate: `32.0` stays `32.0`. */
        private fun pyNum(x: Double): String {
            return if (x == Math.rint(x)) {
                PyFormat.fixed(x, 1)
            } else {
                x.toString()
            }
        }
    }
}
