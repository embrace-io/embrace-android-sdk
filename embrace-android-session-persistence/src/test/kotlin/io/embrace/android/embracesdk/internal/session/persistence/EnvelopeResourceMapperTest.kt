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

    @Test
    fun `every proto field maps back to its payload counterpart`() {
        assertEquals(fullyPopulatedResource, fullyPopulatedResourceProto.toPayload())
    }

    @Test
    fun `absent proto fields map to null payload fields`() {
        val resource = EnvelopeResourceProto().toPayload()
        assertEquals(EnvelopeResource(), resource)
        assertNull(resource.appVersion)
        assertNull(resource.appFramework)
        assertNull(resource.sdkSimpleVersion)
        assertNull(resource.jailbroken)
        assertNull(resource.diskTotalCapacity)
        assertNull(resource.numCores)
        assertNull(resource.usesEmmcStorage)
        assertEquals(emptyMap<String, String>(), resource.extras)
    }

    @Test
    fun `false and zero valued proto fields are preserved`() {
        val resource = EnvelopeResourceProto(
            app_version = "",
            jailbroken = false,
            uses_emmc_storage = false,
            num_cores = 0,
            sdk_simple_version = 0,
            disk_total_capacity = 0L,
        ).toPayload()

        assertEquals("", resource.appVersion)
        assertEquals(false, resource.jailbroken)
        assertEquals(false, resource.usesEmmcStorage)
        assertEquals(0, resource.numCores)
        assertEquals(0, resource.sdkSimpleVersion)
        assertEquals(0L, resource.diskTotalCapacity)
    }

    @Test
    fun `extras are preserved when mapping back`() {
        val extras = mapOf("a" to "1", "b" to "2")
        assertEquals(extras, EnvelopeResourceProto(extras = extras).toPayload().extras)
    }

    @Test
    fun `every proto app framework maps back and unspecified maps to null`() {
        val mapped = EnvelopeResourceProto.AppFramework.entries.associateWith { it.toPayload() }

        assertEquals(
            mapOf(
                EnvelopeResourceProto.AppFramework.UNSPECIFIED to null,
                EnvelopeResourceProto.AppFramework.NATIVE to AppFramework.NATIVE,
                EnvelopeResourceProto.AppFramework.REACT_NATIVE to AppFramework.REACT_NATIVE,
                EnvelopeResourceProto.AppFramework.UNITY to AppFramework.UNITY,
                EnvelopeResourceProto.AppFramework.FLUTTER to AppFramework.FLUTTER,
            ),
            mapped,
        )
    }

    @Test
    fun `app framework survives a full round trip`() {
        AppFramework.entries.forEach { framework ->
            assertEquals(framework, framework.toProto().toPayload())
        }
    }

    @Test
    fun `resource survives a full round trip`() {
        assertEquals(fullyPopulatedResource, fullyPopulatedResource.toProto().toPayload())
        assertEquals(EnvelopeResource(), EnvelopeResource().toProto().toPayload())
    }
}
