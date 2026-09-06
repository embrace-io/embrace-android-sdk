package io.embrace.startup.analysis

import io.embrace.startup.core.json.StartupJson
import io.embrace.startup.core.text.Csv
import io.embrace.startup.perfetto.TraceGoldens
import io.embrace.startup.perfetto.TraceProcessor
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

/**
 * `variance` and `outlier-factors` against the Python's frozen output per device. The Python's
 * `extract` was driven with the same saved `trace_processor` stdout the Kotlin parses here, so the
 * JSON datasets must be equal as trees and the variance report equal line for line.
 */
class VarianceAnalysisTest {

    @Test
    fun `variance report and JSON dataset reproduce variance_analysis py per device`() {
        val goldens = TraceGoldens.all()
        assumeTrue("trace goldens not present", goldens.isNotEmpty())
        var compared = 0
        goldens.groupBy { it.device }.forEach { (device, traces) ->
            val wantReport = TraceGoldens.cliStdout("variance.$device.stdout.txt") ?: return@forEach
            val wantJson = TraceGoldens.root().resolve("_campaign").resolve(device).resolve("pass1.json")
            assumeTrue(Files.isRegularFile(wantJson))
            val data = traces.sortedBy { StartupAnalysis.iterIndex(it.traceFileName) }.map { g ->
                VarianceAnalysis.recordOf(g.traceFileName, TraceProcessor.triplesOf(Csv.parse(g.csv("variance_metrics"))))
            }
            val got = VarianceAnalysis.report(data)
            assertEquals("$device report", wantReport.trimEnd(), got.trimEnd())

            val gotTree = StartupJson.parseToJsonElement(
                StartupJson.encodeToString(ListSerializer(VarianceAnalysis.Record.serializer()), data),
            )
            val wantTree = StartupJson.parseToJsonElement(Files.readString(wantJson))
            assertEquals("$device json", normalise(wantTree), normalise(gotTree))
            compared++
        }
        assumeTrue("no variance CLI goldens captured yet", compared > 0)
    }

    @Test
    fun `outlier factors dataset reproduces outlier_factors py per device`() {
        val goldens = TraceGoldens.all()
        assumeTrue("trace goldens not present", goldens.isNotEmpty())
        var compared = 0
        goldens.groupBy { it.device }.forEach { (device, traces) ->
            val wantJson = TraceGoldens.root().resolve("_campaign").resolve(device).resolve("pass1-factors.json")
            if (!Files.isRegularFile(wantJson)) return@forEach
            val data = traces.sortedBy { StartupAnalysis.iterIndex(it.traceFileName) }.map { g ->
                OutlierFactors.recordOf(g.traceFileName, TraceProcessor.triplesOf(Csv.parse(g.csv("outlier_metrics"))))
            }
            val gotTree = StartupJson.parseToJsonElement(
                StartupJson.encodeToString(ListSerializer(OutlierFactors.Record.serializer()), data),
            )
            val wantTree = StartupJson.parseToJsonElement(Files.readString(wantJson))
            assertEquals("$device factors json", normalise(wantTree), normalise(gotTree))
            compared++
        }
        assumeTrue("no outlier_factors goldens captured yet", compared > 0)
    }

    /** Python's json.dump writes `1.0` where Kotlin writes `1.0` too, but ints vs floats and key order need levelling. */
    private fun normalise(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.filterValues { it !is JsonNull }.mapValues { normalise(it.value) }.toSortedMap())
        is JsonPrimitive -> if (el.isString) el else JsonPrimitive(el.content.toDouble())
        else -> kotlinx.serialization.json.JsonArray(el.jsonArray.map { normalise(it) })
    }
}
