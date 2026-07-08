package io.embrace.android.embracesdk.internal.envelope.resource

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDevice
import io.embrace.android.embracesdk.fakes.FakeHostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

internal class EnvelopeResourceSourceImplTest {

    private val legacyKeys = listOf(
        "app_ecosystem_id",
        "app_version",
        "build_id",
        "device_architecture",
        "device_manufacturer",
        "device_model",
        "os_code",
        "os_type",
        "os_name",
        "os_version",
        "sdk_version",
    )

    @Test
    fun `getEnvelopeResource merges the OTel resource with Embrace internal attributes`() {
        val source = createSource(
            otelResourceAttributes = mapOf(
                ServiceAttributes.SERVICE_VERSION to "2.5.1",
                "my.custom.one" to "1",
                "build_type" to "should-be-ignored",
            ),
        )
        val attrs = source.getEnvelopeResource().attributes

        // OTel resource attributes pass through, and a customer-supplied attribute is kept
        assertEquals("2.5.1", attrs.getValue(ServiceAttributes.SERVICE_VERSION).stringValue)
        assertEquals("1", attrs.getValue("my.custom.one").stringValue)

        // the internal build_type wins over the colliding OTel value
        assertEquals("fakeBuildType", attrs.getValue("build_type").stringValue)

        // Embrace internal-only attribute values from fakes exist
        assertEquals(1, attrs.getValue("app_framework").intValue)
        assertEquals("fakeBuildFlavor", attrs.getValue("build_flavor").stringValue)
        assertEquals("99", attrs.getValue("bundle_version").stringValue)
        assertEquals(53, attrs.getValue("sdk_simple_version").intValue)
        assertEquals(false, attrs.getValue("jailbroken").booleanValue)
        assertEquals(10000000L, attrs.getValue("disk_total_capacity").longValue)
        assertEquals("1920x1080", attrs.getValue("screen_resolution").stringValue)
        assertEquals(8, attrs.getValue("num_cores").intValue)
        assertEquals("prod", attrs.getValue("environment").stringValue)

        // legacy bespoke keys are gone, replaced by their canonical semconv keys
        legacyKeys.forEach {
            assertFalse("'$it' should not exist in the envelope resource", attrs.containsKey(it))
        }
    }

    @Test
    fun `internal attributes added via add() are not overridable by customer attributes`() {
        val source = createSource(
            otelResourceAttributes = mapOf(
                "my.internal.key" to "customer-value",
                "other.custom" to "keep",
            ),
        ).apply {
            add("my.internal.key", "embrace-value")
        }
        val attrs = source.getEnvelopeResource().attributes

        assertEquals("embrace-value", attrs.getValue("my.internal.key").stringValue)
        assertEquals("keep", attrs.getValue("other.custom").stringValue)
    }

    private fun createSource(otelResourceAttributes: Map<String, String>) =
        EnvelopeResourceSourceImpl(
            configService = FakeConfigService(),
            hosted = FakeHostedSdkVersionInfo(),
            environment = AppEnvironment.Environment.PROD,
            device = FakeDevice(),
            versionCode = 53,
            rnBundleIdProvider = { null },
            otelResourceAttributesProvider = { otelResourceAttributes },
        )
}
