package io.embrace.startup.campaign

import io.embrace.startup.core.text.PyFormat
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * `verify_ab_arms.py`: prove that two A/B arms actually differ BEFORE spending hours of device time.
 *
 * A config-flag A/B builds one commit twice with a flag flipped. If the flag does not take effect,
 * both arms are the SAME program, the campaign runs perfectly, and "no significant difference" is
 * indistinguishable from a real null. The dex payload is the best available static check, with one
 * hard limit: Android builds are not byte-reproducible (275 vs 11,790 differing bytes between two
 * rebuilds of one tree), so "the dex differs" is necessary, not sufficient. Only
 * a CONTROL - a rebuild of arm A's own tree - gives a noise floor to compare against, and the
 * propagation gate (section deltas on device) remains the primary arm check.
 */
object ArmVerifier {

    data class Verdict(val exitCode: Int, val text: String)

    fun dexEntries(apk: Path): Map<String, ByteArray> = ZipFile(apk.toFile()).use { zip ->
        zip.entries().asSequence().filter { it.name.endsWith(".dex") }
            .associate { it.name to zip.getInputStream(it).use { s -> s.readBytes() } }
    }

    /** Differing byte positions over the common prefix plus the length difference. */
    fun diffBytes(x: ByteArray, y: ByteArray): Long {
        val n = minOf(x.size, y.size)
        var diff = 0L
        for (i in 0 until n) {
            if (x[i] != y[i]) diff++
        }
        return diff + kotlin.math.abs(x.size - y.size)
    }

    fun totalDiff(a: Map<String, ByteArray>, b: Map<String, ByteArray>): Long =
        (a.keys intersect b.keys).sorted().filter { !a.getValue(it).contentEquals(b.getValue(it)) }
            .sumOf { diffBytes(a.getValue(it), b.getValue(it)) }

    fun verify(aPath: Path, bPath: Path, controlPath: Path?): Verdict {
        val a = dexEntries(aPath)
        val b = dexEntries(bPath)
        val control = controlPath?.let { dexEntries(it) }
        val out = StringBuilder()
        fun line(s: String = "") = out.append(s).append('\n')
        line("arm A: $aPath\n       ${a.size} dex, ${a.values.sumOf { it.size.toLong() }} bytes")
        line("arm B: $bPath\n       ${b.size} dex, ${b.values.sumOf { it.size.toLong() }} bytes")
        line()
        var differing = 0
        var missing = 0
        (a.keys union b.keys).sorted().forEach { name ->
            val x = a[name]
            val y = b[name]
            if (x == null || y == null) {
                line("$name: present in only one arm - STRUCTURAL difference")
                differing++
                missing++
                return@forEach
            }
            if (x.contentEquals(y)) {
                line("$name: identical (${x.size} bytes)")
                return@forEach
            }
            differing++
            val nbytes = diffBytes(x, y)
            line(
                "$name: DIFFERS - $nbytes byte positions " +
                    "(${PyFormat.fixed(PERCENT * nbytes / maxOf(x.size, 1), PCT_DP)}%), sizes ${x.size} vs ${y.size}, " +
                    "sha ${sha12(x)} vs ${sha12(y)}",
            )
        }
        line()
        if (differing == 0) {
            line(
                "FAIL (conclusive): the dex payloads are byte-identical. The flag did NOT take effect " +
                    "- these two arms are the same program, and an A/B over them would measure nothing " +
                    "while reporting it as 'no significant difference'. DO NOT RUN.",
            )
            return Verdict(1, out.toString())
        }
        if (missing > 0) {
            line(
                "WARNING: a dex file is present in only one arm. That is more than a flag flip - " +
                    "confirm the two trees differ in only the config before running.",
            )
            return Verdict(1, out.toString())
        }
        val ab = totalDiff(a, b)
        if (control == null) {
            line(
                "INCONCLUSIVE: the arms differ ($ab byte positions), which is necessary but NOT " +
                    "sufficient - Android builds are not reproducible, so this may be entirely build " +
                    "noise. Re-run with a control (a second build of arm A's own tree) if the verdict " +
                    "matters, or confirm the flag on-device.",
            )
            return Verdict(0, out.toString())
        }
        val floor = totalDiff(a, control)
        line("noise floor from the control (arm A vs its own rebuild): $floor byte positions")
        line("arm A vs arm B:                                         $ab byte positions")
        if (ab <= floor) {
            line(
                "FAIL: the arm difference does not exceed the noise floor, so this method cannot " +
                    "resolve the flag at all. Verify on-device instead of guessing from the artefact.",
            )
            return Verdict(1, out.toString())
        }
        line(
            "PASS: the arm difference exceeds the build-noise floor, consistent with the config " +
                "injection having taken effect.",
        )
        return Verdict(0, out.toString())
    }

    private fun sha12(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(SHA_CHARS)

    private const val PERCENT = 100.0
    private const val PCT_DP = 4
    private const val SHA_CHARS = 12
}
