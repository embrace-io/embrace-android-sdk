package io.embrace.android.embracesdk.internal.serialization

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EnvelopeResourceSerializerTest {

    private val serializer = EmbraceSerializer()

    private val fullResource = EnvelopeResource(
        attributes = linkedMapOf(
            "service.version" to JsonPrimitive("1.0.0"),
            "service.name" to JsonPrimitive("io.embrace.test"),
            "app_framework" to JsonPrimitive(AppFramework.NATIVE.value),
            "build_id" to JsonPrimitive("abc123"),
            "emb.app.environment" to JsonPrimitive("prod"),
            "device_architecture" to JsonPrimitive("arm64-v8a"),
            "os.version" to JsonPrimitive("13"),
            "jailbroken" to JsonPrimitive(false),
            "disk_total_capacity" to JsonPrimitive(64000000000L),
            "num_cores" to JsonPrimitive(8),
            "my.custom.attr" to JsonPrimitive("bar"),
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
        assertEquals(mapOf("service.version" to JsonPrimitive("1.0.0")), decoded.attributes)
    }

    private fun loadGoldenFile(filename: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(filename)).bufferedReader().readText()
}
