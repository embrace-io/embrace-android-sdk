package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Envelope.Companion.createLogEnvelope
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.EnvelopeResourceValue
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.opentelemetry.kotlin.semconv.AndroidAttributes
import io.opentelemetry.kotlin.semconv.AppAttributes
import io.opentelemetry.kotlin.semconv.DeviceAttributes
import io.opentelemetry.kotlin.semconv.HostAttributes
import io.opentelemetry.kotlin.semconv.OsAttributes
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import io.opentelemetry.kotlin.semconv.TelemetryAttributes

fun fakeEmptyLogEnvelope(
    resource: EnvelopeResource = fakeEnvelopeResource,
    metadata: EnvelopeMetadata = fakeEnvelopeMetadata,
) = LogPayload().createLogEnvelope(resource = resource, metadata = metadata)

val fakeEnvelopeResource = EnvelopeResource(
    attributes = mapOf(
        // Every OTel semantic convention resource attribute Embrace sets.
        // Values are kept in sync with the fakes in the respective modules.
        ServiceAttributes.SERVICE_NAME to EnvelopeResourceValue.of("com.fake.package"),
        ServiceAttributes.SERVICE_VERSION to EnvelopeResourceValue.of("2.5.1"),
        AppAttributes.APP_BUILD_ID to EnvelopeResourceValue.of("fakeBuildId"),
        OsAttributes.OS_NAME to EnvelopeResourceValue.of("android"),
        OsAttributes.OS_TYPE to EnvelopeResourceValue.of("linux"),
        OsAttributes.OS_VERSION to EnvelopeResourceValue.of(FakeDeviceInfoValues.OS_VERSION),
        OsAttributes.OS_BUILD_ID to EnvelopeResourceValue.of(FakeDeviceInfoValues.OS_BUILD_ID),
        AndroidAttributes.ANDROID_OS_API_LEVEL to EnvelopeResourceValue.of(FakeDeviceInfoValues.ANDROID_OS_API_LEVEL),
        HostAttributes.HOST_ARCH to EnvelopeResourceValue.of(FakeDeviceInfoValues.ARCHITECTURE),
        DeviceAttributes.DEVICE_MANUFACTURER to EnvelopeResourceValue.of(FakeDeviceInfoValues.DEVICE_MANUFACTURER),
        DeviceAttributes.DEVICE_MODEL_IDENTIFIER to EnvelopeResourceValue.of(FakeDeviceInfoValues.DEVICE_MODEL),
        DeviceAttributes.DEVICE_MODEL_NAME to EnvelopeResourceValue.of(FakeDeviceInfoValues.DEVICE_MODEL),
        TelemetryAttributes.TELEMETRY_DISTRO_NAME to EnvelopeResourceValue.of("embrace-android-sdk"),
        TelemetryAttributes.TELEMETRY_DISTRO_VERSION to EnvelopeResourceValue.of("6.14.0"),

        // Every internal Embrace attribute set for the instantiation of the native SDK (i.e. not hosted)
        "app_framework" to EnvelopeResourceValue.of(AppFramework.NATIVE.value.toLong()),
        "build_type" to EnvelopeResourceValue.of("fakeBuildType"),
        "build_flavor" to EnvelopeResourceValue.of("fakeBuildFlavor"),
        "bundle_version" to EnvelopeResourceValue.of("99"),
        "sdk_simple_version" to EnvelopeResourceValue.of(53L),
        "jailbroken" to EnvelopeResourceValue.of(FakeDeviceInfoValues.JAILBROKEN),
        "disk_total_capacity" to EnvelopeResourceValue.of(FakeDeviceInfoValues.DISK_TOTAL_CAPACITY),
        "screen_resolution" to EnvelopeResourceValue.of(FakeDeviceInfoValues.SCREEN_RESOLUTION),
        "num_cores" to EnvelopeResourceValue.of(FakeDeviceInfoValues.NUMBER_OF_CORES.toLong()),
        "environment" to EnvelopeResourceValue.of("prod"),
    ),
)

val fakeLaterEnvelopeResource = fakeEnvelopeResource.copy(
    attributes = fakeEnvelopeResource.attributes + (ServiceAttributes.SERVICE_VERSION to EnvelopeResourceValue.of("2.5.2")),
)

val fakeEnvelopeMetadata = EnvelopeMetadata(
    userId = "abcde",
    email = "me@mycompany.com",
    username = "admin",
    personas = setOf("stuff-doer"),
)

val fakeLaterEnvelopeMetadata = fakeEnvelopeMetadata.copy(username = "new-admin")
