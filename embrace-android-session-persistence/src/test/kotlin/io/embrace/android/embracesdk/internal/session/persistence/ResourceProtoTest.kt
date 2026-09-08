package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ResourceProtoTest {

    @Test
    fun `fully populated resource round-trips`() {
        val resource = ResourceProto(
            app_version = "1.2.3",
            app_framework = ResourceProto.AppFramework.REACT_NATIVE,
            build_id = "build-id",
            app_ecosystem_id = "io.embrace.testapp",
            build_type = "release",
            build_flavor = "prod",
            environment = "prod",
            bundle_version = "42",
            sdk_version = "7.0.0",
            sdk_simple_version = 70,
            device_manufacturer = "Google",
            device_model = "Pixel 8",
            device_architecture = "arm64-v8a",
            disk_total_capacity = 128_000_000_000L,
            os_type = "linux",
            os_name = "android",
            os_version = "14",
            os_code = "34",
            num_cores = 8,
            device_soc_model = "Tensor G3",
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
        val decoded = roundTrip(ResourceProto(app_version = "1.2.3"))
        assertEquals("1.2.3", decoded.app_version)
        assertNull(decoded.build_flavor)
        assertNull(decoded.sdk_simple_version)
        assertNull(decoded.num_cores)
        assertNull(decoded.disk_total_capacity)
        assertNull(decoded.device_soc_model)
        assertNull(decoded.jailbroken)
        assertNull(decoded.uses_emmc_storage)
        assertNull(decoded.screen_resolution)
        assertNull(decoded.hosted_platform_version)
        assertNull(decoded.hosted_sdk_version)
        assertNull(decoded.javascript_patch_number)
        assertNull(decoded.unity_build_id)
        assertNull(decoded.react_native_bundle_id)
        assertNull(decoded.react_native_version)
    }

    @Test
    fun `zero false and empty are distinguishable from absent`() {
        val decoded = roundTrip(
            ResourceProto(
                num_cores = 0,
                disk_total_capacity = 0L,
                sdk_simple_version = 0,
                app_version = "",
                jailbroken = false,
                uses_emmc_storage = false,
                screen_resolution = "",
            ),
        )
        assertEquals(0, decoded.num_cores)
        assertEquals(0L, decoded.disk_total_capacity)
        assertEquals(0, decoded.sdk_simple_version)
        assertEquals("", decoded.app_version)
        assertEquals(false, decoded.jailbroken)
        assertEquals(false, decoded.uses_emmc_storage)
        assertEquals("", decoded.screen_resolution)
    }

    @Test
    fun `empty extras round-trips`() {
        assertEquals(emptyMap<String, String>(), roundTrip(ResourceProto()).extras)
    }

    @Test
    fun `app framework values match the payload enum and round-trip`() {
        assertEquals(1, ResourceProto.AppFramework.NATIVE.value)
        assertEquals(2, ResourceProto.AppFramework.REACT_NATIVE.value)
        assertEquals(3, ResourceProto.AppFramework.UNITY.value)
        assertEquals(4, ResourceProto.AppFramework.FLUTTER.value)

        ResourceProto.AppFramework.entries.forEach { framework ->
            val decoded = roundTrip(ResourceProto(app_framework = framework))
            assertEquals(framework, decoded.app_framework)
        }
    }

    @Test
    fun `absent app framework is distinct from unspecified`() {
        assertNull(roundTrip(ResourceProto(app_framework = null)).app_framework)
        assertEquals(
            ResourceProto.AppFramework.UNSPECIFIED,
            roundTrip(ResourceProto(app_framework = ResourceProto.AppFramework.UNSPECIFIED)).app_framework,
        )
    }

    private fun roundTrip(resource: ResourceProto): ResourceProto =
        ResourceProto.ADAPTER.decode(ResourceProto.ADAPTER.encode(resource))
}
