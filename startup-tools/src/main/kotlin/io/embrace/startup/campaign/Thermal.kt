package io.embrace.startup.campaign

import io.embrace.startup.core.text.PyFormat
import io.embrace.startup.device.Adb
import io.embrace.startup.device.Topology
import java.io.IOException

/**
 * Device temperature readings for campaigns.
 *
 * Judge thermal state from SILICON (`dumpsys thermalservice`), never from battery: battery
 * temperature understates silicon by 30 °C or more under load, and some devices report a constant
 * battery temperature regardless of load. The battery line is still logged, for the record.
 */
object Thermal {

    /** (sensor name, °C) for up to three CPU-type sensors (mType=0) then one SKIN sensor (mType=3), in HAL order. */
    fun parseSiliconTemps(text: String?): List<Pair<String, Double>> {
        if (text.isNullOrEmpty()) return emptyList()
        val cpu = ArrayList<Pair<String, Double>>()
        var skin: Pair<String, Double>? = null
        Topology.halTemperatureLines(text).mapNotNull { SENSOR.find(it) }.forEach { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return@forEach
            val type = m.groupValues[2].toInt()
            val name = m.groupValues[3].trim()
            if (type == TYPE_CPU && cpu.size < MAX_CPU_SENSORS) {
                cpu.add(name to value)
            } else if (type == TYPE_SKIN && skin == null) {
                skin = name to value
            }
        }
        return cpu + listOfNotNull(skin)
    }

    /** `"{temp}C level={level}"` from `dumpsys battery`, or an error marker - never throws. */
    fun battery(adb: Adb, serial: String): String = try {
        val out = adb.shell(serial, "dumpsys", "battery")
        var temp: Double? = null
        var level: String? = null
        out.lines().map { it.trim() }.forEach { s ->
            if (s.startsWith("temperature:")) {
                temp = s.substringAfter(":").trim().toIntOrNull()?.let { it / TENTHS }
            }
            if (s.startsWith("level:")) {
                level = s.substringAfter(":").trim()
            }
        }
        "${temp}C level=$level"
    } catch (e: IOException) {
        "battery-err:$e"
    }

    /** `"silicon: name=31.0 name=36.2"` or `"silicon: n/a"`. */
    fun silicon(adb: Adb, serial: String): String = try {
        val pairs = parseSiliconTemps(adb.shell(serial, "dumpsys", "thermalservice"))
        if (pairs.isEmpty()) {
            "silicon: n/a"
        } else {
            "silicon: " + pairs.joinToString(" ") { "${it.first}=${PyFormat.fixed(it.second, 1)}" }
        }
    } catch (e: IOException) {
        "silicon-err:$e"
    }

    /** Hottest silicon sensor, or null if unreadable. */
    fun maxSilicon(adb: Adb, serial: String): Double? = runCatching {
        parseSiliconTemps(adb.shell(serial, "dumpsys", "thermalservice")).maxOfOrNull { it.second }
    }.getOrNull()

    private val SENSOR = Regex("mValue\\s*=\\s*([\\-\\d.]+).*mType\\s*=\\s*(\\d+).*mName\\s*=\\s*([^,}]+)")
    private const val TYPE_CPU = 0
    private const val TYPE_SKIN = 3
    private const val MAX_CPU_SENSORS = 3
    private const val TENTHS = 10.0
}
