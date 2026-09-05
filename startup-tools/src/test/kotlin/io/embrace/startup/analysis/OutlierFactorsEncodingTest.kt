package io.embrace.startup.analysis

import io.embrace.startup.core.json.StartupJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OutlierFactorsEncodingTest {

    @Test
    fun `a scalar the query returned no row for is absent from the dataset, never null`() {
        val record = OutlierFactors.Record(
            trace = "iter000.perfetto-trace",
            states = mapOf("Running" to 20.0),
            inproc = emptyMap(),
            othercpu = mapOf("system_server" to 1.5),
            windowMs = 30.0,
            gcSliceMs = 0.0,
        )
        val tree = StartupJson.encodeToJsonElement(OutlierFactors.Record.serializer(), record).jsonObject
        assertEquals(30.0, tree.getValue("window_ms").toString().toDouble(), 0.0)
        assertTrue("a present zero stays present", tree.containsKey("gc_slice_ms"))
        // port log #28: the Python omitted the key, so the Kotlin must too.
        assertFalse(tree.containsKey("freq_cl0_mhz"))
        assertFalse(tree.containsKey("freq_limit_cl0"))
        assertFalse(tree.containsKey("eff_mhz"))
        assertFalse(tree.containsKey("mem_swap"))
    }
}
