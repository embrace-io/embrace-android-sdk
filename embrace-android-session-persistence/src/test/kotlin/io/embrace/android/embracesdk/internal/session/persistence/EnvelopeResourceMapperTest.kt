package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Modifier

internal class EnvelopeResourceMapperTest {

    @Test
    fun `every field maps to its proto counterpart`() {
        assertEquals(fullyPopulatedResourceProto, fullyPopulatedResource.toProto())
    }

    @Test
    fun `the proto holds every resource field`() {
        assertEquals(payloadFieldNames(), protoFieldNames())
    }

    @Test
    fun `null fields map to absent proto fields`() {
        val proto = EnvelopeResource().toProto()
        assertEquals(ResourceProto(), proto)
        assertNull(proto.app_version)
        assertNull(proto.app_framework)
        assertNull(proto.sdk_simple_version)
        assertNull(proto.disk_total_capacity)
        assertNull(proto.num_cores)
        assertNull(proto.jailbroken)
        assertNull(proto.uses_emmc_storage)
        assertNull(proto.screen_resolution)
        assertEquals(emptyMap<String, String>(), proto.extras)
    }

    @Test
    fun `false and zero valued fields are preserved`() {
        val resource = EnvelopeResource(
            appVersion = "",
            jailbroken = false,
            usesEmmcStorage = false,
            screenResolution = "",
            numCores = 0,
            sdkSimpleVersion = 0,
            diskTotalCapacity = 0L,
        )

        with(resource.toProto()) {
            assertEquals("", app_version)
            assertEquals(0, num_cores)
            assertEquals(0, sdk_simple_version)
            assertEquals(0L, disk_total_capacity)
            assertEquals(false, jailbroken)
            assertEquals(false, uses_emmc_storage)
            assertEquals("", screen_resolution)
        }
    }

    @Test
    fun `extras are preserved`() {
        val extras = mapOf("a" to "1", "b" to "2")
        assertEquals(extras, EnvelopeResource(extras = extras).toProto().extras)
    }

    @Test
    fun `every app framework maps to a distinct proto value`() {
        val mapped = AppFramework.entries.associateWith(AppFramework::toProto)

        assertEquals(
            mapOf(
                AppFramework.NATIVE to ResourceProto.AppFramework.NATIVE,
                AppFramework.REACT_NATIVE to ResourceProto.AppFramework.REACT_NATIVE,
                AppFramework.UNITY to ResourceProto.AppFramework.UNITY,
                AppFramework.FLUTTER to ResourceProto.AppFramework.FLUTTER,
            ),
            mapped,
        )
        assertEquals(AppFramework.entries.size, mapped.values.toSet().size)
        assertFalse(mapped.containsValue(ResourceProto.AppFramework.UNSPECIFIED))

        // proto enum must match numeric values in the payload
        mapped.forEach { (framework, proto) ->
            assertEquals(framework.value, proto.value)
        }
    }

    @Test
    fun `every proto field maps back to its payload counterpart`() {
        assertEquals(fullyPopulatedResource, fullyPopulatedResourceProto.toPayload())
    }

    @Test
    fun `absent proto fields map to null payload fields`() {
        val resource = ResourceProto().toPayload()
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
        val resource = ResourceProto(
            app_version = "",
            num_cores = 0,
            sdk_simple_version = 0,
            disk_total_capacity = 0L,
            jailbroken = false,
            uses_emmc_storage = false,
            screen_resolution = "",
        ).toPayload()

        assertEquals("", resource.appVersion)
        assertEquals(false, resource.jailbroken)
        assertEquals(false, resource.usesEmmcStorage)
        assertEquals("", resource.screenResolution)
        assertEquals(0, resource.numCores)
        assertEquals(0, resource.sdkSimpleVersion)
        assertEquals(0L, resource.diskTotalCapacity)
    }

    @Test
    fun `extras are preserved when mapping back`() {
        val extras = mapOf("a" to "1", "b" to "2")
        assertEquals(extras, ResourceProto(extras = extras).toPayload().extras)
    }

    @Test
    fun `every proto app framework maps back and unspecified maps to null`() {
        val mapped = ResourceProto.AppFramework.entries.associateWith { it.toPayload() }

        assertEquals(
            mapOf(
                ResourceProto.AppFramework.UNSPECIFIED to null,
                ResourceProto.AppFramework.NATIVE to AppFramework.NATIVE,
                ResourceProto.AppFramework.REACT_NATIVE to AppFramework.REACT_NATIVE,
                ResourceProto.AppFramework.UNITY to AppFramework.UNITY,
                ResourceProto.AppFramework.FLUTTER to AppFramework.FLUTTER,
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

    private fun protoFieldNames(): Set<String> =
        instanceFieldNames(ResourceProto::class.java).mapTo(mutableSetOf()) { name ->
            name.split("_").reduce { acc, part -> acc + part.replaceFirstChar(Char::uppercase) }
        }

    private fun payloadFieldNames(): Set<String> =
        instanceFieldNames(EnvelopeResource::class.java).toSet()

    private fun instanceFieldNames(type: Class<*>): List<String> =
        type.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic || '$' in it.name }
            .map { it.name }
}
