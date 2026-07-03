package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Envelope.Companion.createLogEnvelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.opentelemetry.kotlin.semconv.DeviceAttributes
import io.opentelemetry.kotlin.semconv.OsAttributes
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import io.opentelemetry.kotlin.semconv.TelemetryAttributes
import kotlinx.serialization.json.JsonPrimitive

fun fakeEmptyLogEnvelope(
    resource: EnvelopeResource = fakeEnvelopeResource,
    metadata: EnvelopeMetadata = fakeEnvelopeMetadata,
) = LogPayload().createLogEnvelope(resource = resource, metadata = metadata)

val fakeEnvelopeResource = EnvelopeResource(
    attributes = mapOf(
        ServiceAttributes.SERVICE_VERSION to JsonPrimitive("1.5"),
        ServiceAttributes.SERVICE_NAME to JsonPrimitive("com.my.app"),
        OsAttributes.OS_TYPE to JsonPrimitive("linux"),
        OsAttributes.OS_NAME to JsonPrimitive("Android"),
        OsAttributes.OS_VERSION to JsonPrimitive("15"),
        TelemetryAttributes.TELEMETRY_DISTRO_VERSION to JsonPrimitive("6.14.0"),
        DeviceAttributes.DEVICE_MANUFACTURER to JsonPrimitive("Google"),
        DeviceAttributes.DEVICE_MODEL_IDENTIFIER to JsonPrimitive("Pixel 5"),
        DeviceAttributes.DEVICE_MODEL_NAME to JsonPrimitive("Pixel 5"),
        "app_framework" to JsonPrimitive(AppFramework.NATIVE.value),
        "build_id" to JsonPrimitive("555"),
    ),
)

val fakeLaterEnvelopeResource = fakeEnvelopeResource.copy(
    attributes = fakeEnvelopeResource.attributes + (ServiceAttributes.SERVICE_VERSION to JsonPrimitive("1.6")),
)

val fakeEnvelopeMetadata = EnvelopeMetadata(
    userId = "abcde",
    email = "me@mycompany.com",
    username = "admin",
    personas = setOf("stuff-doer"),
)

val fakeLaterEnvelopeMetadata = fakeEnvelopeMetadata.copy(username = "new-admin")
