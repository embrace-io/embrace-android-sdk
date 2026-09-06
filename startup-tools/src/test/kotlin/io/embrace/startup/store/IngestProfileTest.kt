package io.embrace.startup.store

import io.embrace.startup.core.json.DeviceProfile
import io.embrace.startup.core.json.StartupJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class IngestProfileTest {

    @Test
    fun `a run without provenance stores an empty profile, not seven nulls`() {
        val encoded = StartupJson.encodeToString(DeviceProfile.serializer(), DeviceProfile())
        assertEquals("{}", encoded)
        assertEquals(JsonObject(emptyMap()), Ingest.profileOf(null))
        assertEquals(JsonObject(emptyMap()), Ingest.profileOf(StartupJson.parseToJsonElement("""{"serial":"x"}""").jsonObject))
    }

    @Test
    fun `the flat run-metadata profile is used as is and the nested cell-state profile is unwrapped`() {
        val flat = StartupJson.parseToJsonElement(
            """{"device_profile":{"api_level":31,"release":"12","vendor":"Google","soc_family":"SDM845",
               "clusters":[1766400,2803200],"ram_class":"3-4GB","storage_class":"unknown"}}""",
        ).jsonObject
        assertEquals("31", Ingest.profileOf(flat).getValue("api_level").jsonPrimitive.content)

        // What the cell runner wrote: the reference set's whole device entry, profile nested.
        val nested = StartupJson.parseToJsonElement(
            """{"device_profile":{"api_level":31,"tier":"entry-mid","vendor":"Google","cool_gate_c":32.0,
               "profile":{"api_level":31,"release":"12","vendor":"Google","soc_family":"SDM845",
               "clusters":[1766400,2803200],"ram_class":"3-4GB","storage_class":"unknown"}}}""",
        ).jsonObject
        val profile = Ingest.profileOf(nested)
        assertEquals("12", profile.getValue("release").jsonPrimitive.content)
        assertNull("non-profile fields of the device entry are not part of the profile", profile["tier"])
        val decoded = StartupJson.decodeFromJsonElement(DeviceProfile.serializer(), profile)
        assertEquals(true, decoded.isComplete)
    }
}
