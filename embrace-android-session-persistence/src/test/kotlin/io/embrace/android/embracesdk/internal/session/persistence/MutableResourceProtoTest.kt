package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class MutableResourceProtoTest {

    @Test
    fun `fully populated resource round-trips`() {
        val resource = MutableResourceProto(
            jailbroken = true,
            screen_resolution = "1080x2400",
            uses_emmc_storage = true,
            hosted_platform_version = "hosted-platform",
            hosted_sdk_version = "hosted-sdk",
            javascript_patch_number = "js-patch",
            unity_build_id = "unity-build-id",
            react_native_bundle_id = "rn-bundle-id",
            react_native_version = "0.74.0",
            extras = mapOf("custom.key" to "custom.value"),
        )
        assertEquals(resource, roundTrip(resource))
    }

    @Test
    fun `absent fields decode back as null rather than identity values`() {
        val decoded = roundTrip(MutableResourceProto(react_native_bundle_id = "rn-bundle-id"))
        assertEquals("rn-bundle-id", decoded.react_native_bundle_id)
        assertNull(decoded.jailbroken)
        assertNull(decoded.uses_emmc_storage)
        assertNull(decoded.screen_resolution)
        assertNull(decoded.hosted_platform_version)
        assertNull(decoded.hosted_sdk_version)
        assertNull(decoded.javascript_patch_number)
        assertNull(decoded.unity_build_id)
        assertNull(decoded.react_native_version)
    }

    @Test
    fun `false and empty are distinguishable from absent`() {
        val decoded = roundTrip(
            MutableResourceProto(
                jailbroken = false,
                uses_emmc_storage = false,
                screen_resolution = "",
            ),
        )

        assertEquals(false, decoded.jailbroken)
        assertEquals(false, decoded.uses_emmc_storage)
        assertEquals("", decoded.screen_resolution)
    }

    @Test
    fun `empty extras round-trips`() {
        assertEquals(emptyMap<String, String>(), roundTrip(MutableResourceProto()).extras)
    }

    private fun roundTrip(resource: MutableResourceProto): MutableResourceProto =
        MutableResourceProto.ADAPTER.decode(MutableResourceProto.ADAPTER.encode(resource))
}
