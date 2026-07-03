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

    @Test
    fun `getEnvelopeResource merges the OTel resource with Embrace internal attributes`() {
        val source = createSource(
            otelResourceAttributes = mapOf(
                ServiceAttributes.SERVICE_VERSION to "2.5.1",
                "my.custom.one" to "1",
                "build_id" to "should-be-ignored",
            ),
        )
        val attrs = source.getEnvelopeResource().attributes

        assertEquals("2.5.1", attrs.getValue(ServiceAttributes.SERVICE_VERSION).content)
        assertEquals("1", attrs.getValue("my.custom.one").content)
        assertEquals("fakeBuildId", attrs.getValue("build_id").content)
        assertEquals("prod", attrs.getValue("emb.app.environment").content)

        // legacy bespoke keys are gone, replaced by their canonical semconv keys
        assertFalse(attrs.containsKey("app_version"))
        assertFalse(attrs.containsKey("app_ecosystem_id"))
        assertFalse(attrs.containsKey("environment"))
        assertFalse(attrs.containsKey("sdk_version"))
        assertFalse(attrs.containsKey("device_manufacturer"))
        assertFalse(attrs.containsKey("device_model"))
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

        assertEquals("embrace-value", attrs.getValue("my.internal.key").content)
        assertEquals("keep", attrs.getValue("other.custom").content)
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
