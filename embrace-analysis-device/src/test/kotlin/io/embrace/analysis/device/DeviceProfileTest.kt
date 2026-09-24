package io.embrace.analysis.device

import io.embrace.analysis.common.json.StartupJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DeviceProfile.isComplete], and the `@EncodeDefault(NEVER)` contract that lets an empty profile
 * round-trip through [StartupJson] as `{}` while a full one keeps every snake_case field name.
 */
class DeviceProfileTest {

    @Test
    fun `isComplete is false for the empty profile and for a profile missing any one field, true when all seven are set`() {
        val full = DeviceProfile(
            apiLevel = 31,
            release = "12",
            vendor = "Google",
            socFamily = "Qualcomm",
            clusters = listOf(1804800L, 2803200L),
            ramClass = "3-4GB",
            storageClass = "ufs-class",
        )

        assertFalse(DeviceProfile().isComplete)
        assertFalse(full.copy(apiLevel = null).isComplete)
        assertFalse(full.copy(release = null).isComplete)
        assertFalse(full.copy(vendor = null).isComplete)
        assertFalse(full.copy(socFamily = null).isComplete)
        assertFalse(full.copy(clusters = null).isComplete)
        assertFalse(full.copy(ramClass = null).isComplete)
        assertFalse(full.copy(storageClass = null).isComplete)
        assertTrue(full.isComplete)
    }

    @Test
    fun `an empty profile encodes to an empty object, since every field is EncodeDefault NEVER`() {
        val json = StartupJson.encodeToString(DeviceProfile.serializer(), DeviceProfile())

        assertEquals("{}", json)
    }

    @Test
    fun `a full profile round-trips through decode and encode with its snake_case field names`() {
        val full = DeviceProfile(
            apiLevel = 31,
            release = "12",
            vendor = "Google",
            socFamily = "Qualcomm",
            clusters = listOf(1804800L, 2803200L),
            ramClass = "3-4GB",
            storageClass = "ufs-class",
        )

        val json = StartupJson.encodeToString(DeviceProfile.serializer(), full)
        listOf("\"api_level\"", "\"soc_family\"", "\"ram_class\"", "\"storage_class\"").forEach {
            assertTrue(json, json.contains(it))
        }
        val decoded = StartupJson.decodeFromString(DeviceProfile.serializer(), json)
        assertEquals(full, decoded)
    }
}
