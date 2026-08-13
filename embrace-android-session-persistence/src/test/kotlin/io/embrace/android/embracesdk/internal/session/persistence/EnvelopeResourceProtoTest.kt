package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class EnvelopeResourceProtoTest {

    @Test
    fun `fully populated resource round-trips`() {
        val resource = EnvelopeResourceProto(
            app_version = "1.2.3",
            app_framework = EnvelopeResourceProto.AppFramework.REACT_NATIVE,
            build_id = "build-id",
            app_ecosystem_id = "io.embrace.testapp",
            build_type = "release",
            build_flavor = "prod",
            environment = "prod",
            bundle_version = "42",
            sdk_version = "7.0.0",
            sdk_simple_version = 70,
            react_native_bundle_id = "rn-bundle-id",
            react_native_version = "0.74.0",
            javascript_patch_number = "js-patch",
            hosted_platform_version = "hosted-platform",
            hosted_sdk_version = "hosted-sdk",
            unity_build_id = "unity-build-id",
            device_manufacturer = "Google",
            device_model = "Pixel 8",
            device_architecture = "arm64-v8a",
            jailbroken = false,
            disk_total_capacity = 128_000_000_000L,
            os_type = "linux",
            os_name = "android",
            os_version = "14",
            os_code = "34",
            screen_resolution = "1080x2400",
            num_cores = 8,
            uses_emmc_storage = false,
            device_soc_model = "Tensor G3",
            extras = mapOf("custom.key" to "custom.value"),
        )
        assertEquals(resource, EnvelopeResourceProto.ADAPTER.decode(EnvelopeResourceProto.ADAPTER.encode(resource)))
    }

    @Test
    fun `absent fields decode back as null rather than identity values`() {
        val resource = EnvelopeResourceProto(app_version = "1.2.3")
        val decoded = EnvelopeResourceProto.ADAPTER.decode(EnvelopeResourceProto.ADAPTER.encode(resource))

        assertEquals("1.2.3", decoded.app_version)
        assertNull(decoded.build_flavor)
        assertNull(decoded.react_native_bundle_id)
        assertNull(decoded.react_native_version)
        assertNull(decoded.javascript_patch_number)
        assertNull(decoded.unity_build_id)
        assertNull(decoded.hosted_platform_version)
        assertNull(decoded.sdk_simple_version)
        assertNull(decoded.num_cores)
        assertNull(decoded.jailbroken)
        assertNull(decoded.uses_emmc_storage)
        assertNull(decoded.disk_total_capacity)
        assertEquals(resource, decoded)
    }

    @Test
    fun `false and zero are distinguishable from absent`() {
        val resource = EnvelopeResourceProto(
            jailbroken = false,
            uses_emmc_storage = false,
            num_cores = 0,
            disk_total_capacity = 0L,
            app_version = "",
        )

        val decoded = EnvelopeResourceProto.ADAPTER.decode(EnvelopeResourceProto.ADAPTER.encode(resource))
        assertEquals(false, decoded.jailbroken)
        assertEquals(false, decoded.uses_emmc_storage)
        assertEquals(0, decoded.num_cores)
        assertEquals(0L, decoded.disk_total_capacity)
        assertEquals("", decoded.app_version)
    }

    @Test
    fun `empty extras round-trips`() {
        val resource = EnvelopeResourceProto()
        val decoded = EnvelopeResourceProto.ADAPTER.decode(EnvelopeResourceProto.ADAPTER.encode(resource))
        assertEquals(emptyMap<String, String>(), decoded.extras)
    }

    @Test
    fun `app framework values match the payload enum and round-trip`() {
        assertEquals(1, EnvelopeResourceProto.AppFramework.NATIVE.value)
        assertEquals(2, EnvelopeResourceProto.AppFramework.REACT_NATIVE.value)
        assertEquals(3, EnvelopeResourceProto.AppFramework.UNITY.value)
        assertEquals(4, EnvelopeResourceProto.AppFramework.FLUTTER.value)

        EnvelopeResourceProto.AppFramework.entries.forEach { framework ->
            val resource = EnvelopeResourceProto(app_framework = framework)
            val decoded = EnvelopeResourceProto.ADAPTER.decode(EnvelopeResourceProto.ADAPTER.encode(resource))
            assertEquals(framework, decoded.app_framework)
        }
    }

    @Test
    fun `absent app framework is distinct from unspecified`() {
        val absent = EnvelopeResourceProto(app_framework = null)
        val unspecified = EnvelopeResourceProto(app_framework = EnvelopeResourceProto.AppFramework.UNSPECIFIED)

        assertNull(EnvelopeResourceProto.ADAPTER.decode(EnvelopeResourceProto.ADAPTER.encode(absent)).app_framework)
        assertEquals(
            EnvelopeResourceProto.AppFramework.UNSPECIFIED,
            EnvelopeResourceProto.ADAPTER.decode(EnvelopeResourceProto.ADAPTER.encode(unspecified)).app_framework,
        )
    }
}
