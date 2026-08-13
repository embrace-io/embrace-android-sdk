package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

internal class EnvelopeResourceMapperTest {

    @Test
    fun `every field maps to its proto counterpart`() {
        assertEquals(fullyPopulatedResourceProto, fullyPopulatedResource.toProto())
    }

    @Test
    fun `null fields map to absent proto fields`() {
        val proto = EnvelopeResource().toProto()
        assertEquals(EnvelopeResourceProto(), proto)
        assertNull(proto.app_version)
        assertNull(proto.app_framework)
        assertNull(proto.sdk_simple_version)
        assertNull(proto.jailbroken)
        assertNull(proto.disk_total_capacity)
        assertNull(proto.num_cores)
        assertNull(proto.uses_emmc_storage)
        assertEquals(emptyMap<String, String>(), proto.extras)
    }

    @Test
    fun `false and zero valued fields are preserved`() {
        val proto = EnvelopeResource(
            appVersion = "",
            jailbroken = false,
            usesEmmcStorage = false,
            numCores = 0,
            sdkSimpleVersion = 0,
            diskTotalCapacity = 0L,
        ).toProto()

        assertEquals("", proto.app_version)
        assertEquals(false, proto.jailbroken)
        assertEquals(false, proto.uses_emmc_storage)
        assertEquals(0, proto.num_cores)
        assertEquals(0, proto.sdk_simple_version)
        assertEquals(0L, proto.disk_total_capacity)
    }

    @Test
    fun `extras are preserved`() {
        val extras = mapOf("a" to "1", "b" to "2")
        assertEquals(extras, EnvelopeResource(extras = extras).toProto().extras)
    }

    @Test
    fun `empty extras map to an empty proto map`() {
        assertEquals(emptyMap<String, String>(), EnvelopeResource().toProto().extras)
    }

    @Test
    fun `every app framework maps to a distinct proto value`() {
        val mapped = AppFramework.entries.associateWith(AppFramework::toProto)

        assertEquals(
            mapOf(
                AppFramework.NATIVE to EnvelopeResourceProto.AppFramework.NATIVE,
                AppFramework.REACT_NATIVE to EnvelopeResourceProto.AppFramework.REACT_NATIVE,
                AppFramework.UNITY to EnvelopeResourceProto.AppFramework.UNITY,
                AppFramework.FLUTTER to EnvelopeResourceProto.AppFramework.FLUTTER,
            ),
            mapped,
        )
        assertEquals(AppFramework.entries.size, mapped.values.toSet().size)
        assertFalse(mapped.containsValue(EnvelopeResourceProto.AppFramework.UNSPECIFIED))

        // proto enum must match numeric values in the payload
        mapped.forEach { (framework, proto) ->
            assertEquals(framework.value, proto.value)
        }
    }

    @Test
    fun `app framework survives a round trip through the resource mapper`() {
        AppFramework.entries.forEach { framework ->
            val proto = EnvelopeResource(appFramework = framework).toProto()
            assertNotNull(proto.app_framework)
            assertEquals(framework.toProto(), proto.app_framework)
        }
    }
}
