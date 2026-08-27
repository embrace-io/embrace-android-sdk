package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class ImmutableResourceProtoTest {

    @Test
    fun `fully populated resource round-trips`() {
        val resource = ImmutableResourceProto(
            app_version = "1.2.3",
            app_framework = ImmutableResourceProto.AppFramework.REACT_NATIVE,
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
        )
        assertEquals(resource, roundTrip(resource))
    }

    @Test
    fun `absent fields decode back as null rather than identity values`() {
        val decoded = roundTrip(ImmutableResourceProto(app_version = "1.2.3"))
        assertEquals("1.2.3", decoded.app_version)
        assertNull(decoded.build_flavor)
        assertNull(decoded.sdk_simple_version)
        assertNull(decoded.num_cores)
        assertNull(decoded.disk_total_capacity)
        assertNull(decoded.device_soc_model)
    }

    @Test
    fun `zero is distinguishable from absent`() {
        val decoded = roundTrip(
            ImmutableResourceProto(
                num_cores = 0,
                disk_total_capacity = 0L,
                sdk_simple_version = 0,
                app_version = "",
            ),
        )

        assertEquals(0, decoded.num_cores)
        assertEquals(0L, decoded.disk_total_capacity)
        assertEquals(0, decoded.sdk_simple_version)
        assertEquals("", decoded.app_version)
    }

    @Test
    fun `app framework values match the payload enum and round-trip`() {
        assertEquals(1, ImmutableResourceProto.AppFramework.NATIVE.value)
        assertEquals(2, ImmutableResourceProto.AppFramework.REACT_NATIVE.value)
        assertEquals(3, ImmutableResourceProto.AppFramework.UNITY.value)
        assertEquals(4, ImmutableResourceProto.AppFramework.FLUTTER.value)

        ImmutableResourceProto.AppFramework.entries.forEach { framework ->
            val decoded = roundTrip(ImmutableResourceProto(app_framework = framework))
            assertEquals(framework, decoded.app_framework)
        }
    }

    @Test
    fun `absent app framework is distinct from unspecified`() {
        assertNull(roundTrip(ImmutableResourceProto(app_framework = null)).app_framework)
        assertEquals(
            ImmutableResourceProto.AppFramework.UNSPECIFIED,
            roundTrip(
                ImmutableResourceProto(app_framework = ImmutableResourceProto.AppFramework.UNSPECIFIED),
            ).app_framework,
        )
    }

    private fun roundTrip(resource: ImmutableResourceProto): ImmutableResourceProto =
        ImmutableResourceProto.ADAPTER.decode(ImmutableResourceProto.ADAPTER.encode(resource))
}
