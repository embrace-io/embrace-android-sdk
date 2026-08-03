package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Envelope.Companion.createLogEnvelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.EnvelopeResourceValue
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.opentelemetry.kotlin.semconv.DeviceAttributes
import io.opentelemetry.kotlin.semconv.OsAttributes
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import io.opentelemetry.kotlin.semconv.TelemetryAttributes

fun fakeEmptyLogEnvelope(
    resource: EnvelopeResource = fakeEnvelopeResource,
    metadata: EnvelopeMetadata = fakeEnvelopeMetadata,
) = LogPayload().createLogEnvelope(resource = resource, metadata = metadata)

val fakeEnvelopeResource = EnvelopeResource(
    attributes = mapOf(
        ServiceAttributes.SERVICE_VERSION to EnvelopeResourceValue.of("1.5"),
        ServiceAttributes.SERVICE_NAME to EnvelopeResourceValue.of("com.my.app"),
        OsAttributes.OS_TYPE to EnvelopeResourceValue.of("linux"),
        OsAttributes.OS_NAME to EnvelopeResourceValue.of("Android"),
        OsAttributes.OS_VERSION to EnvelopeResourceValue.of("15"),
        TelemetryAttributes.TELEMETRY_DISTRO_VERSION to EnvelopeResourceValue.of("6.14.0"),
        DeviceAttributes.DEVICE_MANUFACTURER to EnvelopeResourceValue.of("Google"),
        DeviceAttributes.DEVICE_MODEL_IDENTIFIER to EnvelopeResourceValue.of("Pixel 5"),
        DeviceAttributes.DEVICE_MODEL_NAME to EnvelopeResourceValue.of("Pixel 5"),
        "app_framework" to EnvelopeResourceValue.of(AppFramework.NATIVE.value.toLong()),
        "build_id" to EnvelopeResourceValue.of("555"),
    ),
)

val fakeLaterEnvelopeResource = fakeEnvelopeResource.copy(
    attributes = fakeEnvelopeResource.attributes + (ServiceAttributes.SERVICE_VERSION to EnvelopeResourceValue.of("1.6")),
)

val fakeEnvelopeMetadata = EnvelopeMetadata(
    userId = "abcde",
    email = "me@mycompany.com",
    username = "admin",
    personas = setOf("stuff-doer"),
)

val fakeLaterEnvelopeMetadata = fakeEnvelopeMetadata.copy(username = "new-admin")
