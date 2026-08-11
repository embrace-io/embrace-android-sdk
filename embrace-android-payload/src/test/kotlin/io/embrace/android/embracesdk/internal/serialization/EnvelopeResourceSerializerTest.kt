package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.EnvelopeResourceValue
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EnvelopeResourceSerializerTest {

    private val serializer = EmbraceSerializer()

    private val fullResource = EnvelopeResource(
        attributes = linkedMapOf(
            "service.version" to EnvelopeResourceValue.of("1.0.0"),
            "service.name" to EnvelopeResourceValue.of("io.embrace.test"),
            "app_framework" to EnvelopeResourceValue.of(AppFramework.NATIVE.value.toLong()),
            "app.build_id" to EnvelopeResourceValue.of("abc123"),
            "environment" to EnvelopeResourceValue.of("prod"),
            "host.arch" to EnvelopeResourceValue.of("arm64-v8a"),
            "os.version" to EnvelopeResourceValue.of("13"),
            "jailbroken" to EnvelopeResourceValue.of(false),
            "disk_total_capacity" to EnvelopeResourceValue.of(64000000000L),
            "num_cores" to EnvelopeResourceValue.of(8L),
            "my.custom.attr" to EnvelopeResourceValue.of("bar"),
        ),
    )

    @Test
    fun `full resource matches the golden wire format and round trips preserving value types`() {
        val golden: EnvelopeResource = serializer.fromJson(loadGoldenFile("envelope_resource_full.json"))
        assertEquals(fullResource.attributes, golden.attributes)

        val roundTripped: EnvelopeResource = serializer.fromJson(serializer.toJson(fullResource))
        assertEquals(fullResource.attributes, roundTripped.attributes)
    }

    @Test
    fun `serializes empty resource as an empty object`() {
        assertEquals("{}", serializer.toJson(EnvelopeResource()))
    }

    @Test
    fun `drops json null entries on read`() {
        val decoded: EnvelopeResource = serializer.fromJson("""{"service.version":"1.0.0","ignored":null}""")
        assertEquals(mapOf("service.version" to EnvelopeResourceValue.of("1.0.0")), decoded.attributes)
    }

    private fun loadGoldenFile(filename: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(filename)).bufferedReader().readText()
}
