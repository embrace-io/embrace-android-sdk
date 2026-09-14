package io.embrace.analysis.campaign

import io.embrace.analysis.device.Adb

/**
 * The installed package's dexopt state matches the cell's `compile` level (`profile`, `none`, `full`),
 * read from `dumpsys package dexopt`. Any other level accepts whatever state the package is in; the
 * status line is recorded either way.
 */
class CompileState(
    private val adb: Adb,
    private val serial: String,
    private val pkg: String,
    private val want: String?,
) : Invariant {

    override fun check(): Check {
        val status = dexoptStatus(adb.shell(serial, "dumpsys", "package", "dexopt"), pkg)
            ?: return Check(NAME, false, "no dexopt status for the package (is it installed?)")
        val ok = when (want) {
            "profile" -> "speed-profile" in status
            "none" -> "verify" in status || "run-from-apk" in status
            "full" -> "speed" in status && "speed-profile" !in status
            else -> true
        }
        return Check(NAME, ok, status)
    }

    companion object {
        const val NAME: String = "compile state"

        /** The `status=` line of the package's block in `dumpsys package dexopt`, or null. */
        fun dexoptStatus(dumpsys: String, pkg: String): String? {
            val lines = dumpsys.lines().map { it.trim() }
            val start = lines.indexOfFirst { pkg in it && it.startsWith("[") }
            if (start < 0) {
                return null
            }
            val block = lines.drop(start + 1).takeWhile { !(it.startsWith("[") && pkg !in it) }
            return block.firstOrNull { "status=" in it }
        }
    }
}
