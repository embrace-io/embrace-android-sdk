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
    fun `every immutable field maps to its proto counterpart`() {
        assertEquals(fullyPopulatedImmutableResourceProto, fullyPopulatedResource.toImmutableProto())
    }

    @Test
    fun `every mutable field maps to its proto counterpart`() {
        assertEquals(fullyPopulatedMutableResourceProto, fullyPopulatedResource.toMutableProto())
    }

    @Test
    fun `the two halves partition every resource field`() {
        val immutable = protoFieldNames(ImmutableResourceProto::class.java)
        val mutable = protoFieldNames(MutableResourceProto::class.java)

        assertEquals(emptySet<String>(), immutable intersect mutable)
        assertEquals(payloadFieldNames(), immutable + mutable)
    }

    @Test
    fun `null fields map to absent proto fields`() {
        val immutable = EnvelopeResource().toImmutableProto()
        assertEquals(ImmutableResourceProto(), immutable)
        assertNull(immutable.app_version)
        assertNull(immutable.app_framework)
        assertNull(immutable.sdk_simple_version)
        assertNull(immutable.disk_total_capacity)
        assertNull(immutable.num_cores)

        val mutable = EnvelopeResource().toMutableProto()
        assertEquals(MutableResourceProto(), mutable)
        assertNull(mutable.jailbroken)
        assertNull(mutable.uses_emmc_storage)
        assertNull(mutable.screen_resolution)
        assertEquals(emptyMap<String, String>(), mutable.extras)
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

        with(resource.toImmutableProto()) {
            assertEquals("", app_version)
            assertEquals(0, num_cores)
            assertEquals(0, sdk_simple_version)
            assertEquals(0L, disk_total_capacity)
        }
        with(resource.toMutableProto()) {
            assertEquals(false, jailbroken)
            assertEquals(false, uses_emmc_storage)
            assertEquals("", screen_resolution)
        }
    }

    @Test
    fun `extras are preserved`() {
        val extras = mapOf("a" to "1", "b" to "2")
        assertEquals(extras, EnvelopeResource(extras = extras).toMutableProto().extras)
    }

    @Test
    fun `every app framework maps to a distinct proto value`() {
        val mapped = AppFramework.entries.associateWith(AppFramework::toProto)

        assertEquals(
            mapOf(
                AppFramework.NATIVE to ImmutableResourceProto.AppFramework.NATIVE,
                AppFramework.REACT_NATIVE to ImmutableResourceProto.AppFramework.REACT_NATIVE,
                AppFramework.UNITY to ImmutableResourceProto.AppFramework.UNITY,
                AppFramework.FLUTTER to ImmutableResourceProto.AppFramework.FLUTTER,
            ),
            mapped,
        )
        assertEquals(AppFramework.entries.size, mapped.values.toSet().size)
        assertFalse(mapped.containsValue(ImmutableResourceProto.AppFramework.UNSPECIFIED))

        // proto enum must match numeric values in the payload
        mapped.forEach { (framework, proto) ->
            assertEquals(framework.value, proto.value)
        }
    }

    @Test
    fun `every proto field maps back to its payload counterpart`() {
        assertEquals(
            fullyPopulatedResource,
            fullyPopulatedImmutableResourceProto.toPayload(fullyPopulatedMutableResourceProto),
        )
    }

    @Test
    fun `absent proto fields map to null payload fields`() {
        val resource = ImmutableResourceProto().toPayload(MutableResourceProto())
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
        val resource = ImmutableResourceProto(
            app_version = "",
            num_cores = 0,
            sdk_simple_version = 0,
            disk_total_capacity = 0L,
        ).toPayload(
            MutableResourceProto(
                jailbroken = false,
                uses_emmc_storage = false,
                screen_resolution = "",
            ),
        )

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
        val resource = ImmutableResourceProto()
            .toPayload(MutableResourceProto(extras = extras))
        assertEquals(extras, resource.extras)
    }

    @Test
    fun `every proto app framework maps back and unspecified maps to null`() {
        val mapped = ImmutableResourceProto.AppFramework.entries.associateWith { it.toPayload() }

        assertEquals(
            mapOf(
                ImmutableResourceProto.AppFramework.UNSPECIFIED to null,
                ImmutableResourceProto.AppFramework.NATIVE to AppFramework.NATIVE,
                ImmutableResourceProto.AppFramework.REACT_NATIVE to AppFramework.REACT_NATIVE,
                ImmutableResourceProto.AppFramework.UNITY to AppFramework.UNITY,
                ImmutableResourceProto.AppFramework.FLUTTER to AppFramework.FLUTTER,
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

    private fun protoFieldNames(type: Class<*>): Set<String> =
        instanceFieldNames(type).mapTo(mutableSetOf()) { name ->
            name.split("_").reduce { acc, part -> acc + part.replaceFirstChar(Char::uppercase) }
        }

    private fun payloadFieldNames(): Set<String> =
        instanceFieldNames(EnvelopeResource::class.java).toSet()

    private fun instanceFieldNames(type: Class<*>): List<String> =
        type.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic || '$' in it.name }
            .map { it.name }
}
