package io.embrace.analysis.reports

import io.embrace.analysis.common.json.StartupJson
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Files
import java.nio.file.Path

/**
 * A campaign directory: `pass1.json .. passN.json` (the `variance --json` datasets) and
 * `pass1-factors.json .. ` (the `outlier-factors` datasets), plus an optional `campaign.log`.
 *
 * Two loading conventions are used, both intentional: `hypothesis-tests` and `factors-report` stop
 * at the first missing pass number ([consecutive]); `cross-device-sections` skips gaps and pools
 * whatever exists ([present]).
 */
object Campaign {

    const val MAX_PASSES: Int = 19

    /** `pass1<suffix>` .. up to the first missing number. */
    fun consecutive(dir: Path, suffix: String): List<Path> {
        val out = ArrayList<Path>()
        for (i in 1..MAX_PASSES) {
            val p = dir.resolve("pass$i$suffix")
            if (!Files.exists(p)) break
            out.add(p)
        }
        return out
    }

    /** Every `pass<i><suffix>` that exists for i in 1..19, gaps skipped. */
    fun present(dir: Path, suffix: String): List<Path> =
        (1..MAX_PASSES).map { dir.resolve("pass$it$suffix") }.filter { Files.exists(it) }

    fun variancePasses(dir: Path): List<List<VarianceAnalysis.Record>> =
        consecutive(dir, ".json").map { readVariance(it) }

    fun factorsPasses(dir: Path): List<List<OutlierFactors.Record>> =
        consecutive(dir, "-factors.json").map { readFactors(it) }

    /** All iterations of all present passes across several directories pooled, in order. */
    fun pooledVariance(dirs: List<Path>): List<VarianceAnalysis.Record> =
        dirs.flatMap { dir -> present(dir, ".json").flatMap { readVariance(it) } }

    fun readVariance(file: Path): List<VarianceAnalysis.Record> =
        StartupJson.decodeFromString(ListSerializer(VarianceAnalysis.Record.serializer()), Files.readString(file))

    fun readFactors(file: Path): List<OutlierFactors.Record> =
        StartupJson.decodeFromString(ListSerializer(OutlierFactors.Record.serializer()), Files.readString(file))
}
